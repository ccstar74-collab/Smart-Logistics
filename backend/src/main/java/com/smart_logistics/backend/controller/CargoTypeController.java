package com.smart_logistics.backend.controller;

import com.smart_logistics.backend.common.ApiResponse;
import com.smart_logistics.backend.common.PageResult;
import com.smart_logistics.backend.dto.request.CargoTypeCreateRequest;
import com.smart_logistics.backend.dto.response.CargoTypeResponse;
import com.smart_logistics.backend.service.CargoTypeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/cargo-types")
public class CargoTypeController {

    private final CargoTypeService cargoTypeService;

    public CargoTypeController(CargoTypeService cargoTypeService) {
        this.cargoTypeService = cargoTypeService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER','DISPATCHER','ADMIN')")
    public ApiResponse<PageResult<CargoTypeResponse>> listCargoTypes(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) @Size(max = 100) String keyword) {
        return ApiResponse.success(cargoTypeService.listCargoTypes(page, pageSize, keyword));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER','ADMIN')")
    public ApiResponse<CargoTypeResponse> createCargoType(
            @Valid @RequestBody CargoTypeCreateRequest request) {
        return ApiResponse.success(cargoTypeService.createCargoType(request));
    }
}
