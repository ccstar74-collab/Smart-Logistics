package com.smart_logistics.backend.enums;

/**
 * 消息中心通知类型（docs/notification.md）。
 * P0阶段只接入告警创建/消除与调度指令创建四类核心事件；
 * 任务类与多仓库事件后续扩展，高频GPS/ETA刷新不产生通知。
 */
public enum NotificationType {
    ALARM_CREATED,
    ALARM_RESOLVED,
    DISPATCH_COMMAND_CREATED
}
