package com.smart_logistics.backend.dto.request;

import com.smart_logistics.backend.enums.TransportTaskStatus;
import jakarta.validation.constraints.NotNull;

public class TransportTaskStatusUpdateRequest {

    @NotNull(message = "status must not be null")
    private TransportTaskStatus status;

    public TransportTaskStatus getStatus() { return status; }
    public void setStatus(TransportTaskStatus status) { this.status = status; }
}
