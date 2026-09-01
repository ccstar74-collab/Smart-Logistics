package com.smart_logistics.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart_logistics.backend.dto.realtime.NotificationWsEvent;
import com.smart_logistics.backend.dto.response.NotificationResponse;
import com.smart_logistics.backend.dto.response.UserIdentityResponse;
import com.smart_logistics.backend.entity.Alarm;
import com.smart_logistics.backend.entity.Cargo;
import com.smart_logistics.backend.entity.DispatchCommand;
import com.smart_logistics.backend.entity.Driver;
import com.smart_logistics.backend.entity.Notification;
import com.smart_logistics.backend.entity.Owner;
import com.smart_logistics.backend.entity.TransportTask;
import com.smart_logistics.backend.entity.User;
import com.smart_logistics.backend.enums.NotificationType;
import com.smart_logistics.backend.enums.UserRole;
import com.smart_logistics.backend.enums.UserStatus;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.mapper.AlarmMapper;
import com.smart_logistics.backend.mapper.CargoMapper;
import com.smart_logistics.backend.mapper.DispatchCommandMapper;
import com.smart_logistics.backend.mapper.DriverMapper;
import com.smart_logistics.backend.mapper.NotificationMapper;
import com.smart_logistics.backend.mapper.OwnerMapper;
import com.smart_logistics.backend.mapper.TransportTaskMapper;
import com.smart_logistics.backend.mapper.UserMapper;
import com.smart_logistics.backend.security.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 通知服务核心规则：接收人由服务端精确计算（不广播角色）、
 * 同一业务事件对同一接收人幂等、REST只操作当前用户自己的通知。
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationMapper notificationMapper;
    @Mock private AlarmMapper alarmMapper;
    @Mock private DispatchCommandMapper dispatchCommandMapper;
    @Mock private TransportTaskMapper transportTaskMapper;
    @Mock private CargoMapper cargoMapper;
    @Mock private OwnerMapper ownerMapper;
    @Mock private DriverMapper driverMapper;
    @Mock private UserMapper userMapper;
    @Mock private CurrentUserService currentUserService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notificationMapper, alarmMapper,
                dispatchCommandMapper, transportTaskMapper, cargoMapper,
                ownerMapper, driverMapper, userMapper, currentUserService,
                eventPublisher);
    }

    @Test
    void highLevelAlarmNotifiesDispatchersAdminsAndTaskOwner() {
        Alarm alarm = alarm(101L, 30L, "HIGH");
        when(alarmMapper.selectById(101L)).thenReturn(alarm);
        when(userMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(user(10L), user(11L)))   // DISPATCHER
                .thenReturn(List.of(user(20L)));             // ADMIN
        when(transportTaskMapper.selectById(30L)).thenReturn(task(30L, 7L));
        when(cargoMapper.selectById(7L)).thenReturn(cargo(7L, 3L));
        when(ownerMapper.selectById(3L)).thenReturn(owner(3L, 30L));
        when(notificationMapper.insert(any(Notification.class))).thenReturn(1);

        service.generateAlarmNotifications(101L, NotificationType.ALARM_CREATED);

        List<Long> receivers = captureReceiverUserIds();
        assertEquals(List.of(10L, 11L, 20L, 30L), receivers);
        verify(eventPublisher, times(4)).publishEvent(any(NotificationWsEvent.class));
    }

    @Test
    void deviceLevelAlarmSkipsOwnerButKeepsDispatchers() {
        Alarm alarm = alarm(102L, null, "MEDIUM");
        when(alarmMapper.selectById(102L)).thenReturn(alarm);
        when(userMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(user(10L)));
        when(notificationMapper.insert(any(Notification.class))).thenReturn(1);

        service.generateAlarmNotifications(102L, NotificationType.ALARM_CREATED);

        assertEquals(List.of(10L), captureReceiverUserIds());
        verifyNoInteractions(transportTaskMapper);
    }

    @Test
    void duplicateInsertIsIdempotentAndSkipsPush() {
        Alarm alarm = alarm(103L, null, "LOW");
        when(alarmMapper.selectById(103L)).thenReturn(alarm);
        when(userMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(user(10L)));
        when(notificationMapper.insert(any(Notification.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));

        service.generateAlarmNotifications(103L, NotificationType.ALARM_CREATED);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void missingAlarmGeneratesNothing() {
        when(alarmMapper.selectById(999L)).thenReturn(null);

        service.generateAlarmNotifications(999L, NotificationType.ALARM_CREATED);

        verify(notificationMapper, never()).insert(any(Notification.class));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void commandNotifiesOnlyTargetDriverUser() {
        DispatchCommand command = command(82L, 30L, 5L, "前方封路，请按新路线行驶");
        when(dispatchCommandMapper.selectById(82L)).thenReturn(command);
        when(driverMapper.selectById(5L)).thenReturn(driver(5L, 50L));
        when(notificationMapper.insert(any(Notification.class))).thenReturn(1);

        service.generateDispatchCommandNotification(82L);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationMapper, times(1)).insert(captor.capture());
        Notification notification = captor.getValue();
        assertEquals(50L, notification.getReceiverUserId());
        assertEquals("DISPATCH_COMMAND_CREATED", notification.getType());
        assertEquals("/dispatch?commandId=82", notification.getTargetPath());
        assertEquals(30L, notification.getTaskId());
        verify(eventPublisher, times(1)).publishEvent(any(NotificationWsEvent.class));
    }

    @Test
    void commandSkippedWhenDriverHasNoUserAccount() {
        DispatchCommand command = command(83L, 30L, 6L, "内容");
        when(dispatchCommandMapper.selectById(83L)).thenReturn(command);
        when(driverMapper.selectById(6L)).thenReturn(driver(6L, null));

        service.generateDispatchCommandNotification(83L);

        verify(notificationMapper, never()).insert(any(Notification.class));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void markReadRejectsOtherUsersNotificationAsNotFound() {
        when(currentUserService.getCurrentUser()).thenReturn(identity(1L));
        Notification notification = notification(1001L, 2L, false);
        when(notificationMapper.selectById(1001L)).thenReturn(notification);

        assertThrows(BusinessException.class, () -> service.markRead(1001L));
        verify(notificationMapper, never()).updateById(any(Notification.class));
    }

    @Test
    void markReadIsIdempotentWhenAlreadyRead() {
        when(currentUserService.getCurrentUser()).thenReturn(identity(1L));
        Notification notification = notification(1001L, 1L, true);
        when(notificationMapper.selectById(1001L)).thenReturn(notification);

        NotificationResponse response = service.markRead(1001L);

        assertTrue(response.read());
        verify(notificationMapper, never()).updateById(any(Notification.class));
    }

    @Test
    void markReadPersistsReadAtAndReturnsRead() {
        when(currentUserService.getCurrentUser()).thenReturn(identity(1L));
        Notification notification = notification(1001L, 1L, false);
        when(notificationMapper.selectById(1001L)).thenReturn(notification);
        when(notificationMapper.updateById(any(Notification.class))).thenReturn(1);

        NotificationResponse response = service.markRead(1001L);

        assertTrue(response.read());
        assertEquals(true, notification.getIsRead());
        assertEquals(true, notification.getReadAt() != null);
    }

    @Test
    void markAllReadOnlyTouchesOwnUnreadRows() {
        when(currentUserService.getCurrentUser()).thenReturn(identity(1L));
        when(notificationMapper.update(isNull(), any(LambdaUpdateWrapper.class)))
                .thenReturn(3);

        long updated = service.markAllRead();

        assertEquals(3, updated);
        verify(notificationMapper, times(1))
                .update(isNull(), any(LambdaUpdateWrapper.class));
    }

    @Test
    void unreadCountQueriesOnlyCurrentUser() {
        when(currentUserService.getCurrentUser()).thenReturn(identity(1L));
        when(notificationMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(5L);

        assertEquals(5, service.countMyUnread());
    }

    @Test
    void listReturnsMappedResponsesWithTotal() {
        when(currentUserService.getCurrentUser()).thenReturn(identity(1L));
        Page<Notification> page = new Page<>(1, 20);
        page.setRecords(new ArrayList<>(List.of(notification(1001L, 1L, false))));
        page.setTotal(1);
        when(notificationMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(page);

        var result = service.listMyNotifications(1, 20, null, null);

        assertEquals(1, result.getTotal());
        assertEquals(1001L, result.getRecords().get(0).id());
        assertFalse(result.getRecords().get(0).read());
    }

    private List<Long> captureReceiverUserIds() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationMapper, atLeastOnce()).insert(captor.capture());
        return captor.getAllValues().stream().map(Notification::getReceiverUserId).toList();
    }

    private UserIdentityResponse identity(Long userId) {
        return new UserIdentityResponse(userId, "u" + userId, "用户", null,
                UserRole.DISPATCHER, UserStatus.ACTIVE, null, null);
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setStatus(UserStatus.ACTIVE.name());
        return user;
    }

    private Alarm alarm(Long id, Long taskId, String level) {
        Alarm alarm = new Alarm();
        alarm.setId(id);
        alarm.setTaskId(taskId);
        alarm.setLevel(level);
        alarm.setMessage("车辆连续偏离规划路线");
        return alarm;
    }

    private TransportTask task(Long id, Long cargoId) {
        TransportTask task = new TransportTask();
        task.setId(id);
        task.setCargoId(cargoId);
        return task;
    }

    private Cargo cargo(Long id, Long ownerId) {
        Cargo cargo = new Cargo();
        cargo.setId(id);
        cargo.setOwnerId(ownerId);
        return cargo;
    }

    private Owner owner(Long id, Long userId) {
        Owner owner = new Owner();
        owner.setId(id);
        owner.setUserId(userId);
        return owner;
    }

    private Driver driver(Long id, Long userId) {
        Driver driver = new Driver();
        driver.setId(id);
        driver.setUserId(userId);
        return driver;
    }

    private DispatchCommand command(Long id, Long taskId, Long driverId, String content) {
        DispatchCommand command = new DispatchCommand();
        command.setId(id);
        command.setTaskId(taskId);
        command.setTargetDriverId(driverId);
        command.setContent(content);
        return command;
    }

    private Notification notification(Long id, Long receiverUserId, boolean read) {
        Notification notification = new Notification();
        notification.setId(id);
        notification.setReceiverUserId(receiverUserId);
        notification.setType(NotificationType.ALARM_CREATED.name());
        notification.setTitle("新告警通知");
        notification.setContent("车辆连续偏离规划路线");
        notification.setLevel("WARNING");
        notification.setIsRead(read);
        notification.setBusinessType("ALARM");
        notification.setBusinessId("101");
        notification.setTaskId(30L);
        notification.setTargetPath("/alarms?alarmId=101");
        notification.setCreatedAt(LocalDateTime.of(2026, 8, 30, 14, 30));
        return notification;
    }
}
