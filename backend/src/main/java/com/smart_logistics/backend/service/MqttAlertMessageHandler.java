package com.smart_logistics.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart_logistics.backend.dto.mqtt.MqttAlertPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MqttAlertMessageHandler {

    public enum HandleResult {
        STORED,
        DUPLICATE,
        INVALID
    }

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MqttAlertIngestionService ingestionService;

    public MqttAlertMessageHandler(MqttAlertIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    public HandleResult handle(String rawPayload) {
        try {
            MqttAlertPayload payload = objectMapper.readValue(
                    rawPayload, MqttAlertPayload.class);
            MqttAlertIngestionService.IngestionResult result =
                    ingestionService.ingest(payload);
            if (result == MqttAlertIngestionService.IngestionResult.DUPLICATE) {
                log.info("忽略重复MQTT告警 vehicleId={}, type={}, timestamp={}",
                        payload.vehicleId(), payload.alertType(), payload.timestamp());
                return HandleResult.DUPLICATE;
            }
            log.info("MQTT告警已写入MySQL vehicleId={}, type={}, timestamp={}",
                    payload.vehicleId(), payload.alertType(), payload.timestamp());
            return HandleResult.STORED;
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            log.warn("丢弃不符合alert schema 1.0的MQTT消息 payload={}, reason={}",
                    rawPayload, exception.getMessage());
            return HandleResult.INVALID;
        }
    }
}
