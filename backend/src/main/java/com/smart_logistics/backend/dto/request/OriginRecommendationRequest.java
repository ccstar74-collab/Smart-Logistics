package com.smart_logistics.backend.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class OriginRecommendationRequest {

    @NotNull(message = "ownerId must not be null")
    @Positive(message = "ownerId must be greater than 0")
    private Long ownerId;

    @NotNull(message = "cargoTypeId must not be null")
    @Positive(message = "cargoTypeId must be greater than 0")
    private Long cargoTypeId;

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

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public Long getCargoTypeId() {
        return cargoTypeId;
    }

    public void setCargoTypeId(Long cargoTypeId) {
        this.cargoTypeId = cargoTypeId;
    }

    public String getEndLocation() {
        return endLocation;
    }

    public void setEndLocation(String endLocation) {
        this.endLocation = endLocation;
    }

    public Double getEndLongitude() {
        return endLongitude;
    }

    public void setEndLongitude(Double endLongitude) {
        this.endLongitude = endLongitude;
    }

    public Double getEndLatitude() {
        return endLatitude;
    }

    public void setEndLatitude(Double endLatitude) {
        this.endLatitude = endLatitude;
    }
}
