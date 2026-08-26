package com.smart_logistics.backend.service;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.QueryApi;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import com.smart_logistics.backend.dto.realtime.GpsFieldRecord;
import com.smart_logistics.backend.dto.realtime.GpsSample;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GpsInfluxService {

    private static final String MEASUREMENT = "vehicle_gps";
    private static final Set<String> GPS_FIELDS =
            Set.of("latitude", "longitude", "speed_kmh", "heading",
                    "lat", "lon", "speed", "direction");

    @Resource
    private InfluxDBClient influxDBClient;

    // 读取yml中bucket名称，不要硬编码
    @Value("${influxdb2.bucket}")
    private String bucket;

    @Resource
    private GpsSampleReconstructor gpsSampleReconstructor;

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
     * Writes the production MQTT GPS schema. Heading is omitted when the source payload does not
     * provide it; coordinates and speed always use the canonical field names.
     */
    public void writeGpsPoint(String vehicleId, String lat, String lon, double speed,
                              Double heading, long ts) {
        writeGpsPoint(vehicleId, Double.parseDouble(lon), Double.parseDouble(lat),
                speed, heading, ts);
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
        writeGpsPoint(vehicleId, lon, lat, speed, null, ts);
    }

    private void writeGpsPoint(String vehicleId, double lon, double lat, double speed,
                               Double heading, long ts) {
        WriteApiBlocking writeApi = influxDBClient.getWriteApiBlocking();
        // NS纳秒：毫秒 *1e6
        long nanoTs = ts * 1_000_000L;
        String fields = String.format(Locale.ROOT,
                "longitude=%f,latitude=%f,speed_kmh=%f", lon, lat, speed);
        if (heading != null && Double.isFinite(heading)) {
            fields += String.format(Locale.ROOT, ",heading=%f", heading);
        }
        String lineProtocol = String.format(Locale.ROOT,
                "%s,vehicle_id=%s %s %d",
                MEASUREMENT, escapeTagValue(vehicleId), fields, nanoTs);
        writeApi.writeRecord(WritePrecision.NS, lineProtocol);
    }

    /**
     * 查询某车辆一段时间轨迹
     * @param vehicleId 车辆id
     * @param start 开始Instant
     * @param stop 结束Instant
     * @return 轨迹点集合
     */
    public List<GpsSample> querySamples(Collection<String> vehicleIds,
                                        Instant start, Instant stop) {
        List<String> normalizedIds = normalizeVehicleIds(vehicleIds);
        if (normalizedIds.isEmpty()) {
            return List.of();
        }
        validateRange(start, stop);

        String vehicleSet = normalizedIds.stream()
                .map(this::fluxString)
                .collect(Collectors.joining(","));
        String flux = String.format("""
                from(bucket: %s)
                |> range(start: time(v: %s), stop: time(v: %s))
                |> filter(fn: (r) => r._measurement == %s)
                |> filter(fn: (r) => contains(value: r.vehicle_id, set: [%s]))
                |> filter(fn: (r) => contains(value: r._field, set: ["latitude","longitude","speed_kmh","heading","lat","lon","speed","direction"]))
                |> keep(columns: ["_time","_field","_value","vehicle_id"])
                |> sort(columns: ["_time"])
                """,
                fluxString(bucket), fluxString(start.toString()), fluxString(stop.toString()),
                fluxString(MEASUREMENT), vehicleSet
        );
        return executeQuery(flux);
    }

    /**
     * Returns at most the latest value for each vehicle and GPS field within the range. The
     * server-side reduction preserves the full lookback window without transferring its entire
     * high-frequency time series to the application.
     */
    public List<GpsSample> queryLatestSamples(Collection<String> vehicleIds,
                                              Instant start, Instant stop) {
        List<String> normalizedIds = normalizeVehicleIds(vehicleIds);
        if (normalizedIds.isEmpty()) {
            return List.of();
        }
        validateRange(start, stop);

        String vehicleSet = normalizedIds.stream()
                .map(this::fluxString)
                .collect(Collectors.joining(","));
        String flux = String.format("""
                from(bucket: %s)
                |> range(start: time(v: %s), stop: time(v: %s))
                |> filter(fn: (r) => r._measurement == %s)
                |> filter(fn: (r) => contains(value: r.vehicle_id, set: [%s]))
                |> filter(fn: (r) => contains(value: r._field, set: ["latitude","longitude","speed_kmh","heading","lat","lon","speed","direction"]))
                |> group(columns: ["vehicle_id", "_field"])
                |> last()
                |> keep(columns: ["_time","_field","_value","vehicle_id"])
                """,
                fluxString(bucket), fluxString(start.toString()), fluxString(stop.toString()),
                fluxString(MEASUREMENT), vehicleSet
        );
        return executeQuery(flux);
    }

    private List<GpsSample> executeQuery(String flux) {
        QueryApi queryApi = influxDBClient.getQueryApi();
        List<FluxTable> tables = queryApi.query(flux);
        List<GpsFieldRecord> rawRecords = new ArrayList<>();
        for (FluxTable table : tables) {
            for (FluxRecord record : table.getRecords()) {
                Object vehicleId = record.getValueByKey("vehicle_id");
                Object value = record.getValue();
                if (vehicleId != null && record.getTime() != null
                        && GPS_FIELDS.contains(record.getField()) && value instanceof Number number) {
                    rawRecords.add(new GpsFieldRecord(vehicleId.toString(), record.getField(),
                            number.doubleValue(), record.getTime()));
                }
            }
        }
        return gpsSampleReconstructor.reconstruct(rawRecords);
    }

    private List<String> normalizeVehicleIds(Collection<String> vehicleIds) {
        return vehicleIds.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
    }

    private void validateRange(Instant start, Instant stop) {
        if (start == null || stop == null || !start.isBefore(stop)) {
            throw new IllegalArgumentException("GPS query range must have start before stop");
        }
    }

    /**
     * Legacy adapter retained for development callers. Official Phase 5 controllers use
     * {@link #querySamples(Collection, Instant, Instant)} through business services.
     */
    public List<Map<String,Object>> queryTrack(String vehicleId, Instant start, Instant stop){
        return querySamples(List.of(vehicleId), start, stop).stream().map(sample -> {
            Map<String, Object> point = new HashMap<>();
            point.put("time", sample.collectedAt());
            point.put("vehicleId", sample.vehicleId());
            point.put("lon", sample.longitude());
            point.put("lat", sample.latitude());
            point.put("speed", sample.speed());
            point.put("direction", sample.direction());
            return point;
        }).toList();
    }

    private String fluxString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private String escapeTagValue(String value) {
        return value.replace("\\", "\\\\")
                .replace(" ", "\\ ")
                .replace(",", "\\,")
                .replace("=", "\\=");
    }
}
