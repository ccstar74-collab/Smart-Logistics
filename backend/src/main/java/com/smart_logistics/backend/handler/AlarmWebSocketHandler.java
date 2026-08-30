package com.smart_logistics.backend.handler;

import com.smart_logistics.backend.dto.realtime.AlarmWsEvent;
import com.smart_logistics.backend.dto.realtime.AlarmWsMessage;
import com.smart_logistics.backend.dto.response.AlarmResponse;
import com.smart_logistics.backend.enums.UserRole;
import com.smart_logistics.backend.security.BusinessDataScopeService;
import com.smart_logistics.backend.security.WsSessionAttributes;
import com.smart_logistics.backend.service.AlarmService;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * /ws/alarms告警事件推送处理器。
 * 只负责事件通知：监听业务服务发布的AlarmWsEvent（事务提交后触发），
 * 按会话角色做数据范围过滤后推送，不实现任何告警业务逻辑。
 * 提醒对象：ADMIN/DISPATCHER收全部；OWNER只收本人Cargo/Task对应告警
 * （taskId为null的设备级告警不推给OWNER）；其余角色不推送。
 */
@Slf4j
@Component
public class AlarmWebSocketHandler extends TextWebSocketHandler {

    private static final String ALARMS_PATH = "/ws/alarms";

    private final ObjectMapper objectMapper;
    private final AlarmService alarmService;
    private final BusinessDataScopeService dataScopeService;

    private final CopyOnWriteArrayList<WebSocketSession> alarmSessions = new CopyOnWriteArrayList<>();

    public AlarmWebSocketHandler(ObjectMapper objectMapper,
                                 AlarmService alarmService,
                                 BusinessDataScopeService dataScopeService) {
        this.objectMapper = objectMapper;
        this.alarmService = alarmService;
        this.dataScopeService = dataScopeService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (session.getUri() == null || !ALARMS_PATH.equals(session.getUri().getPath())) {
            log.warn("未知websocket路径 sessionId={}, uri={}", session.getId(), session.getUri());
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        alarmSessions.add(session);
        log.info("告警会话建立 sessionId={}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        alarmSessions.remove(session);
        log.info("告警会话关闭 sessionId={}, status={}", session.getId(), status);
    }

    /**
     * 前端心跳：收到"ping"文本帧立即回写"pong"，保持连接活跃，
     * 避免静置时被反向代理/网关按空闲超时掐断（表现为code:1006）。
     * 该端点为单向推送，其余文本帧忽略。
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        if (!"ping".equals(message.getPayload())) {
            // 非心跳文本帧不进入任何业务逻辑，直接忽略
            return;
        }
        try {
            // isOpen校验放在锁内，避免检查与发送之间会话被关闭的竞态；
            // 与广播路径共用同一把session锁，避免帧交错
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
     * 事务提交后转发告警事件。提交前不推送，保证前端收到的告警
     * 一定已持久化；推送丢失时前端可通过REST刷新最终状态。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true)
    public void onAlarmEvent(AlarmWsEvent event) {
        AlarmResponse alarm = alarmService.findResponse(event.alarmId());
        if (alarm == null) {
            log.warn("告警事件推送跳过，告警不存在 alarmId={}, event={}",
                    event.alarmId(), event.type());
            return;
        }
        AlarmWsMessage message = new AlarmWsMessage(event.type().name(), alarm.getId(), alarm);
        // 同一次事件内缓存OWNER的可见任务集合，避免每个会话重复查询
        Map<Long, List<Long>> ownerTaskCache = new HashMap<>();
        for (WebSocketSession session : alarmSessions) {
            if (!session.isOpen() || !canViewAlarm(session, alarm, ownerTaskCache)) {
                continue;
            }
            sendObject(session, message);
        }
    }

    /**
     * 数据范围过滤：基于握手拦截器写入的角色/身份属性判断。
     * @param session 告警会话
     * @param alarm 待推送告警
     * @param ownerTaskCache 本次事件内的OWNER可见任务缓存
     * @return true可推送
     */
    private boolean canViewAlarm(WebSocketSession session, AlarmResponse alarm,
                                 Map<Long, List<Long>> ownerTaskCache) {
        UserRole role = (UserRole) session.getAttributes().get(WsSessionAttributes.USER_ROLE);
        if (role == UserRole.ADMIN || role == UserRole.DISPATCHER) {
            // 调度员暂按全局调度范围处理，管理员可查看全部
            return true;
        }
        if (role == UserRole.OWNER) {
            Long ownerId = (Long) session.getAttributes().get(WsSessionAttributes.OWNER_ID);
            // 无Task的设备级告警不推给货主
            if (ownerId == null || alarm.getTaskId() == null) {
                return false;
            }
            List<Long> taskIds = ownerTaskCache.computeIfAbsent(
                    ownerId, dataScopeService::taskIdsForOwner);
            return taskIds.contains(alarm.getTaskId());
        }
        // 司机等角色当前不在告警提醒对象内
        return false;
    }

    private void sendObject(WebSocketSession session, Object obj) {
        try {
            String json = objectMapper.writeValueAsString(obj);
            // WebSocketSession并发发送不安全，序列化+发送整体加锁
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (IOException | JacksonException e) {
            log.error("告警推送发送失败 sessionId={}", session.getId(), e);
        }
    }
}
