package com.smart_logistics.backend.service;

import com.smart_logistics.backend.dto.mqtt.MqttAlertRecoveryPayload;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MqttAlertRecoveryMessageHandlerTest {

    @Test
    void parsesAndDelegatesValidRecovery() {
        MqttAlertRecoveryService service = mock(MqttAlertRecoveryService.class);
        when(service.recover(any(MqttAlertRecoveryPayload.class)))
                .thenReturn(MqttAlertRecoveryService.RecoveryResult.RECOVERED);
        MqttAlertRecoveryMessageHandler handler =
                new MqttAlertRecoveryMessageHandler(service);
        String payload = """
                {"schema_version":"1.0","vehicle_id":"real_001",
                 "alert_type":"异常开箱","condition_status":"RECOVERED",
                 "triggered_at":"2026-08-27T09:30:00Z",
                 "recovered_at":"2026-08-27T09:30:03Z","source":"device"}
                """;

        assertEquals(MqttAlertRecoveryMessageHandler.HandleResult.RECOVERED,
                handler.handle(payload));
        verify(service).recover(any(MqttAlertRecoveryPayload.class));
    }

    @Test
    void rejectsMalformedRecovery() {
        MqttAlertRecoveryService service = mock(MqttAlertRecoveryService.class);
        MqttAlertRecoveryMessageHandler handler =
                new MqttAlertRecoveryMessageHandler(service);

        assertEquals(MqttAlertRecoveryMessageHandler.HandleResult.INVALID,
                handler.handle("{bad-json"));
    }
}
