package com.smart_logistics.backend.security;

import com.smart_logistics.backend.dto.response.UserIdentityResponse;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.service.UserService;
import io.jsonwebtoken.JwtException;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Component
public class JwtWebSocketHandshakeInterceptor implements HandshakeInterceptor {

    private static final String TOKEN_PARAM = "token";

    private final JwtService jwtService;
    private final UserService userService;
    private final WebSocketScopeService scopeService;

    public JwtWebSocketHandshakeInterceptor(
            JwtService jwtService,
            UserService userService,
            WebSocketScopeService scopeService) {
        this.jwtService = jwtService;
        this.userService = userService;
        this.scopeService = scopeService;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {

        String token = UriComponentsBuilder
                .fromUri(request.getURI())
                .build()
                .getQueryParams()
                .getFirst(TOKEN_PARAM);

        if (!StringUtils.hasText(token)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        try {
            Long userId = jwtService.extractUserId(token);
            UserIdentityResponse identity = userService.getActiveIdentity(userId);
            // 握手时一次性计算车辆可见范围，推送循环只读会话属性，不再查库
            WebSocketScopeService.VehicleScope scope = scopeService.resolve(identity);
            if (scope.allowAll()) {
                attributes.put(WsSessionAttributes.ALLOW_ALL_VEHICLES, Boolean.TRUE);
            }
            attributes.put(WsSessionAttributes.ALLOWED_VEHICLE_SIM_CODES,
                    scope.allowedSimCodes());
            return true;
        } catch (JwtException
                 | IllegalArgumentException
                 | BusinessException exception) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // No-op.
    }
}