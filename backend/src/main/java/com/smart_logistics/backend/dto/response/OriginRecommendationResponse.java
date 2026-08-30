package com.smart_logistics.backend.dto.response;

public class OriginRecommendationResponse {

    private final Long warehouseId;
    private final String warehouseNo;
    private final String warehouseName;
    private final String warehouseAddress;
    private final Double longitude;
    private final Double latitude;
    private final long distanceMeters;
    private final long durationSeconds;
    private final long availableCargoCount;
    private final long availableVehicleCount;
    private final boolean recommended;

    public OriginRecommendationResponse(Long warehouseId, String warehouseNo,
                                        String warehouseName, String warehouseAddress,
                                        Double longitude, Double latitude,
                                        long distanceMeters, long durationSeconds,
                                        long availableCargoCount,
                                        long availableVehicleCount,
                                        boolean recommended) {
        this.warehouseId = warehouseId;
        this.warehouseNo = warehouseNo;
        this.warehouseName = warehouseName;
        this.warehouseAddress = warehouseAddress;
        this.longitude = longitude;
        this.latitude = latitude;
        this.distanceMeters = distanceMeters;
        this.durationSeconds = durationSeconds;
        this.availableCargoCount = availableCargoCount;
        this.availableVehicleCount = availableVehicleCount;
        this.recommended = recommended;
    }

    public OriginRecommendationResponse withRecommended(boolean value) {
        return new OriginRecommendationResponse(
                warehouseId, warehouseNo, warehouseName, warehouseAddress,
                longitude, latitude, distanceMeters, durationSeconds,
                availableCargoCount, availableVehicleCount, value);
    }

    public Long getWarehouseId() { return warehouseId; }
    public String getWarehouseNo() { return warehouseNo; }
    public String getWarehouseName() { return warehouseName; }
    public String getWarehouseAddress() { return warehouseAddress; }
    public Double getLongitude() { return longitude; }
    public Double getLatitude() { return latitude; }
    public long getDistanceMeters() { return distanceMeters; }
    public long getDurationSeconds() { return durationSeconds; }
    public long getAvailableCargoCount() { return availableCargoCount; }
    public long getAvailableVehicleCount() { return availableVehicleCount; }
    public boolean isRecommended() { return recommended; }
}
