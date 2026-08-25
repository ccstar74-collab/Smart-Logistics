package com.smart_logistics.backend.service;

import com.smart_logistics.backend.dto.mqtt.MqttAlertPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MqttAlertMessageHandlerTest {

    private static final String VALID_ALERT = """
            {
              "schema_version":"1.0",
              "vehicle_id":"real_001",
              "alert_type":"异常开箱",
              "description":"运输途中检测到箱门开启",
              "timestamp":"2026-08-24T10:23:47.000Z",
              "source":"device"
            }
            """;

    @Mock
    private MqttAlertIngestionService ingestionService;

    private MqttAlertMessageHandler handler;

    @BeforeEach
    void setUp() {
        handler = new MqttAlertMessageHandler(ingestionService);
    }

    @Test
    void parsesAndStoresValidAlert() {
        when(ingestionService.ingest(any(MqttAlertPayload.class)))
                .thenReturn(MqttAlertIngestionService.IngestionResult.STORED);

        MqttAlertMessageHandler.HandleResult result = handler.handle(VALID_ALERT);

        assertEquals(MqttAlertMessageHandler.HandleResult.STORED, result);
        verify(ingestionService).ingest(any(MqttAlertPayload.class));
    }

    @Test
    void reportsDuplicateWithoutCreatingAnotherBusinessAlarm() {
        when(ingestionService.ingest(any(MqttAlertPayload.class)))
                .thenReturn(MqttAlertIngestionService.IngestionResult.DUPLICATE);

        MqttAlertMessageHandler.HandleResult result = handler.handle(VALID_ALERT);

        assertEquals(MqttAlertMessageHandler.HandleResult.DUPLICATE, result);
    }

    @Test
    void discardsMalformedJson() {
        MqttAlertMessageHandler.HandleResult result = handler.handle("{bad-json");

        assertEquals(MqttAlertMessageHandler.HandleResult.INVALID, result);
        verify(ingestionService, never()).ingest(any(MqttAlertPayload.class));
    }

    @Test
    void discardsSchemaValidationFailure() {
        when(ingestionService.ingest(any(MqttAlertPayload.class)))
                .thenThrow(new IllegalArgumentException("unsupported alert_type"));

        MqttAlertMessageHandler.HandleResult result = handler.handle(VALID_ALERT);

        assertEquals(MqttAlertMessageHandler.HandleResult.INVALID, result);
    }

    @Test
    void propagatesDatabaseFailureSoQosOneCanRedeliver() {
        when(ingestionService.ingest(any(MqttAlertPayload.class)))
                .thenThrow(new DataAccessResourceFailureException("database offline"));

        assertThrows(
                DataAccessResourceFailureException.class,
                () -> handler.handle(VALID_ALERT)
        );
    }
}
