package com.smart_logistics.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart_logistics.backend.common.PageResult;
import com.smart_logistics.backend.dto.realtime.AlarmWsEvent;
import com.smart_logistics.backend.dto.request.AlarmStatusUpdateRequest;
import com.smart_logistics.backend.dto.response.AlarmResponse;
import com.smart_logistics.backend.entity.Alarm;
import com.smart_logistics.backend.entity.TransportTask;
import com.smart_logistics.backend.entity.Vehicle;
import com.smart_logistics.backend.enums.AlarmConditionStatus;
import com.smart_logistics.backend.enums.AlarmLevel;
import com.smart_logistics.backend.enums.AlarmStatus;
import com.smart_logistics.backend.enums.AlarmType;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.mapper.AlarmMapper;
import com.smart_logistics.backend.mapper.TransportTaskMapper;
import com.smart_logistics.backend.mapper.VehicleMapper;
import com.smart_logistics.backend.security.BusinessDataScopeService;
import com.smart_logistics.backend.security.CurrentUserService;
import com.smart_logistics.backend.dto.response.UserIdentityResponse;
import com.smart_logistics.backend.enums.UserRole;
import org.springframework.context.ApplicationEventPublisher;
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
    private final TransportTaskMapper transportTaskMapper;
    private final VehicleMapper vehicleMapper;
    private final CurrentUserService currentUserService;
    private final ApplicationEventPublisher eventPublisher;

    public AlarmService(AlarmMapper alarmMapper,
                        BusinessDataScopeService dataScopeService,
                        TransportTaskMapper transportTaskMapper,
                        VehicleMapper vehicleMapper,
                        CurrentUserService currentUserService,
                        ApplicationEventPublisher eventPublisher) {
        this.alarmMapper = alarmMapper;
        this.dataScopeService = dataScopeService;
        this.transportTaskMapper = transportTaskMapper;
        this.vehicleMapper = vehicleMapper;
        this.currentUserService = currentUserService;
        this.eventPublisher = eventPublisher;
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
            query.eq(Alarm::getVehicleId, vehicleId);
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

    /**
     * 供WebSocket推送使用的只读查询：不做当前用户数据范围检查，
     * 权限过滤由推送端按会话属性完成；告警不存在时返回null。
     */
    public AlarmResponse findResponse(Long id) {
        Alarm alarm = alarmMapper.selectById(id);
        return alarm == null ? null : toResponse(alarm);
    }

    @Transactional
    public AlarmResponse updateStatus(Long id, AlarmStatusUpdateRequest request) {
        UserIdentityResponse current = currentUserService.getCurrentUser();
        if (current.getRole() != UserRole.DISPATCHER
                && current.getRole() != UserRole.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "current role cannot manually resolve alarms");
        }
        if (request.getStatus() != AlarmStatus.RESOLVED) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "manual alarm status endpoint only supports RESOLVED");
        }
        if (!StringUtils.hasText(request.getRemark())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "remark must not be blank");
        }
        Alarm alarm = lockRequiredAlarm(id);
        AlarmStatus currentStatus = parseStatus(alarm.getStatus());

        if (currentStatus == AlarmStatus.RESOLVED) {
            return toResponse(alarm);
        }

        LocalDateTime now = LocalDateTime.now(API_TIME_ZONE);
        alarm.setStatus(AlarmStatus.RESOLVED.name());
        alarm.setHandledBy(current.getId());
        if (alarm.getHandledAt() == null) {
            alarm.setHandledAt(now);
        }
        alarm.setResolvedAt(now);
        alarm.setResolutionRemark(request.getRemark().trim());

        if (alarmMapper.updateById(alarm) != 1) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "alarm status update conflict");
        }
        // 人工兜底关闭也通知前端移除未处理告警，事务提交后推送
        eventPublisher.publishEvent(AlarmWsEvent.resolved(alarm.getId()));
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

    private Alarm lockRequiredAlarm(Long id) {
        Alarm alarm = alarmMapper.selectOne(new LambdaQueryWrapper<Alarm>()
                .eq(Alarm::getId, id).last("FOR UPDATE"));
        if (alarm == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "alarm not found");
        }
        dataScopeService.requireAlarmAccess(alarm);
        return alarm;
    }

    private AlarmResponse toResponse(Alarm alarm) {
        TransportTask task = alarm.getTaskId() == null ? null
                : transportTaskMapper.selectById(alarm.getTaskId());
        Long vehicleId = alarm.getVehicleId() != null ? alarm.getVehicleId()
                : task == null ? null : task.getVehicleId();
        Vehicle vehicle = vehicleId == null ? null : vehicleMapper.selectById(vehicleId);
        return new AlarmResponse(
                alarm.getId(),
                vehicleId,
                vehicle == null ? null : vehicle.getPlateNumber(),
                alarm.getTaskId(),
                task == null ? null : task.getTaskNo(),
                alarm.getDeviceCode(),
                parseAlarmType(alarm.getAlarmType()),
                parseLevel(alarm.getLevel()),
                alarm.getMessage(),
                parseStatus(alarm.getStatus()),
                parseConditionStatus(alarm.getConditionStatus()),
                alarm.getSource(),
                toOffsetDateTime(alarm.getOccurredAt()),
                toOffsetDateTime(alarm.getRecoveredAt()),
                alarm.getHandledBy(),
                toOffsetDateTime(alarm.getHandledAt()),
                toOffsetDateTime(alarm.getCreatedAt()),
                toOffsetDateTime(alarm.getResolvedAt()),
                alarm.getResolutionRemark(),
                alarm.getLongitude(),
                alarm.getLatitude(),
                alarm.getCoordSystem()
        );
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

    private AlarmConditionStatus parseConditionStatus(String status) {
        if (status == null) {
            return AlarmConditionStatus.ACTIVE;
        }
        try {
            return AlarmConditionStatus.valueOf(status);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "invalid alarm condition status in database");
        }
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        return value == null ? null : value.atZone(API_TIME_ZONE).toOffsetDateTime();
    }
}
