package com.smart_logistics.backend.controller;

import com.smart_logistics.backend.common.ApiResponse;
import com.smart_logistics.backend.common.PageResult;
import com.smart_logistics.backend.dto.response.WarehouseResponse;
import com.smart_logistics.backend.service.WarehouseService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/warehouses")
public class WarehouseController {

    private final WarehouseService warehouseService;

    public WarehouseController(WarehouseService warehouseService) {
        this.warehouseService = warehouseService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER','DISPATCHER','ADMIN')")
    public ApiResponse<PageResult<WarehouseResponse>> listWarehouses(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) @Size(max = 100) String keyword) {
        return ApiResponse.success(warehouseService.listWarehouses(page, pageSize, keyword));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER','DISPATCHER','ADMIN')")
    public ApiResponse<WarehouseResponse> getWarehouse(@PathVariable @Positive Long id) {
        return ApiResponse.success(warehouseService.getWarehouse(id));
    }
}
