CREATE TABLE `user` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    name VARCHAR(50),
    phone VARCHAR(20),
    role VARCHAR(20) NOT NULL COMMENT 'OWNER/DRIVER/DISPATCHER/ADMIN',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE owner (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL UNIQUE,
    company_name VARCHAR(100),
    contact_person VARCHAR(50),

    CONSTRAINT fk_owner_user
        FOREIGN KEY (user_id)
        REFERENCES `user`(id)
);

CREATE TABLE driver (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL UNIQUE,
    license_no VARCHAR(50) UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'OFFLINE'
        COMMENT 'ONLINE/OFFLINE',

    CONSTRAINT fk_driver_user
        FOREIGN KEY (user_id)
        REFERENCES `user`(id)
);

CREATE TABLE vehicle (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,

    plate_number VARCHAR(20) NOT NULL UNIQUE
        COMMENT '车牌号',

    type VARCHAR(50)
        COMMENT '车型',

    capacity DECIMAL(10,2)
        COMMENT '载重',

    status VARCHAR(20) NOT NULL DEFAULT 'IDLE'
        COMMENT 'IDLE / IN_PROGRESS / MAINTENANCE / DISABLED',

    driver_id BIGINT NULL
        COMMENT '当前司机',

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    last_longitude DECIMAL(10,7) NULL,
    last_latitude DECIMAL(10,7) NULL,
    last_updated_at DATETIME NULL,

    CONSTRAINT fk_vehicle_driver
        FOREIGN KEY (driver_id)
        REFERENCES driver(id)
        ON DELETE SET NULL
);

CREATE TABLE cargo (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,

    cargo_no VARCHAR(50) NOT NULL UNIQUE
        COMMENT '货物编号',

    name VARCHAR(100) NOT NULL,

    description VARCHAR(500),

    weight DECIMAL(10,2),

    volume DECIMAL(10,2),

    owner_id BIGINT NOT NULL,

    status VARCHAR(20) NOT NULL DEFAULT 'WAITING'
        COMMENT 'WAITING / ASSIGNED / IN_TRANSIT / DELIVERED / ABNORMAL',

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_cargo_owner
        FOREIGN KEY (owner_id)
        REFERENCES owner(id)
);

CREATE TABLE cargo_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,

    cargo_id BIGINT NOT NULL,

    item_name VARCHAR(100) NOT NULL,

    quantity INT NOT NULL DEFAULT 1,

    unit VARCHAR(20),

    weight DECIMAL(10,2),

    volume DECIMAL(10,2),

    CONSTRAINT fk_cargo_item_cargo
        FOREIGN KEY (cargo_id)
        REFERENCES cargo(id)
        ON DELETE CASCADE
);

CREATE TABLE transport_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,

    task_no VARCHAR(50) NOT NULL UNIQUE
        COMMENT '运输任务编号',

    cargo_id BIGINT NOT NULL,

    vehicle_id BIGINT NOT NULL,

    start_location VARCHAR(255) NOT NULL,

    end_location VARCHAR(255) NOT NULL,

    plan_start_time DATETIME,

    plan_end_time DATETIME,

    actual_start_time DATETIME,

    actual_end_time DATETIME,

    status VARCHAR(20) NOT NULL DEFAULT 'WAITING'
        COMMENT 'WAITING / IN_PROGRESS / COMPLETED / ABNORMAL / CANCELLED',

    estimated_arrival_time DATETIME NULL,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_task_cargo
        FOREIGN KEY (cargo_id)
        REFERENCES cargo(id),

    CONSTRAINT fk_task_vehicle
        FOREIGN KEY (vehicle_id)
        REFERENCES vehicle(id)
);