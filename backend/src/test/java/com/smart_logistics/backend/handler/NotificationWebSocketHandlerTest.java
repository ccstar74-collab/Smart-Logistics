package com.smart_logistics.backend.handler;

import com.smart_logistics.backend.dto.realtime.NotificationWsEvent;
import com.smart_logistics.backend.entity.Notification;
import com.smart_logistics.backend.enums.NotificationLevel;
import com.smart_logistics.backend.enums.NotificationType;
import com.smart_logistics.backend.security.WsSessionAttributes;
import com.smart_logistics.backend.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * /ws/notifications按userId精确推送：只推给通知的接收用户，
 * 同一用户多设备都推，其他用户不推；心跳复用ping/pong。
 */
@ExtendWith(MockitoExtension.class)
class NotificationWebSocketHandlerTest {

    @Mock private NotificationService notificationService;

    private NotificationWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        handler = new NotificationWebSocketHandler(new ObjectMapper(), notificationService);
    }

    @Test
    void notificationIsPushedOnlyToReceiverUser() throws Exception {
        WebSocketSession receiver = openSession(1L);
        WebSocketSession other = openSession(2L);
        when(notificationService.findForPush(1001L))
                .thenReturn(notification(1001L, 1L));
        when(notificationService.toResponse(any(Notification.class)))
                .thenAnswer(invocation -> new com.smart_logistics.backend.dto.response
                        .NotificationResponse(
                        1001L, NotificationType.ALARM_CREATED, "新告警通知",
                        "车辆连续偏离规划路线", NotificationLevel.WARNING, false,
                        null, "ALARM", "101", 30L, "/alarms?alarmId=101"));

        handler.onNotificationEvent(new NotificationWsEvent(1001L));

        TextMessage message = captureSingle(receiver);
        assertTrue(message.getPayload().contains("\"event\":\"NOTIFICATION_CREATED\""));
        assertTrue(message.getPayload().contains("\"id\":1001"));
        verify(other, never()).sendMessage(any());
    }

    @Test
    void multipleDevicesOfSameUserAllReceivePush() throws Exception {
        WebSocketSession first = openSession(1L);
        WebSocketSession second = openSession(1L);
        when(notificationService.findForPush(1001L))
                .thenReturn(notification(1001L, 1L));
        when(notificationService.toResponse(any(Notification.class)))
                .thenAnswer(invocation -> new com.smart_logistics.backend.dto.response
                        .NotificationResponse(
                        1001L, NotificationType.ALARM_CREATED, "新告警通知",
                        "内容", NotificationLevel.WARNING, false,
                        null, "ALARM", "101", 30L, "/alarms?alarmId=101"));

        handler.onNotificationEvent(new NotificationWsEvent(1001L));

        captureSingle(first);
        captureSingle(second);
    }

    @Test
    void offlineReceiverGetsNoPushAndRestCompensates() throws Exception {
        when(notificationService.findForPush(1001L))
                .thenReturn(notification(1001L, 9L));

        handler.onNotificationEvent(new NotificationWsEvent(1001L));

        verify(notificationService, never()).toResponse(any(Notification.class));
    }

    @Test
    void missingNotificationSkipsPush() throws Exception {
        WebSocketSession receiver = openSession(1L);
        when(notificationService.findForPush(999L)).thenReturn(null);

        handler.onNotificationEvent(new NotificationWsEvent(999L));

        verify(receiver, never()).sendMessage(any());
    }

    @Test
    void closedSessionIsRemovedAndNotPushed() throws Exception {
        WebSocketSession receiver = openSession(1L);
        handler.afterConnectionClosed(receiver, CloseStatus.NORMAL);
        lenient().when(notificationService.findForPush(1001L))
                .thenReturn(notification(1001L, 1L));

        handler.onNotificationEvent(new NotificationWsEvent(1001L));

        verify(receiver, never()).sendMessage(any());
        verify(notificationService, never()).toResponse(any(Notification.class));
    }

    @Test
    void pingTextIsAnsweredWithPong() throws Exception {
        WebSocketSession session = openSession(1L);

        handler.handleTextMessage(session, new TextMessage("ping"));

        assertEquals("pong", captureSingle(session).getPayload());
    }

    @Test
    void nonPingTextFrameIsIgnored() throws Exception {
        WebSocketSession session = openSession(1L);

        handler.handleTextMessage(session, new TextMessage("hello"));

        verify(session, never()).sendMessage(any());
    }

    @Test
    void sessionWithoutUserIdIsRejected() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        lenient().when(session.getAttributes()).thenReturn(attributes);
        lenient().when(session.getUri())
                .thenReturn(URI.create("ws://localhost/ws/notifications"));
        lenient().when(session.getId()).thenReturn("no-user");

        handler.afterConnectionEstablished(session);

        verify(session, times(1)).close(CloseStatus.POLICY_VIOLATION);
    }

    private TextMessage captureSingle(WebSocketSession session) throws Exception {
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, times(1)).sendMessage(captor.capture());
        return captor.getValue();
    }

    private WebSocketSession openSession(Long userId) throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(WsSessionAttributes.USER_ID, userId);
        lenient().when(session.getAttributes()).thenReturn(attributes);
        lenient().when(session.isOpen()).thenReturn(true);
        lenient().when(session.getUri())
                .thenReturn(URI.create("ws://localhost/ws/notifications"));
        lenient().when(session.getId()).thenReturn("session-" + userId + "-" + session.hashCode());
        handler.afterConnectionEstablished(session);
        return session;
    }

    private Notification notification(Long id, Long receiverUserId) {
        Notification notification = new Notification();
        notification.setId(id);
        notification.setReceiverUserId(receiverUserId);
        notification.setType(NotificationType.ALARM_CREATED.name());
        notification.setTitle("新告警通知");
        notification.setContent("车辆连续偏离规划路线");
        notification.setLevel(NotificationLevel.WARNING.name());
        notification.setIsRead(false);
        notification.setBusinessType("ALARM");
        notification.setBusinessId("101");
        notification.setTaskId(30L);
        notification.setTargetPath("/alarms?alarmId=101");
        notification.setCreatedAt(LocalDateTime.of(2026, 8, 30, 14, 30));
        return notification;
    }
}
