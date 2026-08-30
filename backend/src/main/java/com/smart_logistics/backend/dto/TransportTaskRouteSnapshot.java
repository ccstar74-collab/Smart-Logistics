package com.smart_logistics.backend.dto;

import com.smart_logistics.backend.enums.TransportTaskRouteStatus;

import java.time.OffsetDateTime;
import java.util.List;

public record TransportTaskRouteSnapshot(Long id,
                                         String routeId,
                                         Long taskId,
                                         String provider,
                                         String coordinateSystem,
                                         List<List<Double>> routePoints,
                                         long distanceMeters,
                                         long durationSeconds,
                                         int routeVersion,
                                         TransportTaskRouteStatus status,
                                         OffsetDateTime createdAt,
                                         OffsetDateTime activatedAt,
                                         OffsetDateTime deactivatedAt,
                                         OffsetDateTime updatedAt) {

    public TransportTaskRouteSnapshot {
        routePoints = routePoints.stream().map(List::copyOf).toList();
    }

    public TransportTaskRouteSnapshot(Long id,
                                      String routeId,
                                      Long taskId,
                                      String provider,
                                      String coordinateSystem,
                                      List<List<Double>> routePoints,
                                      long distanceMeters,
                                      long durationSeconds,
                                      int routeVersion,
                                      TransportTaskRouteStatus status,
                                      OffsetDateTime createdAt,
                                      OffsetDateTime updatedAt) {
        this(id, routeId, taskId, provider, coordinateSystem, routePoints,
                distanceMeters, durationSeconds, routeVersion, status,
                createdAt, null, null, updatedAt);
    }
}
