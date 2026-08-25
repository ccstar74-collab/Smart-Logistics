package com.smart_logistics.backend.dto.mqtt;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MqttAlertPayload(
        @JsonProperty("schema_version") String schemaVersion,
        @JsonProperty("vehicle_id") String vehicleId,
        @JsonProperty("alert_type") String alertType,
        String description,
        String timestamp,
        String source
) {
}
