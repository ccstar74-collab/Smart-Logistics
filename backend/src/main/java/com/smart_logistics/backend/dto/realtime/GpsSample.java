package com.smart_logistics.backend.dto.realtime;

import java.time.Instant;

public record GpsSample(String vehicleId, double longitude, double latitude,
                        Double speed, Double direction, Instant collectedAt) {
}
