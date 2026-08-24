package com.smart_logistics.backend.controller;

import com.smart_logistics.backend.common.ApiResponse;
import com.smart_logistics.backend.common.PageResult;
import com.smart_logistics.backend.dto.request.TransportTaskCreateRequest;
import com.smart_logistics.backend.dto.request.TransportTaskStatusUpdateRequest;
import com.smart_logistics.backend.dto.response.TransportTaskResponse;
import com.smart_logistics.backend.enums.TransportTaskStatus;
import com.smart_logistics.backend.service.TransportTaskService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/transport-tasks")
public class TransportTaskController {

    private final TransportTaskService transportTaskService;

    public TransportTaskController(TransportTaskService transportTaskService) {
        this.transportTaskService = transportTaskService;
    }

    @GetMapping
    public ApiResponse<PageResult<TransportTaskResponse>> listTransportTasks(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(required = false) TransportTaskStatus status) {
        return ApiResponse.success(
                transportTaskService.listTransportTasks(page, pageSize, keyword, status));
    }

    @PostMapping
    public ApiResponse<TransportTaskResponse> createTransportTask(
            @Valid @RequestBody TransportTaskCreateRequest request) {
        return ApiResponse.success(transportTaskService.createTransportTask(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<TransportTaskResponse> getTransportTask(
            @PathVariable @Positive Long id) {
        return ApiResponse.success(transportTaskService.getTransportTask(id));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<TransportTaskResponse> updateTransportTaskStatus(
            @PathVariable @Positive Long id,
            @Valid @RequestBody TransportTaskStatusUpdateRequest request) {
        return ApiResponse.success(
                transportTaskService.updateTransportTaskStatus(id, request));
    }
}
