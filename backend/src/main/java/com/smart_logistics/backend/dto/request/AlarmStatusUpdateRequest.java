package com.smart_logistics.backend.dto.request;

import com.smart_logistics.backend.enums.AlarmStatus;
import jakarta.validation.constraints.NotNull;

public class AlarmStatusUpdateRequest {

    @NotNull(message = "status must not be null")
    private AlarmStatus status;

    public AlarmStatus getStatus() {
        return status;
    }

    public void setStatus(AlarmStatus status) {
        this.status = status;
    }
}
