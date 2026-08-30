package com.smart_logistics.backend.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public class TransportTaskReplanRequest {

    @NotBlank(message = "vehicleDeviceCode must not be blank")
    @Size(max = 7, message = "vehicleDeviceCode must not exceed 7 characters")
    @Pattern(regexp = "^sim_\\d{3}$",
            message = "vehicleDeviceCode must match ^sim_\\d{3}$")
    private String vehicleDeviceCode;

    @NotNull(message = "longitude must not be null")
    @DecimalMin(value = "-180.0", message = "longitude must be at least -180")
    @DecimalMax(value = "180.0", message = "longitude must not exceed 180")
    private Double longitude;

    @NotNull(message = "latitude must not be null")
    @DecimalMin(value = "-90.0", message = "latitude must be at least -90")
    @DecimalMax(value = "90.0", message = "latitude must not exceed 90")
    private Double latitude;

    @NotBlank(message = "coordinateSystem must not be blank")
    @Pattern(regexp = "^WGS84$", message = "coordinateSystem must be WGS84")
    private String coordinateSystem;

    @NotNull(message = "positionAt must not be null")
    private OffsetDateTime positionAt;

    public String getVehicleDeviceCode() { return vehicleDeviceCode; }
    public void setVehicleDeviceCode(String vehicleDeviceCode) {
        this.vehicleDeviceCode = vehicleDeviceCode;
    }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public String getCoordinateSystem() { return coordinateSystem; }
    public void setCoordinateSystem(String coordinateSystem) {
        this.coordinateSystem = coordinateSystem;
    }
    public OffsetDateTime getPositionAt() { return positionAt; }
    public void setPositionAt(OffsetDateTime positionAt) { this.positionAt = positionAt; }
}
