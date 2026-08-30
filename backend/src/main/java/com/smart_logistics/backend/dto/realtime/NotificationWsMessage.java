package com.smart_logistics.backend.dto.realtime;

import com.smart_logistics.backend.dto.response.NotificationResponse;

/**
 * /ws/notifications推送消息信封：{event, notification}。
 * notification字段完全复用NotificationResponse（与GET /api/v1/notifications一致）。
 */
public record NotificationWsMessage(String event, NotificationResponse notification) {
}
