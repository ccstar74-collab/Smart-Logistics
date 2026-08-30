package com.smart_logistics.backend.service.weather;

import com.smart_logistics.backend.dto.WeatherSnapshot;
import com.smart_logistics.backend.dto.response.TransportTaskResponse;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.service.TransportTaskService;
import org.springframework.stereotype.Service;

@Service
public class RouteWeatherService {

    private final TransportTaskService transportTaskService;
    private final WeatherProvider weatherProvider;

    public RouteWeatherService(TransportTaskService transportTaskService,
                               WeatherProvider weatherProvider) {
        this.transportTaskService = transportTaskService;
        this.weatherProvider = weatherProvider;
    }

    public WeatherSnapshot getDestinationWeather(Long taskId) {
        TransportTaskResponse task = transportTaskService.getTransportTask(taskId);
        if (task.getEndLongitude() == null || task.getEndLatitude() == null) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "transport task destination coordinates are incomplete");
        }
        try {
            return weatherProvider.getCurrentWeather(
                    task.getEndLongitude(), task.getEndLatitude());
        } catch (WeatherProviderException exception) {
            throw new BusinessException(ErrorCode.REALTIME_PROVIDER_UNAVAILABLE,
                    "weather is unavailable: " + exception.getMessage());
        }
    }
}
