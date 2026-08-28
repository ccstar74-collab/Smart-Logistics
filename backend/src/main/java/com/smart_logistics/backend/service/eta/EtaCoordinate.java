package com.smart_logistics.backend.service.eta;

public record EtaCoordinate(double longitude, double latitude) {
    public EtaCoordinate {
        if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180
                || !Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("route coordinate is outside the valid range");
        }
    }
}
