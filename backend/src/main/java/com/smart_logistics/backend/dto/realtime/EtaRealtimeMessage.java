package com.smart_logistics.backend.dto.realtime;

import java.time.OffsetDateTime;

public record EtaRealtimeMessage(String type,
                                 Long taskId,
                                 String vehicleId,
                                 OffsetDateTime estimatedArrivalTime,
                                 OffsetDateTime etaCalculatedAt,
                                 long remainingDistanceMeters,
                                 double effectiveSpeedKmh) {

    public EtaRealtimeMessage(Long taskId, String vehicleId,
                              OffsetDateTime estimatedArrivalTime,
                              OffsetDateTime etaCalculatedAt,
                              long remainingDistanceMeters,
                              double effectiveSpeedKmh) {
        this("ETA_UPDATED", taskId, vehicleId, estimatedArrivalTime, etaCalculatedAt,
                remainingDistanceMeters, effectiveSpeedKmh);
    }
}
