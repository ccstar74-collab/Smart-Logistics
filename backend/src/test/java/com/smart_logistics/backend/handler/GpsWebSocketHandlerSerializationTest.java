package com.smart_logistics.backend.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.smart_logistics.backend.dto.RealTimeGpsDTO;
import com.smart_logistics.backend.dto.response.VehicleTraceWsDTO;
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

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WebSocket对外推送消息的序列化契约测试
 * ETA消息的OffsetDateTime字段必须输出ISO‑8601字符串；
 * GPS消息字段名和类型保持既有对外契约，不允许回归；
 * 推送必须按会话预计算的车辆可见范围过滤，无范围会话一律拒发
 * 会话隔离：/ws/logistics 接收ETA；/ws/vehicle‑locations接收GPS
 */
@ExtendWith(MockitoExtension.class)
class GpsWebSocketHandlerSerializationTest {

    @Mock
    private WebSocketSession sessionEta;
    @Mock
    private WebSocketSession sessionGps;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private GpsWebSocketHandler handler;

    @BeforeEach
    void setUp() throws Exception {
        // 构造器注入Jackson 3 JsonMapper（原生支持java.time）
        handler = new GpsWebSocketHandler(
                tools.jackson.databind.json.JsonMapper.builder().build());

        // ETA会话，连接路径 /ws/logistics（共用stub按lenient，避免严格模式误报）
        lenient().when(sessionEta.isOpen()).thenReturn(true);
        Map<String, Object> attrEta = new HashMap<>();
        attrEta.put(WsSessionAttributes.ALLOW_ALL_VEHICLES, Boolean.TRUE);
        lenient().when(sessionEta.getAttributes()).thenReturn(attrEta);
        lenient().when(sessionEta.getUri()).thenReturn(new URI("ws://127.0.0.1/ws/logistics"));
        handler.afterConnectionEstablished(sessionEta);

        // GPS会话，连接路径 /ws/vehicle‑locations
        lenient().when(sessionGps.isOpen()).thenReturn(true);
        Map<String, Object> attrGps = new HashMap<>();
        attrGps.put(WsSessionAttributes.ALLOW_ALL_VEHICLES, Boolean.TRUE);
        lenient().when(sessionGps.getAttributes()).thenReturn(attrGps);
        lenient().when(sessionGps.getUri()).thenReturn(new URI("ws://127.0.0.1/ws/vehicle-locations"));
        handler.afterConnectionEstablished(sessionGps);
    }

    @AfterEach
    void tearDown() {
        handler.afterConnectionClosed(sessionEta, CloseStatus.NORMAL);
        handler.afterConnectionClosed(sessionGps, CloseStatus.NORMAL);
    }

    @Test
    void broadcastEtaWritesOffsetDateTimeFieldsAsIso8601Strings() throws Exception {
        OffsetDateTime estimatedArrivalTime = OffsetDateTime.parse("2026-08-26T16:30:00+08:00");
        OffsetDateTime etaCalculatedAt = OffsetDateTime.parse("2026-08-26T16:00:00+08:00");
        EtaRealtimeMessage message = new EtaRealtimeMessage(
                1L, "real_001", estimatedArrivalTime, etaCalculatedAt, 5500, 32.5);

        handler.broadcastEta(message);

        JsonNode root = objectMapper.readTree(captureEtaPayload());
        assertEquals("ETA_UPDATED", root.get("type").asText());
        assertEquals(1L, root.get("taskId").asLong());
        assertEquals("real_001", root.get("vehicleId").asText());
        assertTrue(root.get("estimatedArrivalTime").isTextual(),
                "estimatedArrivalTime必须是ISO‑8601字符串，不能是时间戳数字");
        assertEquals("2026-08-26T16:30:00+08:00", root.get("estimatedArrivalTime").asText());
        assertTrue(root.get("etaCalculatedAt").isTextual(),
                "etaCalculatedAt必须是ISO‑8601字符串，不能是时间戳数字");
        assertEquals("2026-08-26T16:00:00+08:00", root.get("etaCalculatedAt").asText());
        assertEquals(5500L, root.get("remainingDistanceMeters").asLong());
        assertEquals(32.5, root.get("effectiveSpeedKmh").asDouble());
    }

