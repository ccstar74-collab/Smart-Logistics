package com.smart_logistics.backend.dto.request;

import com.smart_logistics.backend.enums.DispatchCommandStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class DispatchCommandStatusUpdateRequest {

    @NotNull(message = "status must not be null")
    private DispatchCommandStatus status;

    @Size(max = 500, message = "feedback must not exceed 500 characters")
    private String feedback;

    public DispatchCommandStatus getStatus() {
        return status;
    }

    public void setStatus(DispatchCommandStatus status) {
        this.status = status;
    }

    public String getFeedback() {
        return feedback;
    }

    public void setFeedback(String feedback) {
        this.feedback = feedback;
    }
}
