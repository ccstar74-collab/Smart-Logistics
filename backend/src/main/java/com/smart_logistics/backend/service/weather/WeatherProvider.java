package com.smart_logistics.backend.service.weather;

import com.smart_logistics.backend.dto.WeatherSnapshot;

public interface WeatherProvider {

    WeatherSnapshot getCurrentWeather(double longitude, double latitude);
}
