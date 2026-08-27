package com.smart_logistics.backend.service;

import com.smart_logistics.backend.dto.mqtt.MqttAlertRecoveryPayload;
import com.smart_logistics.backend.entity.Alarm;
import com.smart_logistics.backend.mapper.AlarmMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MqttAlertRecoveryServiceTest {

    @Test
    void correlatesOriginalAlarmAndMarksConditionRecovered() {
        AlarmMapper alarmMapper = mock(AlarmMapper.class);
        AlarmResolutionService resolutionService = mock(AlarmResolutionService.class);
        Alarm alarm = new Alarm();
        alarm.setId(77L);
        when(alarmMapper.selectOne(any())).thenReturn(alarm);
        MqttAlertRecoveryService service =
                new MqttAlertRecoveryService(alarmMapper, resolutionService);

        MqttAlertRecoveryService.RecoveryResult result = service.recover(payload());

        assertEquals(MqttAlertRecoveryService.RecoveryResult.RECOVERED, result);
        verify(resolutionService).markConditionRecovered(
                77L, Instant.parse("2026-08-27T09:30:03Z"));
    }

    @Test
    void reportsMissingOriginalAlarmWithoutCreatingRecoveryAlarm() {
        AlarmMapper alarmMapper = mock(AlarmMapper.class);
        AlarmResolutionService resolutionService = mock(AlarmResolutionService.class);
        MqttAlertRecoveryService service =
                new MqttAlertRecoveryService(alarmMapper, resolutionService);

        assertEquals(MqttAlertRecoveryService.RecoveryResult.ALARM_NOT_FOUND,
                service.recover(payload()));
    }

    private MqttAlertRecoveryPayload payload() {
        return new MqttAlertRecoveryPayload(
                "1.0", "real_001", "异常开箱", "RECOVERED",
                "2026-08-27T09:30:00Z", "2026-08-27T09:30:03Z", "device");
    }
}
