-- Migration order is fixed as 005 -> 006 (Cargo) -> 007 (ETA).
-- Do not renumber this migration to 006 or modify the Cargo migration.
-- Coordinates remain nullable for compatibility with existing transport tasks.
-- Transport task start/end coordinates use GCJ-02, matching Amap POI and route APIs.

ALTER TABLE transport_task
    ADD COLUMN start_longitude DECIMAL(10, 7) NULL AFTER start_location,
    ADD COLUMN start_latitude DECIMAL(10, 7) NULL AFTER start_longitude,
    ADD COLUMN end_longitude DECIMAL(10, 7) NULL AFTER end_location,
    ADD COLUMN end_latitude DECIMAL(10, 7) NULL AFTER end_longitude,
    ADD COLUMN eta_calculated_at DATETIME NULL AFTER estimated_arrival_time;
