package com.smart_logistics.backend.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart_logistics.backend.dto.RealTimeGpsDTO;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.concurrent.CopyOnWriteArraySet;

@Component
public class GpsWebSocketHandler extends TextWebSocketHandler {

    private static final CopyOnWriteArraySet<WebSocketSession> SESSION_SET = new CopyOnWriteArraySet<>();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        SESSION_SET.add(session);
        System.out.println("WebSocket客户端接入，在线数量：" + SESSION_SET.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        SESSION_SET.remove(session);
        System.out.println("WebSocket客户端断开，在线数量：" + SESSION_SET.size());
    }

    public static void broadcastGps(RealTimeGpsDTO dto) {
        String json;
        try {
            json = OBJECT_MAPPER.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return;
        }
        TextMessage textMessage = new TextMessage(json);
        for (WebSocketSession session : SESSION_SET) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(textMessage);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}