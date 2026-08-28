package com.smart_logistics.backend.service;

import com.smart_logistics.backend.dto.realtime.GpsFieldRecord;
import com.smart_logistics.backend.dto.realtime.GpsSample;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class GpsSampleReconstructor {
    private static final Map<String, String> CANONICAL_FIELDS = Map.of(
            "latitude", "latitude",
            "lat", "latitude",
            "longitude", "longitude",
            "lon", "longitude",
            "speed", "speed",
            "speed_kmh", "speed",
            "direction", "direction",
            "heading", "direction");
    private final Duration mergeTolerance;

    public GpsSampleReconstructor(
            @Value("${app.realtime.gps-merge-tolerance:PT1S}") Duration mergeTolerance) {
        if (mergeTolerance.isNegative() || mergeTolerance.isZero()) {
            throw new IllegalArgumentException("GPS merge tolerance must be positive");
        }
        this.mergeTolerance = mergeTolerance;
    }

    public List<GpsSample> reconstruct(List<GpsFieldRecord> records) {
        Map<String, List<GpsFieldRecord>> byVehicle = new HashMap<>();
        for (GpsFieldRecord record : records) {
            if (isUsable(record)) {
                GpsFieldRecord canonicalRecord = new GpsFieldRecord(
                        record.vehicleId(), CANONICAL_FIELDS.get(record.field()),
                        record.value(), record.collectedAt());
                byVehicle.computeIfAbsent(record.vehicleId(), ignored -> new ArrayList<>())
                        .add(canonicalRecord);
            }
        }

        List<GpsSample> result = new ArrayList<>();
        for (Map.Entry<String, List<GpsFieldRecord>> entry : byVehicle.entrySet()) {
            List<GpsFieldRecord> ordered = entry.getValue().stream()
                    .sorted(Comparator.comparing(GpsFieldRecord::collectedAt)
                            .thenComparing(GpsFieldRecord::field))
                    .toList();
            List<Candidate> candidates = new ArrayList<>();
            for (GpsFieldRecord record : ordered) {
                Candidate candidate = nearestCompatible(candidates, record);
                if (candidate == null) {
                    candidate = new Candidate(entry.getKey(), record.collectedAt());
                    candidates.add(candidate);
                }
                candidate.add(record);
            }
            candidates.stream().filter(Candidate::isComplete)
                    .map(Candidate::toSample).forEach(result::add);
        }
        result.sort(Comparator.comparing(GpsSample::collectedAt)
                .thenComparing(GpsSample::vehicleId));
        return result;
    }

    private Candidate nearestCompatible(List<Candidate> candidates, GpsFieldRecord record) {
        Candidate nearest = null;
        Duration nearestDistance = null;
        for (Candidate candidate : candidates) {
            if (!candidate.canAccept(record, mergeTolerance)) continue;
            Duration distance = candidate.distanceTo(record.collectedAt());
            if (nearestDistance == null || distance.compareTo(nearestDistance) < 0) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private boolean isUsable(GpsFieldRecord record) {
        return record != null && record.vehicleId() != null && !record.vehicleId().isBlank()
                && record.field() != null && CANONICAL_FIELDS.containsKey(record.field())
                && record.collectedAt() != null && Double.isFinite(record.value());
    }

    private static final class Candidate {
        private final String vehicleId;
        private final Map<String, Double> values = new HashMap<>();
        private final List<Instant> timestamps = new ArrayList<>();
        private Instant earliest;
        private Instant latest;

        private Candidate(String vehicleId, Instant timestamp) {
            this.vehicleId = vehicleId;
            earliest = timestamp;
            latest = timestamp;
        }

        private boolean canAccept(GpsFieldRecord record, Duration tolerance) {
            if (values.containsKey(record.field())) return false;
            Instant min = record.collectedAt().isBefore(earliest) ? record.collectedAt() : earliest;
            Instant max = record.collectedAt().isAfter(latest) ? record.collectedAt() : latest;
            return Duration.between(min, max).compareTo(tolerance) <= 0;
        }

        private Duration distanceTo(Instant timestamp) {
            return timestamps.stream()
                    .map(existing -> Duration.between(existing, timestamp).abs())
                    .min(Duration::compareTo).orElse(Duration.ZERO);
        }

        private void add(GpsFieldRecord record) {
            values.put(record.field(), record.value());
            timestamps.add(record.collectedAt());
            if (record.collectedAt().isBefore(earliest)) earliest = record.collectedAt();
            if (record.collectedAt().isAfter(latest)) latest = record.collectedAt();
        }

        private boolean isComplete() {
            return values.containsKey("latitude") && values.containsKey("longitude");
        }

        private GpsSample toSample() {
            return new GpsSample(vehicleId, values.get("longitude"), values.get("latitude"),
                    values.get("speed"), values.get("direction"), latest);
        }
    }
}
