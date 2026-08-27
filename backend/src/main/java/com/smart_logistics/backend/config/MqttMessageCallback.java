package com.smart_logistics.backend.config;

import com.smart_logistics.backend.dto.RealTimeGpsDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.influxdb.client.InfluxDBClient;
import com.smart_logistics.backend.handler.GpsWebSocketHandler;
import com.smart_logistics.backend.service.GpsInfluxService;
import com.smart_logistics.backend.service.MqttAlertMessageHandler;
import com.smart_logistics.backend.service.MqttAlertRecoveryMessageHandler;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Slf4j
@Component
public class MqttMessageCallback implements MqttCallback {

    @Autowired
    private InfluxDBClient influxDBClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private GpsInfluxService gpsInfluxService;

    @Autowired
    private GpsWebSocketHandler gpsWebSocketHandler;

    @Autowired
    private MqttAlertMessageHandler mqttAlertMessageHandler;

    @Autowired
    private MqttAlertRecoveryMessageHandler mqttAlertRecoveryMessageHandler;

    private static final String ALERT_TOPIC = "iot/carla/alert";
    private static final String ALERT_RECOVERY_TOPIC = "iot/carla/alert/recovery";
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

    public MqttMessageCallback() {
    }

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
        log.debug("收到MQTT消息 topic={}, payload={}", topic, payload);
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
        } else if (ALERT_RECOVERY_TOPIC.equals(topic)) {
            handleAlertRecoveryMessage(payload);
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
            double speed = ((Number) map.getOrDefault("speed", 0d)).doubleValue();
            Double heading = map.get("heading") instanceof Number number
                    ? number.doubleValue() : null;
            long ts = ((Number) map.getOrDefault("timestamp", System.currentTimeMillis())).longValue();

            gpsInfluxService.writeGpsPoint(vehicleId, Double.toString(lat),
                    Double.toString(lon), speed, heading, ts);

            VehicleState state = vehicleStateMap.computeIfAbsent(vehicleId, k -> new VehicleState());
            state.vehicleId = vehicleId;
            state.lat = lat;
            state.lon = lon;
            state.speed = speed;
            state.lastUpdateTs = ts;

            RealTimeGpsDTO dto = objectMapper.readValue(payload, RealTimeGpsDTO.class);
            gpsWebSocketHandler.broadcastGps(dto);

        } catch (Exception e) {
            log.error("GPS消息解析失败 vehicleId={}", vehicleId, e);
        }
    }

    private void handleStatusMessage(String topic, String payload) {
        String vehicleId = parseVehicleId(topic);
        log.info("车辆状态更新 vehicleId={}, payload={}", vehicleId, payload);
    }

    private void handleAlertMessage(String payload) {
        mqttAlertMessageHandler.handle(payload);
    }

    private void handleAlertRecoveryMessage(String payload) {
        mqttAlertRecoveryMessageHandler.handle(payload);
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
