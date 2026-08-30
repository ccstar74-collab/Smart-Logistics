package com.smart_logistics.backend.service.eta;

import com.smart_logistics.backend.dto.TransportTaskRouteSnapshot;
import com.smart_logistics.backend.dto.response.PlannedRouteResponse;
import com.smart_logistics.backend.dto.response.TransportTaskResponse;
import com.smart_logistics.backend.entity.TransportTask;
import com.smart_logistics.backend.entity.Vehicle;
import com.smart_logistics.backend.enums.TransportTaskRouteStatus;
import com.smart_logistics.backend.enums.TransportTaskStatus;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.mapper.VehicleMapper;
import com.smart_logistics.backend.service.TransportTaskRouteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EtaPlannedRouteServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-26T08:00:00Z");

    @Mock private EtaRouteProvider routeProvider;
    @Mock private VehicleMapper vehicleMapper;
    @Mock private TransportTaskRouteService taskRouteService;

    private EtaPlannedRouteService service;

    @BeforeEach
    void setUp() {
        service = new EtaPlannedRouteService(routeProvider, vehicleMapper, taskRouteService);
    }

    @Test
    void firstLegacyReadPlansPersistsAndSubsequentReadUsesDatabaseSnapshot() {
        stubVehicle("sim_008");
        TransportTaskRouteSnapshot snapshot = snapshot();
        when(taskRouteService.getActiveRoute(1L))
                .thenReturn(Optional.empty(), Optional.of(snapshot));
        when(routeProvider.plan(106.57, 29.49, 106.61, 29.52)).thenReturn(route());
        when(taskRouteService.persistInitialActiveRoute(1L, route())).thenReturn(snapshot);

        PlannedRouteResponse first = service.getResponse(
                taskResponse(TransportTaskStatus.WAITING));
        PlannedRouteResponse second = service.getResponse(
                taskResponse(TransportTaskStatus.WAITING));

        assertEquals(1L, first.taskId());
        assertEquals("route_fixed", first.routeId());
        assertEquals(1, first.routeVersion());
        assertEquals(TransportTaskRouteStatus.ACTIVE, first.routeStatus());
        assertEquals("sim_008", first.vehicleDeviceCode());
        assertEquals("AMAP", first.provider());
        assertEquals("GCJ02", first.coordinateSystem());
        assertEquals(List.of(106.5701, 29.4901), first.points().getFirst());
        assertEquals(2, first.points().size());
        assertEquals(5_500, first.distanceMeters());
        assertEquals(first.routeId(), second.routeId());
        assertEquals(first.routeVersion(), second.routeVersion());
        assertEquals(first.points(), second.points());
        assertEquals(first.generatedAt(), second.generatedAt());
        verify(routeProvider, times(1)).plan(106.57, 29.49, 106.61, 29.52);
        verify(taskRouteService, times(1)).persistInitialActiveRoute(1L, route());
    }

    @Test
    void transportingTaskReadsPersistedRouteWithoutProviderCall() {
        stubVehicle("sim_019");
        when(taskRouteService.getActiveRoute(1L)).thenReturn(Optional.of(snapshot()));

        PlannedRouteResponse response = service.getResponse(
                taskResponse(TransportTaskStatus.TRANSPORTING));

        assertEquals("sim_019", response.vehicleDeviceCode());
        assertEquals(TransportTaskRouteStatus.ACTIVE, response.routeStatus());
        assertEquals(2, response.points().size());
        verify(routeProvider, never()).plan(anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void newlyCreatedWaitingTaskReadsPersistedRouteWithoutProviderCall() {
        stubVehicle("sim_008");
        when(taskRouteService.getActiveRoute(1L)).thenReturn(Optional.of(snapshot()));

        PlannedRouteResponse response = service.getResponse(
                taskResponse(TransportTaskStatus.WAITING));

        assertEquals("route_fixed", response.routeId());
        assertEquals(1, response.routeVersion());
        assertEquals("sim_008", response.vehicleDeviceCode());
        verify(routeProvider, never()).plan(anyDouble(), anyDouble(), anyDouble(), anyDouble());
        verify(taskRouteService, never()).persistInitialActiveRoute(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(EtaPlannedRoute.class));
    }

    @Test
    void plannedRouteImmediatelyReflectsActivatedVersion() {
        stubVehicle("sim_019");
        TransportTaskRouteSnapshot activeV2 = new TransportTaskRouteSnapshot(
                8L, "route_v2", 1L, "AMAP", "GCJ02",
                List.of(List.of(106.5701, 29.4901), List.of(106.6201, 29.5301)),
                5_700, 750, 2, TransportTaskRouteStatus.ACTIVE,
                snapshot().createdAt(), snapshot().updatedAt());
        when(taskRouteService.getActiveRoute(1L)).thenReturn(Optional.of(activeV2));

        PlannedRouteResponse response = service.getResponse(
                taskResponse(TransportTaskStatus.TRANSPORTING));

        assertEquals("route_v2", response.routeId());
        assertEquals(2, response.routeVersion());
        assertEquals(5_700, response.distanceMeters());
    }

    @Test
    void etaReadsTheSamePersistedActiveRoute() {
        when(taskRouteService.getActiveRoute(1L)).thenReturn(Optional.of(snapshot()));
        TransportTask task = new TransportTask();
        task.setId(1L);

        EtaPlannedRoute route = service.getRoute(task);

        assertEquals(5_500, route.distanceMeters());
        assertEquals(Duration.ofSeconds(720), route.referenceDuration());
        assertEquals(new EtaCoordinate(106.5701, 29.4901), route.polyline().getFirst());
        verify(routeProvider, never()).plan(anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void completedTaskCannotReadPlannedRoute() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getResponse(taskResponse(TransportTaskStatus.COMPLETED)));

        assertEquals(ErrorCode.STATE_CONFLICT, exception.getErrorCode());
        verify(vehicleMapper, never()).selectById(20L);
        verify(taskRouteService, never()).getActiveRoute(1L);
    }

    @Test
    void convertsWgs84OriginToGcj02WithoutConvertingDestination() {
        double wgsLongitude = 106.570123;
        double wgsLatitude = 29.490987;
        double destinationLongitude = 106.610987;
        double destinationLatitude = 29.520123;
        when(routeProvider.plan(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(route());

        service.planRouteFromWgs84Origin(wgsLongitude, wgsLatitude,
                destinationLongitude, destinationLatitude);

        Wgs84ToGcj02Converter.Coordinate converted =
                Wgs84ToGcj02Converter.convert(wgsLongitude, wgsLatitude);
        verify(routeProvider).plan(converted.longitude(), converted.latitude(),
                destinationLongitude, destinationLatitude);
    }

    @Test
    void rejectsLegacyTaskWithoutCompleteCoordinates() {
        stubVehicle("sim_000");
        when(taskRouteService.getActiveRoute(1L)).thenReturn(Optional.empty());
        TransportTaskResponse incomplete = new TransportTaskResponse(
                1L, "T1", 10L, 20L, "A", null, null,
                "B", 106.61, 29.52, null, null, null, null,
                TransportTaskStatus.WAITING, null, null, null, null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getResponse(incomplete));

        assertEquals(ErrorCode.STATE_CONFLICT, exception.getErrorCode());
        verify(routeProvider, never()).plan(anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void missingVehicleSimCodeIsExplicitBusinessError() {
        stubVehicle(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getResponse(taskResponse(TransportTaskStatus.WAITING)));

        assertEquals(ErrorCode.STATE_CONFLICT, exception.getErrorCode());
        assertEquals("transport task vehicle simCode is missing", exception.getMessage());
        verify(taskRouteService, never()).getActiveRoute(1L);
    }

    @Test
    void missingBoundVehicleIsNotFound() {
        when(vehicleMapper.selectById(20L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getResponse(taskResponse(TransportTaskStatus.WAITING)));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
        verify(taskRouteService, never()).getActiveRoute(1L);
    }

    @Test
    void providerFailureReturnsRouteUnavailableInsteadOfEmptyRoute() {
        stubVehicle("sim_000");
        when(taskRouteService.getActiveRoute(1L)).thenReturn(Optional.empty());
        when(routeProvider.plan(106.57, 29.49, 106.61, 29.52))
                .thenThrow(new EtaProviderException("Amap route API returned no usable polyline"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getResponse(taskResponse(TransportTaskStatus.WAITING)));

        assertEquals(ErrorCode.REALTIME_PROVIDER_UNAVAILABLE, exception.getErrorCode());
        assertEquals("planned route is unavailable: Amap route API returned no usable polyline",
                exception.getMessage());
    }

    @Test
    void plannedRouteRejectsFewerThanTwoPoints() {
        assertThrows(IllegalArgumentException.class, () -> new EtaPlannedRoute(
                List.of(new EtaCoordinate(106.57, 29.49)),
                5_500, Duration.ofMinutes(12)));
    }

    private void stubVehicle(String simCode) {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(20L);
        vehicle.setSimCode(simCode);
        when(vehicleMapper.selectById(20L)).thenReturn(vehicle);
    }

    private EtaPlannedRoute route() {
        return new EtaPlannedRoute(List.of(
                new EtaCoordinate(106.5701, 29.4901),
                new EtaCoordinate(106.6101, 29.5201)),
                5_500, Duration.ofMinutes(12));
    }

    private TransportTaskRouteSnapshot snapshot() {
        OffsetDateTime generatedAt = OffsetDateTime.ofInstant(NOW, ZoneOffset.ofHours(8));
        return new TransportTaskRouteSnapshot(
                7L, "route_fixed", 1L, "AMAP", "GCJ02",
                List.of(List.of(106.5701, 29.4901), List.of(106.6101, 29.5201)),
                5_500, 720, 1, TransportTaskRouteStatus.ACTIVE,
                generatedAt, generatedAt);
    }

    private TransportTaskResponse taskResponse(TransportTaskStatus status) {
        OffsetDateTime now = OffsetDateTime.ofInstant(NOW, ZoneOffset.ofHours(8));
        return new TransportTaskResponse(
                1L, "T1", 10L, 20L, "A", 106.57, 29.49,
                "B", 106.61, 29.52, now, now.plusHours(1), null, null,
                status, null, null, now, now);
    }
}
