package com.smart_logistics.backend.service;

import com.smart_logistics.backend.dto.realtime.AlarmWsEvent;
import com.smart_logistics.backend.dto.realtime.DispatchCommandCreatedEvent;
import com.smart_logistics.backend.enums.NotificationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 通知生成监听器：消费业务事务提交后的事件，落库通知行。
 * 只接入ALARM_CREATED/ALARM_RESOLVED/DISPATCH_COMMAND_CREATED，
 * ALARM_UPDATED等中间态不产生通知；通知模块不重新实现业务逻辑，
 * 业务操作成功后才产生“结果提醒”。
 */
@Slf4j
@Component
public class NotificationEventListener {

    private final NotificationService notificationService;

    public NotificationEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * 告警事件复用现有AlarmWsEvent：覆盖MQTT告警入库、人工消警、
     * 自动消警闭环全部路径，业务服务无需为通知重复发布事件。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true)
    public void onAlarmEvent(AlarmWsEvent event) {
        try {
            switch (event.type()) {
                case ALARM_CREATED -> notificationService.generateAlarmNotifications(
                        event.alarmId(), NotificationType.ALARM_CREATED);
                case ALARM_RESOLVED -> notificationService.generateAlarmNotifications(
                        event.alarmId(), NotificationType.ALARM_RESOLVED);
                default -> {
                    // ALARM_UPDATED等中间态不生成通知
                }
            }
        } catch (RuntimeException exception) {
            // 通知生成失败只记日志，不影响告警推送等既有链路
            log.error("告警通知生成失败 alarmId={}, event={}",
                    event.alarmId(), event.type(), exception);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true)
    public void onDispatchCommandCreated(DispatchCommandCreatedEvent event) {
        try {
            notificationService.generateDispatchCommandNotification(event.commandId());
        } catch (RuntimeException exception) {
            log.error("调度指令通知生成失败 commandId={}", event.commandId(), exception);
        }
    }
}
