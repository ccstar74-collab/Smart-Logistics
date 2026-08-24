package com.smart_logistics.backend.controller;

import com.smart_logistics.backend.common.ApiResponse;
import com.smart_logistics.backend.common.PageResult;
import com.smart_logistics.backend.dto.response.DispatchCommandResponse;
import com.smart_logistics.backend.enums.DispatchCommandStatus;
import com.smart_logistics.backend.service.DispatchCommandService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    public ApiResponse<PageResult<DispatchCommandResponse>> listCommands(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(required = false) DispatchCommandStatus status,
            @RequestParam(required = false) @Positive Long taskId,
            @RequestParam(required = false) @Positive Long vehicleId,
            @RequestParam(required = false)
            @Size(max = 50)
            @Pattern(regexp = "^[A-Z][A-Z0-9_]*$") String commandType) {
        return ApiResponse.success(
                dispatchCommandService.listCommands(
                        page, pageSize, keyword, status, taskId, vehicleId, commandType
                )
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<DispatchCommandResponse> getCommand(@PathVariable @Positive Long id) {
        return ApiResponse.success(dispatchCommandService.getCommand(id));
    }
}
