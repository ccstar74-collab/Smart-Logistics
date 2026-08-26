package com.smart_logistics.backend.service.eta;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.smart_logistics.backend.dto.realtime.EtaRealtimeMessage;
import com.smart_logistics.backend.dto.realtime.GpsSample;
import com.smart_logistics.backend.entity.TransportTask;
import com.smart_logistics.backend.entity.Vehicle;
import com.smart_logistics.backend.enums.TransportTaskStatus;
import com.smart_logistics.backend.handler.GpsWebSocketHandler;
import com.smart_logistics.backend.mapper.TransportTaskMapper;
import com.smart_logistics.backend.mapper.VehicleMapper;
import com.smart_logistics.backend.service.GpsInfluxService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EtaCalculationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-26T08:00:00Z");

    @Mock private TransportTaskMapper transportTaskMapper;
    @Mock private VehicleMapper vehicleMapper;
    @Mock private GpsInfluxService gpsInfluxService;
    @Mock private EtaRouteProvider routeProvider;
    @Mock private GpsWebSocketHandler webSocketHandler;

    private EtaCalculationService service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "eta-test"),
                TransportTask.class);
        service = new EtaCalculationService(
                transportTaskMapper, vehicleMapper, gpsInfluxService, routeProvider,
                new RouteProgressProjector(), webSocketHandler,
                Duration.ofMinutes(2), Duration.ofMinutes(10),
                Duration.ofHours(1), Duration.ofMinutes(2),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void projectsLatestGpsOntoPlannedRouteAndPushesUpdatedEta() {
        TransportTask task = transportingTask();
        when(vehicleMapper.selectById(2L)).thenReturn(vehicle());
        when(gpsInfluxService.querySamples(any(), any(), any())).thenReturn(List.of(
                sample(106.5750, 29.4950, 20.0, NOW.minusSeconds(60)),
                sample(106.5800, 29.5000, 30.0, NOW.minusSeconds(5))));
        when(routeProvider.plan(106.5700, 29.4900, 106.6100, 29.5200))
                .thenReturn(plannedRoute());
        when(transportTaskMapper.update(isNull(), any(LambdaUpdateWrapper.class)))
                .thenReturn(1);

        assertTrue(service.refreshTask(task, NOW));

        verify(routeProvider).plan(106.5700, 29.4900, 106.6100, 29.5200);
        verify(transportTaskMapper).update(isNull(), any(LambdaUpdateWrapper.class));
        verify(webSocketHandler).broadcastEta(any(EtaRealtimeMessage.class));
    }

    @Test
    void cachesTaskPolylineAcrossRefreshes() {
        TransportTask task = transportingTask();
        when(vehicleMapper.selectById(2L)).thenReturn(vehicle());
        when(gpsInfluxService.querySamples(any(), any(), any())).thenReturn(List.of(
                sample(106.5800, 29.5000, 30.0, NOW.minusSeconds(5))));
        when(routeProvider.plan(any(Double.class), any(Double.class),
                any(Double.class), any(Double.class))).thenReturn(plannedRoute());
        when(transportTaskMapper.update(isNull(), any(LambdaUpdateWrapper.class)))
                .thenReturn(1);

        assertTrue(service.refreshTask(task, NOW));
        assertTrue(service.refreshTask(task, NOW.plusSeconds(10)));

        verify(routeProvider, times(1)).plan(any(Double.class), any(Double.class),
                any(Double.class), any(Double.class));
    }

    @Test
    void skipsLegacyTaskWithoutCompleteRouteCoordinates() {
        TransportTask task = transportingTask();
        task.setStartLongitude(null);
        task.setStartLatitude(null);

        assertFalse(service.refreshTask(task, NOW));

        verify(vehicleMapper, never()).selectById(any());
        verify(gpsInfluxService, never()).querySamples(any(), any(), any());
    }

    @Test
    void skipsTaskWithoutRecentGps() {
        when(vehicleMapper.selectById(2L)).thenReturn(vehicle());
        when(gpsInfluxService.querySamples(any(), any(), any())).thenReturn(List.of());

        assertFalse(service.refreshTask(transportingTask(), NOW));

        verify(routeProvider, never()).plan(any(Double.class), any(Double.class),
                any(Double.class), any(Double.class));
    }

    @Test
    void throttlesSmallEtaChangesUntilForcePersistInterval() {
        TransportTask task = transportingTask();
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        task.setEstimatedArrivalTime(LocalDateTime.ofInstant(NOW.plusSeconds(600), zone));
        task.setEtaCalculatedAt(LocalDateTime.ofInstant(NOW.minusSeconds(30), zone));
        when(vehicleMapper.selectById(2L)).thenReturn(vehicle());
        when(gpsInfluxService.querySamples(any(), any(), any())).thenReturn(List.of(
                sample(106.5800, 29.5000, 30.0, NOW.minusSeconds(5))));
        when(routeProvider.plan(any(Double.class), any(Double.class),
                any(Double.class), any(Double.class))).thenReturn(plannedRoute());

        assertFalse(service.refreshTask(task, NOW));

        verify(transportTaskMapper, never()).update(isNull(), any(LambdaUpdateWrapper.class));
        verify(webSocketHandler, never()).broadcastEta(any());
    }

    @Test
    void summarizesProviderFailureWithoutStoppingOtherTasks() {
        when(transportTaskMapper.selectList(any())).thenReturn(List.of(transportingTask()));
        when(vehicleMapper.selectById(2L)).thenReturn(vehicle());
        when(gpsInfluxService.querySamples(any(), any(), any())).thenReturn(List.of(
                sample(106.5800, 29.5000, 30.0, NOW.minusSeconds(5))));
        when(routeProvider.plan(any(Double.class), any(Double.class),
                any(Double.class), any(Double.class)))
                .thenThrow(new EtaProviderException("provider unavailable"));

        EtaCalculationService.EtaRefreshSummary summary =
                service.refreshTransportingTasks();

        assertEquals(1, summary.total());
        assertEquals(0, summary.updated());
        assertEquals(0, summary.skipped());
        assertEquals(1, summary.failed());
    }

    private TransportTask transportingTask() {
        TransportTask task = new TransportTask();
        task.setId(1L);
        task.setVehicleId(2L);
        task.setStatus(TransportTaskStatus.TRANSPORTING.name());
        task.setStartLongitude(106.5700);
        task.setStartLatitude(29.4900);
        task.setEndLongitude(106.6100);
        task.setEndLatitude(29.5200);
        return task;
    }

    private Vehicle vehicle() {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(2L);
        vehicle.setSimCode("real_001");
        return vehicle;
    }

    private GpsSample sample(double longitude, double latitude,
                             double speedKmh, Instant collectedAt) {
        return new GpsSample("real_001", longitude, latitude,
                speedKmh, 90.0, collectedAt);
    }

    private EtaPlannedRoute plannedRoute() {
        return new EtaPlannedRoute(List.of(
                converted(106.5700, 29.4900),
                converted(106.5800, 29.5000),
                converted(106.6100, 29.5200)),
                5_500, Duration.ofMinutes(12));
    }

    private EtaCoordinate converted(double longitude, double latitude) {
        Wgs84ToGcj02Converter.Coordinate value =
                Wgs84ToGcj02Converter.convert(longitude, latitude);
        return new EtaCoordinate(value.longitude(), value.latitude());
    }
}
