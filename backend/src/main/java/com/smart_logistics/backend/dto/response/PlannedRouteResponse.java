package com.smart_logistics.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlannedRouteResponse {
    private List<RoutePoint> points;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoutePoint {
        private Double lon;
        private Double lat;
        private String address;
    }
}