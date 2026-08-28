package com.smart_logistics.backend.dto.response;

import com.smart_logistics.backend.enums.VehicleStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class VehicleResponse {

    private final Long id;
    private final String plateNumber;
    private final String type;
    private final BigDecimal capacity;
    private final VehicleStatus status;
    private final Long driverId;
    private final String driverName;
    private final String simCode;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;
    private final BigDecimal lastLongitude;
    private final BigDecimal lastLatitude;
    private final OffsetDateTime lastUpdatedAt;

    public VehicleResponse(Long id, String plateNumber, String type, BigDecimal capacity,
                           VehicleStatus status, Long driverId, String driverName,
                           String simCode,
                           OffsetDateTime createdAt,
                           OffsetDateTime updatedAt, BigDecimal lastLongitude,
                           BigDecimal lastLatitude, OffsetDateTime lastUpdatedAt) {
        this.id = id;
        this.plateNumber = plateNumber;
        this.type = type;
        this.capacity = capacity;
        this.status = status;
        this.driverId = driverId;
        this.driverName = driverName;
        this.simCode = simCode;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.lastLongitude = lastLongitude;
        this.lastLatitude = lastLatitude;
        this.lastUpdatedAt = lastUpdatedAt;
    }

    public Long getId() {
        return id;
    }

    public String getPlateNumber() {
        return plateNumber;
    }

    public String getType() {
        return type;
    }

    public BigDecimal getCapacity() {
        return capacity;
    }

    public VehicleStatus getStatus() {
        return status;
    }

    public Long getDriverId() {
        return driverId;
    }

    public String getDriverName() {
        return driverName;
    }

    public String getSimCode() {
        return simCode;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public BigDecimal getLastLongitude() {
        return lastLongitude;
    }

    public BigDecimal getLastLatitude() {
        return lastLatitude;
    }

    public OffsetDateTime getLastUpdatedAt() {
        return lastUpdatedAt;
    }
}