package com.smart_logistics.backend.dto.request;

import com.smart_logistics.backend.enums.AlarmStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class AlarmStatusUpdateRequest {

    @NotNull(message = "status must not be null")
    private AlarmStatus status;

    @Size(max = 500, message = "handle note must not exceed 500 characters")
    private String note;

    public AlarmStatus getStatus() {
        return status;
    }

    public void setStatus(AlarmStatus status) {
        this.status = status;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
