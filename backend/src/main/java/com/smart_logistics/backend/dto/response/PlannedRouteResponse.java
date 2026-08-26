package com.smart_logistics.backend.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

public record PlannedRouteResponse(Long taskId,
                                   String provider,
                                   String coordinateSystem,
                                   long distanceMeters,
                                   long referenceDurationSeconds,
                                   OffsetDateTime generatedAt,
                                   List<RoutePoint> points) {

    public PlannedRouteResponse {
        points = List.copyOf(points);
    }

    public record RoutePoint(double longitude, double latitude) {
    }
}
