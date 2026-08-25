package com.smart_logistics.backend.controller;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.QueryApi;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/gps")
public class GpsTrackController {

    private final InfluxDBClient influxDBClient;

    @Value("${influxdb2.bucket}")
    private String bucket;

    public GpsTrackController(InfluxDBClient influxDBClient) {
        this.influxDBClient = influxDBClient;
    }

    /**
     * 查询车辆历史轨迹
     * 访问示例：http://127.0.0.1:8080/api/gps/track/car_001
     */
    @GetMapping("/track/{vehicleId}")
    public List<Map<String, Object>> getVehicleTrack(@PathVariable String vehicleId) {
        List<Map<String, Object>> result = new ArrayList<>();

        String flux = "from(bucket:\"" + bucket + "\") " +
                "|> range(start:-2h) " +
                "|> filter(fn: (r) => r._measurement == \"vehicle_gps\") " +
                "|> filter(fn: (r) => r.vehicleId == \"" + vehicleId + "\")";

        QueryApi queryApi = influxDBClient.getQueryApi();
        List<FluxTable> tables = queryApi.query(flux);

        for (FluxTable table : tables) {
            for (FluxRecord record : table.getRecords()) {
                Map<String, Object> item = new HashMap<>();
                item.put("time", record.getTime());
                item.put("vehicleId", record.getValueByKey("vehicleId"));
                item.put("field", record.getField());
                item.put("value", record.getValue());
                result.add(item);
            }
        }
        return result;
    }
}