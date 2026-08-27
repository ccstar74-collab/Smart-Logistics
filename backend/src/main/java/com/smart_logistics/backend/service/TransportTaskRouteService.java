package com.smart_logistics.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smart_logistics.backend.dto.TransportTaskRouteSnapshot;
import com.smart_logistics.backend.entity.TransportTaskRoute;
import com.smart_logistics.backend.enums.TransportTaskRouteStatus;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.mapper.TransportTaskRouteMapper;
import com.smart_logistics.backend.service.eta.EtaPlannedRoute;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class TransportTaskRouteService {

    private static final ZoneId API_TIME_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int INITIAL_ROUTE_VERSION = 1;
    private static final String AMAP = "AMAP";
    private static final String GCJ02 = "GCJ02";

    private final TransportTaskRouteMapper routeMapper;
    private final Clock clock;
    private final Supplier<String> routeIdSupplier;

    @Autowired
    public TransportTaskRouteService(TransportTaskRouteMapper routeMapper) {
        this(routeMapper, Clock.systemUTC(), () -> "route_" + UUID.randomUUID());
    }

    TransportTaskRouteService(TransportTaskRouteMapper routeMapper, Clock clock,
                              Supplier<String> routeIdSupplier) {
        this.routeMapper = routeMapper;
        this.clock = clock;
        this.routeIdSupplier = routeIdSupplier;
    }

    @Transactional(readOnly = true)
    public Optional<TransportTaskRouteSnapshot> getActiveRoute(Long taskId) {
        TransportTaskRoute route = routeMapper.selectOne(
                new LambdaQueryWrapper<TransportTaskRoute>()
                        .eq(TransportTaskRoute::getTaskId, taskId)
                        .eq(TransportTaskRoute::getStatus,
                                TransportTaskRouteStatus.ACTIVE.name())
                        .orderByDesc(TransportTaskRoute::getRouteVersion)
                        .last("LIMIT 1"));
        return Optional.ofNullable(route).map(this::toSnapshot);
    }

    @Transactional
    public TransportTaskRouteSnapshot persistInitialActiveRoute(
            Long taskId, EtaPlannedRoute plannedRoute) {
        Optional<TransportTaskRouteSnapshot> existing = getActiveRoute(taskId);
        if (existing.isPresent()) {
            return existing.get();
        }

        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), API_TIME_ZONE);
        TransportTaskRoute route = new TransportTaskRoute();
        route.setRouteId(routeIdSupplier.get());
        route.setTaskId(taskId);
        route.setProvider(AMAP);
        route.setCoordinateSystem(GCJ02);
        route.setRoutePoints(plannedRoute.polyline().stream()
                .map(point -> List.of(point.longitude(), point.latitude()))
                .toList());
        route.setDistanceMeters(plannedRoute.distanceMeters());
        route.setDurationSeconds(plannedRoute.referenceDuration().toSeconds());
        route.setRouteVersion(INITIAL_ROUTE_VERSION);
        route.setStatus(TransportTaskRouteStatus.ACTIVE.name());
        route.setCreatedAt(now);
        route.setUpdatedAt(now);

        try {
            if (routeMapper.insert(route) != 1) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                        "failed to persist planned route");
            }
        } catch (DuplicateKeyException exception) {
            Optional<TransportTaskRouteSnapshot> concurrent = getActiveRoute(taskId);
            if (concurrent.isPresent()) {
                return concurrent.get();
            }
            throw duplicateRouteKey(exception);
        }
        return toSnapshot(route);
    }

    @Transactional(readOnly = true)
    public List<TransportTaskRouteSnapshot> findRoutesByTaskId(Long taskId) {
        return routeMapper.selectList(new LambdaQueryWrapper<TransportTaskRoute>()
                        .eq(TransportTaskRoute::getTaskId, taskId)
                        .orderByAsc(TransportTaskRoute::getRouteVersion))
                .stream()
                .map(this::toSnapshot)
                .toList();
    }

    private TransportTaskRouteSnapshot toSnapshot(TransportTaskRoute route) {
        return new TransportTaskRouteSnapshot(
                route.getId(), route.getRouteId(), route.getTaskId(), route.getProvider(),
                route.getCoordinateSystem(), route.getRoutePoints(),
                route.getDistanceMeters(), route.getDurationSeconds(),
                route.getRouteVersion(), parseStatus(route.getStatus()),
                toOffsetDateTime(route.getCreatedAt()),
                toOffsetDateTime(route.getUpdatedAt()));
    }

    private TransportTaskRouteStatus parseStatus(String status) {
        try {
            return TransportTaskRouteStatus.valueOf(status);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "invalid transport task route status in database");
        }
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        return value == null ? null : value.atZone(API_TIME_ZONE).toOffsetDateTime();
    }

    private BusinessException duplicateRouteKey(DuplicateKeyException cause) {
        String message = cause.getMostSpecificCause().getMessage();
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        boolean routeIdConflict = normalized.contains("route_id")
                && !normalized.contains("route_version");
        BusinessException exception = new BusinessException(
                ErrorCode.DATA_CONFLICT,
                routeIdConflict ? "routeId already exists"
                        : "route version already exists for transport task");
        exception.initCause(cause);
        return exception;
    }
}
