package com.smart_logistics.backend.dto.mqtt;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MqttAlertRecoveryPayload(
        @JsonProperty("schema_version") String schemaVersion,
        @JsonProperty("vehicle_id") String vehicleId,
        @JsonProperty("alert_type") String alertType,
        @JsonProperty("condition_status") String conditionStatus,
        @JsonProperty("triggered_at") String triggeredAt,
        @JsonProperty("recovered_at") String recoveredAt,
        String source
) {
}
