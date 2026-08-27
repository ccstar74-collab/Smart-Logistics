package com.smart_logistics.backend.config;

import com.smart_logistics.backend.handler.GpsWebSocketHandler;
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
    private JwtWebSocketHandshakeInterceptor jwtWebSocketHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(gpsWebSocketHandler, "/ws/logistics")
                .addInterceptors(jwtWebSocketHandshakeInterceptor)
                .setAllowedOrigins("*");

        registry.addHandler(gpsWebSocketHandler, "/ws/vehicle-locations")
                .addInterceptors(jwtWebSocketHandshakeInterceptor)
                .setAllowedOrigins("*");
    }
}