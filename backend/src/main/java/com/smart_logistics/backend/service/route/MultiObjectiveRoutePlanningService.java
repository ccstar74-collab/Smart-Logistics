package com.smart_logistics.backend.service.route;

import com.smart_logistics.backend.dto.TransportTaskRouteSnapshot;
import com.smart_logistics.backend.dto.response.TransportTaskResponse;
import com.smart_logistics.backend.dto.response.TransportTaskRouteResponse;
import com.smart_logistics.backend.enums.TransportTaskStatus;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.service.TransportTaskRouteService;
import com.smart_logistics.backend.service.TransportTaskService;
import com.smart_logistics.backend.service.eta.EtaCoordinate;
import com.smart_logistics.backend.service.eta.EtaPlannedRoute;
import com.smart_logistics.backend.service.eta.EtaProviderException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MultiObjectiveRoutePlanningService {

    private static final int MINIMUM_CANDIDATE_COUNT = 2;

    private final TransportTaskService transportTaskService;
    private final TransportTaskRouteService routeService;
    private final MultiObjectiveRouteProvider routeProvider;

    @Autowired
    public MultiObjectiveRoutePlanningService(
            TransportTaskService transportTaskService,
            TransportTaskRouteService routeService,
            MultiObjectiveRouteProvider routeProvider) {
        this.transportTaskService = transportTaskService;
        this.routeService = routeService;
        this.routeProvider = routeProvider;
    }

    public List<TransportTaskRouteResponse> createReadyCandidates(Long taskId) {
        TransportTaskResponse task = transportTaskService.getTransportTask(taskId);
        requirePlanningAllowed(task.getStatus());
        requireCoordinates(task);

        List<EtaPlannedRoute> plannedRoutes;
        try {
            plannedRoutes = routeProvider.planCandidates(
                    task.getStartLongitude(), task.getStartLatitude(),
                    task.getEndLongitude(), task.getEndLatitude());
        } catch (EtaProviderException exception) {
            throw new BusinessException(ErrorCode.REALTIME_PROVIDER_UNAVAILABLE,
                    "route candidates are unavailable: " + exception.getMessage());
        }

        List<EtaPlannedRoute> distinctCandidates = removeExistingRoutes(
                routeService.findRoutesByTaskId(taskId), plannedRoutes);
        if (distinctCandidates.size() < MINIMUM_CANDIDATE_COUNT) {
            throw new BusinessException(ErrorCode.REALTIME_PROVIDER_UNAVAILABLE,
                    "route provider returned fewer than two distinct candidates");
        }

        return routeService.persistReadyRoutes(taskId, distinctCandidates).stream()
                .map(TransportTaskRouteResponse::from)
                .toList();
    }

    private List<EtaPlannedRoute> removeExistingRoutes(
            List<TransportTaskRouteSnapshot> existingRoutes,
            List<EtaPlannedRoute> plannedRoutes) {
        Map<List<EtaCoordinate>, EtaPlannedRoute> distinctByPolyline =
                new LinkedHashMap<>();
        for (EtaPlannedRoute route : plannedRoutes) {
            distinctByPolyline.putIfAbsent(route.polyline(), route);
        }
        for (TransportTaskRouteSnapshot existing : existingRoutes) {
            distinctByPolyline.remove(existing.routePoints().stream()
                    .map(point -> new EtaCoordinate(point.get(0), point.get(1)))
                    .toList());
        }
        return List.copyOf(distinctByPolyline.values());
    }

    private void requirePlanningAllowed(TransportTaskStatus status) {
        if (status != TransportTaskStatus.WAITING
                && status != TransportTaskStatus.TRANSPORTING) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "route candidates can only be generated for waiting or transporting task");
        }
    }

    private void requireCoordinates(TransportTaskResponse task) {
        if (task.getStartLongitude() == null || task.getStartLatitude() == null
                || task.getEndLongitude() == null || task.getEndLatitude() == null) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "transport task route coordinates are incomplete");
        }
    }
}
