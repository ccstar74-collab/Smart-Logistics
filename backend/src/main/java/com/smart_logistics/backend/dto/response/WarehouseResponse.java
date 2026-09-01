package com.smart_logistics.backend.dto.response;

import com.smart_logistics.backend.enums.WarehouseStatus;

import java.time.OffsetDateTime;

public class WarehouseResponse {

    private final Long id;
    private final String warehouseNo;
    private final String name;
    private final String address;
    private final Double longitude;
    private final Double latitude;
    private final String contactName;
    private final String contactPhone;
    private final WarehouseStatus status;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;

    public WarehouseResponse(Long id, String warehouseNo, String name, String address,
                             Double longitude, Double latitude, String contactName,
                             String contactPhone, WarehouseStatus status,
                             OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.warehouseNo = warehouseNo;
        this.name = name;
        this.address = address;
        this.longitude = longitude;
        this.latitude = latitude;
        this.contactName = contactName;
        this.contactPhone = contactPhone;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public String getWarehouseNo() {
        return warehouseNo;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public Double getLongitude() {
        return longitude;
    }

    public Double getLatitude() {
        return latitude;
    }

    public String getContactName() {
        return contactName;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public WarehouseStatus getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
