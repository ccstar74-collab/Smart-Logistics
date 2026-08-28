package com.smart_logistics.backend.dto.response;

import com.smart_logistics.backend.enums.AlarmConditionStatus;
import com.smart_logistics.backend.enums.AlarmLevel;
import com.smart_logistics.backend.enums.AlarmStatus;
import com.smart_logistics.backend.enums.AlarmType;

import java.time.OffsetDateTime;

public class AlarmResponse {

    private final Long id;
    private final Long vehicleId;
    private final String plateNumber;
    private final Long taskId;
    private final String taskNo;
    private final String deviceCode;
    private final AlarmType type;
    private final AlarmLevel level;
    private final String description;
    private final AlarmStatus status;
    private final AlarmConditionStatus conditionStatus;
    private final String source;
    private final OffsetDateTime occurredAt;
    private final OffsetDateTime recoveredAt;
    private final Long handledBy;
    private final OffsetDateTime handledAt;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime resolvedAt;
    private final String resolutionRemark;

    public AlarmResponse(Long id, Long vehicleId, String plateNumber,
                         Long taskId, String taskNo, String deviceCode,
                         AlarmType type, AlarmLevel level, String description,
                         AlarmStatus status, AlarmConditionStatus conditionStatus,
                         String source, OffsetDateTime occurredAt,
                         OffsetDateTime recoveredAt, Long handledBy,
                         OffsetDateTime handledAt, OffsetDateTime createdAt,
                         OffsetDateTime resolvedAt, String resolutionRemark) {
        this.id = id;
        this.vehicleId = vehicleId;
        this.plateNumber = plateNumber;
        this.taskId = taskId;
        this.taskNo = taskNo;
        this.deviceCode = deviceCode;
        this.type = type;
        this.level = level;
        this.description = description;
        this.status = status;
        this.conditionStatus = conditionStatus;
        this.source = source;
        this.occurredAt = occurredAt;
        this.recoveredAt = recoveredAt;
        this.handledBy = handledBy;
        this.handledAt = handledAt;
        this.createdAt = createdAt;
        this.resolvedAt = resolvedAt;
        this.resolutionRemark = resolutionRemark;
    }

    public Long getId() { return id; }
    public Long getVehicleId() { return vehicleId; }
    public String getPlateNumber() { return plateNumber; }
    public Long getTaskId() { return taskId; }
    public String getTaskNo() { return taskNo; }
    public String getDeviceCode() { return deviceCode; }
    public AlarmType getType() { return type; }
    public AlarmType getAlarmType() { return type; }
    public AlarmLevel getLevel() { return level; }
    public String getDescription() { return description; }
    public String getMessage() { return description; }
    public AlarmStatus getStatus() { return status; }
    public AlarmConditionStatus getConditionStatus() { return conditionStatus; }
    public String getSource() { return source; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public OffsetDateTime getRecoveredAt() { return recoveredAt; }
    public Long getHandledBy() { return handledBy; }
    public OffsetDateTime getHandledAt() { return handledAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getResolvedAt() { return resolvedAt; }
    public String getResolutionRemark() { return resolutionRemark; }
}
