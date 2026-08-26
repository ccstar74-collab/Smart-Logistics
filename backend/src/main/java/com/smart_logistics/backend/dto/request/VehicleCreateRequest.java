package com.smart_logistics.backend.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class VehicleCreateRequest {

    @NotBlank(message = "plateNumber must not be blank")
    @Size(max = 20, message = "plateNumber must not exceed 20 characters")
    private String plateNumber;

    @Size(max = 50, message = "type must not exceed 50 characters")
    private String type;

    @NotNull(message = "capacity must not be null")
    @PositiveOrZero(message = "capacity must be greater than or equal to 0")
    private BigDecimal capacity;

    private Long driverId;

    // ✅注解直接写在唯一的simCode字段上，不要复制一份变量
    @JsonProperty(value = "sim_code", access = JsonProperty.Access.WRITE_ONLY)
    private String simCode;

    public String getPlateNumber() {
        return plateNumber;
    }

    public void setPlateNumber(String plateNumber) {
        this.plateNumber = plateNumber;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public BigDecimal getCapacity() {
        return capacity;
    }

    public void setCapacity(BigDecimal capacity) {
        this.capacity = capacity;
    }

    public Long getDriverId() {
        return driverId;
    }

    public void setDriverId(Long driverId) {
        this.driverId = driverId;
    }

    public String getSimCode() {
        return simCode;
    }

    public void setSimCode(String simCode) {
        this.simCode = simCode;
    }
}