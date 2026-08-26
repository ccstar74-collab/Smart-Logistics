package com.smart_logistics.backend.service;

import com.smart_logistics.backend.config.JwtProperties;
import com.smart_logistics.backend.dto.request.LoginRequest;
import com.smart_logistics.backend.dto.response.LoginResponse;
import com.smart_logistics.backend.dto.response.UserIdentityResponse;
import com.smart_logistics.backend.entity.User;
import com.smart_logistics.backend.enums.UserRole;
import com.smart_logistics.backend.enums.UserStatus;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String SECRET = "phase-one-test-secret-at-least-32-bytes-long";

    @Mock
    private UserService userService;

    private BCryptPasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(SECRET);
        properties.setExpiresSeconds(28800);
        passwordEncoder = new BCryptPasswordEncoder();
        jwtService = new JwtService(properties);
        authService = new AuthService(userService, passwordEncoder, jwtService);
    }

    @Test
    void loginGeneratesJwtAndReturnsIdentityWithoutPassword() {
        User user = user(UserStatus.ACTIVE, passwordEncoder.encode("correct-password"));
        UserIdentityResponse identity = identity(UserRole.DRIVER, 9L, null);
        when(userService.findByUsername("driver001")).thenReturn(user);
        when(userService.toIdentity(user)).thenReturn(identity);

        LoginResponse response = authService.login(request("driver001", "correct-password"));

        assertNotNull(response.getAccessToken());
        assertEquals(1L, jwtService.extractUserId(response.getAccessToken()));
        assertEquals("Bearer", response.getTokenType());
        assertEquals(28800, response.getExpiresIn());
        assertEquals(9L, response.getUser().getDriverId());
        assertFalse(ArraysSupport.hasPasswordGetter(response.getUser().getClass()));
    }

    @Test
    void unknownUsernameReturnsUnauthorized() {
        when(userService.findByUsername("missing")).thenReturn(null);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.login(request("missing", "anything")));
        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        assertEquals("invalid username or password", exception.getMessage());
    }

    @Test
    void wrongPasswordReturnsSameUnauthorizedMessage() {
        User user = user(UserStatus.ACTIVE, passwordEncoder.encode("correct-password"));
        when(userService.findByUsername("driver001")).thenReturn(user);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.login(request("driver001", "wrong")));
        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        assertEquals("invalid username or password", exception.getMessage());
        verify(userService, never()).toIdentity(user);
    }

    @Test
    void nonBcryptPasswordHasNoPlaintextFallback() {
        User user = user(UserStatus.ACTIVE, "plain-password");
        when(userService.findByUsername("driver001")).thenReturn(user);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.login(request("driver001", "plain-password")));
        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
    }

    @Test
    void disabledUserReturnsForbiddenAfterCorrectPassword() {
        User user = user(UserStatus.DISABLED, passwordEncoder.encode("correct-password"));
        when(userService.findByUsername("driver001")).thenReturn(user);
        BusinessException disabled = new BusinessException(
                ErrorCode.FORBIDDEN, "account is disabled");
        org.mockito.Mockito.doThrow(disabled).when(userService).requireActive(user);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.login(request("driver001", "correct-password")));
        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
    }

    @Test
    void jwtRejectsWeakSecretClearly() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("too-short");
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> new JwtService(properties));
        assertTrue(exception.getMessage().contains("JWT_SECRET"));
    }

    private LoginRequest request(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }

    private User user(UserStatus status, String password) {
        User user = new User();
        user.setId(1L);
        user.setUsername("driver001");
        user.setPassword(password);
        user.setRole(UserRole.DRIVER.name());
        user.setStatus(status.name());
        return user;
    }

    private UserIdentityResponse identity(UserRole role, Long driverId, Long ownerId) {
        return new UserIdentityResponse(1L, "driver001", "Driver One", "13800000000",
                role, UserStatus.ACTIVE, driverId, ownerId);
    }

    private static final class ArraysSupport {
        private static boolean hasPasswordGetter(Class<?> type) {
            return java.util.Arrays.stream(type.getMethods())
                    .anyMatch(method -> method.getName().equals("getPassword"));
        }
    }
}
