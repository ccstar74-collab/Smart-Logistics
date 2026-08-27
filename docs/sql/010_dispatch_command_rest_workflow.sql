CREATE TABLE dispatch_command (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    target_driver_id BIGINT NOT NULL,
    vehicle_id BIGINT NOT NULL,
    target_route_id VARCHAR(64) NULL,
    command_type VARCHAR(20) NOT NULL COMMENT 'TEXT / ROUTE_CHANGE',
    content VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL
        COMMENT 'SENT / ACKNOWLEDGED / EXECUTING / COMPLETED / REJECTED',
    feedback VARCHAR(500) NULL,
    created_by BIGINT NOT NULL,
    sent_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    acknowledged_at DATETIME NULL,
    executing_at DATETIME NULL,
    completed_at DATETIME NULL,
    rejected_at DATETIME NULL,

    CONSTRAINT fk_dispatch_command_task
        FOREIGN KEY (task_id) REFERENCES transport_task(id),
    CONSTRAINT fk_dispatch_command_driver
        FOREIGN KEY (target_driver_id) REFERENCES driver(id),
    CONSTRAINT fk_dispatch_command_vehicle
        FOREIGN KEY (vehicle_id) REFERENCES vehicle(id),
    CONSTRAINT fk_dispatch_command_route
        FOREIGN KEY (target_route_id) REFERENCES transport_task_route(route_id),
    CONSTRAINT fk_dispatch_command_creator
        FOREIGN KEY (created_by) REFERENCES `user`(id),
    INDEX idx_dispatch_command_task_created (task_id, created_at),
    INDEX idx_dispatch_command_driver_status_created
        (target_driver_id, status, created_at),
    INDEX idx_dispatch_command_status_created (status, created_at)
);
