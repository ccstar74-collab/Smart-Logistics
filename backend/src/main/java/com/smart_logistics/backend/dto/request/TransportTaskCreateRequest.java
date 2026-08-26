package com.smart_logistics.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public class TransportTaskCreateRequest {

    @NotNull(message = "cargoId must not be null")
    @Positive(message = "cargoId must be greater than 0")
    private Long cargoId;

    @NotNull(message = "ownerId must not be null")
    @Positive(message = "ownerId must be greater than 0")
    private Long ownerId;

    @NotNull(message = "vehicleId must not be null")
    @Positive(message = "vehicleId must be greater than 0")
    private Long vehicleId;

    @NotBlank(message = "startLocation must not be blank")
    @Size(max = 255, message = "startLocation must not exceed 255 characters")
    private String startLocation;

    @NotBlank(message = "endLocation must not be blank")
    @Size(max = 255, message = "endLocation must not exceed 255 characters")
    private String endLocation;

    private OffsetDateTime planStartTime;
    private OffsetDateTime planEndTime;

    public Long getCargoId() { return cargoId; }
    public void setCargoId(Long cargoId) { this.cargoId = cargoId; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public Long getVehicleId() { return vehicleId; }
    public void setVehicleId(Long vehicleId) { this.vehicleId = vehicleId; }
    public String getStartLocation() { return startLocation; }
    public void setStartLocation(String startLocation) { this.startLocation = startLocation; }
    public String getEndLocation() { return endLocation; }
    public void setEndLocation(String endLocation) { this.endLocation = endLocation; }
    public OffsetDateTime getPlanStartTime() { return planStartTime; }
    public void setPlanStartTime(OffsetDateTime planStartTime) { this.planStartTime = planStartTime; }
    public OffsetDateTime getPlanEndTime() { return planEndTime; }
    public void setPlanEndTime(OffsetDateTime planEndTime) { this.planEndTime = planEndTime; }
}
