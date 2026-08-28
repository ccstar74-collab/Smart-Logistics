package com.smart_logistics.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smart_logistics.backend.entity.Alarm;
import com.smart_logistics.backend.entity.DispatchCommand;
import com.smart_logistics.backend.enums.AlarmConditionStatus;
import com.smart_logistics.backend.enums.AlarmStatus;
import com.smart_logistics.backend.enums.DispatchCommandStatus;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.mapper.AlarmMapper;
import com.smart_logistics.backend.mapper.DispatchCommandMapper;
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

    public AlarmResolutionService(AlarmMapper alarmMapper,
                                  DispatchCommandMapper dispatchCommandMapper) {
        this.alarmMapper = alarmMapper;
        this.dispatchCommandMapper = dispatchCommandMapper;
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
        resolveLocked(alarm);
    }

    @Transactional
    public boolean tryResolveAlarm(Long alarmId) {
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
