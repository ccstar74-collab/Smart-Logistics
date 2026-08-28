package com.smart_logistics.backend.dto.realtime;

/**
 * 告警WebSocket事件（进程内Spring事件）。
 * 业务服务在事务提交后由监听器转发给/ws/alarms会话，
 * WebSocket只做事件通知，MySQL Alarm仍是唯一真实数据源。
 */
public record AlarmWsEvent(Type type, Long alarmId) {

    public enum Type {
        ALARM_CREATED,
        ALARM_UPDATED,
        ALARM_RESOLVED
    }

    public static AlarmWsEvent created(Long alarmId) {
        return new AlarmWsEvent(Type.ALARM_CREATED, alarmId);
    }

    public static AlarmWsEvent updated(Long alarmId) {
        return new AlarmWsEvent(Type.ALARM_UPDATED, alarmId);
    }

    public static AlarmWsEvent resolved(Long alarmId) {
        return new AlarmWsEvent(Type.ALARM_RESOLVED, alarmId);
    }
}
