package com.smart_logistics.backend.dto;

public record InitialRouteLocationSnapshot(String location,
                                           double longitude,
                                           double latitude,
                                           String coordinateSystem) {
}
