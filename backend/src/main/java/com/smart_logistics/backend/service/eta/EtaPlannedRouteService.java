package com.smart_logistics.backend.service.eta;

import com.smart_logistics.backend.dto.TransportTaskRouteSnapshot;
import com.smart_logistics.backend.dto.response.PlannedRouteResponse;
import com.smart_logistics.backend.dto.response.TransportTaskResponse;
import com.smart_logistics.backend.entity.TransportTask;
import com.smart_logistics.backend.entity.Vehicle;
import com.smart_logistics.backend.enums.TransportTaskStatus;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.mapper.VehicleMapper;
import com.smart_logistics.backend.service.TransportTaskRouteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Optional;

@Service
public class EtaPlannedRouteService {

    private final EtaRouteProvider routeProvider;
    private final VehicleMapper vehicleMapper;
    private final TransportTaskRouteService taskRouteService;

    @Autowired
    public EtaPlannedRouteService(EtaRouteProvider routeProvider,
                                  VehicleMapper vehicleMapper,
                                  TransportTaskRouteService taskRouteService) {
        this.routeProvider = routeProvider;
        this.vehicleMapper = vehicleMapper;
        this.taskRouteService = taskRouteService;
    }

    public EtaPlannedRoute getRoute(TransportTask task) {
        return toEtaPlannedRoute(getOrPlan(
                task.getId(), task.getStartLongitude(), task.getStartLatitude(),
                task.getEndLongitude(), task.getEndLatitude()));
    }

    public EtaPlannedRoute planRoute(double startLongitude, double startLatitude,
                                     double endLongitude, double endLatitude) {
        try {
            return routeProvider.plan(startLongitude, startLatitude,
                    endLongitude, endLatitude);
        } catch (EtaProviderException exception) {
            throw new BusinessException(ErrorCode.REALTIME_PROVIDER_UNAVAILABLE,
                    "planned route is unavailable: " + exception.getMessage());
        }
    }

    public PlannedRouteResponse getResponse(TransportTaskResponse task) {
        requireRouteStatus(task.getStatus());
        String vehicleDeviceCode = getVehicleDeviceCode(task.getVehicleId());
        TransportTaskRouteSnapshot route = getOrPlan(task.getId(), task.getStartLongitude(),
                task.getStartLatitude(), task.getEndLongitude(), task.getEndLatitude());
        return new PlannedRouteResponse(
                task.getId(), route.routeId(), route.routeVersion(), route.status(),
                vehicleDeviceCode, route.provider(), route.coordinateSystem(),
                route.distanceMeters(), route.durationSeconds(), route.createdAt(),
                route.routePoints());
    }

    private void requireRouteStatus(TransportTaskStatus status) {
        if (status != TransportTaskStatus.WAITING
                && status != TransportTaskStatus.TRANSPORTING) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "planned route is only available for waiting or transporting tasks");
        }
    }

    private String getVehicleDeviceCode(Long vehicleId) {
        Vehicle vehicle = vehicleId == null ? null : vehicleMapper.selectById(vehicleId);
        if (vehicle == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                    "transport task vehicle not found");
        }
        if (!StringUtils.hasText(vehicle.getSimCode())) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "transport task vehicle simCode is missing");
        }
        return vehicle.getSimCode().trim();
    }

    private TransportTaskRouteSnapshot getOrPlan(Long taskId,
                                                 Double startLongitude, Double startLatitude,
                                                 Double endLongitude, Double endLatitude) {
        Optional<TransportTaskRouteSnapshot> activeRoute =
                taskRouteService.getActiveRoute(taskId);
        if (activeRoute.isPresent()) {
            return activeRoute.get();
        }
        if (startLongitude == null || startLatitude == null
                || endLongitude == null || endLatitude == null) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "transport task route coordinates are incomplete");
        }
        EtaPlannedRoute route = planRoute(startLongitude, startLatitude,
                endLongitude, endLatitude);
        return taskRouteService.persistInitialActiveRoute(taskId, route);
    }

    private EtaPlannedRoute toEtaPlannedRoute(TransportTaskRouteSnapshot route) {
        return new EtaPlannedRoute(
                route.routePoints().stream()
                        .map(point -> new EtaCoordinate(point.get(0), point.get(1)))
                        .toList(),
                route.distanceMeters(), Duration.ofSeconds(route.durationSeconds()));
    }
}
