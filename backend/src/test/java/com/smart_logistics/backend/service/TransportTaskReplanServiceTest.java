package com.smart_logistics.backend.service;

import com.smart_logistics.backend.dto.TransportTaskRouteSnapshot;
import com.smart_logistics.backend.dto.realtime.GpsSample;
import com.smart_logistics.backend.dto.request.TransportTaskReplanRequest;
import com.smart_logistics.backend.dto.response.PlannedRouteResponse;
import com.smart_logistics.backend.dto.response.TransportTaskResponse;
import com.smart_logistics.backend.entity.Vehicle;
import com.smart_logistics.backend.enums.TransportTaskRouteStatus;
import com.smart_logistics.backend.enums.TransportTaskStatus;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.service.eta.EtaCoordinate;
import com.smart_logistics.backend.service.eta.EtaPlannedRoute;
import com.smart_logistics.backend.service.eta.EtaPlannedRouteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransportTaskReplanServiceTest {

    private static final Instant POSITION_AT =
            Instant.parse("2026-08-28T08:00:01.123Z");

    @Mock private TransportTaskService transportTaskService;
    @Mock private VehicleService vehicleService;
    @Mock private VehicleLocationQueryService locationQueryService;
    @Mock private EtaPlannedRouteService plannedRouteService;
    @Mock private TransportTaskRouteService taskRouteService;

    private final List<Duration> retrySleeps = new ArrayList<>();
    private TransportTaskReplanService service;

    @BeforeEach
    void setUp() {
        retrySleeps.clear();
        service = new TransportTaskReplanService(transportTaskService, vehicleService,
                locationQueryService, plannedRouteService, taskRouteService,
                retrySleeps::add);
    }

    @Test
    void acceptsMatchingAnchorRequest() {
        TransportTaskResponse task = task(TransportTaskStatus.TRANSPORTING);
        GpsSample latest = latestGps(106.580123, 29.620456,
                POSITION_AT.plusMillis(10));
        stubTaskVehicleAndGps(task, latest);
        when(plannedRouteService.planRouteFromWgs84Origin(
                latest.longitude(), latest.latitude(), 106.61, 29.52))
                .thenReturn(plannedRoute());
        when(taskRouteService.replaceActiveRouteFromReplan(
                1L, 20L, plannedRoute())).thenReturn(routeSnapshot());

        PlannedRouteResponse response = service.replanFromLatestLocation(
                1L, request(106.580123, 29.620456));

        assertEquals("route_replanned", response.routeId());
        assertEquals(4, response.routeVersion());
        assertEquals(TransportTaskRouteStatus.ACTIVE, response.routeStatus());
        assertEquals("sim_019", response.vehicleDeviceCode());
        assertEquals("GCJ02", response.coordinateSystem());
        assertEquals(List.of(106.58, 29.50), response.points().getFirst());
        assertEquals("original start", task.getStartLocation());
        assertEquals(106.55, task.getStartLongitude());
        assertEquals(29.48, task.getStartLatitude());
        assertEquals(List.of(), retrySleeps);
    }

    @Test
    void rejectsMismatchedVehicleDeviceCodeBeforeInfluxOrAmap() {
        TransportTaskResponse task = task(TransportTaskStatus.TRANSPORTING);
        Vehicle vehicle = vehicle();
        when(transportTaskService.getTransportTask(1L)).thenReturn(task);
        when(vehicleService.getVehicleForTransport(20L)).thenReturn(vehicle);
        when(vehicleService.requireTransportSimCode(vehicle)).thenReturn("sim_019");
        TransportTaskReplanRequest request = request(106.580123, 29.620456);
        request.setVehicleDeviceCode("sim_020");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.replanFromLatestLocation(1L, request));

        assertEquals(ErrorCode.STATE_CONFLICT, exception.getErrorCode());
        verifyNoInteractions(locationQueryService, plannedRouteService, taskRouteService);
    }

    @Test
    void rejectsUnsupportedCoordinateSystem() {
        TransportTaskReplanRequest request = request(106.580123, 29.620456);
        request.setCoordinateSystem("GCJ02");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.replanFromLatestLocation(1L, request));

        assertEquals(ErrorCode.INVALID_PARAMETER, exception.getErrorCode());
        verifyNoInteractions(transportTaskService, vehicleService,
                locationQueryService, plannedRouteService, taskRouteService);
    }

    @Test
    void retriesUntilAnchorAppearsAndDoesNotUseFirstOldGps() {
        TransportTaskResponse task = task(TransportTaskStatus.TRANSPORTING);
        GpsSample oldGps = latestGps(106.50, 29.50, POSITION_AT.minusSeconds(1));
        GpsSample anchorGps = latestGps(106.580143, 29.620466,
                POSITION_AT.plusMillis(20));
        stubTaskAndVehicle(task);
        when(locationQueryService.getLatestOnlineGps("sim_019"))
                .thenReturn(oldGps, anchorGps);
        when(plannedRouteService.planRouteFromWgs84Origin(
                anchorGps.longitude(), anchorGps.latitude(), 106.61, 29.52))
                .thenReturn(plannedRoute());
        when(taskRouteService.replaceActiveRouteFromReplan(
                1L, 20L, plannedRoute())).thenReturn(routeSnapshot());

        service.replanFromLatestLocation(1L, request(106.580123, 29.620456));

        assertEquals(List.of(Duration.ofMillis(200)), retrySleeps);
        verify(locationQueryService, times(2)).getLatestOnlineGps("sim_019");
        verify(plannedRouteService).planRouteFromWgs84Origin(
                anchorGps.longitude(), anchorGps.latitude(), 106.61, 29.52);
        verify(plannedRouteService, never()).planRouteFromWgs84Origin(
                oldGps.longitude(), oldGps.latitude(), 106.61, 29.52);
    }

    @Test
    void failsWhenAnchorNeverAppears() {
        stubTaskAndVehicle(task(TransportTaskStatus.TRANSPORTING));
        when(locationQueryService.getLatestOnlineGps("sim_019")).thenReturn(
                latestGps(106.580123, 29.620456, POSITION_AT.minusSeconds(3)),
                latestGps(106.580123, 29.620456, POSITION_AT.minusSeconds(2)),
                latestGps(106.580123, 29.620456, POSITION_AT.minusSeconds(1)));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.replanFromLatestLocation(
                        1L, request(106.580123, 29.620456)));

        assertEquals(ErrorCode.STATE_CONFLICT, exception.getErrorCode());
        assertEquals(List.of(Duration.ofMillis(200), Duration.ofMillis(200)),
                retrySleeps);
        verify(locationQueryService, times(3)).getLatestOnlineGps("sim_019");
        verifyNoInteractions(plannedRouteService, taskRouteService);
    }

    @Test
    void rejectsAnchorPositionMismatch() {
        GpsSample latest = latestGps(106.581123, 29.621456,
                POSITION_AT.plusMillis(10));
        stubTaskVehicleAndGps(task(TransportTaskStatus.TRANSPORTING), latest);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.replanFromLatestLocation(
                        1L, request(106.580123, 29.620456)));

        assertEquals(ErrorCode.STATE_CONFLICT, exception.getErrorCode());
        verifyNoInteractions(plannedRouteService, taskRouteService);
    }

    @Test
    void acceptsSmallAnchorDistanceAndPlannerUsesInfluxGpsNotRequestCoordinates() {
        GpsSample latest = latestGps(106.580143, 29.620466,
                POSITION_AT.plusMillis(10));
        stubTaskVehicleAndGps(task(TransportTaskStatus.TRANSPORTING), latest);
        when(plannedRouteService.planRouteFromWgs84Origin(
                latest.longitude(), latest.latitude(), 106.61, 29.52))
                .thenReturn(plannedRoute());
        when(taskRouteService.replaceActiveRouteFromReplan(
                1L, 20L, plannedRoute())).thenReturn(routeSnapshot());

        service.replanFromLatestLocation(1L, request(106.580123, 29.620456));

        verify(plannedRouteService).planRouteFromWgs84Origin(
                106.580143, 29.620466, 106.61, 29.52);
        verify(plannedRouteService, never()).planRouteFromWgs84Origin(
                106.580123, 29.620456, 106.61, 29.52);
    }

    @Test
    void rejectsNonTransportingTaskBeforeGpsOrAmap() {
        when(transportTaskService.getTransportTask(1L))
                .thenReturn(task(TransportTaskStatus.COMPLETED));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.replanFromLatestLocation(
                        1L, request(106.580123, 29.620456)));

        assertEquals(ErrorCode.STATE_CONFLICT, exception.getErrorCode());
        verifyNoInteractions(vehicleService, locationQueryService,
                plannedRouteService, taskRouteService);
    }

    @Test
    void rejectsVehicleWithoutSimCodeBeforeGpsOrAmap() {
        TransportTaskResponse task = task(TransportTaskStatus.TRANSPORTING);
        Vehicle vehicle = vehicle();
        when(transportTaskService.getTransportTask(1L)).thenReturn(task);
        when(vehicleService.getVehicleForTransport(20L)).thenReturn(vehicle);
        when(vehicleService.requireTransportSimCode(vehicle)).thenThrow(
                new BusinessException(ErrorCode.STATE_CONFLICT,
                        "vehicle has no simCode"));

        assertThrows(BusinessException.class,
                () -> service.replanFromLatestLocation(
                        1L, request(106.580123, 29.620456)));

        verifyNoInteractions(locationQueryService, plannedRouteService, taskRouteService);
    }

    @Test
    void staleLatestGpsKeepsOldActive() {
        stubTaskAndVehicle(task(TransportTaskStatus.TRANSPORTING));
        when(locationQueryService.getLatestOnlineGps("sim_019")).thenThrow(
                new BusinessException(ErrorCode.STATE_CONFLICT,
                        "vehicle latest location is offline"));

        assertThrows(BusinessException.class,
                () -> service.replanFromLatestLocation(
                        1L, request(106.580123, 29.620456)));

        verifyNoInteractions(plannedRouteService, taskRouteService);
    }

    @Test
    void amapFailureKeepsOldActiveAndOriginalTaskStart() {
        TransportTaskResponse task = task(TransportTaskStatus.TRANSPORTING);
        GpsSample latest = latestGps(106.580123, 29.620456,
                POSITION_AT.plusMillis(10));
        stubTaskVehicleAndGps(task, latest);
        when(plannedRouteService.planRouteFromWgs84Origin(
                latest.longitude(), latest.latitude(), 106.61, 29.52)).thenThrow(
                new BusinessException(ErrorCode.REALTIME_PROVIDER_UNAVAILABLE,
                        "planned route is unavailable"));

        assertThrows(BusinessException.class,
                () -> service.replanFromLatestLocation(
                        1L, request(106.580123, 29.620456)));

        verify(taskRouteService, never()).replaceActiveRouteFromReplan(
                anyLong(), anyLong(), any(EtaPlannedRoute.class));
        assertEquals("original start", task.getStartLocation());
        assertEquals(106.55, task.getStartLongitude());
        assertEquals(29.48, task.getStartLatitude());
    }

    private void stubTaskVehicleAndGps(TransportTaskResponse task, GpsSample gps) {
        stubTaskAndVehicle(task);
        when(locationQueryService.getLatestOnlineGps("sim_019")).thenReturn(gps);
    }

    private void stubTaskAndVehicle(TransportTaskResponse task) {
        Vehicle vehicle = vehicle();
        when(transportTaskService.getTransportTask(1L)).thenReturn(task);
        when(vehicleService.getVehicleForTransport(20L)).thenReturn(vehicle);
        when(vehicleService.requireTransportSimCode(vehicle)).thenReturn("sim_019");
    }

    private TransportTaskReplanRequest request(double longitude, double latitude) {
        TransportTaskReplanRequest request = new TransportTaskReplanRequest();
        request.setVehicleDeviceCode("sim_019");
        request.setLongitude(longitude);
        request.setLatitude(latitude);
        request.setCoordinateSystem("WGS84");
        request.setPositionAt(OffsetDateTime.parse("2026-08-28T08:00:01.123Z"));
        return request;
    }

    private TransportTaskResponse task(TransportTaskStatus status) {
        return new TransportTaskResponse(
                1L, "T202608280001", 10L, 20L,
                "original start", 106.55, 29.48,
                "destination", 106.61, 29.52,
                null, null, null, null, status, null, null,
                OffsetDateTime.parse("2026-08-28T10:00:00+08:00"),
                OffsetDateTime.parse("2026-08-28T10:00:00+08:00"));
    }

    private Vehicle vehicle() {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(20L);
        vehicle.setSimCode("sim_019");
        return vehicle;
    }

    private GpsSample latestGps(double longitude, double latitude, Instant collectedAt) {
        return new GpsSample("sim_019", longitude, latitude,
                0.0, 90.0, collectedAt);
    }

    private EtaPlannedRoute plannedRoute() {
        return new EtaPlannedRoute(List.of(
                new EtaCoordinate(106.58, 29.50),
                new EtaCoordinate(106.61, 29.52)),
                4_200, Duration.ofSeconds(540));
    }

    private TransportTaskRouteSnapshot routeSnapshot() {
        OffsetDateTime generatedAt =
                OffsetDateTime.parse("2026-08-28T16:00:00+08:00");
        return new TransportTaskRouteSnapshot(
                9L, "route_replanned", 1L, "AMAP", "GCJ02",
                List.of(List.of(106.58, 29.50), List.of(106.61, 29.52)),
                4_200, 540, 4, TransportTaskRouteStatus.ACTIVE,
                generatedAt, generatedAt);
    }
}
