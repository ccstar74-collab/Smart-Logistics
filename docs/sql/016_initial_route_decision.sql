CREATE TABLE initial_route_decision (
    id BIGINT NOT NULL AUTO_INCREMENT,
    decision_id VARCHAR(64) NOT NULL,
    created_by BIGINT NOT NULL,
    origin_warehouse_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    planning_mode VARCHAR(40) NOT NULL,
    planning_result VARCHAR(40) NOT NULL,
    start_snapshot LONGTEXT NOT NULL,
    destination_snapshot LONGTEXT NOT NULL,
    recommended_route_id VARCHAR(80) NULL,
    selected_route_id VARCHAR(80) NULL,
    route_selection_remark VARCHAR(500) NULL,
    scoring_rule_version VARCHAR(50) NOT NULL,
    recommendation_source VARCHAR(40) NOT NULL,
    input_snapshot LONGTEXT NOT NULL,
    weather_snapshot LONGTEXT NOT NULL,
    explanation LONGTEXT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    confirmation_idempotency_key VARCHAR(128) NULL,
    calculated_at DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    confirmed_at DATETIME(3) NULL,
    task_id BIGINT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_initial_route_decision_id (decision_id),
    UNIQUE KEY uk_initial_route_decision_idempotency (idempotency_key),
    UNIQUE KEY uk_initial_route_decision_confirmation_key
        (confirmation_idempotency_key),
    UNIQUE KEY uk_initial_route_decision_task (task_id),
    KEY idx_initial_route_decision_creator_status
        (created_by, status, expires_at),
    KEY idx_initial_route_decision_warehouse
        (origin_warehouse_id, created_at)
);

CREATE TABLE initial_route_candidate (
    id BIGINT NOT NULL AUTO_INCREMENT,
    decision_id VARCHAR(64) NOT NULL,
    preview_route_id VARCHAR(80) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    provider VARCHAR(50) NOT NULL,
    coordinate_system VARCHAR(20) NOT NULL,
    distance_meters BIGINT NOT NULL,
    duration_seconds BIGINT NOT NULL,
    traffic_level VARCHAR(20) NOT NULL,
    traffic_snapshot LONGTEXT NOT NULL,
    weather_snapshot LONGTEXT NOT NULL,
    points LONGTEXT NOT NULL,
    rank_no INT NOT NULL,
    total_score DECIMAL(6,2) NOT NULL,
    score_details LONGTEXT NOT NULL,
    reasons LONGTEXT NOT NULL,
    created_at DATETIME(3) NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_initial_route_candidate_route
        (decision_id, preview_route_id),
    UNIQUE KEY uk_initial_route_candidate_rank
        (decision_id, rank_no),
    KEY idx_initial_route_candidate_decision (decision_id),
    CONSTRAINT fk_initial_route_candidate_decision
        FOREIGN KEY (decision_id)
        REFERENCES initial_route_decision (decision_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
);
