-- Message center notifications (docs/notification.md).
-- Notification rows are the authoritative message-center data source;
-- business modules only produce "result reminders", never business truth.
-- uk_notification_dedup makes per-receiver delivery idempotent: the same
-- business event (type + business object) never lands twice for one user.

CREATE TABLE IF NOT EXISTS notification (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '通知主键，前端去重唯一键',
    receiver_user_id BIGINT NOT NULL COMMENT '最终接收用户，服务端权限过滤核心字段',
    type VARCHAR(64) NOT NULL COMMENT '通知类型，如ALARM_CREATED',
    title VARCHAR(128) NOT NULL COMMENT '简短标题',
    content VARCHAR(512) NOT NULL COMMENT '消息正文',
    level VARCHAR(16) NOT NULL COMMENT 'INFO/SUCCESS/WARNING/ERROR',
    is_read TINYINT(1) NOT NULL DEFAULT 0 COMMENT '未读/已读',
    read_at DATETIME NULL COMMENT '已读时间',
    business_type VARCHAR(32) NOT NULL COMMENT 'ALARM/DISPATCH_COMMAND/TRANSPORT_TASK等',
    business_id VARCHAR(64) NOT NULL COMMENT '对应业务对象ID',
    task_id BIGINT NULL COMMENT '可选，便于跨模块定位任务',
    target_path VARCHAR(255) NULL COMMENT '前端点击通知后的跳转地址',
    created_at DATETIME NOT NULL COMMENT '通知创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_notification_dedup
        (type, business_type, business_id, receiver_user_id),
    KEY idx_notification_receiver_read (receiver_user_id, is_read, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '消息中心通知';
