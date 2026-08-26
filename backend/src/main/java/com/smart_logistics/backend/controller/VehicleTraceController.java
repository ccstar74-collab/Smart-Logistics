package com.smart_logistics.backend.controller;

import com.smart_logistics.backend.dto.RealTimeGpsDTO;
import com.smart_logistics.backend.dto.VehicleTracePointDTO;
import com.smart_logistics.backend.dto.response.VehicleTraceWsDTO;
import com.smart_logistics.backend.service.VehicleTraceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/vehicles")
public class VehicleTraceController {

    @Autowired
    private VehicleTraceService vehicleTraceService;

    /**
     * P1 GET /api/v1/vehicles/{id}/location‑history?start&end
     * 查询单车某个时间范围的位置历史
     */
    @GetMapping("/{id}/location-history")
    public List<VehicleTraceWsDTO> getTrace(
            @PathVariable String id,
            @RequestParam long start,
            @RequestParam long end
    ){
        List<VehicleTracePointDTO> innerList = vehicleTraceService.getVehicleTrace(id, start, end);
        List<VehicleTraceWsDTO> result = new ArrayList<>();
        for (VehicleTracePointDTO point : innerList) {
            VehicleTraceWsDTO dto = new VehicleTraceWsDTO();
            dto.setVehicleId(id);
            dto.setLatitude(point.getLat());
            dto.setLongitude(point.getLon());
            dto.setSpeed(point.getSpeed());
            dto.setDirection(point.getHeading());
            if(point.getTimestamp() != null){
                dto.setCollectedAt(OffsetDateTime.ofInstant(Instant.ofEpochMilli(point.getTimestamp()), ZoneId.systemDefault()));
            }
            result.add(dto);
        }
        return result;
    }

    /**
     * P0 GET /api/v1/vehicles/{id}/location‑latest
     */
    @GetMapping("/{id}/location/latest")
    public VehicleTraceWsDTO getSingleLatestLocation(@PathVariable String id){
        RealTimeGpsDTO inner = vehicleTraceService.getVehicleLatestPoint(id);
        if(inner == null){
            return null;
        }
        VehicleTraceWsDTO dto = new VehicleTraceWsDTO();
        dto.setVehicleId(inner.getVehicleId());
        dto.setLatitude(inner.getLat());
        dto.setLongitude(inner.getLon());
        dto.setSpeed(inner.getSpeed());
        dto.setDirection(inner.getHeading());
        if(inner.getTimestamp() != null){
            dto.setCollectedAt(OffsetDateTime.ofInstant(Instant.ofEpochMilli(inner.getTimestamp()), ZoneId.systemDefault()));
        }
        return dto;
    }

    /**
     * P0 GET /api/v1/vehicles/locations/latest
     */
    @GetMapping("/locations/latest")
    public List<VehicleTraceWsDTO> getAllVehiclesLatestLocation(){
        List<RealTimeGpsDTO> innerList = vehicleTraceService.getAllVehicleLatestPoints();
        List<VehicleTraceWsDTO> result = new ArrayList<>();
        for (RealTimeGpsDTO inner : innerList) {
            VehicleTraceWsDTO dto = new VehicleTraceWsDTO();
            dto.setVehicleId(inner.getVehicleId());
            dto.setLatitude(inner.getLat());
            dto.setLongitude(inner.getLon());
            dto.setSpeed(inner.getSpeed());
            dto.setDirection(inner.getHeading());
            if(inner.getTimestamp() != null){
                dto.setCollectedAt(OffsetDateTime.ofInstant(Instant.ofEpochMilli(inner.getTimestamp()), ZoneId.systemDefault()));
            }
            result.add(dto);
        }
        return result;
    }
}