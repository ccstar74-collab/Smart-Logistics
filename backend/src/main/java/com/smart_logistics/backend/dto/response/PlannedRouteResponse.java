package com.smart_logistics.backend.dto.response;

import com.smart_logistics.backend.enums.TransportTaskRouteStatus;

import java.time.OffsetDateTime;
import java.util.List;

public record PlannedRouteResponse(Long taskId,
                                   String routeId,
                                   int routeVersion,
                                   TransportTaskRouteStatus routeStatus,
                                   String vehicleDeviceCode,
                                   String provider,
                                   String coordinateSystem,
                                   long distanceMeters,
                                   long referenceDurationSeconds,
                                   OffsetDateTime generatedAt,
                                   List<List<Double>> points) {

    public PlannedRouteResponse {
        points = points.stream().map(point -> {
            if (point == null || point.size() != 2) {
                throw new IllegalArgumentException(
                        "planned route point must contain longitude and latitude");
            }
            double longitude = point.get(0);
            double latitude = point.get(1);
            if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180
                    || !Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
                throw new IllegalArgumentException(
                        "planned route point is outside the valid range");
            }
            return List.copyOf(point);
        }).toList();
        if (points.size() < 2) {
            throw new IllegalArgumentException("planned route must contain at least two points");
        }
    }
}
