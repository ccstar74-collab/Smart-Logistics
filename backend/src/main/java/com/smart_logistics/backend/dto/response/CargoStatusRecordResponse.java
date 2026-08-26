package com.smart_logistics.backend.dto.response;

import com.smart_logistics.backend.enums.TransportTaskStatus;
import com.smart_logistics.backend.enums.UserRole;

import java.time.OffsetDateTime;

public class CargoStatusRecordResponse {
    private final Long id;
    private final Long taskId;
    private final Long cargoId;
    private final TransportTaskStatus fromStatus;
    private final TransportTaskStatus toStatus;
    private final Long operatorUserId;
    private final UserRole operatorRole;
    private final OffsetDateTime changedAt;

    public CargoStatusRecordResponse(Long id, Long taskId, Long cargoId,
                                     TransportTaskStatus fromStatus,
                                     TransportTaskStatus toStatus,
                                     Long operatorUserId, UserRole operatorRole,
                                     OffsetDateTime changedAt) {
        this.id = id;
        this.taskId = taskId;
        this.cargoId = cargoId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.operatorUserId = operatorUserId;
        this.operatorRole = operatorRole;
        this.changedAt = changedAt;
    }

    public Long getId() { return id; }
    public Long getTaskId() { return taskId; }
    public Long getCargoId() { return cargoId; }
    public TransportTaskStatus getFromStatus() { return fromStatus; }
    public TransportTaskStatus getToStatus() { return toStatus; }
    public Long getOperatorUserId() { return operatorUserId; }
    public UserRole getOperatorRole() { return operatorRole; }
    public OffsetDateTime getChangedAt() { return changedAt; }
}
