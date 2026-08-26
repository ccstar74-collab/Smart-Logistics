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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
@Configuration
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

    private static final String[] SUBSCRIBE_TOPICS = {
            "iot/carla/vehicle/+/gps",
            "iot/carla/vehicle/+/status",
            "iot/carla/alert",
            "iot/carla/vehicle/+/command/ack"
    };
    private static final int[] QOS_LEVELS = {1,1,1,1};

    private final InfluxDBClient influxDBClient;

    @Autowired
    private MqttMessageCallback mqttMessageCallback;

    public MqttConfig(InfluxDBClient influxDBClient) {
        this.influxDBClient = influxDBClient;
    }

    @Bean(destroyMethod = "close")
    public MqttClient mqttClient() throws MqttException {
        MemoryPersistence persistence = new MemoryPersistence();
        String suffix = UUID.randomUUID().toString().substring(0,8);
        String realClientId = baseClientId + "_" + suffix;
        MqttClient client = new MqttClient(broker, realClientId, persistence);
        client.setCallback(mqttMessageCallback);
        log.info("MQTT订阅客户端对象创建完成，clientId:{}", realClientId);
        return client;
    }

    @Bean(destroyMethod = "close")
    public MqttAsyncClient mqttPubClient() throws MqttException {
        MemoryPersistence persistence = new MemoryPersistence();
        String suffix = UUID.randomUUID().toString().substring(0,8);
        String realPubClientId = basePubClientId + "_" + suffix;
        MqttAsyncClient pubClient = new MqttAsyncClient(broker, realPubClientId, persistence);
        log.info("MQTT发布客户端对象创建完成，clientId:{}", realPubClientId);
        return pubClient;
    }

    public void publish(MqttAsyncClient mqttPubClient, String topic, String payload, int qos) {
        try {
            if (!mqttPubClient.isConnected()) {
                log.warn("发布客户端未连接，跳过发布 topic={}",topic);
                return;
            }
            MqttMessage msg = new MqttMessage(payload.getBytes());
            msg.setQos(qos);
            mqttPubClient.publish(topic, msg);
            log.debug("mqtt发布 topic={}, payload={}",topic,payload);
        } catch (MqttException e) {
            log.error("mqtt发布失败 topic={}",topic,e);
        }
    }
    public void publish(MqttAsyncClient mqttPubClient, String topic, String payload) {
        publish(mqttPubClient, topic, payload, 0);
    }

    public String[] getSubscribeTopics() {
        return SUBSCRIBE_TOPICS;
    }
    public int[] getQosLevels() {
        return QOS_LEVELS;
    }
    public String getBroker() {
        return broker;
    }
    public String getUsername() {
        return username;
    }
    public String getPassword() {
        return password;
    }
}