package com.smart_logistics.backend.config;

import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

/**
 * MQTT连接已由MqttConfig的@Bean方法完成初始化和订阅，
 * 本Runner仅作为启动日志标记，不再重复连接。
 * 与MqttConfig保持同一开关：mqtt.enabled=false时不创建，
 * 避免无条件依赖MqttClient Bean导致应用上下文启动失败。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "mqtt", name = "enabled", havingValue = "true")
public class MqttStartRunner implements ApplicationRunner {

    @Autowired
    private MqttClient mqttClient;

    @Autowired
    private MqttAsyncClient mqttPubClient;

    @Override
    public void run(ApplicationArguments args) {
        log.info("MQTT订阅客户端已连接 isConnected={}", mqttClient.isConnected());
        log.info("MQTT发布客户端已连接 isConnected={}", mqttPubClient.isConnected());
    }
}
