package com.smart_logistics.backend.controller;

import com.smart_logistics.backend.common.ApiResponse;
import com.smart_logistics.backend.common.PageResult;
import com.smart_logistics.backend.dto.request.CargoCreateRequest;
import com.smart_logistics.backend.dto.response.CargoResponse;
import com.smart_logistics.backend.enums.CargoStatus;
import com.smart_logistics.backend.service.CargoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/cargos")
public class CargoController {

    private final CargoService cargoService;

    public CargoController(CargoService cargoService) {
        this.cargoService = cargoService;
    }

    @GetMapping
    public ApiResponse<PageResult<CargoResponse>> listCargos(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(required = false) CargoStatus status) {
        return ApiResponse.success(cargoService.listCargos(page, pageSize, keyword, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<CargoResponse> getCargo(@PathVariable @Positive Long id) {
        return ApiResponse.success(cargoService.getCargo(id));
    }

    @PostMapping
    public ApiResponse<CargoResponse> createCargo(
            @Valid @RequestBody CargoCreateRequest request) {
        return ApiResponse.success(cargoService.createCargo(request));
    }
}
