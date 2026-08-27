package com.smart_logistics.backend.controller;

import com.smart_logistics.backend.common.ApiResponse;
import com.smart_logistics.backend.common.PageResult;
import com.smart_logistics.backend.dto.response.DriverOptionResponse;
import com.smart_logistics.backend.dto.response.DispatchCommandResponse;
import com.smart_logistics.backend.enums.DispatchCommandStatus;
import com.smart_logistics.backend.service.DispatchCommandService;
import com.smart_logistics.backend.service.DriverService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@RestController
@RequestMapping("/api/v1/drivers")
public class DriverController {

    private final DriverService driverService;
    private final DispatchCommandService dispatchCommandService;

    public DriverController(DriverService driverService,
                            DispatchCommandService dispatchCommandService) {
        this.driverService = driverService;
        this.dispatchCommandService = dispatchCommandService;
    }

    @GetMapping("/options")
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER','DISPATCHER','ADMIN')")
    public ApiResponse<List<DriverOptionResponse>> listOptions() {
        return ApiResponse.success(driverService.listOptions());
    }

    @GetMapping("/me/dispatch-commands")
    @PreAuthorize("hasRole('DRIVER')")
    public ApiResponse<PageResult<DispatchCommandResponse>> listMyDispatchCommands(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) DispatchCommandStatus status) {
        return ApiResponse.success(
                dispatchCommandService.listMyCommands(page, pageSize, status));
    }
}
