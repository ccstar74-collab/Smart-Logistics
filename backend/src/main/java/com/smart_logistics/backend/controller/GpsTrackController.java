package com.smart_logistics.backend.controller;

import com.smart_logistics.backend.service.GpsInfluxService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Legacy development endpoint. Frontends must use the scoped /api/v1 APIs. */
@RestController
@RequestMapping("/api/gps")
@PreAuthorize("denyAll()")
public class GpsTrackController {
    private final GpsInfluxService gpsInfluxService;

    public GpsTrackController(GpsInfluxService gpsInfluxService) {
        this.gpsInfluxService = gpsInfluxService;
    }

    @GetMapping("/track/{vehicleId}")
    public List<Map<String, Object>> getVehicleTrack(@PathVariable String vehicleId) {
        Instant end = Instant.now();
        return gpsInfluxService.queryTrack(vehicleId, end.minusSeconds(7200), end);
    }
}
