package com.smart_logistics.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.smart_logistics.backend.dto.TransportTaskRouteSnapshot;
import com.smart_logistics.backend.entity.TransportTask;
import com.smart_logistics.backend.entity.TransportTaskRoute;
import com.smart_logistics.backend.enums.TransportTaskRouteStatus;
import com.smart_logistics.backend.enums.TransportTaskStatus;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.mapper.TransportTaskMapper;
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
    private final TransportTaskMapper taskMapper;
    private final Clock clock;
    private final Supplier<String> routeIdSupplier;

    @Autowired
    public TransportTaskRouteService(TransportTaskRouteMapper routeMapper,
                                     TransportTaskMapper taskMapper) {
        this(routeMapper, taskMapper, Clock.systemUTC(),
                () -> "route_" + UUID.randomUUID());
    }

    TransportTaskRouteService(TransportTaskRouteMapper routeMapper,
                              TransportTaskMapper taskMapper,
                              Clock clock,
                              Supplier<String> routeIdSupplier) {
        this.routeMapper = routeMapper;
        this.taskMapper = taskMapper;
        this.clock = clock;
        this.routeIdSupplier = routeIdSupplier;
    }

    @Transactional(readOnly = true)
    public Optional<TransportTaskRouteSnapshot> getActiveRoute(Long taskId) {
        return Optional.ofNullable(getActiveRouteEntity(taskId)).map(this::toSnapshot);
    }

    @Transactional
    public TransportTaskRouteSnapshot persistInitialActiveRoute(
            Long taskId, EtaPlannedRoute plannedRoute) {
        lockTask(taskId);
        Optional<TransportTaskRouteSnapshot> existing =
                Optional.ofNullable(getActiveRouteEntity(taskId)).map(this::toSnapshot);
        if (existing.isPresent()) {
            return existing.get();
        }

        TransportTaskRoute route = newRoute(taskId, plannedRoute,
                INITIAL_ROUTE_VERSION, TransportTaskRouteStatus.ACTIVE);
        try {
            insertRoute(route);
        } catch (DuplicateKeyException exception) {
            Optional<TransportTaskRouteSnapshot> concurrent =
                    Optional.ofNullable(getActiveRouteEntity(taskId)).map(this::toSnapshot);
            if (concurrent.isPresent()) {
                return concurrent.get();
            }
            throw duplicateRouteKey(exception);
        }
        return toSnapshot(route);
    }

    @Transactional
    public TransportTaskRouteSnapshot persistReadyRoute(
            Long taskId, EtaPlannedRoute plannedRoute) {
        TransportTask task = lockTask(taskId);
        requireRouteMutationAllowed(task);
        if (getActiveRouteEntity(taskId) == null) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "task has no active planned route");
        }

        TransportTaskRoute latest = routeMapper.selectOne(
                new LambdaQueryWrapper<TransportTaskRoute>()
                        .eq(TransportTaskRoute::getTaskId, taskId)
                        .orderByDesc(TransportTaskRoute::getRouteVersion)
                        .last("LIMIT 1"));
        int nextVersion;
        try {
            nextVersion = Math.addExact(latest.getRouteVersion(), 1);
        } catch (ArithmeticException exception) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "route version limit reached for transport task");
        }

        TransportTaskRoute route = newRoute(taskId, plannedRoute,
                nextVersion, TransportTaskRouteStatus.READY);
        try {
            insertRoute(route);
        } catch (DuplicateKeyException exception) {
            throw duplicateRouteKey(exception);
        }
        return toSnapshot(route);
    }

    @Transactional
    public TransportTaskRouteSnapshot activateReadyRoute(Long taskId, String routeId) {
        TransportTask task = lockTask(taskId);
        requireRouteMutationAllowed(task);

        TransportTaskRoute target = routeMapper.selectOne(
                new LambdaQueryWrapper<TransportTaskRoute>()
                        .eq(TransportTaskRoute::getTaskId, taskId)
                        .eq(TransportTaskRoute::getRouteId, routeId));
        if (target == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                    "route not found for transport task");
        }
        TransportTaskRouteStatus targetStatus = parseStatus(target.getStatus());
        if (targetStatus != TransportTaskRouteStatus.READY) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "only READY route can be activated");
        }

        TransportTaskRoute active = getActiveRouteEntity(taskId);
        if (active == null) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "task has no active planned route");
        }
        LocalDateTime now = now();
        if (routeMapper.update(null, new LambdaUpdateWrapper<TransportTaskRoute>()
                .eq(TransportTaskRoute::getId, active.getId())
                .eq(TransportTaskRoute::getStatus, TransportTaskRouteStatus.ACTIVE.name())
                .set(TransportTaskRoute::getStatus, TransportTaskRouteStatus.INACTIVE.name())
                .set(TransportTaskRoute::getUpdatedAt, now)) != 1) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "active route status conflict");
        }
        if (routeMapper.update(null, new LambdaUpdateWrapper<TransportTaskRoute>()
                .eq(TransportTaskRoute::getId, target.getId())
                .eq(TransportTaskRoute::getStatus, TransportTaskRouteStatus.READY.name())
                .set(TransportTaskRoute::getStatus, TransportTaskRouteStatus.ACTIVE.name())
                .set(TransportTaskRoute::getUpdatedAt, now)) != 1) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "ready route status conflict");
        }
        target.setStatus(TransportTaskRouteStatus.ACTIVE.name());
        target.setUpdatedAt(now);
        return toSnapshot(target);
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

    private TransportTask lockTask(Long taskId) {
        TransportTask task = taskMapper.selectOne(new LambdaQueryWrapper<TransportTask>()
                .eq(TransportTask::getId, taskId)
                .last("FOR UPDATE"));
        if (task == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                    "transport task not found");
        }
        return task;
    }

    private void requireRouteMutationAllowed(TransportTask task) {
        TransportTaskStatus status;
        try {
            status = TransportTaskStatus.valueOf(task.getStatus());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "invalid transport task status in database");
        }
        if (status != TransportTaskStatus.WAITING
                && status != TransportTaskStatus.TRANSPORTING) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "route can only be changed for waiting or transporting task");
        }
    }

    private TransportTaskRoute getActiveRouteEntity(Long taskId) {
        return routeMapper.selectOne(new LambdaQueryWrapper<TransportTaskRoute>()
                .eq(TransportTaskRoute::getTaskId, taskId)
                .eq(TransportTaskRoute::getStatus,
                        TransportTaskRouteStatus.ACTIVE.name())
                .orderByDesc(TransportTaskRoute::getRouteVersion)
                .last("LIMIT 1"));
    }

    private TransportTaskRoute newRoute(Long taskId,
                                        EtaPlannedRoute plannedRoute,
                                        int routeVersion,
                                        TransportTaskRouteStatus status) {
        LocalDateTime now = now();
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
        route.setRouteVersion(routeVersion);
        route.setStatus(status.name());
        route.setCreatedAt(now);
        route.setUpdatedAt(now);
        return route;
    }

    private void insertRoute(TransportTaskRoute route) {
        if (routeMapper.insert(route) != 1) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "failed to persist planned route");
        }
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

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), API_TIME_ZONE);
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
