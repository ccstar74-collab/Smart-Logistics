package com.smart_logistics.backend.controller;

import com.smart_logistics.backend.dto.WeatherSnapshot;
import com.smart_logistics.backend.exception.GlobalExceptionHandler;
import com.smart_logistics.backend.service.weather.RouteWeatherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RouteDataControllerTest {

    @Mock private RouteWeatherService routeWeatherService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new RouteDataController(routeWeatherService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsSourceAwareDestinationWeather() throws Exception {
        when(routeWeatherService.getDestinationWeather(1L)).thenReturn(
                new WeatherSnapshot(
                        "AMAP_WEATHER_V3", "500103", "重庆", "渝中区", "多云",
                        new BigDecimal("30"), 59, "西南", "≤3",
                        OffsetDateTime.parse("2026-08-30T19:02:39+08:00")));

        mockMvc.perform(get("/api/v1/transport-tasks/1/route-data/weather"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("AMAP_WEATHER_V3"))
                .andExpect(jsonPath("$.data.adcode").value("500103"))
                .andExpect(jsonPath("$.data.weather").value("多云"))
                .andExpect(jsonPath("$.data.temperature").value(30))
                .andExpect(jsonPath("$.data.humidity").value(59))
                .andExpect(jsonPath("$.data.windPower").value("≤3"))
                .andExpect(jsonPath("$.data.reportTime")
                        .value("2026-08-30T19:02:39+08:00"));

        verify(routeWeatherService).getDestinationWeather(1L);
    }
}
