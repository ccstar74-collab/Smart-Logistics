package com.smart_logistics.backend.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class InitialRouteDecisionCreateRequest {

    @NotNull(message = "originWarehouseId must not be null")
    @Positive(message = "originWarehouseId must be greater than 0")
    private Long originWarehouseId;

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

    @NotBlank(message = "coordinateSystem must not be blank")
    @Pattern(regexp = "^GCJ02$", message = "coordinateSystem must be GCJ02")
    private String coordinateSystem = "GCJ02";

    @NotNull(message = "candidateCount must not be null")
    @Min(value = 2, message = "candidateCount must be at least 2")
    @Max(value = 3, message = "candidateCount must not exceed 3")
    private Integer candidateCount = 3;

    @NotBlank(message = "planningMode must not be blank")
    @Pattern(regexp = "^INITIAL_MULTI_OBJECTIVE$",
            message = "planningMode must be INITIAL_MULTI_OBJECTIVE")
    private String planningMode = "INITIAL_MULTI_OBJECTIVE";

    public Long getOriginWarehouseId() { return originWarehouseId; }
    public void setOriginWarehouseId(Long originWarehouseId) {
        this.originWarehouseId = originWarehouseId;
    }
    public String getEndLocation() { return endLocation; }
    public void setEndLocation(String endLocation) { this.endLocation = endLocation; }
    public Double getEndLongitude() { return endLongitude; }
    public void setEndLongitude(Double endLongitude) { this.endLongitude = endLongitude; }
    public Double getEndLatitude() { return endLatitude; }
    public void setEndLatitude(Double endLatitude) { this.endLatitude = endLatitude; }
    public String getCoordinateSystem() { return coordinateSystem; }
    public void setCoordinateSystem(String coordinateSystem) {
        this.coordinateSystem = coordinateSystem;
    }
    public Integer getCandidateCount() { return candidateCount; }
    public void setCandidateCount(Integer candidateCount) {
        this.candidateCount = candidateCount;
    }
    public String getPlanningMode() { return planningMode; }
    public void setPlanningMode(String planningMode) { this.planningMode = planningMode; }
}
