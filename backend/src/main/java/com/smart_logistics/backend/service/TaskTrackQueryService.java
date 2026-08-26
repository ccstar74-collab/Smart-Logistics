package com.smart_logistics.backend.service;

import com.smart_logistics.backend.dto.realtime.GpsSample;
import com.smart_logistics.backend.dto.response.VehicleLocationResponse;
import com.smart_logistics.backend.entity.TransportTask;
import com.smart_logistics.backend.entity.Vehicle;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.mapper.TransportTaskMapper;
import com.smart_logistics.backend.mapper.VehicleMapper;
import com.smart_logistics.backend.security.BusinessDataScopeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class TaskTrackQueryService {
    private static final ZoneId API_TIME_ZONE = ZoneId.of("Asia/Shanghai");

    private final TransportTaskMapper transportTaskMapper;
    private final VehicleMapper vehicleMapper;
    private final BusinessDataScopeService dataScopeService;
    private final GpsInfluxService gpsInfluxService;
    private final Duration onlineThreshold;

    public TaskTrackQueryService(TransportTaskMapper transportTaskMapper,
                                 VehicleMapper vehicleMapper,
                                 BusinessDataScopeService dataScopeService,
                                 GpsInfluxService gpsInfluxService,
                                 @Value("${app.realtime.online-threshold:PT2M}")
                                 Duration onlineThreshold) {
        this.transportTaskMapper = transportTaskMapper;
        this.vehicleMapper = vehicleMapper;
        this.dataScopeService = dataScopeService;
        this.gpsInfluxService = gpsInfluxService;
        this.onlineThreshold = onlineThreshold;
    }

    public List<VehicleLocationResponse> getTrackPoints(Long taskId) {
        TransportTask task = transportTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "transport task not found");
        }
        dataScopeService.requireTaskAccess(task);
        Vehicle vehicle = vehicleMapper.selectById(task.getVehicleId());
        if (vehicle == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "task vehicle not found");
        }
        if (vehicle.getSimCode() == null || vehicle.getSimCode().isBlank()) return List.of();

        Instant start = toInstant(firstNonNull(task.getActualStartTime(),
                task.getPlanStartTime(), task.getCreatedAt()));
        Instant end = task.getActualEndTime() == null
                ? Instant.now() : toInstant(task.getActualEndTime());
        if (start == null || !start.isBefore(end)) return List.of();

        Instant now = Instant.now();
        try {
            return gpsInfluxService.querySamples(List.of(vehicle.getSimCode()), start, end).stream()
                    .map(sample -> toResponse(vehicle, taskId, sample, now)).toList();
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.REALTIME_PROVIDER_UNAVAILABLE,
                    "realtime track provider unavailable");
        }
    }

    private VehicleLocationResponse toResponse(Vehicle vehicle, Long taskId,
                                               GpsSample sample, Instant now) {
        return new VehicleLocationResponse(vehicle.getId(), vehicle.getPlateNumber(),
                sample.longitude(), sample.latitude(), sample.speed(), sample.direction(),
                sample.collectedAt().atZone(API_TIME_ZONE).toOffsetDateTime(),
                !sample.collectedAt().isBefore(now.minus(onlineThreshold)), taskId);
    }

    private LocalDateTime firstNonNull(LocalDateTime... values) {
        for (LocalDateTime value : values) if (value != null) return value;
        return null;
    }

    private Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.atZone(API_TIME_ZONE).toInstant();
    }
}
