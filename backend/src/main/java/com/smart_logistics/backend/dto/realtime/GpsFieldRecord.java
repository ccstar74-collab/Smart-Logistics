package com.smart_logistics.backend.dto.realtime;

import java.time.Instant;

public record GpsFieldRecord(String vehicleId, String field, double value, Instant collectedAt) {
}
