-- Cargo is unassigned inventory until a transport task is created.
-- The existing fk_cargo_owner foreign key remains in force for non-NULL values.

ALTER TABLE cargo
    MODIFY COLUMN owner_id BIGINT NULL;
