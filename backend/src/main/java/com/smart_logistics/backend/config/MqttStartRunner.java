package com.smart_logistics.backend.config;

import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MqttStartRunner implements ApplicationRunner {

    @Autowired
    private MqttClient mqttClient;

    @Autowired
    private MqttAsyncClient mqttPubClient;

    @Autowired
    private MqttConfig mqttConfig;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setUserName(mqttConfig.getUsername());
        options.setPassword(mqttConfig.getPassword().toCharArray());
        options.setCleanSession(true);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(20);
        options.setAutomaticReconnect(true);

        mqttClient.connect(options);
        log.info("MQTT订阅客户端连接成功，broker:{}", mqttConfig.getBroker());
        mqttClient.subscribe(mqttConfig.getSubscribeTopics(), mqttConfig.getQosLevels());
        log.info("MQTT已订阅主题：{}", String.join(",", mqttConfig.getSubscribeTopics()));

        MqttConnectOptions pubOptions = new MqttConnectOptions();
        pubOptions.setUserName(mqttConfig.getUsername());
        pubOptions.setPassword(mqttConfig.getPassword().toCharArray());
        pubOptions.setCleanSession(true);
        pubOptions.setAutomaticReconnect(true);
        pubOptions.setConnectionTimeout(10);
        pubOptions.setKeepAliveInterval(20);

        mqttPubClient.connect(pubOptions);
        log.info("MQTT发布客户端连接成功，broker:{}", mqttConfig.getBroker());
    }
}