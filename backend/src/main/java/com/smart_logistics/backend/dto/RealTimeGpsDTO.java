package com.smart_logistics.backend.dto;

import lombok.Data;

@Data
public class RealTimeGpsDTO {
    private String vehicleId;
    private Double lng;
    private Double lat;
    private Double speed;
    private Double heading;
    private Long timestamp;
}