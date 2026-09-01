package com.smart_logistics.backend.service.route;

import com.smart_logistics.backend.dto.TransportTaskRouteSnapshot;
import com.smart_logistics.backend.dto.response.TransportTaskResponse;
import com.smart_logistics.backend.enums.TransportTaskRouteStatus;
import com.smart_logistics.backend.enums.TransportTaskStatus;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.service.TransportTaskRouteService;
import com.smart_logistics.backend.service.TransportTaskService;
import com.smart_logistics.backend.service.eta.EtaCoordinate;
import com.smart_logistics.backend.service.eta.EtaPlannedRoute;
import com.smart_logistics.backend.service.eta.EtaProviderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MultiObjectiveRoutePlanningServiceTest {

    @Mock private TransportTaskService transportTaskService;
    @Mock private TransportTaskRouteService routeService;
    @Mock private MultiObjectiveRouteProvider routeProvider;

    private MultiObjectiveRoutePlanningService service;

    @BeforeEach
    void setUp() {
        service = new MultiObjectiveRoutePlanningService(
                transportTaskService, routeService, routeProvider);
    }

    @Test
    void persistsAtLeastTwoDistinctCandidatesWithoutRepeatingExistingRoute() {
        EtaPlannedRoute active = route(106.57, 29.49, 106.58, 29.50, 3800, 600);
        EtaPlannedRoute faster = route(106.57, 29.49, 106.59, 29.51, 4100, 560);
        EtaPlannedRoute shorter = route(106.57, 29.49, 106.60, 29.52, 3500, 680);
        when(transportTaskService.getTransportTask(1L))
                .thenReturn(task(TransportTaskStatus.WAITING));
        when(routeService.findRoutesByTaskId(1L))
                .thenReturn(List.of(snapshot("route_v1", 1, active,
                        TransportTaskRouteStatus.ACTIVE)));
        when(routeProvider.planCandidates(106.57, 29.49, 106.61, 29.53))
                .thenReturn(List.of(active, faster, shorter, shorter));
        when(routeService.persistReadyRoutes(1L, List.of(faster, shorter)))
                .thenReturn(List.of(
                        snapshot("route_v2", 2, faster, TransportTaskRouteStatus.READY),
                        snapshot("route_v3", 3, shorter, TransportTaskRouteStatus.READY)));

        var result = service.createReadyCandidates(1L);

        assertEquals(List.of("route_v2", "route_v3"),
                result.stream().map(route -> route.routeId()).toList());
        ArgumentCaptor<List<EtaPlannedRoute>> captor = ArgumentCaptor.forClass(List.class);
        verify(routeService).persistReadyRoutes(org.mockito.ArgumentMatchers.eq(1L),
                captor.capture());
        assertEquals(List.of(faster, shorter), captor.getValue());
    }

    @Test
    void rejectsProviderResultWithFewerThanTwoNewCandidates() {
        EtaPlannedRoute active = route(106.57, 29.49, 106.58, 29.50, 3800, 600);
        EtaPlannedRoute onlyCandidate =
                route(106.57, 29.49, 106.59, 29.51, 4100, 560);
        when(transportTaskService.getTransportTask(1L))
                .thenReturn(task(TransportTaskStatus.TRANSPORTING));
        when(routeService.findRoutesByTaskId(1L))
                .thenReturn(List.of(snapshot("route_v1", 1, active,
                        TransportTaskRouteStatus.ACTIVE)));
        when(routeProvider.planCandidates(106.57, 29.49, 106.61, 29.53))
                .thenReturn(List.of(active, onlyCandidate));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createReadyCandidates(1L));

        assertEquals(ErrorCode.REALTIME_PROVIDER_UNAVAILABLE, exception.getErrorCode());
        verify(routeService, never()).persistReadyRoutes(
                org.mockito.ArgumentMatchers.anyLong(), anyList());
    }

    @Test
    void providerFailureDoesNotPersistPartialCandidates() {
        when(transportTaskService.getTransportTask(1L))
                .thenReturn(task(TransportTaskStatus.WAITING));
        when(routeProvider.planCandidates(106.57, 29.49, 106.61, 29.53))
                .thenThrow(new EtaProviderException("timeout"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createReadyCandidates(1L));

        assertEquals(ErrorCode.REALTIME_PROVIDER_UNAVAILABLE, exception.getErrorCode());
        verify(routeService, never()).persistReadyRoutes(
                org.mockito.ArgumentMatchers.anyLong(), anyList());
    }

    @Test
    void completedTaskCannotGenerateCandidates() {
        when(transportTaskService.getTransportTask(1L))
                .thenReturn(task(TransportTaskStatus.COMPLETED));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createReadyCandidates(1L));

        assertEquals(ErrorCode.STATE_CONFLICT, exception.getErrorCode());
        verify(routeProvider, never()).planCandidates(
                org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble());
    }

    private TransportTaskResponse task(TransportTaskStatus status) {
        return new TransportTaskResponse(
                1L, "TASK-1", 1L, 2L,
                "start", 106.57, 29.49,
                "end", 106.61, 29.53,
                null, null, null, null,
                status, null, null, null, null);
    }

    private EtaPlannedRoute route(
            double startLongitude, double startLatitude,
            double endLongitude, double endLatitude,
            long distanceMeters, long durationSeconds) {
        return new EtaPlannedRoute(
                List.of(new EtaCoordinate(startLongitude, startLatitude),
                        new EtaCoordinate(endLongitude, endLatitude)),
                distanceMeters, Duration.ofSeconds(durationSeconds));
    }

    private TransportTaskRouteSnapshot snapshot(
            String routeId, int version, EtaPlannedRoute route,
            TransportTaskRouteStatus status) {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-30T12:00:00+08:00");
        return new TransportTaskRouteSnapshot(
                (long) version, routeId, 1L, "AMAP", "GCJ02",
                route.polyline().stream()
                        .map(point -> List.of(point.longitude(), point.latitude()))
                        .toList(),
                route.distanceMeters(), route.referenceDuration().toSeconds(),
                version, status, now, now);
    }
}
