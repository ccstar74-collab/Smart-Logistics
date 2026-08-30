package com.smart_logistics.backend.controller;

import com.smart_logistics.backend.common.ApiResponse;
import com.smart_logistics.backend.common.PageResult;
import com.smart_logistics.backend.dto.response.NotificationResponse;
import com.smart_logistics.backend.dto.response.NotificationUnreadCountResponse;
import com.smart_logistics.backend.enums.NotificationType;
import com.smart_logistics.backend.service.NotificationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消息中心接口（见docs/notification.md）。
 * 前端不传userId；后端从JWT获取当前用户，只返回其本人的通知。
 * 五种角色都可能收到通知（货主/司机/调度员/管理员/仓库管理员），
 * 仓库通知待多仓库阶段接入后自然生效。
 */
@Validated
@RestController
@RequestMapping("/api/v1/notifications")
@PreAuthorize("hasAnyRole('OWNER','DRIVER','WAREHOUSE_MANAGER','DISPATCHER','ADMIN')")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ApiResponse<PageResult<NotificationResponse>> listNotifications(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) Boolean read,
            @RequestParam(required = false) NotificationType type) {
        return ApiResponse.success(
                notificationService.listMyNotifications(page, pageSize, read, type));
    }

    @GetMapping("/unread-count")
    public ApiResponse<NotificationUnreadCountResponse> unreadCount() {
        return ApiResponse.success(
                new NotificationUnreadCountResponse(notificationService.countMyUnread()));
    }

    @PutMapping("/{id}/read")
    public ApiResponse<NotificationResponse> markRead(@PathVariable @Positive Long id) {
        return ApiResponse.success(notificationService.markRead(id));
    }

    @PutMapping("/read-all")
    public ApiResponse<Long> markAllRead() {
        return ApiResponse.success(notificationService.markAllRead());
    }
}
