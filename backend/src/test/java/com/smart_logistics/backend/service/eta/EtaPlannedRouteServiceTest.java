package com.smart_logistics.backend.service.eta;

import com.smart_logistics.backend.dto.response.PlannedRouteResponse;
import com.smart_logistics.backend.dto.response.TransportTaskResponse;
import com.smart_logistics.backend.entity.Vehicle;
import com.smart_logistics.backend.enums.TransportTaskStatus;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.mapper.VehicleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EtaPlannedRouteServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-26T08:00:00Z");

    @Mock private EtaRouteProvider routeProvider;
    @Mock private VehicleMapper vehicleMapper;

    private EtaPlannedRouteService service;

    @BeforeEach
    void setUp() {
        service = new EtaPlannedRouteService(routeProvider, vehicleMapper,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void waitingTaskReturnsSimulatorContractAndReusesEtaRouteCache() {
        stubVehicle("sim_008");
        when(routeProvider.plan(106.57, 29.49, 106.61, 29.52)).thenReturn(route());

        PlannedRouteResponse first = service.getResponse(
                taskResponse(TransportTaskStatus.WAITING));
        PlannedRouteResponse second = service.getResponse(
                taskResponse(TransportTaskStatus.WAITING));

        assertEquals(1L, first.taskId());
        assertEquals("sim_008", first.vehicleDeviceCode());
        assertEquals("AMAP", first.provider());
        assertEquals("GCJ02", first.coordinateSystem());
        assertEquals(List.of(106.5701, 29.4901), first.points().getFirst());
        assertEquals(2, first.points().size());
        assertEquals(5_500, first.distanceMeters());
        assertEquals(first.generatedAt(), second.generatedAt());
        verify(routeProvider, times(1)).plan(106.57, 29.49, 106.61, 29.52);
    }

    @Test
    void transportingTaskCanReadPlannedRoute() {
        stubVehicle("sim_019");
        when(routeProvider.plan(106.57, 29.49, 106.61, 29.52)).thenReturn(route());

        PlannedRouteResponse response = service.getResponse(
                taskResponse(TransportTaskStatus.TRANSPORTING));

        assertEquals("sim_019", response.vehicleDeviceCode());
        assertEquals(2, response.points().size());
    }

    @Test
    void completedTaskCannotReadPlannedRoute() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getResponse(taskResponse(TransportTaskStatus.COMPLETED)));

        assertEquals(ErrorCode.STATE_CONFLICT, exception.getErrorCode());
        verify(vehicleMapper, never()).selectById(20L);
        verify(routeProvider, never()).plan(106.57, 29.49, 106.61, 29.52);
    }

    @Test
    void rejectsTaskWithoutCompleteCoordinates() {
        stubVehicle("sim_000");
        TransportTaskResponse incomplete = new TransportTaskResponse(
                1L, "T1", 10L, 20L, "A", null, null,
                "B", 106.61, 29.52, null, null, null, null,
                TransportTaskStatus.WAITING, null, null, null, null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getResponse(incomplete));

        assertEquals(ErrorCode.STATE_CONFLICT, exception.getErrorCode());
        verify(routeProvider, never()).plan(106.57, 29.49, 106.61, 29.52);
    }

    @Test
    void missingVehicleSimCodeIsExplicitBusinessError() {
        stubVehicle(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getResponse(taskResponse(TransportTaskStatus.WAITING)));

        assertEquals(ErrorCode.STATE_CONFLICT, exception.getErrorCode());
        assertEquals("transport task vehicle simCode is missing", exception.getMessage());
        verify(routeProvider, never()).plan(106.57, 29.49, 106.61, 29.52);
    }

    @Test
    void missingBoundVehicleIsNotFound() {
        when(vehicleMapper.selectById(20L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getResponse(taskResponse(TransportTaskStatus.WAITING)));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
        verify(routeProvider, never()).plan(106.57, 29.49, 106.61, 29.52);
    }

    @Test
    void providerFailureReturnsRouteUnavailableInsteadOfEmptyRoute() {
        stubVehicle("sim_000");
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

    private TransportTaskResponse taskResponse(TransportTaskStatus status) {
        OffsetDateTime now = OffsetDateTime.ofInstant(NOW, ZoneOffset.ofHours(8));
        return new TransportTaskResponse(
                1L, "T1", 10L, 20L, "A", 106.57, 29.49,
                "B", 106.61, 29.52, now, now.plusHours(1), null, null,
                status, null, null, now, now);
    }
}
