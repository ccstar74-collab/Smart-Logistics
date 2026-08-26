package com.smart_logistics.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class TransportTaskUpdateRequest {

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
}
