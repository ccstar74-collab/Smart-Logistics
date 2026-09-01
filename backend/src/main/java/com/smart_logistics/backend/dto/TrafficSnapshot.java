package com.smart_logistics.backend.dto;

public record TrafficSnapshot(String source,
                              String strategy,
                              boolean restriction,
                              int trafficLights,
                              long unknownDistanceMeters,
                              long smoothDistanceMeters,
                              long slowDistanceMeters,
                              long congestedDistanceMeters,
                              long severeCongestedDistanceMeters) {

    public TrafficSnapshot {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("traffic source must not be blank");
        }
        if (strategy == null || strategy.isBlank()) {
            throw new IllegalArgumentException("traffic strategy must not be blank");
        }
        if (trafficLights < 0 || unknownDistanceMeters < 0
                || smoothDistanceMeters < 0 || slowDistanceMeters < 0
                || congestedDistanceMeters < 0
                || severeCongestedDistanceMeters < 0) {
            throw new IllegalArgumentException("traffic values must not be negative");
        }
    }

    public long observedDistanceMeters() {
        return unknownDistanceMeters + smoothDistanceMeters + slowDistanceMeters
                + congestedDistanceMeters + severeCongestedDistanceMeters;
    }
}
