package com.smart_logistics.backend.dto.response;

public class DriverOptionResponse {

    private final Long driverId;
    private final Long userId;
    private final String name;

    public DriverOptionResponse(Long driverId, Long userId, String name) {
        this.driverId = driverId;
        this.userId = userId;
        this.name = name;
    }

    public Long getDriverId() { return driverId; }
    public Long getUserId() { return userId; }
    public String getName() { return name; }
}
