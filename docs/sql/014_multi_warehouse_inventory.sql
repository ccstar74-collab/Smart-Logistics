CREATE TABLE cargo_type (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    unit VARCHAR(20) NULL,
    unit_weight DECIMAL(10,2) NULL,
    unit_volume DECIMAL(10,2) NULL,
    description VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_cargo_type_name (name)
);

CREATE TABLE warehouse (
    id BIGINT NOT NULL AUTO_INCREMENT,
    warehouse_no VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    address VARCHAR(255) NOT NULL,
    longitude DECIMAL(10,7) NOT NULL,
    latitude DECIMAL(10,7) NOT NULL,
    contact_name VARCHAR(50) NULL,
    contact_phone VARCHAR(20) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_warehouse_no (warehouse_no)
);

ALTER TABLE cargo
    ADD COLUMN cargo_type_id BIGINT NULL AFTER volume,
    ADD COLUMN warehouse_id BIGINT NULL AFTER cargo_type_id;

ALTER TABLE vehicle
    ADD COLUMN warehouse_id BIGINT NULL AFTER driver_id;

ALTER TABLE transport_task
    ADD COLUMN origin_warehouse_id BIGINT NULL AFTER vehicle_id;

ALTER TABLE cargo
    ADD CONSTRAINT fk_cargo_cargo_type
        FOREIGN KEY (cargo_type_id) REFERENCES cargo_type(id),
    ADD CONSTRAINT fk_cargo_warehouse
        FOREIGN KEY (warehouse_id) REFERENCES warehouse(id),
    ADD INDEX idx_cargo_type_warehouse_status
        (cargo_type_id, warehouse_id, status),
    ADD INDEX idx_cargo_warehouse_id (warehouse_id);

ALTER TABLE vehicle
    ADD CONSTRAINT fk_vehicle_warehouse
        FOREIGN KEY (warehouse_id) REFERENCES warehouse(id),
    ADD INDEX idx_vehicle_warehouse_status (warehouse_id, status);

ALTER TABLE transport_task
    ADD CONSTRAINT fk_task_origin_warehouse
        FOREIGN KEY (origin_warehouse_id) REFERENCES warehouse(id),
    ADD INDEX idx_task_origin_warehouse (origin_warehouse_id);
