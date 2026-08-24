package com.smart_logistics.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class DispatchCommandCreateRequest {

    @NotNull(message = "taskId must not be null")
    @Positive(message = "taskId must be greater than 0")
    private Long taskId;

    @NotNull(message = "vehicleId must not be null")
    @Positive(message = "vehicleId must be greater than 0")
    private Long vehicleId;

    @NotNull(message = "toUserId must not be null")
    @Positive(message = "toUserId must be greater than 0")
    private Long toUserId;

    @NotBlank(message = "commandType must not be blank")
    @Size(max = 50, message = "commandType must not exceed 50 characters")
    @Pattern(
            regexp = "^[A-Z][A-Z0-9_]*$",
            message = "commandType must use uppercase letters, numbers, and underscores"
    )
    private String commandType;

    @NotBlank(message = "content must not be blank")
    @Size(max = 500, message = "content must not exceed 500 characters")
    private String content;

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public Long getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(Long vehicleId) {
        this.vehicleId = vehicleId;
    }

    public Long getToUserId() {
        return toUserId;
    }

    public void setToUserId(Long toUserId) {
        this.toUserId = toUserId;
    }

    public String getCommandType() {
        return commandType;
    }

    public void setCommandType(String commandType) {
        this.commandType = commandType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
