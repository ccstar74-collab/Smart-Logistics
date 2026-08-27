package com.smart_logistics.backend.dto.response;

import com.smart_logistics.backend.enums.DispatchCommandStatus;
import com.smart_logistics.backend.enums.DispatchCommandType;
import com.smart_logistics.backend.enums.TransportTaskRouteStatus;

import java.time.OffsetDateTime;

public class DispatchCommandResponse {
    private final Long id;
    private final Long alarmId;
    private final Long taskId;
    private final String taskNo;
    private final Long targetDriverId;
    private final String targetDriverName;
    private final Long vehicleId;
    private final String plateNumber;
    private final String routeId;
    private final Integer routeVersion;
    private final TransportTaskRouteStatus routeStatus;
    private final DispatchCommandType commandType;
    private final String content;
    private final DispatchCommandStatus status;
    private final String feedback;
    private final Long createdBy;
    private final OffsetDateTime sentAt;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime acknowledgedAt;
    private final OffsetDateTime executingAt;
    private final OffsetDateTime completedAt;
    private final OffsetDateTime rejectedAt;

    public DispatchCommandResponse(Long id, Long alarmId, Long taskId, String taskNo,
                                   Long targetDriverId, String targetDriverName,
                                   Long vehicleId, String plateNumber,
                                   String routeId, Integer routeVersion,
                                   TransportTaskRouteStatus routeStatus,
                                   DispatchCommandType commandType, String content,
                                   DispatchCommandStatus status, String feedback,
                                   Long createdBy, OffsetDateTime sentAt,
                                   OffsetDateTime createdAt, OffsetDateTime acknowledgedAt,
                                   OffsetDateTime executingAt, OffsetDateTime completedAt,
                                   OffsetDateTime rejectedAt) {
        this.id = id;
        this.alarmId = alarmId;
        this.taskId = taskId;
        this.taskNo = taskNo;
        this.targetDriverId = targetDriverId;
        this.targetDriverName = targetDriverName;
        this.vehicleId = vehicleId;
        this.plateNumber = plateNumber;
        this.routeId = routeId;
        this.routeVersion = routeVersion;
        this.routeStatus = routeStatus;
        this.commandType = commandType;
        this.content = content;
        this.status = status;
        this.feedback = feedback;
        this.createdBy = createdBy;
        this.sentAt = sentAt;
        this.createdAt = createdAt;
        this.acknowledgedAt = acknowledgedAt;
        this.executingAt = executingAt;
        this.completedAt = completedAt;
        this.rejectedAt = rejectedAt;
    }

    public Long getId() { return id; }
    public Long getAlarmId() { return alarmId; }
    public Long getTaskId() { return taskId; }
    public String getTaskNo() { return taskNo; }
    public Long getTargetDriverId() { return targetDriverId; }
    public String getTargetDriverName() { return targetDriverName; }
    public Long getVehicleId() { return vehicleId; }
    public String getPlateNumber() { return plateNumber; }
    public String getRouteId() { return routeId; }
    public Integer getRouteVersion() { return routeVersion; }
    public TransportTaskRouteStatus getRouteStatus() { return routeStatus; }
    public DispatchCommandType getCommandType() { return commandType; }
    public String getContent() { return content; }
    public DispatchCommandStatus getStatus() { return status; }
    public String getFeedback() { return feedback; }
    public Long getCreatedBy() { return createdBy; }
    public OffsetDateTime getSentAt() { return sentAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getAcknowledgedAt() { return acknowledgedAt; }
    public OffsetDateTime getExecutingAt() { return executingAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public OffsetDateTime getRejectedAt() { return rejectedAt; }
}
