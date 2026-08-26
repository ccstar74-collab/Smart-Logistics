package com.smart_logistics.backend.service.eta;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.smart_logistics.backend.dto.realtime.EtaRealtimeMessage;
import com.smart_logistics.backend.dto.realtime.GpsSample;
import com.smart_logistics.backend.entity.TransportTask;
import com.smart_logistics.backend.entity.Vehicle;
import com.smart_logistics.backend.enums.TransportTaskStatus;
import com.smart_logistics.backend.handler.GpsWebSocketHandler;
import com.smart_logistics.backend.mapper.TransportTaskMapper;
import com.smart_logistics.backend.mapper.VehicleMapper;
import com.smart_logistics.backend.service.GpsInfluxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

@Service
public class EtaCalculationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EtaCalculationService.class);
    private static final ZoneId DATABASE_TIME_ZONE = ZoneId.of("Asia/Shanghai");
    private static final double MINIMUM_MOVING_SPEED_KMH = 5.0;
    private static final double MAXIMUM_ROAD_SPEED_KMH = 130.0;

    private final TransportTaskMapper transportTaskMapper;
    private final VehicleMapper vehicleMapper;
    private final GpsInfluxService gpsInfluxService;
    private final EtaPlannedRouteService plannedRouteService;
    private final RouteProgressProjector routeProjector;
    private final GpsWebSocketHandler webSocketHandler;
    private final Duration gpsMaxAge;
    private final Duration speedHistoryWindow;
    private final Duration minEtaChange;
    private final Duration forcePersistInterval;
    private final Clock clock;

    @Autowired
    public EtaCalculationService(
            TransportTaskMapper transportTaskMapper,
            VehicleMapper vehicleMapper,
            GpsInfluxService gpsInfluxService,
            EtaPlannedRouteService plannedRouteService,
            GpsWebSocketHandler webSocketHandler,
            @Value("${app.eta.gps-max-age:PT2M}") Duration gpsMaxAge,
            @Value("${app.eta.speed-history-window:PT10M}") Duration speedHistoryWindow,
            @Value("${app.eta.min-change:PT0S}") Duration minEtaChange,
            @Value("${app.eta.force-persist-interval:PT1S}") Duration forcePersistInterval) {
        this(transportTaskMapper, vehicleMapper, gpsInfluxService, plannedRouteService,
                new RouteProgressProjector(), webSocketHandler, gpsMaxAge,
                speedHistoryWindow, minEtaChange, forcePersistInterval, Clock.systemUTC());
    }

    EtaCalculationService(TransportTaskMapper transportTaskMapper,
                          VehicleMapper vehicleMapper,
                          GpsInfluxService gpsInfluxService,
                          EtaPlannedRouteService plannedRouteService,
                          RouteProgressProjector routeProjector,
                          GpsWebSocketHandler webSocketHandler,
                          Duration gpsMaxAge,
                          Duration speedHistoryWindow,
                          Duration minEtaChange,
                          Duration forcePersistInterval,
                          Clock clock) {
        this.transportTaskMapper = transportTaskMapper;
        this.vehicleMapper = vehicleMapper;
        this.gpsInfluxService = gpsInfluxService;
        this.plannedRouteService = plannedRouteService;
        this.routeProjector = routeProjector;
        this.webSocketHandler = webSocketHandler;
        this.gpsMaxAge = gpsMaxAge;
        this.speedHistoryWindow = speedHistoryWindow;
        this.minEtaChange = minEtaChange;
        this.forcePersistInterval = forcePersistInterval;
        this.clock = clock;
    }

    public EtaRefreshSummary refreshTransportingTasks() {
        List<TransportTask> tasks = transportTaskMapper.selectList(
                new LambdaQueryWrapper<TransportTask>()
                        .eq(TransportTask::getStatus, TransportTaskStatus.TRANSPORTING.name())
                        .orderByAsc(TransportTask::getId));
        int updated = 0;
        int skipped = 0;
        int failed = 0;
        Instant now = clock.instant();
        for (TransportTask task : tasks) {
            try {
                if (refreshTask(task, now)) updated++;
                else skipped++;
            } catch (RuntimeException exception) {
                failed++;
                LOGGER.warn("ETA refresh failed taskId={}: {}",
                        task.getId(), exception.getMessage());
            }
        }
        return new EtaRefreshSummary(tasks.size(), updated, skipped, failed);
    }

    boolean refreshTask(TransportTask task, Instant now) {
        if (!TransportTaskStatus.TRANSPORTING.name().equals(task.getStatus())) {
            return false;
        }
        if (!hasRouteCoordinates(task)) {
            LOGGER.debug("Skip ETA taskId={} because route coordinates are missing",
                    task.getId());
            return false;
        }
        Vehicle vehicle = vehicleMapper.selectById(task.getVehicleId());
        if (vehicle == null || vehicle.getSimCode() == null || vehicle.getSimCode().isBlank()) {
            LOGGER.debug("Skip ETA taskId={} because vehicle simCode is missing",
                    task.getId());
            return false;
        }
        List<GpsSample> samples = gpsInfluxService.querySamples(
                List.of(vehicle.getSimCode()), now.minus(speedHistoryWindow), now.plusMillis(1));
        GpsSample latest = samples.stream()
                .filter(sample -> !sample.collectedAt().isBefore(now.minus(gpsMaxAge)))
                .max(Comparator.comparing(GpsSample::collectedAt))
                .orElse(null);
        if (latest == null) {
            LOGGER.debug("Skip ETA taskId={} because recent GPS is unavailable", task.getId());
            return false;
        }

        EtaPlannedRoute plannedRoute = plannedRouteService.getRoute(task);
        Wgs84ToGcj02Converter.Coordinate convertedCurrent =
                Wgs84ToGcj02Converter.convert(latest.longitude(), latest.latitude());
        EtaRouteProgress progress = routeProjector.project(
                new EtaCoordinate(convertedCurrent.longitude(), convertedCurrent.latitude()),
                plannedRoute.polyline());
        double effectiveSpeedKmh = effectiveSpeedKmh(samples, latest, plannedRoute);
        long remainingDistanceMeters = Math.max(0,
                Math.round(progress.remainingDistanceMeters()));
        long remainingSeconds = remainingDistanceMeters == 0 ? 0
                : Math.max(1, Math.round(remainingDistanceMeters * 3.6 / effectiveSpeedKmh));
        Instant estimatedArrival = now.plusSeconds(remainingSeconds);
        LocalDateTime estimatedArrivalTime = LocalDateTime.ofInstant(
                estimatedArrival, DATABASE_TIME_ZONE);
        LocalDateTime calculatedAt = LocalDateTime.ofInstant(now, DATABASE_TIME_ZONE);
        if (!shouldPersist(task, estimatedArrival, now)) {
            return false;
        }
        int affected = transportTaskMapper.update(null,
                new LambdaUpdateWrapper<TransportTask>()
                        .eq(TransportTask::getId, task.getId())
                        .eq(TransportTask::getStatus, TransportTaskStatus.TRANSPORTING.name())
                        .set(TransportTask::getEstimatedArrivalTime, estimatedArrivalTime)
                        .set(TransportTask::getEtaCalculatedAt, calculatedAt));
        if (affected == 1) {
            webSocketHandler.broadcastEta(new EtaRealtimeMessage(
                    task.getId(), vehicle.getSimCode(),
                    estimatedArrivalTime.atZone(DATABASE_TIME_ZONE).toOffsetDateTime(),
                    calculatedAt.atZone(DATABASE_TIME_ZONE).toOffsetDateTime(),
                    remainingDistanceMeters, effectiveSpeedKmh));
            LOGGER.info("ETA updated taskId={} vehicle={} remainingMeters={} "
                            + "offRouteMeters={} speedKmh={} eta={}",
                    task.getId(), vehicle.getSimCode(), remainingDistanceMeters,
                    Math.round(progress.distanceFromRouteMeters()),
                    String.format(java.util.Locale.ROOT, "%.1f", effectiveSpeedKmh),
                    estimatedArrivalTime);
            return true;
        }
        return false;
    }

    private boolean hasRouteCoordinates(TransportTask task) {
        return task.getStartLongitude() != null && task.getStartLatitude() != null
                && task.getEndLongitude() != null && task.getEndLatitude() != null;
    }

    private double effectiveSpeedKmh(List<GpsSample> samples, GpsSample latest,
                                     EtaPlannedRoute plannedRoute) {
        double historyAverage = samples.stream()
                .map(GpsSample::speed)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .filter(speed -> speed > 0.5 && speed <= MAXIMUM_ROAD_SPEED_KMH)
                .average()
                .orElse(0);
        double currentSpeed = latest.speed() == null ? 0 : latest.speed();
        double observedSpeed;
        if (currentSpeed > 0.5 && historyAverage > 0) {
            observedSpeed = currentSpeed * 0.4 + historyAverage * 0.6;
        } else {
            observedSpeed = Math.max(currentSpeed, historyAverage);
        }
        if (observedSpeed <= 0.5) {
            observedSpeed = plannedRoute.distanceMeters() * 3.6
                    / plannedRoute.referenceDuration().toSeconds();
        }
        return Math.max(MINIMUM_MOVING_SPEED_KMH,
                Math.min(MAXIMUM_ROAD_SPEED_KMH, observedSpeed));
    }

    private boolean shouldPersist(TransportTask task, Instant newEta, Instant now) {
        if (task.getEstimatedArrivalTime() == null || task.getEtaCalculatedAt() == null) {
            return true;
        }
        Instant previousEta = task.getEstimatedArrivalTime()
                .atZone(DATABASE_TIME_ZONE).toInstant();
        Duration etaDifference = Duration.between(previousEta, newEta).abs();
        if (etaDifference.compareTo(minEtaChange) >= 0) {
            return true;
        }
        Instant lastCalculated = task.getEtaCalculatedAt()
                .atZone(DATABASE_TIME_ZONE).toInstant();
        return !lastCalculated.isAfter(now.minus(forcePersistInterval));
    }

    public record EtaRefreshSummary(int total, int updated, int skipped, int failed) {
    }

}
