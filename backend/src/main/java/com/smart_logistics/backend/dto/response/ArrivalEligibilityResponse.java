package com.smart_logistics.backend.dto.response;

import com.smart_logistics.backend.enums.ArrivalEligibilityReason;

import java.time.OffsetDateTime;

public record ArrivalEligibilityResponse(
        Long taskId,
        boolean eligible,
        Double distanceMeters,
        double radiusMeters,
        OffsetDateTime latestLocationAt,
        boolean locationOnline,
        ArrivalEligibilityReason reason) {
}
