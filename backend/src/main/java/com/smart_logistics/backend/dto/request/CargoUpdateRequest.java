package com.smart_logistics.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class CargoUpdateRequest {

    @NotBlank(message = "name must not be blank")
    @Size(max = 100, message = "name must not exceed 100 characters")
    private String name;

    @Size(max = 500, message = "description must not exceed 500 characters")
    private String description;

    @PositiveOrZero(message = "weight must be greater than or equal to 0")
    private BigDecimal weight;

    @PositiveOrZero(message = "volume must be greater than or equal to 0")
    private BigDecimal volume;

    @Positive(message = "cargoTypeId must be greater than 0")
    private Long cargoTypeId;

    @Positive(message = "warehouseId must be greater than 0")
    private Long warehouseId;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getWeight() { return weight; }
    public void setWeight(BigDecimal weight) { this.weight = weight; }
    public BigDecimal getVolume() { return volume; }
    public void setVolume(BigDecimal volume) { this.volume = volume; }
    public Long getCargoTypeId() { return cargoTypeId; }
    public void setCargoTypeId(Long cargoTypeId) { this.cargoTypeId = cargoTypeId; }
    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
}
