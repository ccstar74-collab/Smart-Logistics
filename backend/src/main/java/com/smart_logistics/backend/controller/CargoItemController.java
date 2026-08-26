package com.smart_logistics.backend.controller;

import com.smart_logistics.backend.common.ApiResponse;
import com.smart_logistics.backend.dto.request.CargoItemCreateRequest;
import com.smart_logistics.backend.dto.response.CargoItemResponse;
import com.smart_logistics.backend.service.CargoItemService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/cargos/{cargoId}/items")
public class CargoItemController {

    private final CargoItemService cargoItemService;

    public CargoItemController(CargoItemService cargoItemService) {
        this.cargoItemService = cargoItemService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER','ADMIN')")
    public ApiResponse<CargoItemResponse> createCargoItem(
            @PathVariable @Positive Long cargoId,
            @Valid @RequestBody CargoItemCreateRequest request) {
        return ApiResponse.success(cargoItemService.createCargoItem(cargoId, request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','DRIVER','WAREHOUSE_MANAGER','DISPATCHER','ADMIN')")
    public ApiResponse<List<CargoItemResponse>> getCargoItems(
            @PathVariable @Positive Long cargoId) {
        return ApiResponse.success(cargoItemService.getCargoItemsByCargoId(cargoId));
    }

    @GetMapping("/{itemId}")
    @PreAuthorize("hasAnyRole('OWNER','DRIVER','WAREHOUSE_MANAGER','DISPATCHER','ADMIN')")
    public ApiResponse<CargoItemResponse> getCargoItem(
            @PathVariable @Positive Long cargoId,
            @PathVariable @Positive Long itemId) {
        return ApiResponse.success(
                cargoItemService.getCargoItemByCargoIdAndId(cargoId, itemId)
        );
    }

    @PutMapping("/{itemId}")
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER','ADMIN')")
    public ApiResponse<CargoItemResponse> updateCargoItem(
            @PathVariable @Positive Long cargoId,
            @PathVariable @Positive Long itemId,
            @Valid @RequestBody CargoItemCreateRequest request) {
        return ApiResponse.success(cargoItemService.updateCargoItem(cargoId, itemId, request));
    }

    @DeleteMapping("/{itemId}")
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER','ADMIN')")
    public ApiResponse<Boolean> deleteCargoItem(
            @PathVariable @Positive Long cargoId,
            @PathVariable @Positive Long itemId) {
        cargoItemService.deleteCargoItem(cargoId, itemId);
        return ApiResponse.success(true);
    }
}
