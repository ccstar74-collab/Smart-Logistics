package com.smart_logistics.backend.handler;

import com.smart_logistics.backend.dto.RealTimeGpsDTO;
import com.smart_logistics.backend.dto.realtime.EtaRealtimeMessage;
import com.smart_logistics.backend.security.WsSessionAttributes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WebSocket对外推送消息的序列化契约测试
 * ETA消息的OffsetDateTime字段必须输出ISO-8601字符串；
 * GPS消息字段名和类型保持既有对外契约，不允许回归；
 * 推送必须按会话预计算的车辆可见范围过滤，无范围会话一律拒发
 */
@ExtendWith(MockitoExtension.class)
class GpsWebSocketHandlerSerializationTest {

    @Mock
    private WebSocketSession session;

    // Jackson 3默认配置即原生支持java.time并输出ISO-8601，与Spring Boot自动配置的mapper行为一致
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private GpsWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GpsWebSocketHandler(objectMapper);
        when(session.isOpen()).thenReturn(true);
        // 默认用管理员范围会话验证序列化契约，过滤行为由专门用例覆盖
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(WsSessionAttributes.ALLOW_ALL_VEHICLES, Boolean.TRUE);
        when(session.getAttributes()).thenReturn(attributes);
        handler.afterConnectionEstablished(session);
    }

    @AfterEach
    void tearDown() {
        handler.afterConnectionClosed(session, CloseStatus.NORMAL);
    }

    @Test
    void broadcastEtaWritesOffsetDateTimeFieldsAsIso8601Strings() throws Exception {
        OffsetDateTime estimatedArrivalTime = OffsetDateTime.parse("2026-08-26T16:30:00+08:00");
        OffsetDateTime etaCalculatedAt = OffsetDateTime.parse("2026-08-26T16:00:00+08:00");
        EtaRealtimeMessage message = new EtaRealtimeMessage(
                1L, "real_001", estimatedArrivalTime, etaCalculatedAt, 5500, 32.5);

        handler.broadcastEta(message);

        JsonNode root = objectMapper.readTree(sentPayload());
        assertEquals("ETA_UPDATED", root.get("type").asText());
        assertEquals(1L, root.get("taskId").asLong());
        assertEquals("real_001", root.get("vehicleId").asText());
        assertTrue(root.get("estimatedArrivalTime").isTextual(),
                "estimatedArrivalTime必须是ISO-8601字符串，不能是时间戳数字");
        assertEquals("2026-08-26T16:30:00+08:00", root.get("estimatedArrivalTime").asText());
        assertTrue(root.get("etaCalculatedAt").isTextual(),
                "etaCalculatedAt必须是ISO-8601字符串，不能是时间戳数字");
        assertEquals("2026-08-26T16:00:00+08:00", root.get("etaCalculatedAt").asText());
        assertEquals(5500L, root.get("remainingDistanceMeters").asLong());
        assertEquals(32.5, root.get("effectiveSpeedKmh").asDouble());
    }

    @Test
    void broadcastGpsKeepsExistingFieldContract() throws Exception {
        RealTimeGpsDTO dto = new RealTimeGpsDTO();
        dto.setVehicleId("real_001");
        dto.setLon(106.58);
        dto.setLat(29.50);
        dto.setSpeed(30.0);
        dto.setHeading(90.0);
        dto.setTimestamp(1756195200000L);

        handler.broadcastGps(dto);

        JsonNode root = objectMapper.readTree(sentPayload());
        assertEquals("real_001", root.get("vehicleId").asText());
        assertEquals(106.58, root.get("lon").asDouble());
        assertEquals(29.50, root.get("lat").asDouble());
        assertEquals(30.0, root.get("speed").asDouble());
        assertEquals(90.0, root.get("heading").asDouble());
        assertEquals(1756195200000L, root.get("timestamp").asLong());
    }

    @Test
    void etaMessageIsSkippedForSessionWithoutVehicleInScope() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(WsSessionAttributes.ALLOWED_VEHICLE_SIM_CODES,
                Set.of("other_sim_002"));
        when(session.getAttributes()).thenReturn(attributes);

        handler.broadcastEta(etaMessage());

        verify(session, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    void etaMessageIsSentForSessionContainingVehicleInScope() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(WsSessionAttributes.ALLOWED_VEHICLE_SIM_CODES,
                Set.of("real_001"));
        when(session.getAttributes()).thenReturn(attributes);

        handler.broadcastEta(etaMessage());

        verify(session).sendMessage(any(TextMessage.class));
    }

    @Test
    void broadcastIsRejectedWhenSessionHasNoScopeAttributes() throws Exception {
        when(session.getAttributes()).thenReturn(new HashMap<>());

        handler.broadcastEta(etaMessage());
        handler.broadcastGps(gpsDto());

        verify(session, never()).sendMessage(any(TextMessage.class));
    }

    private EtaRealtimeMessage etaMessage() {
        return new EtaRealtimeMessage(
                1L, "real_001",
                OffsetDateTime.parse("2026-08-26T16:30:00+08:00"),
                OffsetDateTime.parse("2026-08-26T16:00:00+08:00"),
                5500, 32.5);
    }

    private RealTimeGpsDTO gpsDto() {
        RealTimeGpsDTO dto = new RealTimeGpsDTO();
        dto.setVehicleId("real_001");
        dto.setLon(106.58);
        dto.setLat(29.50);
        dto.setSpeed(30.0);
        dto.setHeading(90.0);
        dto.setTimestamp(1756195200000L);
        return dto;
    }

    private String sentPayload() throws Exception {
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        return captor.getValue().getPayload();
    }
}
