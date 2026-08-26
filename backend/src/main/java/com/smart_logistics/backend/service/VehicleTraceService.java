package com.smart_logistics.backend.service;

import com.smart_logistics.backend.dto.RealTimeGpsDTO;
import com.smart_logistics.backend.dto.VehicleTracePointDTO;
import com.smart_logistics.backend.dto.realtime.GpsSample;
import com.smart_logistics.backend.dto.response.SimGpsPointDTO;
import com.smart_logistics.backend.entity.Vehicle;
import com.smart_logistics.backend.mapper.VehicleMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Delegating to GpsInfluxService for all InfluxDB access. */
@Service
public class VehicleTraceService {
    private final GpsInfluxService gpsInfluxService;
    private final VehicleMapper vehicleMapper;

    public VehicleTraceService(GpsInfluxService gpsInfluxService, VehicleMapper vehicleMapper) {
        this.gpsInfluxService = gpsInfluxService;
        this.vehicleMapper = vehicleMapper;
    }

    /**
     * 根据MySQL车辆主键dbVehicleId查询历史轨迹
     */
    public List<VehicleTracePointDTO> getVehicleTraceByDbId(Long dbVehicleId, long startTs, long endTs) {
        Vehicle vehicle = vehicleMapper.selectById(dbVehicleId);
        if (vehicle == null) {
            return new ArrayList<>();
        }
        return getVehicleTrace(vehicle.getSimCode(), startTs, endTs);
    }

    /**
     * 底层查询：直接使用simCode设备编号查询InfluxDB轨迹
     */
    public List<VehicleTracePointDTO> getVehicleTrace(String vehicleId, long startTs, long endTs) {
        return gpsInfluxService.querySamples(List.of(vehicleId),
                        Instant.ofEpochMilli(startTs), Instant.ofEpochMilli(endTs)).stream()
                .map(sample -> {
                    VehicleTracePointDTO point = new VehicleTracePointDTO();
                    point.setLon(sample.longitude());
                    point.setLat(sample.latitude());
                    point.setSpeed(sample.speed());
                    point.setHeading(sample.direction());
                    point.setTimestamp(sample.collectedAt().toEpochMilli());
                    return point;
                }).toList();
    }

    /**
     * 根据MySQL主键dbVehicleId获取车辆最新GPS点位
     */
    public RealTimeGpsDTO getVehicleLatestPointByDbId(Long dbVehicleId) {
        Vehicle vehicle = vehicleMapper.selectById(dbVehicleId);
        if (vehicle == null) {
            return null;
        }
        return getVehicleLatestPoint(vehicle.getSimCode());
    }

    /**
     * 根据simCode获取单台车辆最新GPS点位
     */
    public RealTimeGpsDTO getVehicleLatestPoint(String simCode) {
        List<GpsSample> samples = gpsInfluxService.querySamples(
                List.of(simCode), Instant.ofEpochMilli(0), Instant.now());
        if (samples.isEmpty()) {
            return null;
        }
        GpsSample last = samples.get(samples.size() - 1);
        RealTimeGpsDTO dto = new RealTimeGpsDTO();
        dto.setVehicleId(null);
        dto.setLon(last.longitude());
        dto.setLat(last.latitude());
        dto.setSpeed(last.speed());
        dto.setHeading(last.direction());
        dto.setTimestamp(last.collectedAt().toEpochMilli());
        return dto;
    }

    /**
     * 获取全部车辆最近7天各自最新GPS点位（大屏初始化）
     */
    public List<SimGpsPointDTO> getAllVehicleLatestPoints() {
        List<Vehicle> vehicles = vehicleMapper.selectList(null);
        List<String> simCodes = vehicles.stream()
                .map(Vehicle::getSimCode)
                .filter(code -> code != null && !code.isEmpty())
                .toList();
        if (simCodes.isEmpty()) {
            return new ArrayList<>();
        }
        List<GpsSample> allSamples = gpsInfluxService.querySamples(
                simCodes, Instant.ofEpochMilli(0), Instant.now());

        // Group by vehicleId and take the last sample for each
        java.util.Map<String, GpsSample> latestByVehicle = new java.util.LinkedHashMap<>();
        for (GpsSample sample : allSamples) {
            latestByVehicle.put(sample.vehicleId(), sample);
        }

        List<SimGpsPointDTO> result = new ArrayList<>();
        for (GpsSample sample : latestByVehicle.values()) {
            SimGpsPointDTO dto = new SimGpsPointDTO();
            dto.setSimCode(sample.vehicleId());
            dto.setDbVehicleId(null);
            dto.setLon(sample.longitude());
            dto.setLat(sample.latitude());
            dto.setSpeed(sample.speed());
            dto.setHeading(sample.direction());
            dto.setTimestamp(sample.collectedAt().toEpochMilli());
            result.add(dto);
        }
        return result;
    }
}
