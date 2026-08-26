package com.smart_logistics.backend.config;

import com.influxdb.client.InfluxDBClient;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "mqtt", name = "enabled", havingValue = "true")
public class MqttConfig {

    @Value("${mqtt.broker}")
    private String broker;

    @Value("${mqtt.client-id}")
    private String baseClientId;

    @Value("${mqtt.username}")
    private String username;

    @Value("${mqtt.password}")
    private String password;

    @Value("${mqtt.clientId.pub:carla_backend_pub}")
    private String basePubClientId;

    private static final String ALERT_TOPIC = "iot/carla/alert";

    @Value("${mqtt.realtime-enabled:false}")
    private boolean realtimeEnabled;

    private final InfluxDBClient influxDBClient;

    @Autowired
    private MqttMessageCallback mqttMessageCallback;

    public MqttConfig(InfluxDBClient influxDBClient) {
        this.influxDBClient = influxDBClient;
    }

    @Bean(destroyMethod = "close")
    public MqttClient mqttClient() throws MqttException {
        MemoryPersistence persistence = new MemoryPersistence();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String realClientId = baseClientId + "_" + suffix;
        MqttClient mqttClient = new MqttClient(broker, realClientId, persistence);

        MqttConnectOptions options = new MqttConnectOptions();
        applyCredentials(options);
        options.setCleanSession(true);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(20);
        options.setAutomaticReconnect(true);

        mqttClient.setCallback(mqttMessageCallback);
        mqttClient.connect(options);
        log.info("MQTT订阅客户端连接成功，broker:{}，clientId:{}", broker, realClientId);

        String[] topics = subscriptionTopics();
        int[] qosLevels = new int[topics.length];
        java.util.Arrays.fill(qosLevels, 1);
        mqttClient.subscribe(topics, qosLevels);
        log.info("MQTT已订阅主题：{}", String.join(",", topics));
        return mqttClient;
    }

    @Bean(destroyMethod = "close")
    public MqttAsyncClient mqttPubClient() throws MqttException {
        MemoryPersistence persistence = new MemoryPersistence();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String realPubClientId = basePubClientId + "_" + suffix;
        MqttAsyncClient pubClient = new MqttAsyncClient(broker, realPubClientId, persistence);

        MqttConnectOptions options = new MqttConnectOptions();
        applyCredentials(options);
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(20);

        pubClient.connect(options);
        log.info("MQTT发布客户端连接成功，broker:{}，clientId:{}", broker, realPubClientId);
        return pubClient;
    }

    public void publish(MqttAsyncClient mqttPubClient, String topic, String payload, int qos) {
        try {
            if (!mqttPubClient.isConnected()) {
                log.warn("发布客户端未连接，跳过发布 topic={}", topic);
                return;
            }
            MqttMessage msg = new MqttMessage(payload.getBytes());
            msg.setQos(qos);
            mqttPubClient.publish(topic, msg);
            log.debug("mqtt发布 topic={}, payload={}", topic, payload);
        } catch (MqttException e) {
            log.error("mqtt发布失败 topic={}", topic, e);
        }
    }

    public void publish(MqttAsyncClient mqttPubClient, String topic, String payload) {
        publish(mqttPubClient, topic, payload, 0);
    }

    private void applyCredentials(MqttConnectOptions options) {
        if (username != null && !username.isBlank()) {
            options.setUserName(username);
        }
        if (password != null && !password.isBlank()) {
            options.setPassword(password.toCharArray());
        }
    }

    private String[] subscriptionTopics() {
        List<String> topics = new ArrayList<>();
        topics.add(ALERT_TOPIC);
        if (realtimeEnabled) {
            topics.add("iot/carla/vehicle/+/gps");
            topics.add("iot/carla/vehicle/+/status");
            topics.add("iot/carla/vehicle/+/command/ack");
        }
        return topics.toArray(String[]::new);
    }
}
