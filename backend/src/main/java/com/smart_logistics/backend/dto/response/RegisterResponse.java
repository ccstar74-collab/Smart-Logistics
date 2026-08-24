package com.smart_logistics.backend.dto.response;

import com.smart_logistics.backend.enums.UserRole;

public class RegisterResponse {

    private final Long userId;
    private final String username;
    private final UserRole role;

    public RegisterResponse(Long userId, String username, UserRole role) {
        this.userId = userId;
        this.username = username;
        this.role = role;
    }

    public Long getUserId() { return userId; }
    public String getUsername() { return username; }
    public UserRole getRole() { return role; }
}
