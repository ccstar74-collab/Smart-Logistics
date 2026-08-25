-- Alarm V1 incremental schema.
-- Run this file only after docs/sql/001_core_schema.sql has been applied.
-- IF NOT EXISTS does not repair or replace an existing table with a different structure.

CREATE TABLE IF NOT EXISTS alarm (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,

    task_id BIGINT NULL,

    device_code VARCHAR(64) NOT NULL
        COMMENT 'MQTT vehicle_id，例如 real_001 / sim_000',

    alarm_type VARCHAR(30) NOT NULL
        COMMENT 'ROUTE_DEVIATION / ABNORMAL_STOP / ABNORMAL_OPEN / OTHER',

    level VARCHAR(20) NOT NULL
        COMMENT 'LOW / MEDIUM / HIGH',

    message VARCHAR(500) NOT NULL,

    status VARCHAR(20) NOT NULL
        COMMENT 'UNHANDLED / PROCESSING / RESOLVED',

    source VARCHAR(20) NOT NULL
        COMMENT 'simulator / backend / device',

    schema_version VARCHAR(10) NOT NULL DEFAULT '1.0',

    event_key CHAR(64) NOT NULL
        COMMENT '告警幂等键 SHA-256',

    occurred_at DATETIME(3) NOT NULL
        COMMENT '设备消息中的事件时间',

    handled_by BIGINT NULL,

    handled_at DATETIME NULL,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    resolved_at DATETIME NULL,

    INDEX idx_alarm_task_id (task_id),

    INDEX idx_alarm_status_created_at (status, created_at, id),

    INDEX idx_alarm_created_at (created_at, id),

    INDEX idx_alarm_device_occurred_at (device_code, occurred_at, id),

    UNIQUE INDEX uk_alarm_event_key (event_key),

    CONSTRAINT fk_alarm_task
        FOREIGN KEY (task_id)
        REFERENCES transport_task(id)
);
