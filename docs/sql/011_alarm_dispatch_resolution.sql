-- Alarm business closure and DispatchCommand linkage.
-- Apply after 010_dispatch_command_rest_workflow.sql.

ALTER TABLE alarm
    ADD COLUMN vehicle_id BIGINT NULL AFTER id,
    ADD COLUMN condition_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        COMMENT 'ACTIVE / RECOVERED' AFTER status,
    ADD COLUMN recovered_at DATETIME NULL AFTER created_at,
    ADD COLUMN resolution_remark VARCHAR(500) NULL AFTER resolved_at;

UPDATE alarm a
JOIN transport_task t ON t.id = a.task_id
SET a.vehicle_id = t.vehicle_id
WHERE a.vehicle_id IS NULL;

UPDATE alarm a
JOIN vehicle v ON v.sim_code = a.device_code
SET a.vehicle_id = v.id
WHERE a.vehicle_id IS NULL;

ALTER TABLE alarm
    ADD CONSTRAINT fk_alarm_vehicle
        FOREIGN KEY (vehicle_id) REFERENCES vehicle(id),
    ADD INDEX idx_alarm_vehicle_created (vehicle_id, created_at),
    ADD INDEX idx_alarm_condition_status (condition_status, id);

ALTER TABLE dispatch_command
    ADD COLUMN alarm_id BIGINT NULL AFTER id,
    ADD CONSTRAINT fk_dispatch_command_alarm
        FOREIGN KEY (alarm_id) REFERENCES alarm(id),
    ADD INDEX idx_dispatch_command_alarm_status (alarm_id, status, id);
