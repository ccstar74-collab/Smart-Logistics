package com.smart_logistics.backend.dto.response;

import lombok.Data;
import java.time.OffsetDateTime;

/**
 * WebSocket实时推送对外输出DTO
 * 【对外契约字段】 latitude / longitude / speed / direction
 * 仅用于序列化输出给前端，内部MQTT解析不要使用本类
 */
@Data
public class VehicleTraceWsDTO {
    // 车辆编号，字符串，如 sim_001
    private String vehicleId;

    // ---------- 严格和前端约定的字段名，不能修改 ----------
    private Double latitude;
    private Double longitude;
    private Double speed;
    private Double direction;
    // ---------------------------------------------------

    // 采集时间，OffsetDateTime，前端需要
    private OffsetDateTime collectedAt;
}

