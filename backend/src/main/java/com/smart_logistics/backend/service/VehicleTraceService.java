package com.smart_logistics.backend.service;

import com.influxdb.client.QueryApi;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import com.smart_logistics.backend.dto.RealTimeGpsDTO;
import com.smart_logistics.backend.dto.response.SimGpsPointDTO;
import com.smart_logistics.backend.dto.VehicleTracePointDTO;
import com.smart_logistics.backend.entity.Vehicle;
import com.smart_logistics.backend.mapper.VehicleMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class VehicleTraceService {

    @Autowired
    private QueryApi queryApi;

    @Autowired
    private VehicleMapper vehicleMapper;

    @Value("${influxdb2.bucket}")
    private String bucket;

    /**
     * 根据MySQL车辆主键dbVehicleId查询历史轨迹
     * 内部先查询MySQL拿到simCode，再调用InfluxDB查询轨迹
     * @param dbVehicleId MySQL车辆主键id
     * @param startTs 开始时间戳 毫秒
     * @param endTs 结束时间戳 毫秒
     * @return 轨迹点集合；车辆不存在返回空List，按时间从小到大排序
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
     * @param simCode 设备sim编号 sim_018
     * @param startTs 开始时间戳 毫秒
     * @param endTs 结束时间戳 毫秒
     * @return 轨迹点集合，按时间从小到大排序；无数据返回空List
     */
    public List<VehicleTracePointDTO> getVehicleTrace(String simCode, long startTs, long endTs) {
        List<VehicleTracePointDTO> pointList = new ArrayList<>();

        String flux = String.format("""
                from(bucket:"%s")
                |> range(start: %d, stop: %d)
                |> filter(fn: (r) => r._measurement == "vehicle_gps" and r.vehicle_id == "%s")
                |> pivot(rowKey:["_time","vehicle_id"], columnKey: ["_field"], valueColumn: "_value")
                |> sort(columns:["_time"])
                """, bucket, startTs, endTs, simCode);

        List<FluxTable> tables = queryApi.query(flux);

        for (FluxTable table : tables) {
            for (FluxRecord record : table.getRecords()) {
                VehicleTracePointDTO point = new VehicleTracePointDTO();
                point.setLon((Double) record.getValueByKey("lon"));
                point.setLat((Double) record.getValueByKey("lat"));
                point.setSpeed((Double) record.getValueByKey("speed_kmh"));
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
     * 根据MySQL主键dbVehicleId获取车辆最新GPS点位
     * @param dbVehicleId MySQL车辆主键
     * @return 最新GPS数据；车辆不存在 / 无GPS上报返回null
     */
    public RealTimeGpsDTO getVehicleLatestPointByDbId(Long dbVehicleId){
        Vehicle vehicle = vehicleMapper.selectById(dbVehicleId);
        if(vehicle == null){
            return null;
        }
        return getVehicleLatestPoint(vehicle.getSimCode());
    }

    /**
     * 底层查询：根据simCode获取单台车辆最新GPS点位
     * 【仅供Service内部调用，不要直接返回给前端HTTP】
     * @param simCode sim设备编号 sim_018
     * @return RealTimeGpsDTO，dbId字段置null；无点位返回null
     */
    public RealTimeGpsDTO getVehicleLatestPoint(String simCode){
        String flux = String.format("""
                from(bucket:"%s")
                |> range(start: -7d)
                |> filter(fn: (r) => r._measurement == "vehicle_gps" and r.vehicle_id == "%s")
                |> last()
                |> pivot(rowKey:["_time","vehicle_id"], columnKey: ["_field"], valueColumn: "_value")
                """, bucket, simCode);

        List<FluxTable> tables = queryApi.query(flux);
        for (FluxTable table : tables) {
            for (FluxRecord record : table.getRecords()) {
                RealTimeGpsDTO dto = new RealTimeGpsDTO();
                dto.setVehicleId(null);
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
     * 获取全部车辆最近7天各自最新GPS点位（大屏初始化）
     * 返回SimGpsPointDTO，内部填充simCode；dbVehicleId由Controller回填MySQL主键
     * @return SimGpsPointDTO列表；只包含最近7天有GPS上报的设备
     */
    public List<SimGpsPointDTO> getAllVehicleLatestPoints(){
        List<SimGpsPointDTO> result = new ArrayList<>();
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
                SimGpsPointDTO dto = new SimGpsPointDTO();
                String simCode = (String) record.getValueByKey("vehicle_id");
                dto.setSimCode(simCode);
                dto.setDbVehicleId(null);

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