package com.smart_logistics.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart_logistics.backend.common.PageResult;
import com.smart_logistics.backend.dto.realtime.NotificationWsEvent;
import com.smart_logistics.backend.dto.response.NotificationResponse;
import com.smart_logistics.backend.entity.Alarm;
import com.smart_logistics.backend.entity.Cargo;
import com.smart_logistics.backend.entity.DispatchCommand;
import com.smart_logistics.backend.entity.Driver;
import com.smart_logistics.backend.entity.Notification;
import com.smart_logistics.backend.entity.Owner;
import com.smart_logistics.backend.entity.TransportTask;
import com.smart_logistics.backend.entity.User;
import com.smart_logistics.backend.enums.AlarmLevel;
import com.smart_logistics.backend.enums.NotificationLevel;
import com.smart_logistics.backend.enums.NotificationType;
import com.smart_logistics.backend.enums.UserRole;
import com.smart_logistics.backend.enums.UserStatus;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.mapper.AlarmMapper;
import com.smart_logistics.backend.mapper.CargoMapper;
import com.smart_logistics.backend.mapper.DispatchCommandMapper;
import com.smart_logistics.backend.mapper.DriverMapper;
import com.smart_logistics.backend.mapper.NotificationMapper;
import com.smart_logistics.backend.mapper.OwnerMapper;
import com.smart_logistics.backend.mapper.TransportTaskMapper;
import com.smart_logistics.backend.mapper.UserMapper;
import com.smart_logistics.backend.security.CurrentUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 消息中心通知服务（权威数据源，见docs/notification.md）。
 * REST接口只返回JWT当前用户自己的通知，前端不传userId；
 * 生成逻辑不重新实现业务，只在业务事件确认后产生“结果提醒”，
 * 按服务端算出的具体receiverUserId落库，绝不依赖前端按角色过滤。
 */
@Slf4j
@Service
public class NotificationService {

    private static final ZoneId API_TIME_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String BUSINESS_TYPE_ALARM = "ALARM";
    private static final String BUSINESS_TYPE_DISPATCH_COMMAND = "DISPATCH_COMMAND";

    private final NotificationMapper notificationMapper;
    private final AlarmMapper alarmMapper;
    private final DispatchCommandMapper dispatchCommandMapper;
    private final TransportTaskMapper transportTaskMapper;
    private final CargoMapper cargoMapper;
    private final OwnerMapper ownerMapper;
    private final DriverMapper driverMapper;
    private final UserMapper userMapper;
    private final CurrentUserService currentUserService;
    private final ApplicationEventPublisher eventPublisher;

    public NotificationService(NotificationMapper notificationMapper,
                               AlarmMapper alarmMapper,
                               DispatchCommandMapper dispatchCommandMapper,
                               TransportTaskMapper transportTaskMapper,
                               CargoMapper cargoMapper,
                               OwnerMapper ownerMapper,
                               DriverMapper driverMapper,
                               UserMapper userMapper,
                               CurrentUserService currentUserService,
                               ApplicationEventPublisher eventPublisher) {
        this.notificationMapper = notificationMapper;
        this.alarmMapper = alarmMapper;
        this.dispatchCommandMapper = dispatchCommandMapper;
        this.transportTaskMapper = transportTaskMapper;
        this.cargoMapper = cargoMapper;
        this.ownerMapper = ownerMapper;
        this.driverMapper = driverMapper;
        this.userMapper = userMapper;
        this.currentUserService = currentUserService;
        this.eventPublisher = eventPublisher;
    }

    // ==================== REST：只返回当前用户自己的通知 ====================

    @Transactional(readOnly = true)
    public PageResult<NotificationResponse> listMyNotifications(
            long page, long pageSize, Boolean read, NotificationType type) {
        Long userId = currentUserService.getCurrentUser().getId();
        LambdaQueryWrapper<Notification> query = new LambdaQueryWrapper<>();
        query.eq(Notification::getReceiverUserId, userId);
        if (read != null) {
            query.eq(Notification::getIsRead, read);
        }
        if (type != null) {
            query.eq(Notification::getType, type.name());
        }
        query.orderByDesc(Notification::getCreatedAt).orderByDesc(Notification::getId);
        Page<Notification> entityPage = notificationMapper.selectPage(
                new Page<>(page, pageSize), query);
        List<NotificationResponse> records = entityPage.getRecords().stream()
                .map(this::toResponse)
                .toList();
        return new PageResult<>(records, entityPage.getTotal(), page, pageSize);
    }

