package com.smart_logistics.backend.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart_logistics.backend.dto.RealTimeGpsDTO;
import com.smart_logistics.backend.dto.realtime.EtaRealtimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.concurrent.CopyOnWriteArraySet;

@Component
public class GpsWebSocketHandler extends TextWebSocketHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GpsWebSocketHandler.class);
    private static final CopyOnWriteArraySet<WebSocketSession> SESSION_SET = new CopyOnWriteArraySet<>();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

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

    public void broadcastEta(EtaRealtimeMessage message) {
        broadcast(message);
    }

    private void broadcast(Object payload) {
        String json;
        try {
            json = OBJECT_MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            LOGGER.error("WebSocket消息序列化失败", e);
            return;
        }
        TextMessage textMessage = new TextMessage(json);
        for (WebSocketSession session : SESSION_SET) {
            try {
                if (session.isOpen()) {
                    synchronized (session) {
                        session.sendMessage(textMessage);
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("WebSocket消息发送失败 sessionId={}", session.getId(), e);
            }
        }
    }
}
