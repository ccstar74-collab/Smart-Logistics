package com.smart_logistics.backend.controller;

import com.smart_logistics.backend.common.ApiResponse;
import com.smart_logistics.backend.dto.response.DriverOptionResponse;
import com.smart_logistics.backend.service.DriverService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@RestController
@RequestMapping("/api/v1/drivers")
public class DriverController {

    private final DriverService driverService;

    public DriverController(DriverService driverService) {
        this.driverService = driverService;
    }

    @GetMapping("/options")
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER','DISPATCHER','ADMIN')")
    public ApiResponse<List<DriverOptionResponse>> listOptions() {
        return ApiResponse.success(driverService.listOptions());
    }
}