    @Transactional(readOnly = true)
    public long countMyUnread() {
        Long userId = currentUserService.getCurrentUser().getId();
        return notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getReceiverUserId, userId)
                .eq(Notification::getIsRead, false));
    }

    /**
     * 单条标记已读：必须属于当前用户；重复调用幂等（已读直接返回成功）。
     * 非本人通知按404返回，不泄露其他用户通知的存在。
     */
    @Transactional
    public NotificationResponse markRead(Long id) {
        Long userId = currentUserService.getCurrentUser().getId();
        Notification notification = notificationMapper.selectById(id);
        if (notification == null
                || !Objects.equals(notification.getReceiverUserId(), userId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                    "notification not found");
        }
        if (Boolean.TRUE.equals(notification.getIsRead())) {
            return toResponse(notification);
        }
        notification.setIsRead(true);
        notification.setReadAt(LocalDateTime.now(API_TIME_ZONE));
        if (notificationMapper.updateById(notification) != 1) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "notification read update conflict");
        }
        return toResponse(notification);
    }

    /**
     * 全部标记已读：只更新JWT当前用户的未读通知，绝不全表更新。
     * @return 本次更新的条数
     */
    @Transactional
    public long markAllRead() {
        Long userId = currentUserService.getCurrentUser().getId();
        return notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getReceiverUserId, userId)
                .eq(Notification::getIsRead, false)
                .set(Notification::getIsRead, true)
                .set(Notification::getReadAt, LocalDateTime.now(API_TIME_ZONE)));
    }

    // ==================== 推送路径：按receiverUserId精确查询 ====================

    /**
     * 供WebSocket推送使用：不做当前用户范围检查，
     * 推送端按会话绑定的userId精确匹配；通知不存在时返回null。
     */
    public Notification findForPush(Long id) {
        return notificationMapper.selectById(id);
    }

    public NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                parseType(notification.getType()),
                notification.getTitle(),
                notification.getContent(),
                parseLevel(notification.getLevel()),
                Boolean.TRUE.equals(notification.getIsRead()),
                toOffsetDateTime(notification.getCreatedAt()),
                notification.getBusinessType(),
                notification.getBusinessId(),
                notification.getTaskId(),
                notification.getTargetPath());
    }

    // ==================== 生成：独立事务，失败不影响已提交的业务 ====================

    /**
     * 告警事件生成通知。接收人按通知路由规则由服务端算出：
     * 全部调度员；HIGH级另加管理员；有归属任务时加该任务货主。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void generateAlarmNotifications(Long alarmId, NotificationType type) {
        Alarm alarm = alarmMapper.selectById(alarmId);
        if (alarm == null) {
            log.warn("告警通知生成跳过，告警不存在 alarmId={}, type={}", alarmId, type);
            return;
        }
        Set<Long> receiverUserIds = alarmReceiverUserIds(alarm);
        if (receiverUserIds.isEmpty()) {
            return;
        }
        boolean created = type == NotificationType.ALARM_CREATED;
        Notification prototype = new Notification();
        prototype.setType(type.name());
        prototype.setTitle(created ? "新告警通知" : "告警已解除");
        prototype.setContent(created ? alarm.getMessage()
                : "告警已消除并完成处理：" + alarm.getMessage());
        prototype.setLevel(alarmLevelFor(alarm, created).name());
        prototype.setBusinessType(BUSINESS_TYPE_ALARM);
        prototype.setBusinessId(String.valueOf(alarm.getId()));
        prototype.setTaskId(alarm.getTaskId());
        prototype.setTargetPath("/alarms?alarmId=" + alarm.getId());
        insertForReceivers(prototype, receiverUserIds);
    }

    /**
     * 调度指令创建通知：只发给指令目标司机对应的用户账号，
     * 绝不广播给所有DRIVER。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void generateDispatchCommandNotification(Long commandId) {
        DispatchCommand command = dispatchCommandMapper.selectById(commandId);
        if (command == null) {
            log.warn("调度指令通知生成跳过，指令不存在 commandId={}", commandId);
            return;
        }
        Driver driver = driverMapper.selectById(command.getTargetDriverId());
        if (driver == null || driver.getUserId() == null) {
            log.warn("调度指令通知生成跳过，目标司机无用户账号 commandId={}, driverId={}",
                    commandId, command.getTargetDriverId());
            return;
        }
        Notification prototype = new Notification();
        prototype.setType(NotificationType.DISPATCH_COMMAND_CREATED.name());
        prototype.setTitle("收到新的调度指令");
        prototype.setContent(command.getContent());
        prototype.setLevel(NotificationLevel.WARNING.name());
        prototype.setBusinessType(BUSINESS_TYPE_DISPATCH_COMMAND);
        prototype.setBusinessId(String.valueOf(command.getId()));
        prototype.setTaskId(command.getTaskId());
        prototype.setTargetPath("/dispatch?commandId=" + command.getId());
        insertForReceivers(prototype, Set.of(driver.getUserId()));
    }

    private Set<Long> alarmReceiverUserIds(Alarm alarm) {
        // 调度员暂按全局调度范围处理，全部接收告警通知
        Set<Long> receiverUserIds = new LinkedHashSet<>(
                activeUserIdsByRole(UserRole.DISPATCHER));
        // 高等级告警额外通知管理员，避免管理员接收所有普通业务通知
        if (AlarmLevel.HIGH.name().equals(alarm.getLevel())) {
            receiverUserIds.addAll(activeUserIdsByRole(UserRole.ADMIN));
        }
        // 有归属任务时通知该任务货主；设备级告警（无任务）不推给货主
        if (alarm.getTaskId() != null) {
            TransportTask task = transportTaskMapper.selectById(alarm.getTaskId());
            Cargo cargo = task == null || task.getCargoId() == null
                    ? null : cargoMapper.selectById(task.getCargoId());
            Owner owner = cargo == null || cargo.getOwnerId() == null
                    ? null : ownerMapper.selectById(cargo.getOwnerId());
            if (owner != null && owner.getUserId() != null) {
                receiverUserIds.add(owner.getUserId());
            }
        }
        return receiverUserIds;
    }

    private List<Long> activeUserIdsByRole(UserRole role) {
        return userMapper.selectList(new LambdaQueryWrapper<User>()
                        .eq(User::getRole, role.name())
                        .eq(User::getStatus, UserStatus.ACTIVE.name()))
                .stream()
                .map(User::getId)
                .toList();
    }

    /**
     * 按接收人逐个落库并发布推送事件。唯一键（type+业务对象+接收人）保证幂等：
     * 同一业务事件对同一用户重复触发时跳过，不生成重复通知也不重复推送。
     */
    private void insertForReceivers(Notification prototype,
                                    Collection<Long> receiverUserIds) {
        LocalDateTime now = LocalDateTime.now(API_TIME_ZONE);
        for (Long receiverUserId : receiverUserIds) {
            Notification notification = copyOf(prototype);
            notification.setReceiverUserId(receiverUserId);
            notification.setIsRead(false);
            notification.setCreatedAt(now);
            try {
                if (notificationMapper.insert(notification) != 1) {
                    continue;
                }
            } catch (DuplicateKeyException duplicate) {
                // 幂等去重：该业务事件对此接收人已生成过通知
                continue;
            }
            // 落库成功后发布，事务提交后由/ws/notifications推送
            eventPublisher.publishEvent(new NotificationWsEvent(notification.getId()));
        }
    }

    private Notification copyOf(Notification prototype) {
        Notification notification = new Notification();
        notification.setType(prototype.getType());
        notification.setTitle(prototype.getTitle());
        notification.setContent(prototype.getContent());
        notification.setLevel(prototype.getLevel());
        notification.setBusinessType(prototype.getBusinessType());
        notification.setBusinessId(prototype.getBusinessId());
        notification.setTaskId(prototype.getTaskId());
        notification.setTargetPath(prototype.getTargetPath());
        return notification;
    }

    private NotificationLevel alarmLevelFor(Alarm alarm, boolean created) {
        if (!created) {
            return NotificationLevel.SUCCESS;
        }
        try {
            return switch (AlarmLevel.valueOf(alarm.getLevel())) {
                case HIGH -> NotificationLevel.ERROR;
                case MEDIUM -> NotificationLevel.WARNING;
                case LOW -> NotificationLevel.INFO;
            };
        } catch (IllegalArgumentException | NullPointerException exception) {
            return NotificationLevel.WARNING;
        }
    }

    private NotificationType parseType(String type) {
        try {
            return NotificationType.valueOf(type);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "invalid notification type in database");
        }
    }

    private NotificationLevel parseLevel(String level) {
        try {
            return NotificationLevel.valueOf(level);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "invalid notification level in database");
        }
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        return value == null ? null : value.atZone(API_TIME_ZONE).toOffsetDateTime();
    }
}
