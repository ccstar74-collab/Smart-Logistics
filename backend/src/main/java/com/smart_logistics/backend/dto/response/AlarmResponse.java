package com.smart_logistics.backend.dto.response;

import com.smart_logistics.backend.enums.AlarmLevel;
import com.smart_logistics.backend.enums.AlarmStatus;
import com.smart_logistics.backend.enums.AlarmType;

import java.time.OffsetDateTime;

public class AlarmResponse {

    private final Long id;
    private final Long taskId;
    private final AlarmType alarmType;
    private final AlarmLevel level;
    private final String message;
    private final AlarmStatus status;
    private final Long handledBy;
    private final OffsetDateTime handledAt;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime resolvedAt;

    public AlarmResponse(Long id, Long taskId, AlarmType alarmType, AlarmLevel level,
                         String message, AlarmStatus status, Long handledBy,
                         OffsetDateTime handledAt, OffsetDateTime createdAt,
                         OffsetDateTime resolvedAt) {
        this.id = id;
        this.taskId = taskId;
        this.alarmType = alarmType;
        this.level = level;
        this.message = message;
        this.status = status;
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
