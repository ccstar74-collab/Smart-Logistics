package com.smart_logistics.backend.dto;

import com.smart_logistics.backend.dto.realtime.GpsSample;

import java.time.Instant;
import java.util.List;

public record TaskTrackSnapshot(
        Instant transportStart,
        Instant transportEnd,
        List<GpsSample> points) {

    public TaskTrackSnapshot {
        points = List.copyOf(points);
    }
}
