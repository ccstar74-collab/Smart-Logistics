package com.smart_logistics.backend.dto.response;

import com.smart_logistics.backend.enums.DispatchCommandStatus;

import java.time.OffsetDateTime;

public class DispatchCommandResponse {

    private final Long id;
    private final Long taskId;
    private final Long vehicleId;
    private final Long fromUserId;
    private final Long toUserId;
    private final String commandType;
    private final String content;
    private final DispatchCommandStatus status;
    private final OffsetDateTime sentAt;
    private final OffsetDateTime executedAt;
    private final OffsetDateTime createdAt;

    public DispatchCommandResponse(Long id, Long taskId, Long vehicleId,
                                   Long fromUserId, Long toUserId,
                                   String commandType, String content,
                                   DispatchCommandStatus status,
                                   OffsetDateTime sentAt, OffsetDateTime executedAt,
                                   OffsetDateTime createdAt) {
        this.id = id;
        this.taskId = taskId;
        this.vehicleId = vehicleId;
        this.fromUserId = fromUserId;
        this.toUserId = toUserId;
        this.commandType = commandType;
        this.content = content;
        this.status = status;
        this.sentAt = sentAt;
        this.executedAt = executedAt;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getTaskId() {
        return taskId;
    }

    public Long getVehicleId() {
        return vehicleId;
    }

    public Long getFromUserId() {
        return fromUserId;
    }

    public Long getToUserId() {
        return toUserId;
    }

    public String getCommandType() {
        return commandType;
    }

    public String getContent() {
        return content;
    }

    public DispatchCommandStatus getStatus() {
        return status;
    }

    public OffsetDateTime getSentAt() {
        return sentAt;
    }

    public OffsetDateTime getExecutedAt() {
        return executedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
