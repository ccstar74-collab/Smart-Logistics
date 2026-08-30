-- Persist the traffic facts returned together with an immutable planned route.
-- Existing routes remain NULL because their historical Amap response cannot be
-- reconstructed reliably. Apply after 009 and 013.

ALTER TABLE transport_task_route
    ADD COLUMN traffic_snapshot LONGTEXT NULL
        COMMENT 'JSON traffic facts with source, strategy, restriction and TMC distances'
        AFTER duration_seconds;
