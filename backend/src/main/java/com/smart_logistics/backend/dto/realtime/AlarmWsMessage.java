package com.smart_logistics.backend.dto.realtime;

import com.smart_logistics.backend.dto.response.AlarmResponse;

/**
 * /ws/alarms推送消息信封。
 * alarm字段完全复用GET /api/v1/alarms的AlarmResponse字段名，
 * 前端不需要维护第二套告警模型。
 */
public record AlarmWsMessage(String event, Long alarmId, AlarmResponse alarm) {
}
