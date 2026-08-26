package com.smart_logistics.backend.dto.response;

import com.smart_logistics.backend.enums.AlarmLevel;
import com.smart_logistics.backend.enums.AlarmStatus;
import com.smart_logistics.backend.enums.AlarmType;

import java.time.OffsetDateTime;

public class AlarmResponse {

    private final Long id;
    private final Long taskId;
    private final String deviceCode;
    private final AlarmType alarmType;
    private final AlarmLevel level;
    private final String message;
    private final AlarmStatus status;
    private final String source;
    private final OffsetDateTime occurredAt;
    private final Long handledBy;
    private final OffsetDateTime handledAt;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime resolvedAt;

    public AlarmResponse(Long id, Long taskId, String deviceCode,
                         AlarmType alarmType, AlarmLevel level,
                         String message, AlarmStatus status, String source,
                         OffsetDateTime occurredAt, Long handledBy,
                         OffsetDateTime handledAt, OffsetDateTime createdAt,
                         OffsetDateTime resolvedAt) {
        this.id = id;
        this.taskId = taskId;
        this.deviceCode = deviceCode;
        this.alarmType = alarmType;
        this.level = level;
        this.message = message;
        this.status = status;
        this.source = source;
        this.occurredAt = occurredAt;
        this.handledBy = handledBy;
        this.handledAt = handledAt;
        this.createdAt = createdAt;
        this.resolvedAt = resolvedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getTaskId() {
        return taskId;
    }

    public String getDeviceCode() {
        return deviceCode;
    }

    public AlarmType getAlarmType() {
        return alarmType;
    }

    public AlarmLevel getLevel() {
        return level;
    }

    public String getMessage() {
        return message;
    }

    public AlarmStatus getStatus() {
        return status;
    }

    public String getSource() {
        return source;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public Long getHandledBy() {
        return handledBy;
    }

    public OffsetDateTime getHandledAt() {
        return handledAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getResolvedAt() {
        return resolvedAt;
    }
}
