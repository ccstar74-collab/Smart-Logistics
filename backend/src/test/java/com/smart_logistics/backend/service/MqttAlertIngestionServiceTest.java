package com.smart_logistics.backend.service;

import com.smart_logistics.backend.dto.mqtt.MqttAlertPayload;
import com.smart_logistics.backend.dto.realtime.GpsSample;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MqttAlertIngestionServiceTest {

    @Mock
    private AlarmMapper alarmMapper;
    @Mock
    private AlarmAssociationService associationService;

    @Mock
    private GpsInfluxService gpsInfluxService;

    private MqttAlertIngestionService service;

    @BeforeEach
    void setUp() {
        service = new MqttAlertIngestionService(
                alarmMapper, associationService, gpsInfluxService);
        lenient().when(associationService.resolve("real_001"))
                .thenReturn(new AlarmAssociationService.AlarmAssociation(23L, 15L));
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
        assertEquals(23L, alarm.getVehicleId());
        assertEquals(15L, alarm.getTaskId());
        assertEquals("ACTIVE", alarm.getConditionStatus());
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
    void associatesRegisteredVehicleActiveTaskAndLocation() {
        when(associationService.resolve("real_001"))
                .thenReturn(new AlarmAssociationService.AlarmAssociation(20L, 30L));
        when(gpsInfluxService.querySamples(anyCollection(), any(), any()))
                .thenReturn(List.of(new GpsSample("real_001", 121.5, 31.2,
                        40.0, 90.0, Instant.parse("2026-08-24T10:23:45Z"))));
        when(alarmMapper.insert(any(Alarm.class))).thenReturn(1);

        service.ingest(payload("异常开箱", "2026-08-24T10:23:47.000Z"));

        ArgumentCaptor<Alarm> captor = ArgumentCaptor.forClass(Alarm.class);
        verify(alarmMapper).insert(captor.capture());
        Alarm alarm = captor.getValue();
        assertEquals(20L, alarm.getVehicleId());
        assertEquals(30L, alarm.getTaskId());
        assertEquals(BigDecimal.valueOf(121.5), alarm.getLongitude());
        assertEquals(BigDecimal.valueOf(31.2), alarm.getLatitude());
    }

    @Test
    void keepsNullAssociationsForUnregisteredDevice() {
        when(associationService.resolve("real_001"))
                .thenReturn(new AlarmAssociationService.AlarmAssociation(null, null));
        when(alarmMapper.insert(any(Alarm.class))).thenReturn(1);

        service.ingest(payload("异常停留", "2026-08-24T10:23:47.000Z"));

        ArgumentCaptor<Alarm> captor = ArgumentCaptor.forClass(Alarm.class);
        verify(alarmMapper).insert(captor.capture());
        Alarm alarm = captor.getValue();
        assertNull(alarm.getVehicleId());
        assertNull(alarm.getTaskId());
        assertNull(alarm.getLongitude());
        assertNull(alarm.getLatitude());
    }

    @Test
    void ingestionSucceedsWhenLocationProviderIsUnavailable() {
        when(associationService.resolve("real_001"))
                .thenReturn(new AlarmAssociationService.AlarmAssociation(20L, null));
        when(gpsInfluxService.querySamples(anyCollection(), any(), any()))
                .thenThrow(new RuntimeException("realtime location provider unavailable"));
        when(alarmMapper.insert(any(Alarm.class))).thenReturn(1);

        MqttAlertIngestionService.IngestionResult result = service.ingest(payload(
                "异常开箱", "2026-08-24T10:23:47.000Z"));

        ArgumentCaptor<Alarm> captor = ArgumentCaptor.forClass(Alarm.class);
        verify(alarmMapper).insert(captor.capture());
        assertEquals(MqttAlertIngestionService.IngestionResult.STORED, result);
        assertEquals(20L, captor.getValue().getVehicleId());
        assertNull(captor.getValue().getTaskId());
        assertNull(captor.getValue().getLongitude());
    }

    @Test
    void reportsDuplicateWhenDatabaseUniqueIndexRejectsEventKey() {
        when(alarmMapper.insert(any(Alarm.class)))
                .thenThrow(new DuplicateKeyException("uk_alarm_event_key"));

        MqttAlertIngestionService.IngestionResult result = service.ingest(payload(
                "异常开箱", "2026-08-24T10:23:47.000Z"));

        assertEquals(MqttAlertIngestionService.IngestionResult.DUPLICATE, result);
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
