package com.smart_logistics.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smart_logistics.backend.dto.realtime.AlarmWsEvent;
import com.smart_logistics.backend.entity.Alarm;
import com.smart_logistics.backend.entity.DispatchCommand;
import com.smart_logistics.backend.enums.AlarmConditionStatus;
import com.smart_logistics.backend.enums.AlarmStatus;
import com.smart_logistics.backend.enums.DispatchCommandStatus;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.mapper.AlarmMapper;
import com.smart_logistics.backend.mapper.DispatchCommandMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneId;

@Service
public class AlarmResolutionService {

    private static final ZoneId DATABASE_TIME_ZONE = ZoneId.of("Asia/Shanghai");

    private final AlarmMapper alarmMapper;
    private final DispatchCommandMapper dispatchCommandMapper;
    private final ApplicationEventPublisher eventPublisher;

    public AlarmResolutionService(AlarmMapper alarmMapper,
                                  DispatchCommandMapper dispatchCommandMapper,
                                  ApplicationEventPublisher eventPublisher) {
        this.alarmMapper = alarmMapper;
        this.dispatchCommandMapper = dispatchCommandMapper;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void markConditionRecovered(Long alarmId, Instant recoveredAt) {
        if (recoveredAt == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "recoveredAt must not be null");
        }
        Alarm alarm = lockAlarm(alarmId);
        if (!AlarmConditionStatus.RECOVERED.name().equals(alarm.getConditionStatus())) {
            alarm.setConditionStatus(AlarmConditionStatus.RECOVERED.name());
            alarm.setRecoveredAt(LocalDateTime.ofInstant(recoveredAt, DATABASE_TIME_ZONE));
            updateAlarm(alarm, "failed to mark alarm condition recovered");
        }
        // 条件已标记为RECOVERED，检查是否同事务直达RESOLVED
        if (!AlarmStatus.RESOLVED.name().equals(alarm.getStatus())
                && hasCompletedCommand(alarmId)) {
            // 同一次事务直达RESOLVED，只发ALARM_RESOLVED，不发中间态ALARM_UPDATED
            alarm.setStatus(AlarmStatus.RESOLVED.name());
            alarm.setResolvedAt(LocalDateTime.now(DATABASE_TIME_ZONE));
            updateAlarm(alarm, "alarm resolution update conflict");
            eventPublisher.publishEvent(AlarmWsEvent.resolved(alarm.getId()));
        } else if (!AlarmStatus.RESOLVED.name().equals(alarm.getStatus())
                && !AlarmStatus.PROCESSING.name().equals(alarm.getStatus())) {
            // 尚未满足消警条件，标记业务状态为PROCESSING等待指令完成
            alarm.setStatus(AlarmStatus.PROCESSING.name());
            updateAlarm(alarm, "failed to update alarm status");
            eventPublisher.publishEvent(AlarmWsEvent.updated(alarmId));
        }
    }

    @Transactional
    public boolean tryResolveAlarm(Long alarmId) {
        // RESOLVED事件统一由resolveLocked在实际发生消警时发布，避免重复
        return resolveLocked(lockAlarm(alarmId));
    }

    private boolean resolveLocked(Alarm alarm) {
        if (AlarmStatus.RESOLVED.name().equals(alarm.getStatus())) {
            return false;
        }
        if (!AlarmConditionStatus.RECOVERED.name().equals(alarm.getConditionStatus())
                || !hasCompletedCommand(alarm.getId())) {
            return false;
        }
        alarm.setStatus(AlarmStatus.RESOLVED.name());
        alarm.setResolvedAt(LocalDateTime.now(DATABASE_TIME_ZONE));
        updateAlarm(alarm, "alarm resolution update conflict");
        // 自动消警闭环完成（RECOVERED + 至少一条COMPLETED指令）
        eventPublisher.publishEvent(AlarmWsEvent.resolved(alarm.getId()));
        return true;
    }

    private boolean hasCompletedCommand(Long alarmId) {
        return dispatchCommandMapper.selectCount(new LambdaQueryWrapper<DispatchCommand>()
                .eq(DispatchCommand::getAlarmId, alarmId)
                .eq(DispatchCommand::getStatus, DispatchCommandStatus.COMPLETED.name())) > 0;
    }

    private Alarm lockAlarm(Long alarmId) {
        Alarm alarm = alarmMapper.selectOne(new LambdaQueryWrapper<Alarm>()
                .eq(Alarm::getId, alarmId)
                .last("FOR UPDATE"));
        if (alarm == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "alarm not found");
        }
        return alarm;
    }

    private void updateAlarm(Alarm alarm, String message) {
        if (alarmMapper.updateById(alarm) != 1) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, message);
        }
    }
}
