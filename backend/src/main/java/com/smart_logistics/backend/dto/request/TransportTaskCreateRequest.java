package com.smart_logistics.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

import java.time.OffsetDateTime;

public class TransportTaskCreateRequest {

    @NotNull(message = "cargoId must not be null")
    @Positive(message = "cargoId must be greater than 0")
    private Long cargoId;

    @NotNull(message = "vehicleId must not be null")
    @Positive(message = "vehicleId must be greater than 0")
    private Long vehicleId;

    @NotBlank(message = "startLocation must not be blank")
    @Size(max = 255, message = "startLocation must not exceed 255 characters")
    private String startLocation;

    @DecimalMin(value = "-180.0", message = "startLongitude must be at least -180")
    @DecimalMax(value = "180.0", message = "startLongitude must not exceed 180")
    private Double startLongitude;

    @DecimalMin(value = "-90.0", message = "startLatitude must be at least -90")
    @DecimalMax(value = "90.0", message = "startLatitude must not exceed 90")
    private Double startLatitude;

    @NotBlank(message = "endLocation must not be blank")
    @Size(max = 255, message = "endLocation must not exceed 255 characters")
    private String endLocation;

    @DecimalMin(value = "-180.0", message = "endLongitude must be at least -180")
    @DecimalMax(value = "180.0", message = "endLongitude must not exceed 180")
    private Double endLongitude;

    @DecimalMin(value = "-90.0", message = "endLatitude must be at least -90")
    @DecimalMax(value = "90.0", message = "endLatitude must not exceed 90")
    private Double endLatitude;

    private OffsetDateTime planStartTime;
    private OffsetDateTime planEndTime;

    public Long getCargoId() { return cargoId; }
    public void setCargoId(Long cargoId) { this.cargoId = cargoId; }
    public Long getVehicleId() { return vehicleId; }
    public void setVehicleId(Long vehicleId) { this.vehicleId = vehicleId; }
    public String getStartLocation() { return startLocation; }
    public void setStartLocation(String startLocation) { this.startLocation = startLocation; }
    public Double getStartLongitude() { return startLongitude; }
    public void setStartLongitude(Double startLongitude) { this.startLongitude = startLongitude; }
    public Double getStartLatitude() { return startLatitude; }
    public void setStartLatitude(Double startLatitude) { this.startLatitude = startLatitude; }
    public String getEndLocation() { return endLocation; }
    public void setEndLocation(String endLocation) { this.endLocation = endLocation; }
    public Double getEndLongitude() { return endLongitude; }
    public void setEndLongitude(Double endLongitude) { this.endLongitude = endLongitude; }
    public Double getEndLatitude() { return endLatitude; }
    public void setEndLatitude(Double endLatitude) { this.endLatitude = endLatitude; }
    public OffsetDateTime getPlanStartTime() { return planStartTime; }
    public void setPlanStartTime(OffsetDateTime planStartTime) { this.planStartTime = planStartTime; }
    public OffsetDateTime getPlanEndTime() { return planEndTime; }
    public void setPlanEndTime(OffsetDateTime planEndTime) { this.planEndTime = planEndTime; }
}