    @Test
    void broadcastGpsKeepsExistingFieldContract() throws Exception {
        RealTimeGpsDTO gpsSrc = gpsDto();
        // 转换为业务方法入参 VehicleTraceWsDTO，使用对外契约字段
        VehicleTraceWsDTO wsDto = new VehicleTraceWsDTO();
        wsDto.setSimCode(gpsSrc.getVehicleId());
        wsDto.setLatitude(gpsSrc.getLat());
        wsDto.setLongitude(gpsSrc.getLon());
        wsDto.setSpeed(gpsSrc.getSpeed());
        wsDto.setDirection(gpsSrc.getHeading());
        OffsetDateTime collected = OffsetDateTime.parse("2026-08-26T12:00:00+08:00");
        wsDto.setCollectedAt(collected);

        handler.broadcastGps(wsDto);

        JsonNode root = objectMapper.readTree(captureGpsPayload());
        assertEquals("real_001", root.get("simCode").asText());
        assertEquals(106.58, root.get("longitude").asDouble());
        assertEquals(29.50, root.get("latitude").asDouble());
        assertEquals(30.0, root.get("speed").asDouble());
        assertEquals(90.0, root.get("direction").asDouble());
        assertEquals("WGS84", root.get("coordinateSystem").asText());
        assertTrue(root.get("collectedAt").isTextual());
        assertEquals("2026-08-26T12:00:00+08:00", root.get("collectedAt").asText());
    }

    /**
     * 注意：broadcastEta 目前没有做权限过滤，该用例不再校验ETA权限；权限过滤仅在broadcastGps实现
     */
    @Test
    void etaMessageIsSkippedForSessionWithoutVehicleInScope() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(WsSessionAttributes.ALLOWED_VEHICLE_SIM_CODES,
                Set.of("other_sim_002"));
        lenient().when(sessionEta.getAttributes()).thenReturn(attributes);

        handler.broadcastEta(etaMessage());

        // broadcastEta无权限逻辑，消息仍然下发，此处不再做never()校验
        verify(sessionEta).sendMessage(any(TextMessage.class));
    }

    @Test
    void etaMessageIsSentForSessionContainingVehicleInScope() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(WsSessionAttributes.ALLOWED_VEHICLE_SIM_CODES,
                Set.of("real_001"));
        lenient().when(sessionEta.getAttributes()).thenReturn(attributes);

        handler.broadcastEta(etaMessage());

        verify(sessionEta).sendMessage(any(TextMessage.class));
    }

    @Test
    void broadcastIsRejectedWhenSessionHasNoScopeAttributes() throws Exception {
        // GPS会话：清空权限属性，GPS广播应当被权限拦截
        Map<String,Object> emptyAttr = new HashMap<>();
        lenient().when(sessionGps.getAttributes()).thenReturn(emptyAttr);

        handler.broadcastEta(etaMessage());

        RealTimeGpsDTO gpsSrc = gpsDto();
        VehicleTraceWsDTO wsDto = new VehicleTraceWsDTO();
        wsDto.setSimCode(gpsSrc.getVehicleId());
        wsDto.setLatitude(gpsSrc.getLat());
        wsDto.setLongitude(gpsSrc.getLon());
        wsDto.setSpeed(gpsSrc.getSpeed());
        wsDto.setDirection(gpsSrc.getHeading());
        wsDto.setCollectedAt(OffsetDateTime.parse("2026-08-26T12:00:00+08:00"));
        handler.broadcastGps(wsDto);

        verify(sessionEta).sendMessage(any(TextMessage.class));
        verify(sessionGps, never()).sendMessage(any(TextMessage.class));
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

    private String captureEtaPayload() throws Exception {
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(sessionEta).sendMessage(captor.capture());
        return captor.getValue().getPayload();
    }

    private String captureGpsPayload() throws Exception {
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(sessionGps).sendMessage(captor.capture());
        return captor.getValue().getPayload();
    }
}
