package com.smart_logistics.backend.dto.realtime;

/**
 * 通知WebSocket推送事件（进程内Spring事件）。
 * 通知行落库事务提交后发布，处理器按receiverUserId精确推送，
 * 保证前端收到的通知一定已持久化，断线补偿走REST列表接口。
 */
public record NotificationWsEvent(Long notificationId) {
}
