package com.smart_logistics.backend.service.eta;

import com.smart_logistics.backend.dto.TrafficSnapshot;

import java.time.Duration;
import java.util.List;

public record EtaPlannedRoute(List<EtaCoordinate> polyline,
                              long distanceMeters,
                              Duration referenceDuration,
                              TrafficSnapshot trafficSnapshot) {

    public EtaPlannedRoute(List<EtaCoordinate> polyline,
                           long distanceMeters,
                           Duration referenceDuration) {
        this(polyline, distanceMeters, referenceDuration, null);
    }

    public EtaPlannedRoute {
        polyline = List.copyOf(polyline);
        if (polyline.size() < 2) {
            throw new IllegalArgumentException("planned route must contain at least two points");
        }
        if (distanceMeters <= 0 || referenceDuration.isZero()
                || referenceDuration.isNegative()) {
            throw new IllegalArgumentException("planned route distance and duration must be positive");
        }
    }
}
