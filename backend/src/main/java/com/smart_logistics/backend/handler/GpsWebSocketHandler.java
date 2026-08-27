package com.smart_logistics.backend.handler;

import com.smart_logistics.backend.dto.RealTimeGpsDTO;
import com.smart_logistics.backend.dto.realtime.EtaRealtimeMessage;
import com.smart_logistics.backend.dto.response.VehicleTraceWsDTO;
import com.smart_logistics.backend.security.WsSessionAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
public class GpsWebSocketHandler extends TextWebSocketHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GpsWebSocketHandler.class);
    private static final CopyOnWriteArraySet<WebSocketSession> SESSION_SET = new CopyOnWriteArraySet<>();
    // 注入Spring Boot自动配置的ObjectMapper（Jackson 3），原生支持java.time并输出ISO-8601，
    // 禁止自建ObjectMapper：Jackson 2的com.fasterxml实例无法序列化OffsetDateTime
    private final ObjectMapper objectMapper;

    public GpsWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        SESSION_SET.add(session);
        LOGGER.info("WebSocket客户端接入，在线数量：{}", SESSION_SET.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        SESSION_SET.remove(session);
        LOGGER.info("WebSocket客户端断开，在线数量：{}", SESSION_SET.size());
    }

    public void broadcastGps(RealTimeGpsDTO dto) {
        broadcast(dto);
    }

    public void broadcastGps(VehicleTraceWsDTO dto) {
        broadcast(dto);
    }

    public void broadcastEta(EtaRealtimeMessage message) {
        broadcast(message);
    }

    private void broadcast(Object payload) {
        String vehicleId = vehicleIdOf(payload);
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JacksonException e) {
            LOGGER.error("WebSocket消息序列化失败", e);
            return;
        }
        TextMessage textMessage = new TextMessage(json);
        for (WebSocketSession session : SESSION_SET) {
            try {
                // 按握手阶段预计算的车辆可见范围过滤，未携带范围的会话一律拒绝
                if (session.isOpen() && canViewVehicle(session, vehicleId)) {
                    synchronized (session) {
                        session.sendMessage(textMessage);
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("WebSocket消息发送失败 sessionId={}", session.getId(), e);
            }
        }
    }

    private boolean canViewVehicle(WebSocketSession session, String vehicleId) {
        Map<String, Object> attributes = session.getAttributes();
        if (attributes == null) {
            return false;
        }
        if (Boolean.TRUE.equals(attributes.get(WsSessionAttributes.ALLOW_ALL_VEHICLES))) {
            return true;
        }
        Object scope = attributes.get(WsSessionAttributes.ALLOWED_VEHICLE_SIM_CODES);
        return scope instanceof Set<?> allowedSimCodes
                && vehicleId != null
                && allowedSimCodes.contains(vehicleId);
    }

    private String vehicleIdOf(Object payload) {
        if (payload instanceof RealTimeGpsDTO dto) {
            return dto.getVehicleId();
        }
        if (payload instanceof VehicleTraceWsDTO dto) {
            return dto.getVehicleId();
        }
        if (payload instanceof EtaRealtimeMessage message) {
            return message.vehicleId();
        }
        return null;
    }
}
