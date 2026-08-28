package com.smart_logistics.backend.security;

import com.smart_logistics.backend.dto.response.UserIdentityResponse;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.service.UserService;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Slf4j
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

        String path = request.getURI().getPath();
        String token = UriComponentsBuilder
                .fromUri(request.getURI())
                .build()
                .getQueryParams()
                .getFirst(TOKEN_PARAM);

        log.info("[WS握手请求] path={}, tokenPresent={}", path, StringUtils.hasText(token));

        if (!StringUtils.hasText(token)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            log.warn("[WS握手拒绝] token为空 path={}", path);
            return false;
        }

        try {
            Long userId = jwtService.extractUserId(token);
            log.info("[WS握手] jwt解析成功 userId={} path={}", userId, path);

            UserIdentityResponse identity = userService.getActiveIdentity(userId);
            WebSocketScopeService.VehicleScope scope = scopeService.resolve(identity);

            log.info("[WS握手权限] userId={}, allowAll={}, simCodes={}",
                    userId, scope.allowAll(), scope.allowedSimCodes());

            if (scope.allowAll()) {
                attributes.put(WsSessionAttributes.ALLOW_ALL_VEHICLES, Boolean.TRUE);
            }
            attributes.put(WsSessionAttributes.ALLOWED_VEHICLE_SIM_CODES,
                    scope.allowedSimCodes());

            log.info("[WS握手] 允许建立连接 path={}", path);
            return true;
        } catch (JwtException
                 | IllegalArgumentException
                 | BusinessException exception) {
            log.warn("[WS握手鉴权失败 path={}]", path, exception);
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
        // No‑op.
    }
}