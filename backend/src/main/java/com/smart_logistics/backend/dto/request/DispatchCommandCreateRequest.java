package com.smart_logistics.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import com.smart_logistics.backend.enums.DispatchCommandType;

public class DispatchCommandCreateRequest {

    @NotNull(message = "taskId must not be null")
    @Positive(message = "taskId must be greater than 0")
    private Long taskId;

    @NotNull(message = "commandType must not be null")
    private DispatchCommandType commandType;

    @NotBlank(message = "content must not be blank")
    @Size(max = 500, message = "content must not exceed 500 characters")
    private String content;

    @Size(max = 64, message = "routeId must not exceed 64 characters")
    @Pattern(regexp = "^route_[A-Za-z0-9-]+$", message = "routeId has invalid format")
    private String routeId;

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public DispatchCommandType getCommandType() {
        return commandType;
    }

    public void setCommandType(DispatchCommandType commandType) {
        this.commandType = commandType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getRouteId() {
        return routeId;
    }

    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }
}
