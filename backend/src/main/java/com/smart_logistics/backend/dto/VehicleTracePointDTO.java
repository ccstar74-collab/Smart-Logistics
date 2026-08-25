package com.smart_logistics.backend.dto;
import lombok.Data;

@Data
public class VehicleTracePointDTO {
    /** 经度 */
    private Double lng;
    /** 纬度 */
    private Double lat;
    /** 速度 */
    private Double speed;
    /** 航向 */
    private Double heading;
    /** 时间戳，毫秒，给前端 */
    private Long timestamp;
}