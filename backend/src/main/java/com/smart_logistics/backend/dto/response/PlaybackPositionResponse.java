package com.smart_logistics.backend.dto.response;

public record PlaybackPositionResponse(
        double longitude,
        double latitude,
        String coordinateSystem) {
}
