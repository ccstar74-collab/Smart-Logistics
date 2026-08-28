package com.smart_logistics.backend.handler;

import com.smart_logistics.backend.dto.realtime.AlarmWsEvent;
import com.smart_logistics.backend.dto.response.AlarmResponse;
import com.smart_logistics.backend.enums.AlarmConditionStatus;
import com.smart_logistics.backend.enums.AlarmLevel;
import com.smart_logistics.backend.enums.AlarmStatus;
import com.smart_logistics.backend.enums.AlarmType;
import com.smart_logistics.backend.enums.UserRole;
import com.smart_logistics.backend.security.BusinessDataScopeService;
import com.smart_logistics.backend.security.WsSessionAttributes;
import com.smart_logistics.backend.service.AlarmService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * /ws/alarms推送过滤：ADMIN/DISPATCHER收全部；
 * OWNER只收本人任务告警（设备级告警不推）；其他角色不推送
 */
@ExtendWith(MockitoExtension.class)
class AlarmWebSocketHandlerTest {

    @Mock private AlarmService alarmService;
    @Mock private BusinessDataScopeService dataScopeService;

    private AlarmWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AlarmWebSocketHandler(
                new ObjectMapper(), alarmService, dataScopeService);
    }

    @Test
    void adminAndDispatcherReceiveDeviceLevelAlarm() throws Exception {
        WebSocketSession admin = openSession(UserRole.ADMIN, null);
        WebSocketSession dispatcher = openSession(UserRole.DISPATCHER, null);
        when(alarmService.findResponse(42L)).thenReturn(alarm(42L, null));

        handler.onAlarmEvent(AlarmWsEvent.created(42L));

        TextMessage message = captureSingle(admin);
        assertTrue(message.getPayload().contains("\"event\":\"ALARM_CREATED\""));
        assertTrue(message.getPayload().contains("\"alarmId\":42"));
        captureSingle(dispatcher);
    }

    @Test
    void ownerReceivesOnlyOwnTaskAlarms() throws Exception {
        WebSocketSession owner = openSession(UserRole.OWNER, 3L);
        when(dataScopeService.taskIdsForOwner(3L)).thenReturn(List.of(30L));
        lenient().when(alarmService.findResponse(42L)).thenReturn(alarm(42L, 30L));
        lenient().when(alarmService.findResponse(43L)).thenReturn(alarm(43L, 31L));
        lenient().when(alarmService.findResponse(44L)).thenReturn(alarm(44L, null));

        handler.onAlarmEvent(AlarmWsEvent.updated(42L));
        handler.onAlarmEvent(AlarmWsEvent.updated(43L));
        handler.onAlarmEvent(AlarmWsEvent.created(44L));

        TextMessage message = captureSingle(owner);
        assertTrue(message.getPayload().contains("\"alarmId\":42"));
    }

    @Test
    void driverAndOtherRolesReceiveNothing() throws Exception {
        WebSocketSession driver = openSession(UserRole.DRIVER, null);
        when(alarmService.findResponse(42L)).thenReturn(alarm(42L, 30L));

        handler.onAlarmEvent(AlarmWsEvent.resolved(42L));

        verify(driver, never()).sendMessage(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void missingAlarmSkipsBroadcast() throws Exception {
        WebSocketSession admin = openSession(UserRole.ADMIN, null);
        when(alarmService.findResponse(99L)).thenReturn(null);

        handler.onAlarmEvent(AlarmWsEvent.created(99L));

        verify(admin, never()).sendMessage(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void closedSessionIsSkipped() throws Exception {
        WebSocketSession closed = openSession(UserRole.ADMIN, null);
        when(closed.isOpen()).thenReturn(false);
        lenient().when(alarmService.findResponse(anyLong())).thenReturn(alarm(42L, null));

        handler.onAlarmEvent(AlarmWsEvent.created(42L));

        verify(closed, never()).sendMessage(org.mockito.ArgumentMatchers.any());
    }

    private TextMessage captureSingle(WebSocketSession session) throws Exception {
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, times(1)).sendMessage(captor.capture());
        return captor.getValue();
    }

    private WebSocketSession openSession(UserRole role, Long ownerId) throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(WsSessionAttributes.USER_ROLE, role);
        if (ownerId != null) {
            attributes.put(WsSessionAttributes.OWNER_ID, ownerId);
        }
        lenient().when(session.getAttributes()).thenReturn(attributes);
        lenient().when(session.isOpen()).thenReturn(true);
        lenient().when(session.getUri()).thenReturn(URI.create("ws://localhost/ws/alarms"));
        lenient().when(session.getId()).thenReturn(role.name());
        handler.afterConnectionEstablished(session);
        return session;
    }

    private AlarmResponse alarm(Long id, Long taskId) {
        return new AlarmResponse(id, 23L, "渝A33333", taskId,
                taskId == null ? null : "T20260828001", "real_001",
                AlarmType.ROUTE_DEVIATION, AlarmLevel.HIGH, "车辆连续偏离规划路线",
                AlarmStatus.UNHANDLED, AlarmConditionStatus.ACTIVE, "backend",
                OffsetDateTime.parse("2026-08-28T10:00:00+08:00"), null, null, null,
                OffsetDateTime.parse("2026-08-28T10:00:01+08:00"), null, null,
                null, null, null);
    }
}
