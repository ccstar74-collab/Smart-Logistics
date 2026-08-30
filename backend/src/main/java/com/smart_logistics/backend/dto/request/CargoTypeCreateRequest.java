package com.smart_logistics.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class CargoTypeCreateRequest {

    @NotBlank(message = "name must not be blank")
    @Size(max = 100, message = "name must not exceed 100 characters")
    private String name;

    @Size(max = 20, message = "unit must not exceed 20 characters")
    private String unit;

    @PositiveOrZero(message = "unitWeight must be greater than or equal to 0")
    private BigDecimal unitWeight;

    @PositiveOrZero(message = "unitVolume must be greater than or equal to 0")
    private BigDecimal unitVolume;

    @Size(max = 500, message = "description must not exceed 500 characters")
    private String description;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public BigDecimal getUnitWeight() {
        return unitWeight;
    }

    public void setUnitWeight(BigDecimal unitWeight) {
        this.unitWeight = unitWeight;
    }

    public BigDecimal getUnitVolume() {
        return unitVolume;
    }

    public void setUnitVolume(BigDecimal unitVolume) {
        this.unitVolume = unitVolume;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
