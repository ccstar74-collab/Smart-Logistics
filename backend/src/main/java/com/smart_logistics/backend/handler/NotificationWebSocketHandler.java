package com.smart_logistics.backend.handler;

import com.smart_logistics.backend.dto.realtime.NotificationWsEvent;
import com.smart_logistics.backend.dto.realtime.NotificationWsMessage;
import com.smart_logistics.backend.entity.Notification;
import com.smart_logistics.backend.security.WsSessionAttributes;
import com.smart_logistics.backend.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /ws/notifications用户级消息提醒处理器。
 * 只负责推送已持久化的Notification：监听通知落库事务提交后发布的
 * NotificationWsEvent，按会话绑定的userId精确推送（同一用户多设备都推），
 * 不按角色广播，不实现任何通知业务逻辑。
 * 断线补偿由前端重连后调用GET /api/v1/notifications完成。
 */
@Slf4j
@Component
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    private static final String NOTIFICATIONS_PATH = "/ws/notifications";
    private static final String EVENT_NOTIFICATION_CREATED = "NOTIFICATION_CREATED";

    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    /** userId -> 该用户全部在线会话，兼容同一用户多设备登录 */
    private final Map<Long, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();

    public NotificationWebSocketHandler(ObjectMapper objectMapper,
                                        NotificationService notificationService) {
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (session.getUri() == null
                || !NOTIFICATIONS_PATH.equals(session.getUri().getPath())) {
            log.warn("未知websocket路径 sessionId={}, uri={}", session.getId(), session.getUri());
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        Long userId = (Long) session.getAttributes().get(WsSessionAttributes.USER_ID);
        if (userId == null) {
            // 握手拦截器未写入userId属于异常链路，直接拒绝
            log.warn("通知会话缺少userId，拒绝连接 sessionId={}", session.getId());
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        userSessions.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet())
                .add(session);
        log.info("通知会话建立 sessionId={}, userId={}", session.getId(), userId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = (Long) session.getAttributes().get(WsSessionAttributes.USER_ID);
        if (userId != null) {
            Set<WebSocketSession> sessions = userSessions.get(userId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    userSessions.remove(userId, sessions);
                }
            }
        }
        log.info("通知会话关闭 sessionId={}, status={}", session.getId(), status);
    }

    /**
     * 前端心跳：与/ws/alarms保持一致，收到"ping"立即回"pong"，
     * 约25秒一次，避免静置时被反向代理按空闲超时掐断。
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        if (!"ping".equals(message.getPayload())) {
            // 非心跳文本帧不进入任何业务逻辑，直接忽略
            return;
        }
        try {
            synchronized (session) {
                if (!session.isOpen()) {
                    return;
                }
                session.sendMessage(new TextMessage("pong"));
            }
        } catch (IOException e) {
            log.error("pong回写失败 sessionId={}", session.getId(), e);
        }
    }

    /**
     * 通知落库事务提交后推送：前端收到的通知一定已持久化；
     * 只推给该通知的接收用户，推送丢失时前端可通过REST补偿。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true)
    public void onNotificationEvent(NotificationWsEvent event) {
        Notification notification = notificationService.findForPush(event.notificationId());
        if (notification == null) {
            log.warn("通知推送跳过，通知不存在 notificationId={}", event.notificationId());
            return;
        }
        Set<WebSocketSession> sessions = userSessions.get(notification.getReceiverUserId());
        if (sessions == null || sessions.isEmpty()) {
            // 接收人不在线：离线补偿交给GET /api/v1/notifications
            return;
        }
        NotificationWsMessage message = new NotificationWsMessage(
                EVENT_NOTIFICATION_CREATED, notificationService.toResponse(notification));
        String json = serialize(message);
        if (json == null) {
            return;
        }
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                continue;
            }
            send(session, json);
        }
    }

    private String serialize(NotificationWsMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JacksonException e) {
            log.error("通知推送序列化失败 notificationId={}",
                    message.notification() == null ? null : message.notification().id(), e);
            return null;
        }
    }

    private void send(WebSocketSession session, String json) {
        try {
            // WebSocketSession并发发送不安全，与告警推送路径保持一致加锁
            synchronized (session) {
                if (!session.isOpen()) {
                    return;
                }
                session.sendMessage(new TextMessage(json));
            }
        } catch (IOException e) {
            log.error("通知推送发送失败 sessionId={}", session.getId(), e);
        }
    }
}
