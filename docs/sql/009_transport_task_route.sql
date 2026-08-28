-- Persist immutable route snapshots planned for transport tasks.
-- route_points is a JSON array stored in LONGTEXT for compatibility across the
-- MySQL versions used by existing deployments. The Java type handler validates
-- and maps it as [[longitude, latitude], ...].

CREATE TABLE transport_task_route (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    route_id VARCHAR(64) NOT NULL
        COMMENT 'Stable route identifier exposed through the REST API',
    task_id BIGINT NOT NULL,
    provider VARCHAR(20) NOT NULL,
    coordinate_system VARCHAR(20) NOT NULL,
    route_points LONGTEXT NOT NULL
        COMMENT 'JSON array: [[longitude, latitude], ...]',
    distance_meters BIGINT NOT NULL,
    duration_seconds BIGINT NOT NULL,
    route_version INT NOT NULL,
    status VARCHAR(20) NOT NULL
        COMMENT 'READY / ACTIVE / INACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uk_transport_task_route_id UNIQUE (route_id),
    CONSTRAINT uk_transport_task_route_version UNIQUE (task_id, route_version),
    CONSTRAINT fk_transport_task_route_task
        FOREIGN KEY (task_id) REFERENCES transport_task(id),
    INDEX idx_transport_task_route_task_status (task_id, status)
);
