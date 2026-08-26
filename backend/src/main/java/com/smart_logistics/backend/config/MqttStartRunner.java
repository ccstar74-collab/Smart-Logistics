package com.smart_logistics.backend.config;

import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

/**
 * MQTT连接已由MqttConfig的@Bean方法完成初始化和订阅，
 * 本Runner仅作为启动日志标记，不再重复连接。
 */
@Slf4j
@Component
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
