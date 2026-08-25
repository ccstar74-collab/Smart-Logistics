package com.smart_logistics.backend.controller;

import com.smart_logistics.backend.dto.VehicleTracePointDTO;
import com.smart_logistics.backend.service.VehicleTraceService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/vehicle")
public class VehicleTraceController {

    @Autowired
    private VehicleTraceService vehicleTraceService;

    /**
     * 查询车辆历史轨迹
     * @param vehicleId 车辆编号
     * @param start 开始时间戳（毫秒）
     * @param end 结束时间戳（毫秒）
     * @return GPS点位数组
     */
    @GetMapping("/trace")
    public List<VehicleTracePointDTO> getTrace(
            @RequestParam String vehicleId,
            @RequestParam long start,
            @RequestParam long end
    ){
        return vehicleTraceService.getVehicleTrace(vehicleId, start, end);
    }
}