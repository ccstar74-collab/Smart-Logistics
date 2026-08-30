package com.smart_logistics.backend.dto.response;

import com.smart_logistics.backend.dto.TransportTaskRouteSnapshot;
import com.smart_logistics.backend.dto.TrafficSnapshot;
import com.smart_logistics.backend.enums.TransportTaskRouteStatus;

import java.time.OffsetDateTime;
import java.util.List;

public record TransportTaskRouteResponse(String routeId,
                                         Long taskId,
                                         int routeVersion,
                                         TransportTaskRouteStatus routeStatus,
                                         String provider,
                                         String coordinateSystem,
                                         long distanceMeters,
                                         long referenceDurationSeconds,
                                         TrafficSnapshot traffic,
                                         OffsetDateTime generatedAt,
                                         OffsetDateTime activatedAt,
                                         OffsetDateTime deactivatedAt,
                                         List<List<Double>> points) {

    public TransportTaskRouteResponse {
        points = points.stream().map(List::copyOf).toList();
    }

    public TransportTaskRouteResponse(String routeId,
                                      Long taskId,
                                      int routeVersion,
                                      TransportTaskRouteStatus routeStatus,
                                      String provider,
                                      String coordinateSystem,
                                      long distanceMeters,
                                      long referenceDurationSeconds,
                                      OffsetDateTime generatedAt,
                                      OffsetDateTime activatedAt,
                                      OffsetDateTime deactivatedAt,
                                      List<List<Double>> points) {
        this(routeId, taskId, routeVersion, routeStatus, provider,
                coordinateSystem, distanceMeters, referenceDurationSeconds,
                null, generatedAt, activatedAt, deactivatedAt, points);
    }

    public TransportTaskRouteResponse(String routeId,
                                      Long taskId,
                                      int routeVersion,
                                      TransportTaskRouteStatus routeStatus,
                                      String provider,
                                      String coordinateSystem,
                                      long distanceMeters,
                                      long referenceDurationSeconds,
                                      OffsetDateTime generatedAt,
                                      List<List<Double>> points) {
        this(routeId, taskId, routeVersion, routeStatus, provider,
                coordinateSystem, distanceMeters, referenceDurationSeconds,
                null, generatedAt, null, null, points);
    }

    public static TransportTaskRouteResponse from(TransportTaskRouteSnapshot route) {
        return new TransportTaskRouteResponse(
                route.routeId(), route.taskId(), route.routeVersion(), route.status(),
                route.provider(), route.coordinateSystem(), route.distanceMeters(),
                route.durationSeconds(), route.trafficSnapshot(), route.createdAt(), route.activatedAt(),
                route.deactivatedAt(), route.routePoints());
    }
}
