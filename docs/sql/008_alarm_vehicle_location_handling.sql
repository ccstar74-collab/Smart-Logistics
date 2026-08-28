-- Alarm V2 incremental schema.
-- Run this file only after docs/sql/002_alarm_schema.sql has been applied.
-- Adds the registered vehicle association, the alarm location captured at
-- ingestion time, and the handling note saved when DISPATCHER/ADMIN process
-- the alarm. Coordinates follow the transport_task convention: DECIMAL(10,7).

ALTER TABLE alarm
    ADD COLUMN vehicle_id BIGINT NULL
        COMMENT '关联的业务车辆ID，按device_code匹配vehicle.sim_code，未登记车辆为NULL'
        AFTER task_id,

    ADD COLUMN longitude DECIMAL(10, 7) NULL
        COMMENT '告警发生时的车辆经度（尽力捕获，可能为NULL）'
        AFTER message,

    ADD COLUMN latitude DECIMAL(10, 7) NULL
        COMMENT '告警发生时的车辆纬度（尽力捕获，可能为NULL）'
        AFTER longitude,

    ADD COLUMN handle_note VARCHAR(500) NULL
        COMMENT '处理备注，处理告警时填写'
        AFTER handled_at,

    ADD INDEX idx_alarm_vehicle_id (vehicle_id, created_at, id);
