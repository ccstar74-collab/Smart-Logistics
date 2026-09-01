package com.smart_logistics.backend.service.weather;

import com.smart_logistics.backend.dto.WeatherSnapshot;
import com.smart_logistics.backend.dto.response.TransportTaskResponse;
import com.smart_logistics.backend.enums.TransportTaskStatus;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.service.TransportTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RouteWeatherServiceTest {

    @Mock private TransportTaskService transportTaskService;
    @Mock private WeatherProvider weatherProvider;

    private RouteWeatherService service;

    @BeforeEach
    void setUp() {
        service = new RouteWeatherService(transportTaskService, weatherProvider);
    }

    @Test
    void queriesDestinationRegionAndPreservesProviderSource() {
        WeatherSnapshot snapshot = weather();
        when(transportTaskService.getTransportTask(1L)).thenReturn(task(106.65, 29.66));
        when(weatherProvider.getCurrentWeather(106.65, 29.66)).thenReturn(snapshot);

        assertEquals(snapshot, service.getDestinationWeather(1L));
        verify(weatherProvider).getCurrentWeather(106.65, 29.66);
    }

    @Test
    void rejectsIncompleteDestinationWithoutCallingProvider() {
        when(transportTaskService.getTransportTask(1L)).thenReturn(task(null, 29.66));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getDestinationWeather(1L));

        assertEquals(ErrorCode.STATE_CONFLICT, exception.getErrorCode());
        verifyNoInteractions(weatherProvider);
    }

    @Test
    void mapsProviderFailureToServiceUnavailable() {
        when(transportTaskService.getTransportTask(1L)).thenReturn(task(106.65, 29.66));
        when(weatherProvider.getCurrentWeather(106.65, 29.66))
                .thenThrow(new WeatherProviderException("timeout"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getDestinationWeather(1L));

        assertEquals(ErrorCode.REALTIME_PROVIDER_UNAVAILABLE, exception.getErrorCode());
    }

    private TransportTaskResponse task(Double longitude, Double latitude) {
        return new TransportTaskResponse(
                1L, "TASK-1", 1L, 2L,
                "start", 106.55, 29.56,
                "end", longitude, latitude,
                null, null, null, null,
                TransportTaskStatus.WAITING, null, null, null, null);
    }

    private WeatherSnapshot weather() {
        return new WeatherSnapshot(
                "AMAP_WEATHER_V3", "500103", "重庆", "渝中区", "多云",
                new BigDecimal("30"), 59, "西南", "≤3",
                OffsetDateTime.parse("2026-08-30T19:02:39+08:00"));
    }
}
