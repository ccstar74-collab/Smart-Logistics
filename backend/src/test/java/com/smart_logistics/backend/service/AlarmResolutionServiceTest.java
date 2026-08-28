package com.smart_logistics.backend.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.smart_logistics.backend.entity.Alarm;
import com.smart_logistics.backend.entity.DispatchCommand;
import com.smart_logistics.backend.enums.AlarmConditionStatus;
import com.smart_logistics.backend.enums.AlarmStatus;
import com.smart_logistics.backend.mapper.AlarmMapper;
import com.smart_logistics.backend.mapper.DispatchCommandMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlarmResolutionServiceTest {

    @Mock private AlarmMapper alarmMapper;
    @Mock private DispatchCommandMapper commandMapper;

    private AlarmResolutionService service;

    @BeforeEach
    void setUp() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(configuration, "resolution-alarm"), Alarm.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(configuration, "resolution-command"),
                DispatchCommand.class);
        service = new AlarmResolutionService(alarmMapper, commandMapper);
    }

    @Test
    void completedCommandDoesNotResolveActiveCondition() {
        Alarm alarm = alarm(AlarmConditionStatus.ACTIVE);
        when(alarmMapper.selectOne(any(Wrapper.class))).thenReturn(alarm);

        assertFalse(service.tryResolveAlarm(35L));

        assertEquals(AlarmStatus.PROCESSING.name(), alarm.getStatus());
        verify(commandMapper, never()).selectCount(any(Wrapper.class));
        verify(alarmMapper, never()).updateById(any(Alarm.class));
    }

    @Test
    void recoveredConditionWaitsForCompletedCommand() {
        Alarm alarm = alarm(AlarmConditionStatus.ACTIVE);
        when(alarmMapper.selectOne(any(Wrapper.class))).thenReturn(alarm);
        when(alarmMapper.updateById(alarm)).thenReturn(1);
        when(commandMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        service.markConditionRecovered(35L,
                Instant.parse("2026-08-27T08:00:00Z"));

        assertEquals(AlarmConditionStatus.RECOVERED.name(), alarm.getConditionStatus());
        assertNotNull(alarm.getRecoveredAt());
        assertEquals(AlarmStatus.PROCESSING.name(), alarm.getStatus());
        verify(alarmMapper).updateById(alarm);
    }

    @Test
    void recoveredAndCompletedResolveAlarm() {
        Alarm alarm = alarm(AlarmConditionStatus.RECOVERED);
        when(alarmMapper.selectOne(any(Wrapper.class))).thenReturn(alarm);
        when(commandMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        when(alarmMapper.updateById(alarm)).thenReturn(1);

        assertTrue(service.tryResolveAlarm(35L));

        assertEquals(AlarmStatus.RESOLVED.name(), alarm.getStatus());
        assertNotNull(alarm.getResolvedAt());
    }

    @Test
    void commandFirstThenRecoveryStillResolves() {
        Alarm alarm = alarm(AlarmConditionStatus.ACTIVE);
        when(alarmMapper.selectOne(any(Wrapper.class))).thenReturn(alarm);
        when(commandMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        when(alarmMapper.updateById(alarm)).thenReturn(1);

        service.markConditionRecovered(35L,
                Instant.parse("2026-08-27T08:00:00Z"));

        assertEquals(AlarmStatus.RESOLVED.name(), alarm.getStatus());
        verify(alarmMapper, times(2)).updateById(alarm);
    }

    @Test
    void rejectedOnlyCommandCannotResolveRecoveredAlarm() {
        Alarm alarm = alarm(AlarmConditionStatus.RECOVERED);
        when(alarmMapper.selectOne(any(Wrapper.class))).thenReturn(alarm);
        when(commandMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        assertFalse(service.tryResolveAlarm(35L));

        assertEquals(AlarmStatus.PROCESSING.name(), alarm.getStatus());
        verify(alarmMapper, never()).updateById(any(Alarm.class));
    }

    private Alarm alarm(AlarmConditionStatus conditionStatus) {
        Alarm alarm = new Alarm();
        alarm.setId(35L);
        alarm.setStatus(AlarmStatus.PROCESSING.name());
        alarm.setConditionStatus(conditionStatus.name());
        return alarm;
    }
}
