package com.smart_logistics.backend.dto.response;

import lombok.Data;

@Data
public class SimGpsPointDTO {
    // InfluxDB tag读出的设备sim编号 sim_018
    private String simCode;
    // MySQL数据库主键，InfluxDB拿不到，controller层回填
    private Long dbVehicleId;

    private Double lat;
    private Double lon;
    private Double speed;
    private Double heading;
    private Long timestamp;
}