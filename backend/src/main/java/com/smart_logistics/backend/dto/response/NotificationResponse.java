package com.smart_logistics.backend.dto.response;

import com.smart_logistics.backend.enums.NotificationLevel;
import com.smart_logistics.backend.enums.NotificationType;

import java.time.OffsetDateTime;

/**
 * 消息中心通知响应（与docs/notification.md契约一致）。
 * businessId统一为字符串；前端点击后统一执行router.push(targetPath)，
 * 不需要自行维护type到页面的映射。
 */
public record NotificationResponse(
        Long id,
        NotificationType type,
        String title,
        String content,
        NotificationLevel level,
        boolean read,
        OffsetDateTime createdAt,
        String businessType,
        String businessId,
        Long taskId,
        String targetPath) {
}
