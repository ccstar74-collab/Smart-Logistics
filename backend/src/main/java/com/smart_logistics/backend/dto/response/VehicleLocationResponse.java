package com.smart_logistics.backend.dto.response;

import java.time.OffsetDateTime;

public class VehicleLocationResponse {
    private final Long vehicleId;
    private final String plateNumber;
    private final Double longitude;
    private final Double latitude;
    private final Double speed;
    private final Double direction;
    private final OffsetDateTime collectedAt;
    private final boolean online;
    private final Long taskId;

    public VehicleLocationResponse(Long vehicleId, String plateNumber,
                                   Double longitude, Double latitude,
                                   Double speed, Double direction,
                                   OffsetDateTime collectedAt, boolean online,
                                   Long taskId) {
        this.vehicleId = vehicleId;
        this.plateNumber = plateNumber;
        this.longitude = longitude;
        this.latitude = latitude;
        this.speed = speed;
        this.direction = direction;
        this.collectedAt = collectedAt;
        this.online = online;
        this.taskId = taskId;
    }

    public Long getVehicleId() { return vehicleId; }
    public String getPlateNumber() { return plateNumber; }
    public Double getLongitude() { return longitude; }
    public Double getLatitude() { return latitude; }
    public Double getSpeed() { return speed; }
    public Double getDirection() { return direction; }
    public OffsetDateTime getCollectedAt() { return collectedAt; }
    public boolean isOnline() { return online; }
    public Long getTaskId() { return taskId; }
}
