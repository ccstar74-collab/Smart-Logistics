package com.smart_logistics.backend.service;

import com.smart_logistics.backend.dto.realtime.AlarmWsEvent;
import com.smart_logistics.backend.dto.realtime.DispatchCommandCreatedEvent;
import com.smart_logistics.backend.enums.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * 通知生成监听：只消费CREATED/RESOLVED告警事件与指令创建事件；
 * 中间态不生成通知；生成失败不影响既有告警链路。
 */
@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock private NotificationService notificationService;

    private NotificationEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new NotificationEventListener(notificationService);
    }

    @Test
    void alarmCreatedEventGeneratesCreatedNotification() {
        listener.onAlarmEvent(AlarmWsEvent.created(42L));

        verify(notificationService).generateAlarmNotifications(
                42L, NotificationType.ALARM_CREATED);
    }

    @Test
    void alarmResolvedEventGeneratesResolvedNotification() {
        listener.onAlarmEvent(AlarmWsEvent.resolved(42L));

        verify(notificationService).generateAlarmNotifications(
                42L, NotificationType.ALARM_RESOLVED);
    }

    @Test
    void alarmUpdatedEventDoesNotGenerateNotification() {
        listener.onAlarmEvent(AlarmWsEvent.updated(42L));

        verifyNoInteractions(notificationService);
    }

    @Test
    void commandCreatedEventGeneratesNotification() {
        listener.onDispatchCommandCreated(new DispatchCommandCreatedEvent(82L));

        verify(notificationService).generateDispatchCommandNotification(82L);
    }

    @Test
    void generationFailureIsSwallowedToProtectAlarmPipeline() {
        doThrow(new RuntimeException("db down"))
                .when(notificationService).generateAlarmNotifications(
                        anyLong(), any(NotificationType.class));

        listener.onAlarmEvent(AlarmWsEvent.created(42L));

        verify(notificationService).generateAlarmNotifications(
                eq(42L), eq(NotificationType.ALARM_CREATED));
        verifyNoMoreInteractions(notificationService);
    }

    @Test
    void commandGenerationFailureIsSwallowed() {
        doThrow(new RuntimeException("db down"))
                .when(notificationService).generateDispatchCommandNotification(anyLong());

        listener.onDispatchCommandCreated(new DispatchCommandCreatedEvent(82L));

        verify(notificationService, never()).generateAlarmNotifications(
                anyLong(), any(NotificationType.class));
    }
}
