package com.smart_logistics.backend.service;

import com.smart_logistics.backend.dto.realtime.GpsSample;
import com.smart_logistics.backend.dto.TaskTrackSnapshot;
import com.smart_logistics.backend.dto.TransportTaskStatusTransitionSnapshot;
import com.smart_logistics.backend.dto.response.VehicleLocationResponse;
import com.smart_logistics.backend.entity.TransportTask;
import com.smart_logistics.backend.entity.Vehicle;
import com.smart_logistics.backend.enums.TransportTaskStatus;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.mapper.TransportTaskMapper;
import com.smart_logistics.backend.mapper.VehicleMapper;
import com.smart_logistics.backend.security.BusinessDataScopeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

@Service
public class TaskTrackQueryService {
    private static final ZoneId API_TIME_ZONE = ZoneId.of("Asia/Shanghai");

    private final TransportTaskMapper transportTaskMapper;
    private final VehicleMapper vehicleMapper;
    private final BusinessDataScopeService dataScopeService;
    private final GpsInfluxService gpsInfluxService;
    private final TransportTaskStatusRecordService statusRecordService;
    private final Duration onlineThreshold;
    private final Clock clock;

    @Autowired
    public TaskTrackQueryService(TransportTaskMapper transportTaskMapper,
                                 VehicleMapper vehicleMapper,
                                 BusinessDataScopeService dataScopeService,
                                 GpsInfluxService gpsInfluxService,
                                 TransportTaskStatusRecordService statusRecordService,
                                 @Value("${app.realtime.online-threshold:PT2M}")
                                 Duration onlineThreshold) {
        this(transportTaskMapper, vehicleMapper, dataScopeService, gpsInfluxService,
                statusRecordService, onlineThreshold, Clock.systemUTC());
    }

    TaskTrackQueryService(TransportTaskMapper transportTaskMapper,
                          VehicleMapper vehicleMapper,
                          BusinessDataScopeService dataScopeService,
                          GpsInfluxService gpsInfluxService,
                          TransportTaskStatusRecordService statusRecordService,
                          Duration onlineThreshold,
                          Clock clock) {
        this.transportTaskMapper = transportTaskMapper;
        this.vehicleMapper = vehicleMapper;
        this.dataScopeService = dataScopeService;
        this.gpsInfluxService = gpsInfluxService;
        this.statusRecordService = statusRecordService;
        this.onlineThreshold = onlineThreshold;
        this.clock = clock;
    }

    public List<VehicleLocationResponse> getTrackPoints(Long taskId) {
        TaskTrackSnapshot track = getTaskTrack(taskId);
        if (track.points().isEmpty()) {
            return List.of();
        }
        TransportTask task = transportTaskMapper.selectById(taskId);
        Vehicle vehicle = vehicleMapper.selectById(task.getVehicleId());
        Instant now = clock.instant();
        return track.points().stream()
                .map(sample -> toResponse(vehicle, taskId, sample, now))
                .toList();
    }

    public TaskTrackSnapshot getTaskTrack(Long taskId) {
        TransportTask task = transportTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "transport task not found");
        }
        dataScopeService.requireTaskAccess(task);
        if (task.getVehicleId() == null) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "transport task vehicle is missing");
        }
        Vehicle vehicle = vehicleMapper.selectById(task.getVehicleId());
        if (vehicle == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "task vehicle not found");
        }
        if (vehicle.getSimCode() == null || vehicle.getSimCode().isBlank()) {
            return new TaskTrackSnapshot(null, null, List.of());
        }

        List<TransportTaskStatusTransitionSnapshot> transitions =
                statusRecordService.findTaskTransitions(taskId);
        Instant start = transportStart(task, transitions);
        Instant end = transportEnd(task, transitions, start);
        if (start == null || end == null || end.isBefore(start)) {
            return new TaskTrackSnapshot(start, end, List.of());
        }
        try {
            List<GpsSample> points = gpsInfluxService.querySamples(
                            List.of(vehicle.getSimCode()), start, end.plusNanos(1))
                    .stream()
                    .filter(sample -> sample.collectedAt() != null)
                    .filter(sample -> !sample.collectedAt().isBefore(start)
                            && !sample.collectedAt().isAfter(end))
                    .sorted(Comparator.comparing(GpsSample::collectedAt))
                    .toList();
            return new TaskTrackSnapshot(start, end, points);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.REALTIME_PROVIDER_UNAVAILABLE,
                    "realtime track provider unavailable");
        }
    }

    private Instant transportStart(
            TransportTask task,
            List<TransportTaskStatusTransitionSnapshot> transitions) {
        return transitions.stream()
                .filter(transition ->
                        transition.toStatus() == TransportTaskStatus.TRANSPORTING)
                .map(TransportTaskStatusTransitionSnapshot::changedAt)
                .filter(java.util.Objects::nonNull)
                .map(OffsetDateTime::toInstant)
                .findFirst()
                .orElseGet(() -> toInstant(task.getActualStartTime()));
    }

    private Instant transportEnd(
            TransportTask task,
            List<TransportTaskStatusTransitionSnapshot> transitions,
            Instant start) {
        Instant transitionEnd = transitions.stream()
                .filter(transition ->
                        transition.fromStatus() == TransportTaskStatus.TRANSPORTING
                                && transition.toStatus() != TransportTaskStatus.TRANSPORTING)
                .map(TransportTaskStatusTransitionSnapshot::changedAt)
                .filter(java.util.Objects::nonNull)
                .map(OffsetDateTime::toInstant)
                .filter(value -> start == null || !value.isBefore(start))
                .findFirst()
                .orElse(null);
        if (transitionEnd != null) {
            return transitionEnd;
        }
        if (TransportTaskStatus.TRANSPORTING.name().equals(task.getStatus())) {
            return clock.instant();
        }
        return toInstant(task.getActualEndTime());
    }

    private VehicleLocationResponse toResponse(Vehicle vehicle, Long taskId,
                                               GpsSample sample, Instant now) {
        return new VehicleLocationResponse(vehicle.getId(), vehicle.getPlateNumber(),
                sample.longitude(), sample.latitude(), sample.speed(), sample.direction(),
                sample.collectedAt().atZone(API_TIME_ZONE).toOffsetDateTime(),
                !sample.collectedAt().isBefore(now.minus(onlineThreshold)), taskId);
    }

    private Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.atZone(API_TIME_ZONE).toInstant();
    }
}
