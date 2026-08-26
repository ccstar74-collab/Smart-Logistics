package com.smart_logistics.backend.security;

import com.smart_logistics.backend.config.JwtProperties;
import com.smart_logistics.backend.enums.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expiresSeconds;
    private final Clock clock;

    @Autowired
    public JwtService(JwtProperties properties) {
        this(properties, Clock.systemUTC());
    }

    JwtService(JwtProperties properties, Clock clock) {
        if (!StringUtils.hasText(properties.getSecret())
                || properties.getSecret().getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET must be configured with at least 32 UTF-8 bytes");
        }
        if (properties.getExpiresSeconds() <= 0) {
            throw new IllegalStateException("JWT_EXPIRES_SECONDS must be greater than 0");
        }
        this.key = Keys.hmacShaKeyFor(
                properties.getSecret().getBytes(StandardCharsets.UTF_8));
        this.expiresSeconds = properties.getExpiresSeconds();
        this.clock = clock;
    }

    public String generateToken(Long userId, String username, UserRole role) {
        Instant issuedAt = clock.instant();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("username", username)
                .claim("role", role.name())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plusSeconds(expiresSeconds)))
                .signWith(key)
                .compact();
    }

    public Long extractUserId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return Long.valueOf(claims.getSubject());
    }

    public long getExpiresSeconds() {
        return expiresSeconds;
    }
}
