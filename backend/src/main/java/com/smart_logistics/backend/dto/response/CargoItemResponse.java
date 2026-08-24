package com.smart_logistics.backend.dto.response;

import java.math.BigDecimal;

public class CargoItemResponse {

    private final Long id;
    private final Long cargoId;
    private final String itemName;
    private final Integer quantity;
    private final String unit;
    private final BigDecimal weight;
    private final BigDecimal volume;

    public CargoItemResponse(Long id, Long cargoId, String itemName, Integer quantity,
                             String unit, BigDecimal weight, BigDecimal volume) {
        this.id = id;
        this.cargoId = cargoId;
        this.itemName = itemName;
        this.quantity = quantity;
        this.unit = unit;
        this.weight = weight;
        this.volume = volume;
    }

    public Long getId() {
        return id;
    }

    public Long getCargoId() {
        return cargoId;
    }

    public String getItemName() {
        return itemName;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public String getUnit() {
        return unit;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public BigDecimal getVolume() {
        return volume;
    }
}
