package com.smart_logistics.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.influxdb.client.WriteApi;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import com.smart_logistics.backend.dto.RealTimeGpsDTO;
import com.smart_logistics.backend.dto.response.VehicleTraceWsDTO;
import com.smart_logistics.backend.handler.GpsWebSocketHandler;
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

    // ========= 这里就是 WriteApi 注入位置 =========
    @Autowired
    private WriteApi writeApi;

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
        String vehicleId = parseVehicleId(topic);
        try {
            Map<String, Object> map = objectMapper.readValue(payload, Map.class);

            double lat = ((Number) map.get("lat")).doubleValue();
            double lon = ((Number) map.get("lon")).doubleValue();
            double speed_kmh = ((Number) map.getOrDefault("speed_kmh", 0d)).doubleValue();
            double heading = ((Number) map.getOrDefault("heading", 0d)).doubleValue();

            String timestamp = String.valueOf(map.get("timestamp"));
            long ts = Instant.parse(timestamp).toEpochMilli();

            VehicleState state = vehicleStateMap.computeIfAbsent(vehicleId, k -> new VehicleState());
            state.vehicleId = vehicleId;
            state.lat = lat;
            state.lon = lon;
            state.speed = speed_kmh;
            state.lastUpdateTs = ts;

            RealTimeGpsDTO internalDto = new RealTimeGpsDTO();
            internalDto.setVehicleId(vehicleId);
            internalDto.setLon(lon);
            internalDto.setLat(lat);
            internalDto.setSpeed(speed_kmh);
            internalDto.setHeading(heading);
            internalDto.setTimestamp(ts);

            // ========= 这里就是 InfluxDB Point 写入代码 =========
            Point point = Point.measurement("vehicle_gps")
                    .addTag("vehicle_id", vehicleId)
                    .addField("lat", lat)
                    .addField("lon", lon)
                    .addField("speed_kmh", speed_kmh)
                    .addField("heading", heading)
                    .time(Instant.ofEpochMilli(ts), WritePrecision.MS);
            writeApi.writePoint(point);

            // 转换WebSocket对外输出DTO
            VehicleTraceWsDTO outDto = new VehicleTraceWsDTO();
            outDto.setVehicleId(vehicleId);
            outDto.setLatitude(internalDto.getLat());
            outDto.setLongitude(internalDto.getLon());
            outDto.setSpeed(internalDto.getSpeed());
            outDto.setDirection(internalDto.getHeading());
            OffsetDateTime collectedAt = OffsetDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.systemDefault());
            outDto.setCollectedAt(collectedAt);

            gpsWebSocketHandler.broadcastGps(outDto);

        } catch (Exception e) {
            log.error("GPS消息解析/写入失败 vehicleId={}", vehicleId, e);
        }
    }

    private void handleStatusMessage(String topic, String payload) {
        String vehicleId = parseVehicleId(topic);
        log.info("车辆状态更新 vehicleId={}, payload={}", vehicleId, payload);
    }

    private void handleAlertMessage(String payload) {
        log.warn("收到车辆告警：{}", payload);
    }

    private void handleCommandAck(String topic, String payload) {
        String vehicleId = parseVehicleId(topic);
        log.info("车辆指令应答 vehicleId={}, ack={}", vehicleId, payload);
    }

    private String parseVehicleId(String topic) {
        String[] parts = topic.split("/");
        if (parts.length >= 4) {
            return parts[3];
        }
        return "unknown";
    }

    public CopyOnWriteArrayList<VehicleState> getAllVehicleState() {
        return new CopyOnWriteArrayList<>(vehicleStateMap.values());
    }

    public VehicleState getSingleVehicle(String vehicleId) {
        return vehicleStateMap.get(vehicleId);
    }
}