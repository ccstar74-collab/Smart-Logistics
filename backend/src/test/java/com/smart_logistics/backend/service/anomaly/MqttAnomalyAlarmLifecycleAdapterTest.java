package com.smart_logistics.backend.service.anomaly;

import com.smart_logistics.backend.dto.mqtt.MqttAlertPayload;
import com.smart_logistics.backend.service.MqttAlertIngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MqttAnomalyAlarmLifecycleAdapterTest {

    private static final Instant NOW = Instant.parse("2026-08-27T03:00:00Z");

    @Mock private MqttAlertIngestionService alertIngestionService;
    @Mock private ObjectProvider<AlarmConditionRecoveryPort> recoveryPortProvider;
    @Mock private AlarmConditionRecoveryPort recoveryPort;

    private MqttAnomalyAlarmLifecycleAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new MqttAnomalyAlarmLifecycleAdapter(
                alertIngestionService, recoveryPortProvider);
    }

    @Test
    void opensAlarmAndReturnsStableIdentity() {
        when(alertIngestionService.ingestWithIdentity(
                any(MqttAlertPayload.class), eq(12L)))
                .thenReturn(new MqttAlertIngestionService.AlarmIngestionResult(77L, true));

        Long alarmId = adapter.open(
                12L, "real_001", "偏航", "车辆连续偏离规划路线", NOW);

        assertEquals(77L, alarmId);
    }

    @Test
    void reportsPendingWhenFourthRoundRecoveryServiceIsUnavailable() {
        when(recoveryPortProvider.getIfAvailable()).thenReturn(null);

        assertFalse(adapter.markConditionRecovered(77L, NOW));
    }

    @Test
    void delegatesRecoveryWithoutReadingDispatchCommand() {
        when(recoveryPortProvider.getIfAvailable()).thenReturn(recoveryPort);

        assertTrue(adapter.markConditionRecovered(77L, NOW));

        verify(recoveryPort).markConditionRecovered(77L, NOW);
    }
}
