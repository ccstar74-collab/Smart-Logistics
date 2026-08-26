package com.smart_logistics.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class TransportTaskUpdateRequest {
    @NotBlank(message = "起点不能为空")
    private String startLocation;

    @NotBlank(message = "终点不能为空")
    private String endLocation;

    private OffsetDateTime planStartTime;

    private OffsetDateTime planEndTime;
}