package com.smart_logistics.backend.service.anomaly;

import com.smart_logistics.backend.dto.realtime.GpsSample;
import com.smart_logistics.backend.entity.TransportTask;
import com.smart_logistics.backend.enums.AlarmType;
import com.smart_logistics.backend.service.eta.EtaRouteProgress;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detects route deviation and abnormal stops from raw GPS samples.
 *
 * <p>The detector is deliberately episode based: a condition must remain true for the
 * configured duration, one alarm is emitted, and another alarm is allowed only after
 * the vehicle has recovered. Device-originated abnormal-open alerts still use MQTT.</p>
 */
@Service
public class RealtimeAnomalyDetectionService {

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private final AnomalyAlarmLifecyclePort alarmLifecyclePort;
    private final boolean enabled;
    private final double deviationThresholdMeters;
    private final double deviationRecoveryMeters;
    private final Duration deviationDuration;
    private final double stopSpeedThresholdKmh;
    private final double stopRadiusMeters;
    private final Duration stopDuration;
    private final double destinationGuardMeters;
    private final Map<DetectionKey, DetectionState> states = new ConcurrentHashMap<>();

    @Autowired
    public RealtimeAnomalyDetectionService(
            AnomalyAlarmLifecyclePort alarmLifecyclePort,
            @Value("${app.realtime-anomaly.enabled:true}") boolean enabled,
            @Value("${app.realtime-anomaly.deviation-threshold-meters:100}")
            double deviationThresholdMeters,
            @Value("${app.realtime-anomaly.deviation-recovery-meters:60}")
            double deviationRecoveryMeters,
            @Value("${app.realtime-anomaly.deviation-duration:PT30S}")
            Duration deviationDuration,
            @Value("${app.realtime-anomaly.stop-speed-threshold-kmh:1}")
            double stopSpeedThresholdKmh,
            @Value("${app.realtime-anomaly.stop-radius-meters:20}")
            double stopRadiusMeters,
            @Value("${app.realtime-anomaly.stop-duration:PT2M}") Duration stopDuration,
            @Value("${app.realtime-anomaly.destination-guard-meters:100}")
            double destinationGuardMeters) {
        this.alarmLifecyclePort = alarmLifecyclePort;
        this.enabled = enabled;
        this.deviationThresholdMeters = requirePositive(
                deviationThresholdMeters, "deviation threshold");
        this.deviationRecoveryMeters = requireNonNegative(
                deviationRecoveryMeters, "deviation recovery threshold");
        if (deviationRecoveryMeters >= deviationThresholdMeters) {
            throw new IllegalArgumentException(
                    "deviation recovery threshold must be below trigger threshold");
        }
        this.deviationDuration = requirePositive(deviationDuration, "deviation duration");
        this.stopSpeedThresholdKmh = requireNonNegative(
                stopSpeedThresholdKmh, "stop speed threshold");
        this.stopRadiusMeters = requirePositive(stopRadiusMeters, "stop radius");
        this.stopDuration = requirePositive(stopDuration, "stop duration");
        this.destinationGuardMeters = requireNonNegative(
                destinationGuardMeters, "destination guard");
    }

    public void evaluate(TransportTask task,
                         String vehicleDeviceCode,
                         List<GpsSample> samples,
                         GpsSample latest,
                         EtaRouteProgress routeProgress,
                         Instant now) {
        if (!enabled) {
            return;
        }
        if (task == null || task.getId() == null || vehicleDeviceCode == null
                || vehicleDeviceCode.isBlank() || latest == null
                || routeProgress == null || now == null) {
            throw new IllegalArgumentException("anomaly detection context is incomplete");
        }
        Instant observationTime = latest.collectedAt().isAfter(now)
                ? now : latest.collectedAt();
        evaluateDeviation(task.getId(), vehicleDeviceCode,
                routeProgress.distanceFromRouteMeters(), observationTime);
        evaluateStop(task.getId(), vehicleDeviceCode, samples, latest,
                routeProgress.remainingDistanceMeters(), observationTime);
    }

    public void clearTask(Long taskId) {
        if (taskId != null) {
            states.entrySet().removeIf(entry -> entry.getKey().taskId().equals(taskId)
                    && !entry.getValue().triggered);
        }
    }

    public void retainTasks(Set<Long> activeTaskIds) {
        states.entrySet().removeIf(entry -> !activeTaskIds.contains(entry.getKey().taskId())
                && !entry.getValue().triggered);
    }

