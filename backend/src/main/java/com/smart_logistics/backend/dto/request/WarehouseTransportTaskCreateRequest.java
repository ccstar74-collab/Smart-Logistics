package com.smart_logistics.backend.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public class WarehouseTransportTaskCreateRequest {

    @NotNull(message = "ownerId must not be null")
    @Positive(message = "ownerId must be greater than 0")
    private Long ownerId;

    @NotNull(message = "cargoTypeId must not be null")
    @Positive(message = "cargoTypeId must be greater than 0")
    private Long cargoTypeId;

    @NotNull(message = "originWarehouseId must not be null")
    @Positive(message = "originWarehouseId must be greater than 0")
    private Long originWarehouseId;

    @NotNull(message = "cargoId must not be null")
    @Positive(message = "cargoId must be greater than 0")
    private Long cargoId;

    @NotNull(message = "vehicleId must not be null")
    @Positive(message = "vehicleId must be greater than 0")
    private Long vehicleId;

    @NotBlank(message = "endLocation must not be blank")
    @Size(max = 255, message = "endLocation must not exceed 255 characters")
    private String endLocation;

    @NotNull(message = "endLongitude must not be null")
    @DecimalMin(value = "-180.0", message = "endLongitude must be at least -180")
    @DecimalMax(value = "180.0", message = "endLongitude must not exceed 180")
    private Double endLongitude;

    @NotNull(message = "endLatitude must not be null")
    @DecimalMin(value = "-90.0", message = "endLatitude must be at least -90")
    @DecimalMax(value = "90.0", message = "endLatitude must not exceed 90")
    private Double endLatitude;

    @JsonAlias("plannedStartTime")
    private OffsetDateTime planStartTime;
    private OffsetDateTime planEndTime;

    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public Long getCargoTypeId() { return cargoTypeId; }
    public void setCargoTypeId(Long cargoTypeId) { this.cargoTypeId = cargoTypeId; }
    public Long getOriginWarehouseId() { return originWarehouseId; }
    public void setOriginWarehouseId(Long originWarehouseId) {
        this.originWarehouseId = originWarehouseId;
    }
    public Long getCargoId() { return cargoId; }
    public void setCargoId(Long cargoId) { this.cargoId = cargoId; }
    public Long getVehicleId() { return vehicleId; }
    public void setVehicleId(Long vehicleId) { this.vehicleId = vehicleId; }
    public String getEndLocation() { return endLocation; }
    public void setEndLocation(String endLocation) { this.endLocation = endLocation; }
    public Double getEndLongitude() { return endLongitude; }
    public void setEndLongitude(Double endLongitude) { this.endLongitude = endLongitude; }
    public Double getEndLatitude() { return endLatitude; }
    public void setEndLatitude(Double endLatitude) { this.endLatitude = endLatitude; }
    public OffsetDateTime getPlanStartTime() { return planStartTime; }
    public void setPlanStartTime(OffsetDateTime planStartTime) {
        this.planStartTime = planStartTime;
    }
    public OffsetDateTime getPlanEndTime() { return planEndTime; }
    public void setPlanEndTime(OffsetDateTime planEndTime) { this.planEndTime = planEndTime; }
}
