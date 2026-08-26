package com.smart_logistics.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    private String secret;
    private long expiresSeconds = 28800;

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
    public long getExpiresSeconds() { return expiresSeconds; }
    public void setExpiresSeconds(long expiresSeconds) { this.expiresSeconds = expiresSeconds; }
}
