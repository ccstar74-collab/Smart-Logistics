package com.smart_logistics.backend.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.smart_logistics.backend.dto.response.VehicleTraceWsDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.concurrent.CopyOnWriteArraySet;

@Slf4j
@Component
public class GpsWebSocketHandler extends TextWebSocketHandler {

    private static final CopyOnWriteArraySet<WebSocketSession> SESSION_SET = new CopyOnWriteArraySet<>();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        SESSION_SET.add(session);
        log.info("WebSocket客户端接入，在线数量：{}", SESSION_SET.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        SESSION_SET.remove(session);
        log.info("WebSocket客户端断开，在线数量：{}", SESSION_SET.size());
    }

    public static void broadcastGps(VehicleTraceWsDTO dto) {
        String json;
        try {
            json = OBJECT_MAPPER.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            log.error("GPS对外DTO序列化为JSON失败", e);
            return;
        }
        for (WebSocketSession session : SESSION_SET) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(json));
                }
            } catch (Exception e) {
                log.warn("向WebSocket客户端发送GPS消息异常", e);
            }
        }
    }
}