package com.smart_logistics.backend.dto.response;

import java.util.List;

public record PlaybackActualTrackResponse(
        String coordinateSystem,
        List<PlaybackTrackPointResponse> points) {

    public PlaybackActualTrackResponse {
        points = List.copyOf(points);
    }
}
