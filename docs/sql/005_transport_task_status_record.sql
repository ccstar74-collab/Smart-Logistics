-- Phase 5 authoritative TransportTask status transition history.
-- Run only after 004_vehicle_sim_code.sql. Do not backfill fabricated history.

CREATE TABLE transport_task_status_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    cargo_id BIGINT NOT NULL,
    from_status VARCHAR(20) NOT NULL,
    to_status VARCHAR(20) NOT NULL,
    operator_user_id BIGINT NOT NULL,
    operator_role VARCHAR(30) NOT NULL,
    changed_at DATETIME(3) NOT NULL,

    INDEX idx_task_status_record_task (task_id, changed_at, id),
    INDEX idx_task_status_record_cargo (cargo_id, changed_at, id),

    CONSTRAINT fk_task_status_record_task
        FOREIGN KEY (task_id) REFERENCES transport_task(id),
    CONSTRAINT fk_task_status_record_cargo
        FOREIGN KEY (cargo_id) REFERENCES cargo(id),
    CONSTRAINT fk_task_status_record_operator
        FOREIGN KEY (operator_user_id) REFERENCES `user`(id)
);
