package com.smart_logistics.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart_logistics.backend.dto.mqtt.MqttAlertRecoveryPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MqttAlertRecoveryMessageHandler {

    public enum HandleResult {
        RECOVERED,
        ALARM_NOT_FOUND,
        INVALID
    }

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MqttAlertRecoveryService recoveryService;

    public MqttAlertRecoveryMessageHandler(MqttAlertRecoveryService recoveryService) {
        this.recoveryService = recoveryService;
    }

    public HandleResult handle(String rawPayload) {
        try {
            MqttAlertRecoveryPayload payload = objectMapper.readValue(
                    rawPayload, MqttAlertRecoveryPayload.class);
            MqttAlertRecoveryService.RecoveryResult result = recoveryService.recover(payload);
            if (result == MqttAlertRecoveryService.RecoveryResult.ALARM_NOT_FOUND) {
                log.warn("MQTT recovery did not match an alarm vehicleId={}, type={}, triggeredAt={}",
                        payload.vehicleId(), payload.alertType(), payload.triggeredAt());
                return HandleResult.ALARM_NOT_FOUND;
            }
            log.info("MQTT alarm condition recovered vehicleId={}, type={}, recoveredAt={}",
                    payload.vehicleId(), payload.alertType(), payload.recoveredAt());
            return HandleResult.RECOVERED;
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            log.warn("Discarding invalid MQTT alert recovery payload={}, reason={}",
                    rawPayload, exception.getMessage());
            return HandleResult.INVALID;
        }
    }
}
