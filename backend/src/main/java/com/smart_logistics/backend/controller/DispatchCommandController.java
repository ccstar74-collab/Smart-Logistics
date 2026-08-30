package com.smart_logistics.backend.controller;

import com.smart_logistics.backend.common.ApiResponse;
import com.smart_logistics.backend.common.PageResult;
import com.smart_logistics.backend.dto.response.DispatchCommandResponse;
import com.smart_logistics.backend.dto.request.DispatchCommandCreateRequest;
import com.smart_logistics.backend.dto.request.DispatchCommandStatusUpdateRequest;
import com.smart_logistics.backend.enums.DispatchCommandStatus;
import com.smart_logistics.backend.enums.DispatchCommandType;
import com.smart_logistics.backend.service.DispatchCommandService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/dispatch-commands")
public class DispatchCommandController {

    private final DispatchCommandService dispatchCommandService;

    public DispatchCommandController(DispatchCommandService dispatchCommandService) {
        this.dispatchCommandService = dispatchCommandService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('DISPATCHER','ADMIN')")
    public ApiResponse<PageResult<DispatchCommandResponse>> listCommands(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(required = false) DispatchCommandStatus status,
            @RequestParam(required = false) @Positive Long taskId,
            @RequestParam(required = false) @Positive Long driverId,
            @RequestParam(required = false) DispatchCommandType commandType) {
        return ApiResponse.success(
                dispatchCommandService.listCommands(
                        page, pageSize, keyword, status, taskId, driverId, commandType
                )
        );
    }

    // 下发调度指令仅限调度员；管理员只读（可看列表/详情，不能下发）
    @PostMapping
    @PreAuthorize("hasRole('DISPATCHER')")
    public ApiResponse<DispatchCommandResponse> createCommand(
            @Valid @RequestBody DispatchCommandCreateRequest request) {
        return ApiResponse.success(dispatchCommandService.createCommand(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('DRIVER','DISPATCHER','ADMIN')")
    public ApiResponse<DispatchCommandResponse> getCommand(@PathVariable @Positive Long id) {
        return ApiResponse.success(dispatchCommandService.getCommand(id));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('DRIVER')")
    public ApiResponse<DispatchCommandResponse> updateStatus(
            @PathVariable @Positive Long id,
            @Valid @RequestBody DispatchCommandStatusUpdateRequest request) {
        return ApiResponse.success(dispatchCommandService.updateStatus(id, request));
    }
}
