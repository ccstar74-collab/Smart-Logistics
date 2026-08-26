package com.smart_logistics.backend.service;

import com.influxdb.client.QueryApi;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import com.smart_logistics.backend.dto.RealTimeGpsDTO;
import com.smart_logistics.backend.dto.VehicleTracePointDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class VehicleTraceService {

    @Autowired
    private QueryApi queryApi;

    @Value("${influxdb2.bucket}")
    private String bucket;

    /**
     * 查询车辆历史轨迹
     * @param vehicleId 车辆id, example sim_000
     * @param startTs 开始时间戳 毫秒
     * @param endTs 结束时间戳 毫秒
     * @return 轨迹点集合,按时间从小到大排序
     */
    public List<VehicleTracePointDTO> getVehicleTrace(String vehicleId, long startTs, long endTs) {
        List<VehicleTracePointDTO> pointList = new ArrayList<>();

        String flux = String.format("""
                from(bucket:"%s")
                |> range(start: %d, stop: %d)
                |> filter(fn: (r) => r._measurement == "vehicle_gps" and r.vehicle_id == "%s")
                |> pivot(rowKey:["_time","vehicle_id"], columnKey: ["_field"], valueColumn: "_value")
                |> sort(columns:["_time"])
                """, bucket, startTs, endTs, vehicleId);

        List<FluxTable> tables = queryApi.query(flux);

        for (FluxTable table : tables) {
            for (FluxRecord record : table.getRecords()) {
                VehicleTracePointDTO point = new VehicleTracePointDTO();
                point.setLon((Double) record.getValueByKey("lon"));
                point.setLat((Double) record.getValueByKey("lat"));
                point.setSpeed((Double) record.getValueByKey("speed_kmh"));
                // heading字段不存在，null兼容
                Object headingObj = record.getValueByKey("heading");
                point.setHeading(headingObj != null ? (Double) headingObj : null);
                if(record.getTime() != null){
                    point.setTimestamp(record.getTime().toEpochMilli());
                }
                pointList.add(point);
            }
        }
        return pointList;
    }

    /**
     * 获取单台车辆最新GPS点位
     * @param vehicleId 车辆ID
     * @return 最新GPS数据,无数据返回null
     */
    public RealTimeGpsDTO getVehicleLatestPoint(String vehicleId){
        String flux = String.format("""
                from(bucket:"%s")
                |> range(start: -7d)
                |> filter(fn: (r) => r._measurement == "vehicle_gps" and r.vehicle_id == "%s")
                |> last()
                |> pivot(rowKey:["_time","vehicle_id"], columnKey: ["_field"], valueColumn: "_value")
                """, bucket, vehicleId);

        List<FluxTable> tables = queryApi.query(flux);
        for (FluxTable table : tables) {
            for (FluxRecord record : table.getRecords()) {
                RealTimeGpsDTO dto = new RealTimeGpsDTO();
                dto.setVehicleId(vehicleId);
                dto.setLon((Double) record.getValueByKey("lon"));
                dto.setLat((Double) record.getValueByKey("lat"));
                dto.setSpeed((Double) record.getValueByKey("speed_kmh"));
                Object headingObj = record.getValueByKey("heading");
                dto.setHeading(headingObj != null ? (Double) headingObj : null);
                if(record.getTime() != null){
                    dto.setTimestamp(record.getTime().toEpochMilli());
                }
                return dto;
            }
        }
        return null;
    }

    /**
     * 获取全部车辆各自最新GPS点位（调度大屏）
     * @return 所有车辆最新GPS列表
     */
    public List<RealTimeGpsDTO> getAllVehicleLatestPoints(){
        List<RealTimeGpsDTO> result = new ArrayList<>();
        String flux = String.format("""
                from(bucket:"%s")
                |> range(start: -7d)
                |> filter(fn: (r) => r._measurement == "vehicle_gps")
                |> last()
                |> pivot(rowKey:["_time","vehicle_id"], columnKey: ["_field"], valueColumn: "_value")
                |> group()
                """, bucket);

        List<FluxTable> tables = queryApi.query(flux);
        for (FluxTable table : tables) {
            for (FluxRecord record : table.getRecords()) {
                RealTimeGpsDTO dto = new RealTimeGpsDTO();
                // 修复tag key：vehicle_id，不是vehicleID
                String vid = (String) record.getValueByKey("vehicle_id");
                dto.setVehicleId(vid);
                dto.setLon((Double) record.getValueByKey("lon"));
                dto.setLat((Double) record.getValueByKey("lat"));
                dto.setSpeed((Double) record.getValueByKey("speed_kmh"));
                Object headingObj = record.getValueByKey("heading");
                dto.setHeading(headingObj != null ? (Double) headingObj : null);
                if(record.getTime() != null){
                    dto.setTimestamp(record.getTime().toEpochMilli());
                }
                result.add(dto);
            }
        }
        return result;
    }
}