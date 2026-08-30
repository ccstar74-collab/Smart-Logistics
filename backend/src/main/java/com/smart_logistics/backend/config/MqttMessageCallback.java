package com.smart_logistics.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart_logistics.backend.dto.RealTimeGpsDTO;
import com.smart_logistics.backend.dto.response.VehicleTraceWsDTO;
import com.smart_logistics.backend.entity.Vehicle;
import com.smart_logistics.backend.handler.GpsWebSocketHandler;
import com.smart_logistics.backend.service.GpsInfluxService;
import com.smart_logistics.backend.service.MqttAlertMessageHandler;
import com.smart_logistics.backend.service.MqttAlertRecoveryMessageHandler;
import com.smart_logistics.backend.service.VehicleService;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "mqtt", name = "enabled", havingValue = "true")
public class MqttMessageCallback implements MqttCallback {

    @Autowired
    private GpsWebSocketHandler gpsWebSocketHandler;

    @Autowired
    private VehicleService vehicleService;

    @Autowired
    private GpsInfluxService gpsInfluxService;

    @Autowired
    private MqttAlertMessageHandler mqttAlertMessageHandler;

    private final ObjectMapper objectMapper = new ObjectMapper();

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
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload());
        log.info("收到MQTT消息 topic={}, payload={}", topic, payload);
        try {
            dispatchMessage(topic, payload);
        } catch (Exception e) {
            // 外层兜底（含数据库DuplicateKeyException等）：只打日志、跳过该消息，
            // 绝不抛到Paho回调——抛异常会触发connectionLost，
            // 导致单条坏消息让整个MQTT客户端断开。
            // 网络IO类异常同样不主动断开，链路恢复交给Paho的
            // keepAlive检测 + setAutomaticReconnect自动重连机制。
            if (isNetworkIoFailure(e)) {
                log.error("MQTT消息处理遇网络IO异常，跳过该消息，链路恢复交给Paho自动重连 topic={}", topic, e);
            } else {
                log.error("MQTT消息处理异常，跳过该消息 topic={}", topic, e);
            }
        }
    }

    /**
     * 沿异常链判断是否为网络IO故障（连接重置、超时等）。
     * 仅用于区分日志语义，不影响"绝不上抛"的行为。
     */
    private boolean isNetworkIoFailure(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof java.net.SocketException
                    || current instanceof java.net.SocketTimeoutException
                    || current instanceof java.io.EOFException) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return false;
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

            // Influx写入走统一通道（canonical schema：longitude/latitude/speed_kmh/heading），
            // tag vehicle_id存入原始simCode字符串

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

            gpsInfluxService.writeGpsPoint(simCode, Double.toString(lat),
                    Double.toString(lon), speed_kmh, heading, ts);

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

    private void handleAlertRecoveryMessage(String payload) {
        mqttAlertRecoveryMessageHandler.handle(payload);
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