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

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
        when(gpsInfluxService.querySamples(any(), any(), any())).thenReturn(List.of(
                new GpsSample("sim_019", 121.5, 31.2, 42.0, 90.0,
                        Instant.now().minusSeconds(5))));

        VehicleLocationResponse response = service.getLatestLocation(1L);

        assertEquals(1L, response.getVehicleId());
        assertEquals("沪A00019", response.getPlateNumber());
        assertEquals(121.5, response.getLongitude());
        assertEquals(10L, response.getTaskId());
        assertTrue(response.isOnline());
        verify(dataScopeService).requireVehicleAccess(vehicle);
    }

    @Test
    void latestListAppliesExistingVehicleScopeBeforeRealtimeQuery() {
        Vehicle vehicle = vehicle();
        when(vehicleMapper.selectList(any())).thenReturn(List.of(vehicle));
        when(transportTaskMapper.selectList(any())).thenReturn(List.of());
        when(gpsInfluxService.querySamples(any(), any(), any())).thenReturn(List.of(
                new GpsSample("sim_019", 121.5, 31.2, null, null,
                        Instant.now().minusSeconds(5))));

        assertEquals(1, service.getLatestLocations().size());

        verify(dataScopeService).applyVehicleScope(any(),
                org.mockito.ArgumentMatchers.isNull());
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
    }

    @Test
    void mapsProviderFailureToStandardUnavailableError() {
        when(vehicleMapper.selectById(1L)).thenReturn(vehicle());
        when(gpsInfluxService.querySamples(any(), any(), any()))
                .thenThrow(new IllegalStateException("test provider failure"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getLatestLocation(1L));
        assertEquals(ErrorCode.REALTIME_PROVIDER_UNAVAILABLE, exception.getErrorCode());
    }

    private Vehicle vehicle() {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(1L);
        vehicle.setPlateNumber("沪A00019");
        vehicle.setSimCode("sim_019");
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
