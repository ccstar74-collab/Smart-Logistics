package com.smart_logistics.backend.controller;

import com.smart_logistics.backend.common.ApiResponse;
import com.smart_logistics.backend.common.PageResult;
import com.smart_logistics.backend.dto.request.VehicleCreateRequest;
import com.smart_logistics.backend.dto.request.VehicleUpdateRequest;
import com.smart_logistics.backend.dto.response.VehicleResponse;
import com.smart_logistics.backend.enums.VehicleStatus;
import com.smart_logistics.backend.service.VehicleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/vehicles")
public class VehicleController {

    private final VehicleService vehicleService;

    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @GetMapping
    public ApiResponse<PageResult<VehicleResponse>> listVehicles(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(required = false) VehicleStatus status) {
        return ApiResponse.success(vehicleService.listVehicles(page, pageSize, keyword, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<VehicleResponse> getVehicle(@PathVariable @Positive Long id) {
        return ApiResponse.success(vehicleService.getVehicle(id));
    }

    @PostMapping
    public ApiResponse<VehicleResponse> createVehicle(
            @Valid @RequestBody VehicleCreateRequest request) {
        return ApiResponse.success(vehicleService.createVehicle(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<VehicleResponse> updateVehicle(
            @PathVariable @Positive Long id,
            @Valid @RequestBody VehicleUpdateRequest request) {
        return ApiResponse.success(vehicleService.updateVehicle(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> disableVehicle(@PathVariable @Positive Long id) {
        vehicleService.disableVehicle(id);
        return ApiResponse.success(true);
    }
}
