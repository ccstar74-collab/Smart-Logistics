package com.smart_logistics.backend.service;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.QueryApi;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GpsInfluxService {

    @Resource
    private InfluxDBClient influxDBClient;

    // 读取yml中bucket名称，不要硬编码
    @Value("${influxdb2.bucket}")
    private String bucket;

    /**
     * 写入GPS点，使用系统当前时间（旧接口保留）
     * @param vehicleId 车辆id
     * @param lon 经度
     * @param lat 纬度
     * @param speed 速度
     */
    public void writeGpsPoint(String vehicleId, double lon, double lat, double speed){
        writeGpsPoint(vehicleId, lon, lat, speed, System.currentTimeMillis());
    }

    /**
     * 写入GPS点，使用MQTT报文自带时间戳（MqttMessageCallback调用这个）
     * @param vehicleId 车辆ID
     * @param lat 纬度
     * @param lon 经度
     * @param speed 速度
     * @param ts 毫秒时间戳
     */
    public void writeGpsPoint(String vehicleId, String lat, String lon, double speed, long ts) {
        writeGpsPoint(vehicleId, Double.parseDouble(lon), Double.parseDouble(lat), speed, ts);
    }

    /**
     * 核心实现
     * @param vehicleId 车辆ID
     * @param lon 经度
     * @param lat 纬度
     * @param speed 速度
     * @param ts 毫秒时间戳
     */
    public void writeGpsPoint(String vehicleId, double lon, double lat, double speed, long ts){
        WriteApiBlocking writeApi = influxDBClient.getWriteApiBlocking();
        // NS纳秒：毫秒 *1e6
        long nanoTs = ts * 1_000_000L;
        String lineProtocol = String.format(
                "vehicle_gps,vehicle_id=%s lon=%f,lat=%f,speed=%f %d",
                vehicleId, lon, lat, speed, nanoTs
        );
        writeApi.writeRecord(WritePrecision.NS, lineProtocol);
    }

    /**
     * 查询某车辆一段时间轨迹
     * @param vehicleId 车辆id
     * @param start 开始Instant
     * @param stop 结束Instant
     * @return 轨迹点集合
     */
    public List<Map<String,Object>> queryTrack(String vehicleId, Instant start, Instant stop){
        QueryApi queryApi = influxDBClient.getQueryApi();
        String flux = String.format("""
                from(bucket:"%s")
                |> range(start:%s, stop:%s)
                |> filter(fn: (r) => r._measurement == "vehicle_gps" and r.vehicle_id == "%s")
                |> pivot(rowKey:["_time"], columnKey: ["_field"], valueColumn: "_value")
                """,
                bucket, start.toString(), stop.toString(), vehicleId
        );

        List<FluxTable> tables = queryApi.query(flux);
        List<Map<String,Object>> result = new ArrayList<>();
        for (FluxTable table : tables) {
            for (FluxRecord record : table.getRecords()) {
                Map<String,Object> point = new HashMap<>();
                point.put("time", record.getTime());
                point.put("vehicleId", record.getValueByKey("vehicle_id"));
                point.put("lon", record.getValueByKey("lon"));
                point.put("lat", record.getValueByKey("lat"));
                point.put("speed", record.getValueByKey("speed"));
                result.add(point);
            }
        }
        return result;
    }
}