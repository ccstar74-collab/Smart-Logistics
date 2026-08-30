package com.smart_logistics.backend.controller;

import com.smart_logistics.backend.common.ApiResponse;
import com.smart_logistics.backend.common.PageResult;
import com.smart_logistics.backend.dto.request.CargoCreateRequest;
import com.smart_logistics.backend.dto.request.CargoUpdateRequest;
import com.smart_logistics.backend.dto.response.CargoResponse;
import com.smart_logistics.backend.dto.response.CargoStatusRecordResponse;
import com.smart_logistics.backend.enums.CargoStatus;
import com.smart_logistics.backend.service.CargoService;
import com.smart_logistics.backend.service.TransportTaskStatusRecordService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/cargos")
public class CargoController {

    private final CargoService cargoService;
    private final TransportTaskStatusRecordService statusRecordService;

    public CargoController(CargoService cargoService,
                           TransportTaskStatusRecordService statusRecordService) {
        this.cargoService = cargoService;
        this.statusRecordService = statusRecordService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','DRIVER','WAREHOUSE_MANAGER','DISPATCHER','ADMIN')")
    public ApiResponse<PageResult<CargoResponse>> listCargos(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(required = false) CargoStatus status,
            @RequestParam(required = false) @Positive Long ownerId,
            @RequestParam(required = false) @Positive Long cargoTypeId,
            @RequestParam(required = false) @Positive Long warehouseId) {
        return ApiResponse.success(
                cargoService.listCargos(page, pageSize, keyword, status, ownerId,
                        cargoTypeId, warehouseId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','DRIVER','WAREHOUSE_MANAGER','DISPATCHER','ADMIN')")
    public ApiResponse<CargoResponse> getCargo(@PathVariable @Positive Long id) {
        return ApiResponse.success(cargoService.getCargo(id));
    }

    @GetMapping("/available")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    public ApiResponse<List<CargoResponse>> listAvailableCargos(
            @RequestParam(required = false) @Positive Long cargoTypeId,
            @RequestParam(required = false) @Positive Long warehouseId,
            @RequestParam(required = false) @Positive Long ownerId) {
        return ApiResponse.success(
                cargoService.listAvailableCargos(cargoTypeId, warehouseId, ownerId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER','ADMIN')")
    public ApiResponse<CargoResponse> createCargo(
            @Valid @RequestBody CargoCreateRequest request) {
        return ApiResponse.success(cargoService.createCargo(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER','ADMIN')")
    public ApiResponse<CargoResponse> updateCargo(
            @PathVariable @Positive Long id,
            @Valid @RequestBody CargoUpdateRequest request) {
        return ApiResponse.success(cargoService.updateCargo(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER','ADMIN')")
    public ApiResponse<Boolean> deleteCargo(@PathVariable @Positive Long id) {
        cargoService.deleteCargo(id);
        return ApiResponse.success(true);
    }

    @GetMapping("/{id}/status-records")
    @PreAuthorize("hasAnyRole('OWNER','DRIVER','WAREHOUSE_MANAGER','DISPATCHER','ADMIN')")
    public ApiResponse<List<CargoStatusRecordResponse>> getStatusRecords(
            @PathVariable @Positive Long id) {
        return ApiResponse.success(statusRecordService.listCargoStatusRecords(id));
    }
}
