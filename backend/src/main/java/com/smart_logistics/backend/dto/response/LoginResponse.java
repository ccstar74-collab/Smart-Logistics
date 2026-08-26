package com.smart_logistics.backend.dto.response;

public class LoginResponse {

    private final String accessToken;
    private final String tokenType;
    private final long expiresIn;
    private final UserIdentityResponse user;

    public LoginResponse(String accessToken, long expiresIn, UserIdentityResponse user) {
        this.accessToken = accessToken;
        this.tokenType = "Bearer";
        this.expiresIn = expiresIn;
        this.user = user;
    }

    public String getAccessToken() { return accessToken; }
    public String getTokenType() { return tokenType; }
    public long getExpiresIn() { return expiresIn; }
    public UserIdentityResponse getUser() { return user; }
}
