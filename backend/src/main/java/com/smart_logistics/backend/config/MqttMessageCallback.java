package com.smart_logistics.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.influxdb.client.WriteApi;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import com.smart_logistics.backend.dto.RealTimeGpsDTO;
import com.smart_logistics.backend.dto.response.VehicleTraceWsDTO;
import com.smart_logistics.backend.entity.Vehicle;
import com.smart_logistics.backend.handler.GpsWebSocketHandler;
import com.smart_logistics.backend.service.GpsInfluxService;
import com.smart_logistics.backend.service.MqttAlertMessageHandler;
import com.smart_logistics.backend.service.VehicleService;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Slf4j
@Component
public class MqttMessageCallback implements MqttCallback {

    @Autowired
    private GpsWebSocketHandler gpsWebSocketHandler;

    @Autowired
    private WriteApi writeApi;

    @Autowired
    private VehicleService vehicleService;

    @Autowired
    private GpsInfluxService gpsInfluxService;

    @Autowired
    private MqttAlertMessageHandler mqttAlertMessageHandler;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String ALERT_TOPIC = "iot/carla/alert";
    private static final String COMMAND_ACK_SUFFIX = "/command/ack";

    public static class VehicleState {
        String vehicleId;
        double lat;
        double lon;
        double speed;
        long lastUpdateTs;
    }

    private final ConcurrentHashMap<String, VehicleState> vehicleStateMap = new ConcurrentHashMap<>();
    private ScheduledExecutorService aggExecutor;

    @PostConstruct
    public void init() {
        aggExecutor = Executors.newSingleThreadScheduledExecutor();
        log.info("MqttMessageCallback 业务处理器初始化完成");
    }

    @PreDestroy
    public void destroy() {
        if (aggExecutor != null) {
            aggExecutor.shutdown();
        }
        log.info("MqttMessageCallback 线程池已关闭");
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("MQTT连接丢失", cause);
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        String payload = new String(message.getPayload());
        log.info("收到MQTT消息 topic={}, payload={}", topic, payload);
        try {
            dispatchMessage(topic, payload);
        } catch (Exception e) {
            log.error("消息处理异常 topic={}", topic, e);
            throw e;
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
    }

    private void dispatchMessage(String topic, String payload) {
        if (topic.startsWith("iot/carla/vehicle/") && topic.endsWith("/gps")) {
            handleGpsMessage(topic, payload);
        } else if (topic.startsWith("iot/carla/vehicle/") && topic.endsWith("/status")) {
            handleStatusMessage(topic, payload);
        } else if (ALERT_TOPIC.equals(topic)) {
            handleAlertMessage(payload);
        } else if (topic.endsWith(COMMAND_ACK_SUFFIX)) {
            handleCommandAck(topic, payload);
        } else {
            log.warn("未识别的topic:{}", topic);
        }
    }

    private void handleGpsMessage(String topic, String payload) {
        // parseVehicleId返回的是simCode(sim_018)，设备外部编号，不是数据库Long主键
        String simCode = parseVehicleId(topic);
        try {
            Map<String, Object> map = objectMapper.readValue(payload, Map.class);

            double lat = ((Number) map.get("lat")).doubleValue();
            double lon = ((Number) map.get("lon")).doubleValue();
            double speed_kmh = ((Number) map.getOrDefault("speed_kmh", 0d)).doubleValue();
            double heading = ((Number) map.getOrDefault("heading", 0d)).doubleValue();

            String timestamp = String.valueOf(map.get("timestamp"));
            long ts = Instant.parse(timestamp).toEpochMilli();

            // vehicleStateMap key 使用simCode，内存状态以simCode区分设备
            VehicleState state = vehicleStateMap.computeIfAbsent(simCode, k -> new VehicleState());
            state.vehicleId = simCode;
            state.lat = lat;
            state.lon = lon;
            state.speed = speed_kmh;
            state.lastUpdateTs = ts;

            RealTimeGpsDTO internalDto = new RealTimeGpsDTO();
            internalDto.setVehicleId(null); // RealTimeGpsDTO vehicleId为Long数据库主键，此处尚未查询数据库，赋值null；该字段后续不会读取，不影响业务
            internalDto.setLon(lon);
            internalDto.setLat(lat);
            internalDto.setSpeed(speed_kmh);
            internalDto.setHeading(heading);
            internalDto.setTimestamp(ts);

            // InfluxDB tag vehicle_id存入原始simCode字符串 sim_018
            Point point = Point.measurement("vehicle_gps")
                    .addTag("vehicle_id", simCode)
                    .addField("lat", lat)
                    .addField("lon", lon)
                    .addField("speed_kmh", speed_kmh)
                    .addField("heading", heading)
                    .time(Instant.ofEpochMilli(ts), WritePrecision.MS);
            writeApi.writePoint(point);

            VehicleTraceWsDTO outDto = new VehicleTraceWsDTO();
            outDto.setLatitude(internalDto.getLat());
            outDto.setLongitude(internalDto.getLon());
            outDto.setSpeed(internalDto.getSpeed());
            outDto.setDirection(internalDto.getHeading());
            OffsetDateTime collectedAt = OffsetDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.systemDefault());
            outDto.setCollectedAt(collectedAt);

            // 通过simCode查询MySQL记录
            Vehicle vehicle = vehicleService.getVehicleBySimCode(simCode);
            if (vehicle != null) {
                // DTO vehicleId字段为String，数据库Long主键转为字符串
                outDto.setVehicleId(vehicle.getId().toString());
                outDto.setSimCode(vehicle.getSimCode());
            } else {
                log.warn("数据库无simCode={}对应的车辆记录，设备未注册", simCode);
                outDto.setVehicleId(null);
                outDto.setSimCode(null);
            }

            gpsWebSocketHandler.broadcastGps(outDto);

        } catch (Exception e) {
            log.error("GPS消息解析/写入失败 simCode={}", simCode, e);
        }
    }

    private void handleStatusMessage(String topic, String payload) {
        String simCode = parseVehicleId(topic);
        log.info("车辆状态更新 simCode={}, payload={}", simCode, payload);
    }

    private void handleAlertMessage(String payload) {
        mqttAlertMessageHandler.handle(payload);
    }

    private void handleCommandAck(String topic, String payload) {
        log.info("收到命令确认 topic={}, payload={}", topic, payload);
    }

    private String parseVehicleId(String topic) {
        // topic format: iot/carla/vehicle/{simCode}/gps or iot/carla/vehicle/{simCode}/status
        String[] parts = topic.split("/");
        return parts.length >= 4 ? parts[3] : null;
    }
}
