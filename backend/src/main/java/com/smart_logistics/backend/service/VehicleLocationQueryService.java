package com.smart_logistics.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smart_logistics.backend.dto.realtime.GpsSample;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class VehicleLocationQueryService {
    private static final ZoneId API_TIME_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Logger LOGGER =
            LoggerFactory.getLogger(VehicleLocationQueryService.class);

    private final VehicleMapper vehicleMapper;
    private final TransportTaskMapper transportTaskMapper;
    private final BusinessDataScopeService dataScopeService;
    private final GpsInfluxService gpsInfluxService;
    private final Duration latestLookback;
    private final Duration onlineThreshold;

    public VehicleLocationQueryService(
            VehicleMapper vehicleMapper,
            TransportTaskMapper transportTaskMapper,
            BusinessDataScopeService dataScopeService,
            GpsInfluxService gpsInfluxService,
            @Value("${app.realtime.latest-lookback:PT24H}") Duration latestLookback,
            @Value("${app.realtime.online-threshold:PT2M}") Duration onlineThreshold) {
        this.vehicleMapper = vehicleMapper;
        this.transportTaskMapper = transportTaskMapper;
        this.dataScopeService = dataScopeService;
        this.gpsInfluxService = gpsInfluxService;
        this.latestLookback = latestLookback;
        this.onlineThreshold = onlineThreshold;
    }

    public List<VehicleLocationResponse> getLatestLocations() {
        LambdaQueryWrapper<Vehicle> query = new LambdaQueryWrapper<>();
        dataScopeService.applyVehicleScope(query, null);
        query.orderByAsc(Vehicle::getId);
        List<Vehicle> vehicles = vehicleMapper.selectList(query);
        List<Vehicle> mappedVehicles = vehicles.stream()
                .filter(vehicle -> vehicle.getSimCode() != null && !vehicle.getSimCode().isBlank())
                .toList();
        if (mappedVehicles.isEmpty()) return List.of();

        Instant now = Instant.now();
        List<GpsSample> samples = queryLatestRealtime(
                mappedVehicles.stream().map(Vehicle::getSimCode).toList(),
                latestLookback);
        Map<String, GpsSample> latestBySimCode = new HashMap<>();
        for (GpsSample sample : samples) {
            latestBySimCode.merge(sample.vehicleId(), sample,
                    (left, right) -> left.collectedAt().isAfter(right.collectedAt()) ? left : right);
        }
        Map<Long, Long> taskIds = activeTaskIds(
                mappedVehicles.stream().map(Vehicle::getId).toList());
        List<VehicleLocationResponse> result = new ArrayList<>();
        for (Vehicle vehicle : mappedVehicles) {
            GpsSample sample = latestBySimCode.get(vehicle.getSimCode());
            if (sample != null) result.add(toResponse(vehicle, sample, taskIds.get(vehicle.getId()), now));
        }
        return result;
    }

    public VehicleLocationResponse getLatestLocation(Long vehicleId) {
        Vehicle vehicle = getAuthorizedVehicle(vehicleId);
        if (vehicle.getSimCode() == null || vehicle.getSimCode().isBlank()) {
            throw locationNotFound();
        }
        Instant now = Instant.now();
        GpsSample latest = queryLatestRealtime(List.of(vehicle.getSimCode()),
                latestLookback).stream()
                .max(java.util.Comparator.comparing(GpsSample::collectedAt))
                .orElseThrow(this::locationNotFound);
        return toResponse(vehicle, latest, activeTaskIds(List.of(vehicleId)).get(vehicleId), now);
    }

    public List<VehicleLocationResponse> getLocationHistory(
            Long vehicleId, OffsetDateTime startTime, OffsetDateTime endTime) {
        if (startTime == null || endTime == null || !startTime.isBefore(endTime)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "startTime must be before endTime");
        }
        Vehicle vehicle = getAuthorizedVehicle(vehicleId);
        if (vehicle.getSimCode() == null || vehicle.getSimCode().isBlank()) return List.of();
        Instant now = Instant.now();
        return queryRealtime(List.of(vehicle.getSimCode()), startTime.toInstant(), endTime.toInstant())
                .stream().map(sample -> toResponse(vehicle, sample, null, now)).toList();
    }

    private Vehicle getAuthorizedVehicle(Long id) {
        Vehicle vehicle = vehicleMapper.selectById(id);
        if (vehicle == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "vehicle not found");
        }
        dataScopeService.requireVehicleAccess(vehicle);
        return vehicle;
    }

    private List<GpsSample> queryRealtime(List<String> simCodes, Instant start, Instant end) {
        try {
            return gpsInfluxService.querySamples(simCodes, start, end);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            logProviderFailure("history", simCodes, start, end, exception);
            throw new BusinessException(ErrorCode.REALTIME_PROVIDER_UNAVAILABLE,
                    "realtime location provider unavailable");
        }
    }

    private List<GpsSample> queryLatestRealtime(List<String> simCodes,
                                                Duration lookback) {
        try {
            return gpsInfluxService.queryLatestSamples(simCodes, lookback);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            LOGGER.error("Realtime GPS latest query failed simCodes={} lookback={}",
                    simCodes, lookback, exception);
            throw new BusinessException(ErrorCode.REALTIME_PROVIDER_UNAVAILABLE,
                    "realtime location provider unavailable");
        }
    }

    private void logProviderFailure(String queryType, List<String> simCodes,
                                    Instant start, Instant end, RuntimeException exception) {
        LOGGER.error("Realtime GPS {} query failed simCodes={} start={} end={}",
                queryType, simCodes, start, end, exception);
    }

    private Map<Long, Long> activeTaskIds(List<Long> vehicleIds) {
        if (vehicleIds.isEmpty()) return Map.of();
        List<TransportTask> tasks = transportTaskMapper.selectList(
                new LambdaQueryWrapper<TransportTask>()
                        .in(TransportTask::getVehicleId, vehicleIds)
                        .in(TransportTask::getStatus, TransportTaskStatus.WAITING.name(),
                                TransportTaskStatus.TRANSPORTING.name())
                        .orderByDesc(TransportTask::getUpdatedAt)
                        .orderByDesc(TransportTask::getId));
        Map<Long, Long> result = new LinkedHashMap<>();
        for (TransportTask task : tasks) result.putIfAbsent(task.getVehicleId(), task.getId());
        return result;
    }

    private VehicleLocationResponse toResponse(Vehicle vehicle, GpsSample sample,
                                               Long taskId, Instant now) {
        return new VehicleLocationResponse(vehicle.getId(), vehicle.getPlateNumber(),
                sample.longitude(), sample.latitude(), sample.speed(), sample.direction(),
                sample.collectedAt().atZone(API_TIME_ZONE).toOffsetDateTime(),
                !sample.collectedAt().isBefore(now.minus(onlineThreshold)), taskId);
    }

    private BusinessException locationNotFound() {
        return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "vehicle location not found");
    }
}
