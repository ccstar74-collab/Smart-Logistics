package com.smart_logistics.backend.dto.response;

public class OwnerOptionResponse {

    private final Long ownerId;
    private final Long userId;
    private final String name;
    private final String companyName;

    public OwnerOptionResponse(Long ownerId, Long userId, String name, String companyName) {
        this.ownerId = ownerId;
        this.userId = userId;
        this.name = name;
        this.companyName = companyName;
    }

    public Long getOwnerId() { return ownerId; }
    public Long getUserId() { return userId; }
    public String getName() { return name; }
    public String getCompanyName() { return companyName; }
}
