-- Alarm V1 incremental schema.
-- Run this file only after docs/sql/001_core_schema.sql has been applied.
-- IF NOT EXISTS does not repair or replace an existing table with a different structure.

CREATE TABLE IF NOT EXISTS alarm (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,

    task_id BIGINT NOT NULL,

    alarm_type VARCHAR(30) NOT NULL
        COMMENT 'ROUTE_DEVIATION / ABNORMAL_STOP / ABNORMAL_OPEN / OTHER',

    level VARCHAR(20) NOT NULL
        COMMENT 'LOW / MEDIUM / HIGH',

    message VARCHAR(500) NOT NULL,

    status VARCHAR(20) NOT NULL
        COMMENT 'UNHANDLED / PROCESSING / RESOLVED',

    handled_by BIGINT NULL,

    handled_at DATETIME NULL,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    resolved_at DATETIME NULL,

    INDEX idx_alarm_task_id (task_id),

    INDEX idx_alarm_status_created_at (status, created_at, id),

    INDEX idx_alarm_created_at (created_at, id),

    CONSTRAINT fk_alarm_task
        FOREIGN KEY (task_id)
        REFERENCES transport_task(id)
);
