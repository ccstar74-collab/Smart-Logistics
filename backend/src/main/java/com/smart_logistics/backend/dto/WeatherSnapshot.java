package com.smart_logistics.backend.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record WeatherSnapshot(String source,
                              String adcode,
                              String province,
                              String city,
                              String weather,
                              BigDecimal temperature,
                              int humidity,
                              String windDirection,
                              String windPower,
                              OffsetDateTime reportTime) {

    public WeatherSnapshot {
        requireText(source, "weather source");
        requireText(adcode, "weather adcode");
        requireText(weather, "weather description");
        requireText(windPower, "weather wind power");
        if (temperature == null || reportTime == null) {
            throw new IllegalArgumentException(
                    "weather temperature and report time must not be null");
        }
        if (humidity < 0 || humidity > 100) {
            throw new IllegalArgumentException("weather humidity is outside valid range");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
