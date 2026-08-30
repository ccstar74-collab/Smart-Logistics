package com.smart_logistics.backend.controller;

import com.smart_logistics.backend.common.ApiResponse;
import com.smart_logistics.backend.dto.WeatherSnapshot;
import com.smart_logistics.backend.service.weather.RouteWeatherService;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/transport-tasks")
public class RouteDataController {

    private final RouteWeatherService routeWeatherService;

    public RouteDataController(RouteWeatherService routeWeatherService) {
        this.routeWeatherService = routeWeatherService;
    }

    @GetMapping("/{id}/route-data/weather")
    @PreAuthorize("hasAnyRole('OWNER','DRIVER','WAREHOUSE_MANAGER','DISPATCHER','ADMIN')")
    public ApiResponse<WeatherSnapshot> getDestinationWeather(
            @PathVariable @Positive Long id) {
        return ApiResponse.success(routeWeatherService.getDestinationWeather(id));
    }
}
