-- Route activation intervals for task playback.
-- Apply after 009_transport_task_route.sql.
-- Existing rows remain NULL because their historical switch instants cannot be
-- reconstructed reliably from created_at / updated_at.

ALTER TABLE transport_task_route
    ADD COLUMN activated_at DATETIME(3) NULL AFTER status,
    ADD COLUMN deactivated_at DATETIME(3) NULL AFTER activated_at,
    ADD INDEX idx_transport_task_route_task_activation
        (task_id, activated_at, route_version);
