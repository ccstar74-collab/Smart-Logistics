package com.smart_logistics.backend.service;
import com.influxdb.client.QueryApi;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
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

    @Value("${influxdb.bucket}")
    private String bucket;

    /**
     * 查询车辆历史轨迹
     * @param vehicleId 车辆id，例如 sim_000
     * @param startTs 开始时间戳 毫秒
     * @param endTs 结束时间戳 毫秒
     * @return 轨迹点集合，按时间从小到大排序
     */
    public List<VehicleTracePointDTO> getVehicleTrace(String vehicleId, long startTs, long endTs) {
        List<VehicleTracePointDTO> pointList = new ArrayList<>();

        // Flux查询语句
        String flux = String.format("""
                from(bucket:"%s")
                |> range(start: %d, stop: %d)
                |> filter(fn: (r) => r._measurement == "vehicle_gps" and r.vehicle_id == "%s")
                |> pivot(rowKey:["_time"], columnKey: ["_field"], valueColumn: "_value")
                |> sort(columns:["_time"])
                """, bucket, startTs, endTs, vehicleId);

        // 执行查询
        List<FluxTable> tables = queryApi.query(flux);

        // 解析结果
        for (FluxTable table : tables) {
            for (FluxRecord record : table.getRecords()) {
                VehicleTracePointDTO point = new VehicleTracePointDTO();
                point.setLng((Double) record.getValueByKey("lng"));
                point.setLat((Double) record.getValueByKey("lat"));
                point.setSpeed((Double) record.getValueByKey("speed"));
                point.setHeading((Double) record.getValueByKey("heading"));
                if(record.getTime() != null){
                    point.setTimestamp(record.getTime().toEpochMilli());
                }
                pointList.add(point);
            }
        }
        return pointList;
    }
}