    private void evaluateDeviation(Long taskId, String vehicleDeviceCode,
                                   double distanceFromRouteMeters, Instant now) {
        DetectionKey key = new DetectionKey(taskId, AlarmType.ROUTE_DEVIATION);
        DetectionState state = states.computeIfAbsent(key, ignored -> new DetectionState());
        if (distanceFromRouteMeters >= deviationThresholdMeters) {
            if (state.conditionSince == null) {
                state.conditionSince = now;
            }
            if (!state.triggered
                    && elapsed(state.conditionSince, now).compareTo(deviationDuration) >= 0) {
                state.alarmId = alarmLifecyclePort.open(
                        taskId, vehicleDeviceCode, "偏航",
                        "车辆连续偏离规划路线，当前偏离约"
                                + Math.round(distanceFromRouteMeters) + "米",
                        now);
                state.triggered = true;
            }
            return;
        }
        if (distanceFromRouteMeters <= deviationRecoveryMeters) {
            recoverOrReset(state, now);
        }
    }

    private void evaluateStop(Long taskId, String vehicleDeviceCode,
                              List<GpsSample> samples, GpsSample latest,
                              double remainingDistanceMeters, Instant now) {
        DetectionKey key = new DetectionKey(taskId, AlarmType.ABNORMAL_STOP);
        DetectionState state = states.computeIfAbsent(key, ignored -> new DetectionState());
        if (remainingDistanceMeters <= destinationGuardMeters
                || latest.speed() == null
                || latest.speed() > stopSpeedThresholdKmh) {
            recoverOrReset(state, now);
            return;
        }
        if (state.conditionSince == null) {
            state.conditionSince = now;
            return;
        }
        List<GpsSample> episodeSamples = samples == null ? List.of() : samples.stream()
                .filter(sample -> !sample.collectedAt().isBefore(state.conditionSince))
                .toList();
        if (!isStationary(episodeSamples, latest)) {
            recoverOrReset(state, now);
            return;
        }
        if (!state.triggered
                && elapsed(state.conditionSince, now).compareTo(stopDuration) >= 0) {
            state.alarmId = alarmLifecyclePort.open(
                    taskId, vehicleDeviceCode, "异常停留",
                    "车辆运输途中在约" + Math.round(stopRadiusMeters)
                            + "米范围内持续停留" + stopDuration.toSeconds() + "秒",
                    now);
            state.triggered = true;
        }
    }

    private void recoverOrReset(DetectionState state, Instant recoveredAt) {
        if (!state.triggered) {
            state.reset();
            return;
        }
        if (state.alarmId == null) {
            throw new IllegalStateException("active alarm id is unavailable");
        }
        if (alarmLifecyclePort.markConditionRecovered(state.alarmId, recoveredAt)) {
            state.reset();
        }
    }

    private boolean isStationary(List<GpsSample> samples, GpsSample latest) {
        if (samples.size() < 2) {
            return false;
        }
        for (GpsSample sample : samples) {
            if (sample.speed() != null && sample.speed() > stopSpeedThresholdKmh) {
                return false;
            }
            if (distanceMeters(sample.latitude(), sample.longitude(),
                    latest.latitude(), latest.longitude()) > stopRadiusMeters) {
                return false;
            }
        }
        return true;
    }

    private double distanceMeters(double firstLatitude, double firstLongitude,
                                  double secondLatitude, double secondLongitude) {
        double latitudeDelta = Math.toRadians(secondLatitude - firstLatitude);
        double longitudeDelta = Math.toRadians(secondLongitude - firstLongitude);
        double firstLatitudeRadians = Math.toRadians(firstLatitude);
        double secondLatitudeRadians = Math.toRadians(secondLatitude);
        double value = Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2)
                + Math.cos(firstLatitudeRadians) * Math.cos(secondLatitudeRadians)
                * Math.sin(longitudeDelta / 2) * Math.sin(longitudeDelta / 2);
        return 2 * EARTH_RADIUS_METERS
                * Math.atan2(Math.sqrt(value), Math.sqrt(1 - value));
    }

    private Duration elapsed(Instant since, Instant now) {
        return now.isBefore(since) ? Duration.ZERO : Duration.between(since, now);
    }

    private double requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private double requireNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
        return value;
    }

    private Duration requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private record DetectionKey(Long taskId, AlarmType alarmType) {
    }

    private static final class DetectionState {
        private Instant conditionSince;
        private boolean triggered;
        private Long alarmId;

        private void reset() {
            conditionSince = null;
            triggered = false;
            alarmId = null;
        }
    }
}
