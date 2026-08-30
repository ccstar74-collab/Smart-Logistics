package com.smart_logistics.backend.dto.response;

import java.time.OffsetDateTime;

public record PlaybackTrackPointResponse(
        double longitude,
        double latitude,
        Double speed,
        Double heading,
        OffsetDateTime timestamp) {
}
