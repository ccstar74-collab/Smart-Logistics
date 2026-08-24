package com.smart_logistics.backend.dto.request;

import jakarta.validation.constraints.Positive;

public class VehicleDriverUpdateRequest {

    @Positive(message = "driverId must be greater than 0")
    private Long driverId;

    public Long getDriverId() { return driverId; }
    public void setDriverId(Long driverId) { this.driverId = driverId; }
}
