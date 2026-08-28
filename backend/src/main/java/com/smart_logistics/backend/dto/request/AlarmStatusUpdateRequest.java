package com.smart_logistics.backend.dto.request;

import com.smart_logistics.backend.enums.AlarmStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AlarmStatusUpdateRequest {

    @NotNull(message = "status must not be null")
    private AlarmStatus status;

    @NotBlank(message = "remark must not be blank")
    @Size(max = 500, message = "remark must not exceed 500 characters")
    private String remark;

    public AlarmStatus getStatus() {
        return status;
    }

    public void setStatus(AlarmStatus status) {
        this.status = status;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
