package com.smart_logistics.backend.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class CargoTypeResponse {

    private final Long id;
    private final String name;
    private final String unit;
    private final BigDecimal unitWeight;
    private final BigDecimal unitVolume;
    private final String description;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;

    public CargoTypeResponse(Long id, String name, String unit, BigDecimal unitWeight,
                             BigDecimal unitVolume, String description,
                             OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.name = name;
        this.unit = unit;
        this.unitWeight = unitWeight;
        this.unitVolume = unitVolume;
        this.description = description;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getUnit() {
        return unit;
    }

    public BigDecimal getUnitWeight() {
        return unitWeight;
    }

    public BigDecimal getUnitVolume() {
        return unitVolume;
    }

    public String getDescription() {
        return description;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
