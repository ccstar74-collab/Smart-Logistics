package com.smart_logistics.backend.security;

import com.smart_logistics.backend.dto.response.UserIdentityResponse;
import com.smart_logistics.backend.enums.UserRole;
import com.smart_logistics.backend.enums.UserStatus;
import com.smart_logistics.backend.service.UserService;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * WebSocket握手拦截器：无token或token无效拒绝握手；
 * 鉴权通过后把预计算的车辆可见范围写入会话属性
 */
@ExtendWith(MockitoExtension.class)
class JwtWebSocketHandshakeInterceptorTest {

    @Mock private JwtService jwtService;
    @Mock private UserService userService;
    @Mock private WebSocketScopeService scopeService;
    @Mock private ServerHttpRequest request;
    @Mock private ServerHttpResponse response;
    @Mock private WebSocketHandler wsHandler;

    private JwtWebSocketHandshakeInterceptor interceptor;
    private Map<String, Object> attributes;

    @BeforeEach
    void setUp() {
        interceptor = new JwtWebSocketHandshakeInterceptor(jwtService, userService, scopeService);
        attributes = new HashMap<>();
    }

    @Test
    void rejectsHandshakeWithoutToken() {
        when(request.getURI()).thenReturn(URI.create("ws://localhost/ws/logistics"));

        boolean allowed = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertFalse(allowed);
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(jwtService, userService, scopeService);
    }

    @Test
    void rejectsHandshakeWithInvalidToken() {
        when(request.getURI()).thenReturn(URI.create("ws://localhost/ws/logistics?token=bad"));
        when(jwtService.extractUserId("bad")).thenThrow(new JwtException("invalid token"));

        boolean allowed = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertFalse(allowed);
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(scopeService);
    }

    @Test
    void storesSimCodeScopeInSessionAttributesForScopedRole() {
        when(request.getURI()).thenReturn(URI.create("ws://localhost/ws/logistics?token=valid"));
        when(jwtService.extractUserId("valid")).thenReturn(7L);
        when(userService.getActiveIdentity(7L)).thenReturn(identity(UserRole.OWNER));
        when(scopeService.resolve(org.mockito.ArgumentMatchers.any()))
                .thenReturn(WebSocketScopeService.VehicleScope.of(Set.of("sim_001")));

        boolean allowed = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertTrue(allowed);
        assertEquals(Set.of("sim_001"),
                attributes.get(WsSessionAttributes.ALLOWED_VEHICLE_SIM_CODES));
        assertFalse(attributes.containsKey(WsSessionAttributes.ALLOW_ALL_VEHICLES));
    }

    @Test
    void storesAllowAllFlagForAdmin() {
        when(request.getURI()).thenReturn(URI.create("ws://localhost/ws/logistics?token=valid"));
        when(jwtService.extractUserId("valid")).thenReturn(1L);
        when(userService.getActiveIdentity(1L)).thenReturn(identity(UserRole.ADMIN));
        when(scopeService.resolve(org.mockito.ArgumentMatchers.any()))
                .thenReturn(WebSocketScopeService.VehicleScope.all());

        boolean allowed = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertTrue(allowed);
        assertEquals(Boolean.TRUE, attributes.get(WsSessionAttributes.ALLOW_ALL_VEHICLES));
    }

    private UserIdentityResponse identity(UserRole role) {
        return new UserIdentityResponse(
                1L, "user", "用户", "13800000000", role, UserStatus.ACTIVE, null, null);
    }
}
