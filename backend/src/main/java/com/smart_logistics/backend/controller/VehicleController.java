package com.smart_logistics.backend.controller;

import com.smart_logistics.backend.common.ApiResponse;
import com.smart_logistics.backend.common.PageResult;
import com.smart_logistics.backend.dto.request.VehicleCreateRequest;
import com.smart_logistics.backend.dto.request.VehicleUpdateRequest;
import com.smart_logistics.backend.dto.request.VehicleDriverUpdateRequest;
import com.smart_logistics.backend.dto.response.VehicleResponse;
import com.smart_logistics.backend.dto.response.VehicleLocationResponse;
import com.smart_logistics.backend.enums.VehicleStatus;
import com.smart_logistics.backend.service.VehicleService;
import com.smart_logistics.backend.service.VehicleLocationQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.time.OffsetDateTime;

@Validated
@RestController
@RequestMapping("/api/v1/vehicles")
public class VehicleController{

    private final VehicleService vehicleService;
    private final VehicleLocationQueryService vehicleLocationQueryService;

    public VehicleController(VehicleService vehicleService,
                             VehicleLocationQueryService vehicleLocationQueryService) {
        this.vehicleService = vehicleService;
        this.vehicleLocationQueryService = vehicleLocationQueryService;
    }

    @GetMapping("/locations/latest")
    @PreAuthorize("hasAnyRole('OWNER','DRIVER','WAREHOUSE_MANAGER','DISPATCHER','ADMIN')")
    public ApiResponse<List<VehicleLocationResponse>> getLatestLocations() {
        return ApiResponse.success(vehicleLocationQueryService.getLatestLocations());
    }

    @GetMapping("/{id}/location/latest")
    @PreAuthorize("hasAnyRole('OWNER','DRIVER','WAREHOUSE_MANAGER','DISPATCHER','ADMIN')")
    public ApiResponse<VehicleLocationResponse> getLatestLocation(
            @PathVariable @Positive Long id) {
        return ApiResponse.success(vehicleLocationQueryService.getLatestLocation(id));
    }

    @GetMapping("/{id}/location-history")
    @PreAuthorize("hasAnyRole('OWNER','DRIVER','WAREHOUSE_MANAGER','DISPATCHER','ADMIN')")
    public ApiResponse<List<VehicleLocationResponse>> getLocationHistory(
            @PathVariable @Positive Long id,
            @RequestParam OffsetDateTime startTime,
            @RequestParam OffsetDateTime endTime) {
        return ApiResponse.success(
                vehicleLocationQueryService.getLocationHistory(id, startTime, endTime));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','DRIVER','WAREHOUSE_MANAGER','DISPATCHER','ADMIN')")
    public ApiResponse<PageResult<VehicleResponse>> listVehicles(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(required = false) VehicleStatus status,
            @RequestParam(required = false) @Positive Long driverId) {
        return ApiResponse.success(
                vehicleService.listVehicles(page, pageSize, keyword, status, driverId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','DRIVER','WAREHOUSE_MANAGER','DISPATCHER','ADMIN')")
    public ApiResponse<VehicleResponse> getVehicle(@PathVariable @Positive Long id) {
        return ApiResponse.success(vehicleService.getVehicle(id));
    }

    @GetMapping("/available")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    public ApiResponse<List<VehicleResponse>> listAvailableVehicles(
            @RequestParam(required = false) @Positive Long warehouseId) {
        return ApiResponse.success(vehicleService.listAvailableVehicles(warehouseId));
    }

    @GetMapping("/sim-codes/available")
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER','ADMIN')")
    public ApiResponse<List<String>> listAvailableSimCodes(
            @RequestParam(required = false) @Size(max = 20) String keyword) {
        return ApiResponse.success(vehicleService.listAvailableSimCodes(keyword));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER','ADMIN')")
    public ApiResponse<VehicleResponse> createVehicle(
            @Valid @RequestBody VehicleCreateRequest request) {
        return ApiResponse.success(vehicleService.createVehicle(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER','ADMIN')")
    public ApiResponse<VehicleResponse> updateVehicle(
            @PathVariable @Positive Long id,
            @Valid @RequestBody VehicleUpdateRequest request) {
        return ApiResponse.success(vehicleService.updateVehicle(id, request));
    }

    @PutMapping("/{id}/driver")
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER','ADMIN')")
    public ApiResponse<VehicleResponse> updateDriver(
            @PathVariable @Positive Long id,
            @Valid @RequestBody VehicleDriverUpdateRequest request) {
        return ApiResponse.success(vehicleService.updateDriverBinding(id, request.getDriverId()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER','ADMIN')")
    public ApiResponse<Boolean> disableVehicle(@PathVariable @Positive Long id) {
        vehicleService.disableVehicle(id);
        return ApiResponse.success(true);
    }
}
