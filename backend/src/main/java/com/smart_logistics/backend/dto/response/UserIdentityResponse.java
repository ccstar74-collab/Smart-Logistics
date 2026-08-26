package com.smart_logistics.backend.dto.response;

import com.smart_logistics.backend.enums.UserRole;
import com.smart_logistics.backend.enums.UserStatus;

public class UserIdentityResponse {

    private final Long id;
    private final String username;
    private final String name;
    private final String phone;
    private final UserRole role;
    private final UserStatus status;
    private final Long driverId;
    private final Long ownerId;

    public UserIdentityResponse(Long id, String username, String name, String phone,
                                UserRole role, UserStatus status, Long driverId, Long ownerId) {
        this.id = id;
        this.username = username;
        this.name = name;
        this.phone = phone;
        this.role = role;
        this.status = status;
        this.driverId = driverId;
        this.ownerId = ownerId;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public UserRole getRole() { return role; }
    public UserStatus getStatus() { return status; }
    public Long getDriverId() { return driverId; }
    public Long getOwnerId() { return ownerId; }
}
