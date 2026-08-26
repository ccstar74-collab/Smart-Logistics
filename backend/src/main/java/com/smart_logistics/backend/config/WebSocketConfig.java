package com.smart_logistics.backend.config;

import com.smart_logistics.backend.handler.GpsWebSocketHandler;
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

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 对外地址 ws://ip:8081/ws/vehicle-locations
        registry.addHandler(gpsWebSocketHandler, "/ws/vehicle-locations")
                .setAllowedOrigins("*");
    }
}