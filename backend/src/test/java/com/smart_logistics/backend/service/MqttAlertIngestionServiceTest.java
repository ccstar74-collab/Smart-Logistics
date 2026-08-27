package com.smart_logistics.backend.service;

import com.smart_logistics.backend.dto.mqtt.MqttAlertPayload;
import com.smart_logistics.backend.entity.Alarm;
import com.smart_logistics.backend.enums.AlarmLevel;
import com.smart_logistics.backend.enums.AlarmStatus;
import com.smart_logistics.backend.enums.AlarmType;
import com.smart_logistics.backend.mapper.AlarmMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MqttAlertIngestionServiceTest {

    @Mock
    private AlarmMapper alarmMapper;

    private MqttAlertIngestionService service;

    @BeforeEach
    void setUp() {
        service = new MqttAlertIngestionService(alarmMapper);
    }

    @Test
    void storesDeviceAbnormalOpenWithDeterministicEventKey() {
        when(alarmMapper.insert(any(Alarm.class))).thenReturn(1);

        MqttAlertIngestionService.IngestionResult result = service.ingest(payload(
                "异常开箱", "2026-08-24T10:23:47.000Z"));

        ArgumentCaptor<Alarm> captor = ArgumentCaptor.forClass(Alarm.class);
        verify(alarmMapper).insert(captor.capture());
        Alarm alarm = captor.getValue();
        assertEquals(MqttAlertIngestionService.IngestionResult.STORED, result);
        assertEquals("real_001", alarm.getDeviceCode());
        assertEquals(AlarmType.ABNORMAL_OPEN.name(), alarm.getAlarmType());
        assertEquals(AlarmLevel.HIGH.name(), alarm.getLevel());
        assertEquals(AlarmStatus.UNHANDLED.name(), alarm.getStatus());
        assertEquals("device", alarm.getSource());
        assertEquals("1.0", alarm.getSchemaVersion());
        assertEquals(64, alarm.getEventKey().length());
        assertEquals(LocalDateTime.of(2026, 8, 24, 18, 23, 47), alarm.getOccurredAt());
        assertNotNull(alarm.getCreatedAt());
    }

    @Test
    void reportsDuplicateWhenDatabaseUniqueIndexRejectsEventKey() {
        when(alarmMapper.insert(any(Alarm.class)))
                .thenThrow(new DuplicateKeyException("uk_alarm_event_key"));
        Alarm existing = new Alarm();
        existing.setId(88L);
        when(alarmMapper.selectOne(any())).thenReturn(existing);

        MqttAlertIngestionService.IngestionResult result = service.ingest(payload(
                "异常开箱", "2026-08-24T10:23:47.000Z"));

        assertEquals(MqttAlertIngestionService.IngestionResult.DUPLICATE, result);
    }

    @Test
    void returnsGeneratedAlarmIdentityForStateMachine() {
        doAnswer(invocation -> {
            Alarm alarm = invocation.getArgument(0);
            alarm.setId(77L);
            return 1;
        }).when(alarmMapper).insert(any(Alarm.class));

        MqttAlertIngestionService.AlarmIngestionResult result =
                service.ingestWithIdentity(
                        new MqttAlertPayload(
                                "1.0", "real_001", "异常停留", "车辆异常停留",
                                "2026-08-27T03:00:00Z", "backend"),
                        12L);

        assertEquals(77L, result.alarmId());
        assertEquals(true, result.created());
    }

    @Test
    void storesBackendDetectedAlertWithTaskAssociation() {
        when(alarmMapper.insert(any(Alarm.class))).thenReturn(1);
        MqttAlertPayload payload = new MqttAlertPayload(
                "1.0", "real_001", "偏航", "车辆连续偏离规划路线",
                "2026-08-27T03:00:00Z", "backend");

        service.ingest(payload, 12L);

        ArgumentCaptor<Alarm> captor = ArgumentCaptor.forClass(Alarm.class);
        verify(alarmMapper).insert(captor.capture());
        assertEquals(12L, captor.getValue().getTaskId());
        assertEquals(AlarmType.ROUTE_DEVIATION.name(), captor.getValue().getAlarmType());
        assertEquals("backend", captor.getValue().getSource());
    }

    @Test
    void sameInstantWithDifferentOffsetsBuildsSameEventKey() {
        when(alarmMapper.insert(any(Alarm.class))).thenReturn(1);

        service.ingest(payload("异常开箱", "2026-08-24T10:23:47Z"));
        service.ingest(payload("异常开箱", "2026-08-24T18:23:47+08:00"));

        ArgumentCaptor<Alarm> captor = ArgumentCaptor.forClass(Alarm.class);
        verify(alarmMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertEquals(
                captor.getAllValues().get(0).getEventKey(),
                captor.getAllValues().get(1).getEventKey());
    }

    @Test
    void laterF1EventBuildsDifferentEventKey() {
        when(alarmMapper.insert(any(Alarm.class))).thenReturn(1);

        service.ingest(payload("异常开箱", "2026-08-24T10:23:47Z"));
        service.ingest(payload("异常开箱", "2026-08-24T10:24:10Z"));

        ArgumentCaptor<Alarm> captor = ArgumentCaptor.forClass(Alarm.class);
        verify(alarmMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertNotEquals(
                captor.getAllValues().get(0).getEventKey(),
                captor.getAllValues().get(1).getEventKey());
    }

    @Test
    void rejectsUnsupportedAlertBeforeDatabaseWrite() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.ingest(payload("非法告警", "2026-08-24T10:23:47Z"))
        );

        assertEquals("unsupported alert_type", exception.getMessage());
        verify(alarmMapper, never()).insert(any(Alarm.class));
    }

    @Test
    void reportsDatabaseFailureWhenInsertAffectsNoRows() {
        when(alarmMapper.insert(any(Alarm.class))).thenReturn(0);

        assertThrows(
                DataAccessResourceFailureException.class,
                () -> service.ingest(payload(
                        "异常开箱", "2026-08-24T10:23:47Z"))
        );
    }

    private MqttAlertPayload payload(String type, String timestamp) {
        return new MqttAlertPayload(
                "1.0",
                "real_001",
                type,
                "运输途中检测到箱门开启",
                timestamp,
                "device"
        );
    }
}
