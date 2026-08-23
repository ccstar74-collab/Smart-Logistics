package com.smart_logistics.backend.dto.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class CargoItemCreateRequest {

    @NotBlank(message = "itemName must not be blank")
    @Size(max = 100, message = "itemName must not exceed 100 characters")
    private String itemName;

    @NotNull(message = "quantity must not be null")
    @Positive(message = "quantity must be greater than 0")
    private Integer quantity;

    @Size(max = 20, message = "unit must not exceed 20 characters")
    private String unit;

    @PositiveOrZero(message = "weight must be greater than or equal to 0")
    @Digits(integer = 8, fraction = 2,
            message = "weight must have at most 8 integer digits and 2 fraction digits")
    private BigDecimal weight;

    @PositiveOrZero(message = "volume must be greater than or equal to 0")
    @Digits(integer = 8, fraction = 2,
            message = "volume must have at most 8 integer digits and 2 fraction digits")
    private BigDecimal volume;

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public void setWeight(BigDecimal weight) {
        this.weight = weight;
    }

    public BigDecimal getVolume() {
        return volume;
    }

    public void setVolume(BigDecimal volume) {
        this.volume = volume;
    }
}
