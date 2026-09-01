package com.smart_logistics.backend.dto.response;

import com.smart_logistics.backend.enums.CargoStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class CargoResponse {

    private final Long id;
    private final String cargoNo;
    private final String name;
    private final String description;
    private final BigDecimal weight;
    private final BigDecimal volume;
    private final Long cargoTypeId;
    private final Long warehouseId;
    private final Long ownerId;
    private final String ownerName;
    private final CargoStatus status;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;

    public CargoResponse(Long id, String cargoNo, String name, String description,
                         BigDecimal weight, BigDecimal volume, Long cargoTypeId,
                         Long warehouseId, Long ownerId,
                         String ownerName, CargoStatus status, OffsetDateTime createdAt,
                         OffsetDateTime updatedAt) {
        this.id = id;
        this.cargoNo = cargoNo;
        this.name = name;
        this.description = description;
        this.weight = weight;
        this.volume = volume;
        this.cargoTypeId = cargoTypeId;
        this.warehouseId = warehouseId;
        this.ownerId = ownerId;
        this.ownerName = ownerName;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public CargoResponse(Long id, String cargoNo, String name, String description,
                         BigDecimal weight, BigDecimal volume, Long ownerId,
                         String ownerName, CargoStatus status, OffsetDateTime createdAt,
                         OffsetDateTime updatedAt) {
        this(id, cargoNo, name, description, weight, volume, null, null, ownerId,
                ownerName, status, createdAt, updatedAt);
    }

    public Long getId() {
        return id;
    }

    public String getCargoNo() {
        return cargoNo;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public BigDecimal getVolume() {
        return volume;
    }

    public Long getCargoTypeId() {
        return cargoTypeId;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public CargoStatus getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
