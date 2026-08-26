package com.smart_logistics.backend.controller;

import com.smart_logistics.backend.dto.VehicleTracePointDTO;
import com.smart_logistics.backend.dto.response.SimGpsPointDTO;
import com.smart_logistics.backend.dto.response.VehicleTraceWsDTO;
import com.smart_logistics.backend.entity.Vehicle;
import com.smart_logistics.backend.service.VehicleService;
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

    @Autowired
    private VehicleService vehicleService;

    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");

    // ====================== 新业务接口：使用MySQL数字主键 dbId ======================
    /**
     * GET /api/v1/vehicles/db/{dbId}/location‑history?start&end
     * 根据数据库车辆主键查询轨迹历史
     * @param dbId MySQL车辆主键
     * @param start 开始时间戳(毫秒)
     * @param end 结束时间戳(毫秒)
     * @return 轨迹点集合
     */
    @GetMapping("/db/{dbId}/location-history")
    public List<VehicleTraceWsDTO> getTraceByDbId(
            @PathVariable Long dbId,
            @RequestParam long start,
            @RequestParam long end
    ){
        List<VehicleTracePointDTO> innerList = vehicleTraceService.getVehicleTraceByDbId(dbId, start, end);
        List<VehicleTraceWsDTO> result = new ArrayList<>();
        for (VehicleTracePointDTO point : innerList) {
            VehicleTraceWsDTO dto = new VehicleTraceWsDTO();
            dto.setVehicleId(dbId.toString());
            dto.setLatitude(point.getLat());
            dto.setLongitude(point.getLon());
            dto.setSpeed(point.getSpeed());
            dto.setDirection(point.getHeading());
            if(point.getTimestamp() != null){
                dto.setCollectedAt(OffsetDateTime.ofInstant(Instant.ofEpochMilli(point.getTimestamp()), SHANGHAI_ZONE));
            }
            result.add(dto);
        }
        return result;
    }

    /**
     * GET /api/v1/vehicles/db/{dbId}/location/latest
     * 根据数据库主键获取车辆最新GPS点位
     */
    @GetMapping("/db/{dbId}/location/latest")
    public VehicleTraceWsDTO getSingleLatestLocationByDbId(@PathVariable Long dbId){
        var inner = vehicleTraceService.getVehicleLatestPointByDbId(dbId);
        if(inner == null){
            return null;
        }
        VehicleTraceWsDTO dto = new VehicleTraceWsDTO();
        dto.setVehicleId(dbId.toString());
        dto.setLatitude(inner.getLat());
        dto.setLongitude(inner.getLon());
        dto.setSpeed(inner.getSpeed());
        dto.setDirection(inner.getHeading());
        if(inner.getTimestamp() != null){
            dto.setCollectedAt(OffsetDateTime.ofInstant(Instant.ofEpochMilli(inner.getTimestamp()), SHANGHAI_ZONE));
        }
        return dto;
    }

    // ====================== 原有旧接口：simCode字符串，完全保留兼容 ======================
    /**
     * GET /api/v1/vehicles/{simCode}/location‑history?start&end
     * 直接传入simCode设备编号查询轨迹
     */
    @GetMapping("/{simCode}/location-history")
    public List<VehicleTraceWsDTO> getTraceBySimCode(
            @PathVariable String simCode,
            @RequestParam long start,
            @RequestParam long end
    ){
        List<VehicleTracePointDTO> innerList = vehicleTraceService.getVehicleTrace(simCode, start, end);
        List<VehicleTraceWsDTO> result = new ArrayList<>();
        for (VehicleTracePointDTO point : innerList) {
            VehicleTraceWsDTO dto = new VehicleTraceWsDTO();
            dto.setVehicleId(simCode);
            dto.setLatitude(point.getLat());
            dto.setLongitude(point.getLon());
            dto.setSpeed(point.getSpeed());
            dto.setDirection(point.getHeading());
            if(point.getTimestamp() != null){
                dto.setCollectedAt(OffsetDateTime.ofInstant(Instant.ofEpochMilli(point.getTimestamp()), SHANGHAI_ZONE));
            }
            result.add(dto);
        }
        return result;
    }

    /**
     * GET /api/v1/vehicles/{simCode}/location/latest
     * 直接传入simCode获取最新点位
     */
    @GetMapping("/{simCode}/location/latest")
    public VehicleTraceWsDTO getSingleLatestLocationBySimCode(@PathVariable String simCode){
        var inner = vehicleTraceService.getVehicleLatestPoint(simCode);
        if(inner == null){
            return null;
        }
        VehicleTraceWsDTO dto = new VehicleTraceWsDTO();
        dto.setVehicleId(simCode);
        dto.setLatitude(inner.getLat());
        dto.setLongitude(inner.getLon());
        dto.setSpeed(inner.getSpeed());
        dto.setDirection(inner.getHeading());
        if(inner.getTimestamp() != null){
            dto.setCollectedAt(OffsetDateTime.ofInstant(Instant.ofEpochMilli(inner.getTimestamp()), SHANGHAI_ZONE));
        }
        return dto;
    }

    // ====================== 大屏全量车辆最新点位 ======================
    @GetMapping("/locations/latest")
    public List<VehicleTraceWsDTO> getAllVehiclesLatestLocation(){
        List<SimGpsPointDTO> simPointList = vehicleTraceService.getAllVehicleLatestPoints();
        List<VehicleTraceWsDTO> result = new ArrayList<>();

        for (SimGpsPointDTO simPoint : simPointList) {
            VehicleTraceWsDTO dto = new VehicleTraceWsDTO();
            Vehicle vehicle = vehicleService.getVehicleBySimCode(simPoint.getSimCode());
            if(vehicle != null){
                dto.setVehicleId(vehicle.getId().toString());
            }else{
                dto.setVehicleId(null);
            }

            dto.setLatitude(simPoint.getLat());
            dto.setLongitude(simPoint.getLon());
            dto.setSpeed(simPoint.getSpeed());
            dto.setDirection(simPoint.getHeading());
            if(simPoint.getTimestamp() != null){
                dto.setCollectedAt(OffsetDateTime.ofInstant(Instant.ofEpochMilli(simPoint.getTimestamp()), SHANGHAI_ZONE));
            }
            result.add(dto);
        }
        return result;
    }
}
