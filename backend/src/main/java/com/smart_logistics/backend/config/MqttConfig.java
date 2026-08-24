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

@Slf4j
@Configuration
public class MqttConfig {

    @Value("${mqtt.broker}")
    private String broker;

    @Value("${mqtt.client-id}")
    private String clientId;

    @Value("${mqtt.username}")
    private String username;

    @Value("${mqtt.password}")
    private String password;

    @Value("${mqtt.clientId.pub:carla_backend_pub}")
    private String pubClientId;

    private static final String[] SUBSCRIBE_TOPICS = {
            "iot/carla/vehicle/+/gps",
            "iot/carla/vehicle/+/status",
            "iot/carla/alert",
            "iot/carla/vehicle/+/command/ack"
    };
    private static final int[] QOS_LEVELS = {1,1,1,1};

    private final InfluxDBClient influxDBClient;

    // 方案A：注入Spring管理的回调Bean
    @Autowired
    private MqttMessageCallback mqttMessageCallback;

    public MqttConfig(InfluxDBClient influxDBClient) {
        this.influxDBClient = influxDBClient;
    }

    @Bean
    public MqttClient mqttClient() throws MqttException {
        MemoryPersistence persistence = new MemoryPersistence();
        MqttClient mqttClient = new MqttClient(broker, clientId, persistence);

        MqttConnectOptions options = new MqttConnectOptions();
        options.setUserName(username);
        options.setPassword(password.toCharArray());
        options.setCleanSession(true);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(20);
        options.setAutomaticReconnect(true);

        // 使用Spring的Bean实例，不再手动new
        mqttClient.setCallback(mqttMessageCallback);

        mqttClient.connect(options);
        log.info("MQTT订阅客户端连接成功，broker:{}，clientId:{}", broker, clientId);

        mqttClient.subscribe(SUBSCRIBE_TOPICS, QOS_LEVELS);
        log.info("MQTT已订阅主题：{}", String.join(",",SUBSCRIBE_TOPICS));
        return mqttClient;
    }

    @Bean(destroyMethod = "close")
    public MqttAsyncClient mqttPubClient() throws MqttException {
        MemoryPersistence persistence = new MemoryPersistence();
        MqttAsyncClient pubClient = new MqttAsyncClient(broker, pubClientId, persistence);

        MqttConnectOptions options = new MqttConnectOptions();
        options.setUserName(username);
        options.setPassword(password.toCharArray());
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(20);

        pubClient.connect(options);
        log.info("MQTT发布客户端连接成功，broker:{}，clientId:{}", broker, pubClientId);
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
}