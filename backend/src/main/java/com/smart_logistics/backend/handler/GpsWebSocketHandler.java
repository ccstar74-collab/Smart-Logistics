package com.smart_logistics.backend.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart_logistics.backend.dto.RealTimeGpsDTO;
import com.smart_logistics.backend.dto.response.VehicleTraceWsDTO;
import com.smart_logistics.backend.security.WsSessionAttributes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Component
public class GpsWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // /ws/logistics 会话集合：只用于ETA广播，交给其他同学维护
    private final CopyOnWriteArrayList<WebSocketSession> logisticsSessions = new CopyOnWriteArrayList<>();
    // /ws/vehicle-locations 会话集合：只用于GPS点位广播
    private final CopyOnWriteArrayList<WebSocketSession> vehicleLocationSessions = new CopyOnWriteArrayList<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (session.getUri() == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        String path = session.getUri().getPath();
        if ("/ws/logistics".equals(path)) {
            logisticsSessions.add(session);
            log.info("ETA会话建立 sessionId={}", session.getId());
        } else if ("/ws/vehicle-locations".equals(path)) {
            vehicleLocationSessions.add(session);
            log.info("GPS点位会话建立 sessionId={}", session.getId());
        } else {
            log.warn("未知websocket路径 path={}", path);
            session.close(CloseStatus.POLICY_VIOLATION);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        logisticsSessions.remove(session);
        vehicleLocationSessions.remove(session);
        log.info("websocket会话关闭 sessionId={}, status={}", session.getId(), status);
    }

    /**
     * GPS广播：只推送给 /ws/vehicle-locations 的会话，做权限过滤
     */
    public void broadcastGps(VehicleTraceWsDTO payload) {
        String matchKey = vehicleIdOf(payload);
        if (matchKey == null) {
            log.warn("GPS广播 matchKey为null，跳过广播");
            return;
        }
        for (WebSocketSession session : vehicleLocationSessions) {
            if (!session.isOpen()) {
                continue;
            }
            if (!canViewVehicle(session, matchKey)) {
                continue;
            }
            sendObject(session, payload);
        }
    }

    /**
     * ETA广播：留给其他同学维护，app.eta.enabled=false会关闭生成逻辑
     *      * ETA广播：留给其他同学维护，app.eta.enabled=false会关闭生成逻辑,删除这个分支
     *      * private String vehicleIdOf(Object payload) {
     *      *     if (payload instanceof RealTimeGpsDTO dto) {
     *      *         return dto.getVehicleId();
     *      *     }
     *      *     if (payload instanceof VehicleTraceWsDTO dto) {
     *      *         // 权限校验使用simCode，不要用数据库主键id
     *      *         return dto.getSimCode();
     *      *     }
     *      *     // EtaRealtimeMessage交给其他同学维护，此处移除依赖，避免编译报错
     *      *     return null;
     *      * }
     *
     */
    public void broadcastEta(Object message) {
        for (WebSocketSession session : logisticsSessions) {
            if (!session.isOpen()) {
                continue;
            }
            sendObject(session, message);
        }
    }

    private void sendObject(WebSocketSession session, Object obj) {
        try {
            String json = objectMapper.writeValueAsString(obj);
            session.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            log.error("websocket发送消息失败 sessionId={}", session.getId(), e);
        }
    }

    /**
     * 权限过滤：复用拦截器存入的WsSessionAttributes权限属性
     * @param session ws会话
     * @param targetSimCode 需要访问的车辆simCode
     * @return true有权限
     */
    @SuppressWarnings("unchecked")
    private boolean canViewVehicle(WebSocketSession session, String targetSimCode) {
        Boolean allowAll = (Boolean) session.getAttributes().get(WsSessionAttributes.ALLOW_ALL_VEHICLES);
        if (Boolean.TRUE.equals(allowAll)) {
            return true;
        }
        Set<String> allowedSimCodes = (Set<String>) session.getAttributes().get(WsSessionAttributes.ALLOWED_VEHICLE_SIM_CODES);
        if (allowedSimCodes == null || targetSimCode == null) {
            return false;
        }
        return allowedSimCodes.contains(targetSimCode);
    }

    /**
     * 获取权限比对key，GPS使用VehicleTraceWsDTO#simCode作为权限key；ETA部分交给其他同学维护
     */
    private String vehicleIdOf(Object payload) {
        if (payload instanceof RealTimeGpsDTO dto) {
            return dto.getVehicleId();
        }
        if (payload instanceof VehicleTraceWsDTO dto) {
            // 权限校验使用simCode，不要使用数据库主键vehicleId
            return dto.getSimCode();
        }
        return null;
    }
}