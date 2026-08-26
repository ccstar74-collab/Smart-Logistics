package com.smart_logistics.backend.service;

import com.smart_logistics.backend.dto.VehicleTracePointDTO;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/** Legacy development adapter delegating to the shared raw-record query path. */
@Service
public class VehicleTraceService {
    private final GpsInfluxService gpsInfluxService;

    public VehicleTraceService(GpsInfluxService gpsInfluxService) {
        this.gpsInfluxService = gpsInfluxService;
    }

    public List<VehicleTracePointDTO> getVehicleTrace(
            String vehicleId, long startTs, long endTs) {
        return gpsInfluxService.querySamples(List.of(vehicleId),
                        Instant.ofEpochMilli(startTs), Instant.ofEpochMilli(endTs)).stream()
                .map(sample -> {
                    VehicleTracePointDTO point = new VehicleTracePointDTO();
                    point.setLng(sample.longitude());
                    point.setLat(sample.latitude());
                    point.setSpeed(sample.speed());
                    point.setHeading(sample.direction());
                    point.setTimestamp(sample.collectedAt().toEpochMilli());
                    return point;
                }).toList();
    }
}
