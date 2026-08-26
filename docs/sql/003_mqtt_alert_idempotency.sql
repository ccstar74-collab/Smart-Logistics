-- MQTT alert ingestion upgrade for an existing Alarm V1 database.
-- Run once after docs/sql/002_alarm_schema.sql has already been applied.
-- The staged nullable columns and backfill preserve existing alarm rows.

ALTER TABLE alarm
    MODIFY COLUMN task_id BIGINT NULL,
    ADD COLUMN device_code VARCHAR(64) NULL
        COMMENT 'MQTT vehicle_id，例如 real_001 / sim_000'
        AFTER task_id,
    ADD COLUMN source VARCHAR(20) NULL
        COMMENT 'simulator / backend / device'
        AFTER status,
    ADD COLUMN schema_version VARCHAR(10) NULL
        AFTER source,
    ADD COLUMN event_key CHAR(64) NULL
        COMMENT '告警幂等键 SHA-256'
        AFTER schema_version,
    ADD COLUMN occurred_at DATETIME(3) NULL
        COMMENT '设备消息中的事件时间'
        AFTER event_key;

UPDATE alarm
SET device_code = CONCAT('legacy_', id),
    source = 'backend',
    schema_version = 'legacy',
    event_key = SHA2(CONCAT_WS('|', 'legacy', id, task_id, alarm_type, created_at), 256),
    occurred_at = created_at
WHERE event_key IS NULL;

ALTER TABLE alarm
    MODIFY COLUMN device_code VARCHAR(64) NOT NULL,
    MODIFY COLUMN source VARCHAR(20) NOT NULL,
    MODIFY COLUMN schema_version VARCHAR(10) NOT NULL DEFAULT '1.0',
    MODIFY COLUMN event_key CHAR(64) NOT NULL,
    MODIFY COLUMN occurred_at DATETIME(3) NOT NULL,
    ADD INDEX idx_alarm_device_occurred_at (device_code, occurred_at, id),
    ADD UNIQUE INDEX uk_alarm_event_key (event_key);
