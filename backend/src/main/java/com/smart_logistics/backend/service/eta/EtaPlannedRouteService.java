package com.smart_logistics.backend.service.eta;

import com.smart_logistics.backend.dto.response.PlannedRouteResponse;
import com.smart_logistics.backend.dto.response.TransportTaskResponse;
import com.smart_logistics.backend.entity.TransportTask;
import com.smart_logistics.backend.entity.Vehicle;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.mapper.VehicleMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EtaPlannedRouteService {

    private static final ZoneId API_TIME_ZONE = ZoneId.of("Asia/Shanghai");

    private final EtaRouteProvider routeProvider;
    private final VehicleMapper vehicleMapper;
    private final Clock clock;
    private final Map<Long, CachedRoute> routeCache = new ConcurrentHashMap<>();

    @Autowired
    public EtaPlannedRouteService(EtaRouteProvider routeProvider, VehicleMapper vehicleMapper) {
        this(routeProvider, vehicleMapper, Clock.systemUTC());
    }

    EtaPlannedRouteService(EtaRouteProvider routeProvider, VehicleMapper vehicleMapper,
                           Clock clock) {
        this.routeProvider = routeProvider;
        this.vehicleMapper = vehicleMapper;
        this.clock = clock;
    }

    public EtaPlannedRoute getRoute(TransportTask task) {
        return getOrPlan(task.getId(), task.getStartLongitude(), task.getStartLatitude(),
                task.getEndLongitude(), task.getEndLatitude()).route();
    }

    public PlannedRouteResponse getResponse(TransportTaskResponse task) {
        validateCoordinates(task.getStartLongitude(), task.getStartLatitude(),
                task.getEndLongitude(), task.getEndLatitude());
        String vehicleDeviceCode = getVehicleDeviceCode(task.getVehicleId());
        CachedRoute cached = getOrPlan(task.getId(), task.getStartLongitude(),
                task.getStartLatitude(), task.getEndLongitude(), task.getEndLatitude());
        return new PlannedRouteResponse(
                task.getId(), vehicleDeviceCode, "AMAP", "GCJ02",
                cached.route().distanceMeters(),
                cached.route().referenceDuration().toSeconds(),
                cached.generatedAt().atZone(API_TIME_ZONE).toOffsetDateTime(),
                cached.route().polyline().stream()
                        .map(point -> new PlannedRouteResponse.RoutePoint(
                                point.longitude(), point.latitude()))
                        .toList());
    }

    private String getVehicleDeviceCode(Long vehicleId) {
        Vehicle vehicle = vehicleId == null ? null : vehicleMapper.selectById(vehicleId);
        if (vehicle == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                    "transport task vehicle does not exist");
        }
        if (vehicle.getSimCode() == null || vehicle.getSimCode().isBlank()) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "transport task vehicle simCode is not configured");
        }
        return vehicle.getSimCode().trim();
    }

    private synchronized CachedRoute getOrPlan(Long taskId,
                                               Double startLongitude, Double startLatitude,
                                               Double endLongitude, Double endLatitude) {
        validateCoordinates(startLongitude, startLatitude, endLongitude, endLatitude);
        RouteKey key = new RouteKey(startLongitude, startLatitude,
                endLongitude, endLatitude);
        CachedRoute cached = routeCache.get(taskId);
        if (cached != null && cached.key().equals(key)) {
            return cached;
        }
        CachedRoute planned = new CachedRoute(key, routeProvider.plan(
                key.startLongitude(), key.startLatitude(),
                key.endLongitude(), key.endLatitude()), clock.instant());
        routeCache.put(taskId, planned);
        return planned;
    }

    private void validateCoordinates(Double startLongitude, Double startLatitude,
                                     Double endLongitude, Double endLatitude) {
        if (startLongitude == null || startLatitude == null
                || endLongitude == null || endLatitude == null) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "transport task route coordinates are incomplete");
        }
    }

    private record RouteKey(double startLongitude, double startLatitude,
                            double endLongitude, double endLatitude) {
    }

    private record CachedRoute(RouteKey key, EtaPlannedRoute route, Instant generatedAt) {
    }
}
