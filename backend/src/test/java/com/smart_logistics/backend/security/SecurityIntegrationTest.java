package com.smart_logistics.backend.security;

import com.smart_logistics.backend.dto.response.UserIdentityResponse;
import com.smart_logistics.backend.dto.response.RegisterResponse;
import com.smart_logistics.backend.entity.User;
import com.smart_logistics.backend.enums.UserRole;
import com.smart_logistics.backend.enums.UserStatus;
import com.smart_logistics.backend.service.UserService;
import com.smart_logistics.backend.service.RegistrationService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.jwt.secret=phase-one-integration-test-secret-at-least-32-bytes",
        "app.jwt.expires-seconds=28800"
})
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    private static final String TEST_SECRET =
            "phase-one-integration-test-secret-at-least-32-bytes";

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;

    @MockitoBean private UserService userService;
    @MockitoBean private RegistrationService registrationService;

    @Test
    void loginSuccessReturnsBearerTokenAndNoPassword() throws Exception {
        User user = activeUser(UserRole.DRIVER);
        user.setPassword(passwordEncoder.encode("correct-password"));
        when(userService.findByUsername("driver001")).thenReturn(user);
        when(userService.toIdentity(user)).thenReturn(identity(UserRole.DRIVER, 3L, null));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"driver001","password":"correct-password"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(28800))
                .andExpect(jsonPath("$.data.user.driverId").value(3))
                .andExpect(jsonPath("$.data.user.password").doesNotExist());
    }

    @Test
    void anonymousRegistrationIsAllowedAndReturnsNoToken() throws Exception {
        when(registrationService.register(any())).thenReturn(
                new RegisterResponse(10L, "owner001", UserRole.OWNER));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"owner001","password":"correct-password",
                                 "name":"Owner One","role":"OWNER"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("registration successful"))
                .andExpect(jsonPath("$.data.userId").value(10))
                .andExpect(jsonPath("$.data.role").value("OWNER"))
                .andExpect(jsonPath("$.data.accessToken").doesNotExist());
    }

    @Test
    void usersMeRejectsMissingToken() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101))
                .andExpect(jsonPath("$.message").value("unauthorized"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/vehicles", "/api/v1/cargos", "/api/v1/transport-tasks",
            "/api/v1/alarms"
    })
    void businessApisRejectAnonymousRequests(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void usersMeRejectsInvalidToken() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void usersMeRejectsExpiredToken() throws Exception {
        Instant now = Instant.now();
        String token = Jwts.builder()
                .subject("1")
                .issuedAt(Date.from(now.minusSeconds(120)))
                .expiration(Date.from(now.minusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void usersMeUsesTokenSubjectThenReloadsDatabaseIdentity() throws Exception {
        String token = jwtService.generateToken(1L, "stale-name", UserRole.DRIVER);
        when(userService.getActiveIdentity(1L))
                .thenReturn(identity(UserRole.DRIVER, 37L, null));

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("Current Database Name"))
                .andExpect(jsonPath("$.data.driverId").value(37))
                .andExpect(jsonPath("$.data.ownerId").doesNotExist())
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    void optionsPreflightRemainsPermitAll() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .options("/api/v1/drivers/options")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk());
    }

    private User activeUser(UserRole role) {
        User user = new User();
        user.setId(1L);
        user.setUsername("driver001");
        user.setName("Current Database Name");
        user.setRole(role.name());
        user.setStatus(UserStatus.ACTIVE.name());
        return user;
    }

    private UserIdentityResponse identity(UserRole role, Long driverId, Long ownerId) {
        return new UserIdentityResponse(1L, "driver001", "Current Database Name",
                "13800000000", role, UserStatus.ACTIVE, driverId, ownerId);
    }
}
