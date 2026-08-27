package com.smart_logistics.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart_logistics.backend.common.PageResult;
import com.smart_logistics.backend.dto.request.AlarmStatusUpdateRequest;
import com.smart_logistics.backend.dto.response.AlarmResponse;
import com.smart_logistics.backend.entity.Alarm;
import com.smart_logistics.backend.enums.AlarmLevel;
import com.smart_logistics.backend.enums.AlarmStatus;
import com.smart_logistics.backend.enums.AlarmType;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.mapper.AlarmMapper;
import com.smart_logistics.backend.security.BusinessDataScopeService;
import com.smart_logistics.backend.security.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class AlarmService {

    private static final ZoneId API_TIME_ZONE = ZoneId.of("Asia/Shanghai");

    private final AlarmMapper alarmMapper;
    private final BusinessDataScopeService dataScopeService;
    private final CurrentUserService currentUserService;

    public AlarmService(AlarmMapper alarmMapper,
                        BusinessDataScopeService dataScopeService,
                        CurrentUserService currentUserService) {
        this.alarmMapper = alarmMapper;
        this.dataScopeService = dataScopeService;
        this.currentUserService = currentUserService;
    }

    public PageResult<AlarmResponse> listAlarms(long page, long pageSize, String keyword,
                                                AlarmStatus status, AlarmLevel level,
                                                AlarmType alarmType) {
        return listAlarms(page, pageSize, keyword, status, level, alarmType,
                null, null, null);
    }

    public PageResult<AlarmResponse> listAlarms(long page, long pageSize, String keyword,
                                                AlarmStatus status, AlarmLevel level,
                                                AlarmType alarmType, Long taskId,
                                                Long vehicleId, Long ownerId) {
        LambdaQueryWrapper<Alarm> query = new LambdaQueryWrapper<>();
        dataScopeService.applyAlarmScope(query, ownerId);
        if (StringUtils.hasText(keyword)) {
            query.like(Alarm::getMessage, keyword.trim());
        }
        if (status != null) {
            query.eq(Alarm::getStatus, status.name());
        }
        if (level != null) {
            query.eq(Alarm::getLevel, level.name());
        }
        if (alarmType != null) {
            query.eq(Alarm::getAlarmType, alarmType.name());
        }
        if (taskId != null) {
            query.eq(Alarm::getTaskId, taskId);
        }
        if (vehicleId != null) {
            // 兼容历史数据：旧告警只有task_id，新告警还有vehicle_id，两者任一匹配即可
            List<Long> taskIds = dataScopeService.taskIdsForVehicle(vehicleId);
            query.and(wrapper -> {
                if (taskIds.isEmpty()) {
                    wrapper.eq(Alarm::getVehicleId, vehicleId);
                } else {
                    wrapper.in(Alarm::getTaskId, taskIds)
                            .or()
                            .eq(Alarm::getVehicleId, vehicleId);
                }
            });
        }
        if (ownerId != null) {
            applyTaskIds(query, dataScopeService.taskIdsForOwner(ownerId));
        }
        query.orderByDesc(Alarm::getCreatedAt).orderByDesc(Alarm::getId);

        Page<Alarm> entityPage = alarmMapper.selectPage(new Page<>(page, pageSize), query);
        List<AlarmResponse> records = entityPage.getRecords().stream()
                .map(this::toResponse)
                .toList();
        return new PageResult<>(records, entityPage.getTotal(), page, pageSize);
    }

    private void applyTaskIds(LambdaQueryWrapper<Alarm> query, List<Long> taskIds) {
        if (taskIds.isEmpty()) query.eq(Alarm::getTaskId, -1L);
        else query.in(Alarm::getTaskId, taskIds);
    }

    public AlarmResponse getAlarm(Long id) {
        return toResponse(getRequiredAlarm(id));
    }

    @Transactional
    public AlarmResponse updateStatus(Long id, AlarmStatusUpdateRequest request) {
        Alarm alarm = getRequiredAlarm(id);
        AlarmStatus currentStatus = parseStatus(alarm.getStatus());
        AlarmStatus targetStatus = request.getStatus();

        if (currentStatus == targetStatus) {
            return toResponse(alarm);
        }
        if (!isAllowedTransition(currentStatus, targetStatus)) {
            throw new BusinessException(
                    ErrorCode.STATE_CONFLICT,
                    "alarm status cannot transition from " + currentStatus + " to " + targetStatus
            );
        }

        LocalDateTime now = LocalDateTime.now(API_TIME_ZONE);
        alarm.setStatus(targetStatus.name());
        // 处理权限由Controller限定为DISPATCHER/ADMIN，此处记录实际处理人、处理时间和备注
        alarm.setHandledBy(currentUserService.getCurrentUser().getId());
        if (alarm.getHandledAt() == null) {
            alarm.setHandledAt(now);
        }
        if (request.getNote() != null) {
            alarm.setHandleNote(request.getNote().trim());
        }
        if (targetStatus == AlarmStatus.RESOLVED) {
            alarm.setResolvedAt(now);
        }

        if (alarmMapper.updateById(alarm) != 1) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "failed to update alarm status");
        }
        return toResponse(alarm);
    }

    private Alarm getRequiredAlarm(Long id) {
        Alarm alarm = alarmMapper.selectById(id);
        if (alarm == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "alarm not found");
        }
        dataScopeService.requireAlarmAccess(alarm);
        return alarm;
    }

    private boolean isAllowedTransition(AlarmStatus currentStatus, AlarmStatus targetStatus) {
        return currentStatus == AlarmStatus.UNHANDLED
                && (targetStatus == AlarmStatus.PROCESSING
                || targetStatus == AlarmStatus.RESOLVED)
                || currentStatus == AlarmStatus.PROCESSING
                && targetStatus == AlarmStatus.RESOLVED;
    }

    private AlarmResponse toResponse(Alarm alarm) {
        AlarmResponse response = new AlarmResponse();
        response.setId(alarm.getId());
        response.setTaskId(alarm.getTaskId());
        response.setVehicleId(alarm.getVehicleId());
        response.setDeviceCode(alarm.getDeviceCode());
        response.setAlarmType(parseAlarmType(alarm.getAlarmType()));
        response.setLevel(parseLevel(alarm.getLevel()));
        response.setMessage(alarm.getMessage());
        response.setLongitude(alarm.getLongitude());
        response.setLatitude(alarm.getLatitude());
        response.setStatus(parseStatus(alarm.getStatus()));
        response.setSource(alarm.getSource());
        response.setOccurredAt(toOffsetDateTime(alarm.getOccurredAt()));
        response.setHandledBy(alarm.getHandledBy());
        response.setHandleNote(alarm.getHandleNote());
        response.setHandledAt(toOffsetDateTime(alarm.getHandledAt()));
        response.setCreatedAt(toOffsetDateTime(alarm.getCreatedAt()));
        response.setResolvedAt(toOffsetDateTime(alarm.getResolvedAt()));
        return response;
    }

    private AlarmType parseAlarmType(String alarmType) {
        try {
            return AlarmType.valueOf(alarmType);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "invalid alarm type in database");
        }
    }

    private AlarmLevel parseLevel(String level) {
        try {
            return AlarmLevel.valueOf(level);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "invalid alarm level in database");
        }
    }

    private AlarmStatus parseStatus(String status) {
        try {
            return AlarmStatus.valueOf(status);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "invalid alarm status in database");
        }
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        return value == null ? null : value.atZone(API_TIME_ZONE).toOffsetDateTime();
    }
}
