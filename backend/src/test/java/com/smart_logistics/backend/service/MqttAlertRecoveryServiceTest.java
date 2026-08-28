package com.smart_logistics.backend.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.smart_logistics.backend.dto.mqtt.MqttAlertRecoveryPayload;
import com.smart_logistics.backend.entity.Alarm;
import com.smart_logistics.backend.entity.DispatchCommand;
import com.smart_logistics.backend.enums.AlarmConditionStatus;
import com.smart_logistics.backend.enums.AlarmStatus;
import com.smart_logistics.backend.enums.AlarmType;
import com.smart_logistics.backend.enums.DispatchCommandStatus;
import com.smart_logistics.backend.mapper.AlarmMapper;
import com.smart_logistics.backend.mapper.DispatchCommandMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MqttAlertRecoveryServiceTest {

    private static final ZoneId DATABASE_TIME_ZONE = ZoneId.of("Asia/Shanghai");

    private AlarmMapper alarmMapper;
    private DispatchCommandMapper commandMapper;
    private ApplicationEventPublisher eventPublisher;
    private MqttAlertRecoveryService service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "recovery-alarm"),
                Alarm.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "recovery-command"),
                DispatchCommand.class);
        alarmMapper = mock(AlarmMapper.class);
        commandMapper = mock(DispatchCommandMapper.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new MqttAlertRecoveryService(alarmMapper,
                new AlarmResolutionService(alarmMapper, commandMapper, eventPublisher));
    }

    @Test
    void recoversAbnormalOpenAndWritesPhysicalRecoveryFields() {
        Alarm alarm = activeAlarm(77L);
        when(alarmMapper.selectOne(any())).thenReturn(alarm);
        when(alarmMapper.updateById(alarm)).thenReturn(1);

        MqttAlertRecoveryService.RecoveryResult result = service.recover(
                payload("异常开箱", "RECOVERED",
                        "2026-08-27T09:30:00Z", "2026-08-27T09:30:03Z"));

        assertEquals(MqttAlertRecoveryService.RecoveryResult.RECOVERED, result);
        assertEquals(AlarmConditionStatus.RECOVERED.name(), alarm.getConditionStatus());
        assertNotNull(alarm.getRecoveredAt());
        assertEquals(AlarmStatus.PROCESSING.name(), alarm.getStatus());
        verify(alarmMapper).updateById(alarm);
    }

    @Test
    void recoversAbnormalStopByExactCorrelationAndResolvesCompletedAlarm() {
        Alarm alarm = activeAlarm(78L);
        when(alarmMapper.selectOne(any())).thenReturn(alarm);
        when(alarmMapper.updateById(alarm)).thenReturn(1);
        when(commandMapper.selectCount(any())).thenReturn(1L);

        MqttAlertRecoveryService.RecoveryResult result = service.recover(
                payload("异常停留", "RECOVERED",
                        "2026-08-27T09:30:00Z", "2026-08-27T09:30:03Z"));

        assertEquals(MqttAlertRecoveryService.RecoveryResult.RECOVERED, result);
        assertEquals(AlarmConditionStatus.RECOVERED.name(), alarm.getConditionStatus());
        assertNotNull(alarm.getRecoveredAt());
        assertEquals(AlarmStatus.RESOLVED.name(), alarm.getStatus());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<Alarm>> queryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(alarmMapper, times(2)).selectOne(queryCaptor.capture());
        LambdaQueryWrapper<Alarm> correlationQuery = queryCaptor.getAllValues().get(0);
        assertTrue(correlationQuery.getSqlSegment().contains("device_code"));
        assertTrue(correlationQuery.getSqlSegment().contains("alarm_type"));
        assertTrue(correlationQuery.getSqlSegment().contains("occurred_at"));
        assertTrue(correlationQuery.getParamNameValuePairs().containsValue("real_001"));
        assertTrue(correlationQuery.getParamNameValuePairs().containsValue("ABNORMAL_STOP"));
        assertTrue(correlationQuery.getParamNameValuePairs().containsValue(
                LocalDateTime.ofInstant(Instant.parse("2026-08-27T09:30:00Z"),
                        DATABASE_TIME_ZONE)));
    }

    @Test
    void recoversRouteDeviationByExactCorrelationAndResolvesCompletedAlarm() {
        Instant triggeredAt = Instant.parse("2026-08-27T09:30:00Z");
        Alarm alarm = activeAlarm(79L);
        alarm.setDeviceCode("sim_002");
        alarm.setAlarmType(AlarmType.ROUTE_DEVIATION.name());
        alarm.setOccurredAt(LocalDateTime.ofInstant(triggeredAt, DATABASE_TIME_ZONE));
        when(alarmMapper.selectOne(any())).thenReturn(alarm);
        when(alarmMapper.updateById(alarm)).thenReturn(1);
        when(commandMapper.selectCount(any())).thenReturn(1L);

        MqttAlertRecoveryService.RecoveryResult result = service.recover(
                new MqttAlertRecoveryPayload(
                        "1.0", "sim_002", "偏航", "RECOVERED",
                        triggeredAt.toString(), "2026-08-27T09:30:03Z", "device"));

        assertEquals(MqttAlertRecoveryService.RecoveryResult.RECOVERED, result);
        assertEquals(AlarmConditionStatus.RECOVERED.name(), alarm.getConditionStatus());
        assertNotNull(alarm.getRecoveredAt());
        assertEquals(AlarmStatus.RESOLVED.name(), alarm.getStatus());
        verify(alarmMapper, times(2)).updateById(alarm);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<Alarm>> alarmQueryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(alarmMapper, times(2)).selectOne(alarmQueryCaptor.capture());
        LambdaQueryWrapper<Alarm> correlationQuery = alarmQueryCaptor.getAllValues().get(0);
        assertTrue(correlationQuery.getSqlSegment().contains("device_code"));
        assertTrue(correlationQuery.getSqlSegment().contains("alarm_type"));
        assertTrue(correlationQuery.getSqlSegment().contains("occurred_at"));
        assertTrue(correlationQuery.getParamNameValuePairs().containsValue("sim_002"));
        assertTrue(correlationQuery.getParamNameValuePairs()
                .containsValue(AlarmType.ROUTE_DEVIATION.name()));
        assertTrue(correlationQuery.getParamNameValuePairs().containsValue(
                LocalDateTime.ofInstant(triggeredAt, DATABASE_TIME_ZONE)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<DispatchCommand>> commandQueryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(commandMapper).selectCount(commandQueryCaptor.capture());
        LambdaQueryWrapper<DispatchCommand> commandQuery = commandQueryCaptor.getValue();
        assertTrue(commandQuery.getSqlSegment().contains("alarm_id"));
        assertTrue(commandQuery.getSqlSegment().contains("status"));
        assertTrue(commandQuery.getParamNameValuePairs().containsValue(79L));
        assertTrue(commandQuery.getParamNameValuePairs()
                .containsValue(DispatchCommandStatus.COMPLETED.name()));
    }

    @Test
    void rejectsUnknownAlertTypeBeforeReadingOrUpdatingAlarm() {
        assertThrows(IllegalArgumentException.class, () -> service.recover(
                payload("未知异常", "RECOVERED",
                        "2026-08-27T09:30:00Z", "2026-08-27T09:30:03Z")));

        verifyNoInteractions(alarmMapper, commandMapper);
    }

    @Test
    void rejectsWrongConditionStatusBeforeReadingOrUpdatingAlarm() {
        assertThrows(IllegalArgumentException.class, () -> service.recover(
                payload("异常停留", "ACTIVE",
                        "2026-08-27T09:30:00Z", "2026-08-27T09:30:03Z")));

        verifyNoInteractions(alarmMapper, commandMapper);
    }

    @Test
    void rejectsRecoveryBeforeTriggerTime() {
        assertThrows(IllegalArgumentException.class, () -> service.recover(
                payload("异常停留", "RECOVERED",
                        "2026-08-27T09:30:03Z", "2026-08-27T09:30:00Z")));

        verifyNoInteractions(alarmMapper, commandMapper);
    }

    @Test
    void reportsMissingOriginalAlarmWithoutCreatingRecoveryAlarm() {

        assertEquals(MqttAlertRecoveryService.RecoveryResult.ALARM_NOT_FOUND,
                service.recover(payload("异常停留", "RECOVERED",
                        "2026-08-27T09:30:00Z", "2026-08-27T09:30:03Z")));
        verify(alarmMapper, never()).insert(any(Alarm.class));
        verifyNoInteractions(commandMapper);
    }

    private Alarm activeAlarm(Long id) {
        Alarm alarm = new Alarm();
        alarm.setId(id);
        alarm.setStatus(AlarmStatus.PROCESSING.name());
        alarm.setConditionStatus(AlarmConditionStatus.ACTIVE.name());
        return alarm;
    }

    private MqttAlertRecoveryPayload payload(String alertType, String conditionStatus,
                                              String triggeredAt, String recoveredAt) {
        return new MqttAlertRecoveryPayload(
                "1.0", "real_001", alertType, conditionStatus,
                triggeredAt, recoveredAt, "device");
    }
}
