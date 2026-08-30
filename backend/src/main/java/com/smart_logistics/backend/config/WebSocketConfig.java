package com.smart_logistics.backend.config;

import com.smart_logistics.backend.handler.AlarmWebSocketHandler;
import com.smart_logistics.backend.handler.GpsWebSocketHandler;
import com.smart_logistics.backend.handler.NotificationWebSocketHandler;
import com.smart_logistics.backend.security.JwtWebSocketHandshakeInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private GpsWebSocketHandler gpsWebSocketHandler;

    @Autowired
    private AlarmWebSocketHandler alarmWebSocketHandler;

    @Autowired
    private NotificationWebSocketHandler notificationWebSocketHandler;

    @Autowired
    private JwtWebSocketHandshakeInterceptor jwtWebSocketHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 将两个路径合并到同一个 addHandler，避免同一个Handler实例重复注册
        registry.addHandler(gpsWebSocketHandler, "/ws/logistics", "/ws/vehicle-locations")
                .addInterceptors(jwtWebSocketHandshakeInterceptor)
                .setAllowedOrigins("*");
        // 告警事件推送，复用同一JWT握手拦截器（query token + active user检查）
        registry.addHandler(alarmWebSocketHandler, "/ws/alarms")
                .addInterceptors(jwtWebSocketHandshakeInterceptor)
                .setAllowedOrigins("*");
        // 用户级消息中心推送，握手时绑定userId，按用户精确推送
        registry.addHandler(notificationWebSocketHandler, "/ws/notifications")
                .addInterceptors(jwtWebSocketHandshakeInterceptor)
                .setAllowedOrigins("*");
    }
}