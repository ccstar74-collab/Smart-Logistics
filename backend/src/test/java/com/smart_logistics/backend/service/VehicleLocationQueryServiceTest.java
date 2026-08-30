package com.smart_logistics.backend.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.smart_logistics.backend.dto.realtime.GpsSample;
import com.smart_logistics.backend.dto.response.VehicleLocationResponse;
import com.smart_logistics.backend.entity.TransportTask;
import com.smart_logistics.backend.entity.Vehicle;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.mapper.TransportTaskMapper;
import com.smart_logistics.backend.mapper.VehicleMapper;
import com.smart_logistics.backend.security.BusinessDataScopeService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleLocationQueryServiceTest {
    @Mock private VehicleMapper vehicleMapper;
    @Mock private TransportTaskMapper transportTaskMapper;
    @Mock private BusinessDataScopeService dataScopeService;
    @Mock private GpsInfluxService gpsInfluxService;
    private VehicleLocationQueryService service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(
                new MybatisConfiguration(), "location-vehicle"), Vehicle.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(
                new MybatisConfiguration(), "location-task"), TransportTask.class);
        service = new VehicleLocationQueryService(vehicleMapper, transportTaskMapper,
                dataScopeService, gpsInfluxService, Duration.ofHours(24), Duration.ofMinutes(2));
    }

    @Test
    void mapsAuthorizedVehicleAndMockedRealtimeSample() {
        Vehicle vehicle = vehicle();
        when(vehicleMapper.selectById(1L)).thenReturn(vehicle);
        when(transportTaskMapper.selectList(any())).thenReturn(List.of(task()));
        when(gpsInfluxService.queryLatestSamples(any(), any())).thenReturn(List.of(
                new GpsSample("sim_019", 120.0, 30.0, 10.0, 45.0,
                        Instant.now().minus(Duration.ofHours(23))),
                new GpsSample("sim_019", 121.5, 31.2, 42.0, 90.0,
                        Instant.now().minusSeconds(5))));

        VehicleLocationResponse response = service.getLatestLocation(1L);

        assertEquals(1L, response.getVehicleId());
        assertEquals("沪A00019", response.getPlateNumber());
        assertEquals(121.5, response.getLongitude());
        assertEquals(10L, response.getTaskId());
        assertTrue(response.isOnline());
        verify(dataScopeService).requireVehicleAccess(vehicle);
        verify(gpsInfluxService).queryLatestSamples(
                eq(List.of("sim_019")), eq(Duration.ofHours(24)));
        verify(gpsInfluxService, never()).querySamples(any(), any(), any());
    }

    @Test
    void latestListAppliesExistingVehicleScopeBeforeRealtimeQuery() {
        Vehicle vehicle = vehicle();
        Vehicle second = vehicle(2L, "sim_020", "沪A00020");
        when(vehicleMapper.selectList(any())).thenReturn(List.of(vehicle, second));
        when(transportTaskMapper.selectList(any())).thenReturn(List.of());
        when(gpsInfluxService.queryLatestSamples(any(), any())).thenReturn(List.of(
                new GpsSample("sim_019", 121.5, 31.2, null, null,
                        Instant.now().minusSeconds(5)),
                new GpsSample("sim_020", 121.6, 31.3, null, null,
                        Instant.now().minusSeconds(10))));

        assertEquals(2, service.getLatestLocations().size());

        verify(dataScopeService).applyVehicleScope(any(),
                org.mockito.ArgumentMatchers.isNull());
        verify(gpsInfluxService).queryLatestSamples(
                eq(List.of("sim_019", "sim_020")), eq(Duration.ofHours(24)));
        verify(gpsInfluxService, never()).querySamples(any(), any(), any());
    }

    @Test
    void rejectsOutOfScopeVehicleBeforeRealtimeCall() {
        Vehicle vehicle = vehicle();
        when(vehicleMapper.selectById(1L)).thenReturn(vehicle);
        doThrow(new BusinessException(ErrorCode.FORBIDDEN, "forbidden"))
                .when(dataScopeService).requireVehicleAccess(vehicle);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getLatestLocation(1L));

        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
        verify(gpsInfluxService, never()).querySamples(any(), any(), any());
        verify(gpsInfluxService, never()).queryLatestSamples(any(), any());
    }

    @Test
    void validatesHistoryRangeBeforeDatabaseOrInflux() {
        OffsetDateTime time = OffsetDateTime.parse("2026-08-25T10:00:00+08:00");
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getLocationHistory(1L, time, time.minusMinutes(1)));
        assertEquals(ErrorCode.INVALID_PARAMETER, exception.getErrorCode());
        verify(vehicleMapper, never()).selectById(any());
        verify(gpsInfluxService, never()).querySamples(any(), any(), any());
    }

    @Test
    void emptyHistoryIsValid() {
        when(vehicleMapper.selectById(1L)).thenReturn(vehicle());
        when(gpsInfluxService.querySamples(any(), any(), any())).thenReturn(List.of());
        OffsetDateTime start = OffsetDateTime.parse("2026-08-25T09:00:00+08:00");
        assertEquals(List.of(), service.getLocationHistory(1L, start, start.plusHours(1)));
        verify(gpsInfluxService).querySamples(any(), any(), any());
        verify(gpsInfluxService, never()).queryLatestSamples(any(), any());
    }

    @Test
    void returnsLastKnownLocationAcrossTwentyFourHoursAsOffline() {
        Vehicle vehicle = vehicle();
        when(vehicleMapper.selectById(1L)).thenReturn(vehicle);
        when(transportTaskMapper.selectList(any())).thenReturn(List.of());
        when(gpsInfluxService.queryLatestSamples(any(), any())).thenReturn(List.of(
                new GpsSample("sim_019", 121.5, 31.2, 42.0, 90.0,
                        Instant.now().minus(Duration.ofHours(23)))));

        VehicleLocationResponse response = service.getLatestLocation(1L);

        assertEquals(false, response.isOnline());
        verify(gpsInfluxService).queryLatestSamples(
                eq(List.of("sim_019")), eq(Duration.ofHours(24)));
    }

    @Test
    void mapsProviderFailureToStandardUnavailableError() {
        when(vehicleMapper.selectById(1L)).thenReturn(vehicle());
        when(gpsInfluxService.queryLatestSamples(any(), any()))
                .thenThrow(new IllegalStateException("test provider failure"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getLatestLocation(1L));
        assertEquals(ErrorCode.REALTIME_PROVIDER_UNAVAILABLE, exception.getErrorCode());
    }

    @Test
    void latestOnlineGpsReusesOnlineThresholdAndRequiresMatchingSimCode() {
        Instant now = Instant.parse("2026-08-28T08:00:00Z");
        service = new VehicleLocationQueryService(vehicleMapper, transportTaskMapper,
                dataScopeService, gpsInfluxService, Duration.ofHours(24),
                Duration.ofMinutes(2), Clock.fixed(now, ZoneOffset.UTC));
        GpsSample expected = new GpsSample("sim_019", 121.5, 31.2, null, null,
                now.minusSeconds(30));
        when(gpsInfluxService.queryLatestSamples(any(), any())).thenReturn(List.of(
                new GpsSample("sim_020", 122.0, 32.0, null, null,
                        now.minusSeconds(1)), expected));

        assertEquals(expected, service.getLatestOnlineGps("sim_019"));
    }

    @Test
    void latestOnlineGpsRejectsMissingAndStaleSamples() {
        Instant now = Instant.parse("2026-08-28T08:00:00Z");
        service = new VehicleLocationQueryService(vehicleMapper, transportTaskMapper,
                dataScopeService, gpsInfluxService, Duration.ofHours(24),
                Duration.ofMinutes(2), Clock.fixed(now, ZoneOffset.UTC));
        when(gpsInfluxService.queryLatestSamples(any(), any()))
                .thenReturn(List.of())
                .thenReturn(List.of(new GpsSample("sim_019", 121.5, 31.2,
                        null, null, now.minusSeconds(121))));

        BusinessException missing = assertThrows(BusinessException.class,
                () -> service.getLatestOnlineGps("sim_019"));
        BusinessException stale = assertThrows(BusinessException.class,
                () -> service.getLatestOnlineGps("sim_019"));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, missing.getErrorCode());
        assertEquals(ErrorCode.STATE_CONFLICT, stale.getErrorCode());
        assertEquals("vehicle latest location is offline", stale.getMessage());
    }

    @Test
    void latestOnlineGpsRejectsInvalidCoordinates() {
        Instant now = Instant.parse("2026-08-28T08:00:00Z");
        service = new VehicleLocationQueryService(vehicleMapper, transportTaskMapper,
                dataScopeService, gpsInfluxService, Duration.ofHours(24),
                Duration.ofMinutes(2), Clock.fixed(now, ZoneOffset.UTC));
        when(gpsInfluxService.queryLatestSamples(any(), any())).thenReturn(List.of(
                new GpsSample("sim_019", Double.NaN, 31.2, null, null,
                        now.minusSeconds(10))));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getLatestOnlineGps("sim_019"));

        assertEquals(ErrorCode.INVALID_PARAMETER, exception.getErrorCode());
    }

    private Vehicle vehicle() {
        return vehicle(1L, "sim_019", "沪A00019");
    }

    private Vehicle vehicle(Long id, String simCode, String plateNumber) {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(id);
        vehicle.setPlateNumber(plateNumber);
        vehicle.setSimCode(simCode);
        return vehicle;
    }

    private TransportTask task() {
        TransportTask task = new TransportTask();
        task.setId(10L);
        task.setVehicleId(1L);
        task.setStatus("TRANSPORTING");
        return task;
    }
}
