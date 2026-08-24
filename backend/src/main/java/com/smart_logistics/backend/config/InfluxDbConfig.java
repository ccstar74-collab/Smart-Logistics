package com.smart_logistics.backend.config;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.QueryApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InfluxDbConfig {

    @Value("${influxdb2.url}")
    private String url;

    @Value("${influxdb2.token}")
    private String token;

    @Value("${influxdb2.org}")
    private String org;

    @Value("${influxdb2.bucket}")
    private String bucket;

    @Bean
    public InfluxDBClient influxDBClient(){
        InfluxDBClient client = InfluxDBClientFactory.create(url, token.toCharArray(), org, bucket);
        System.out.println("InfluxDB客户端初始化完成，url="+url+", org="+org+", bucket="+bucket);
        return client;
    }

    //新增这个Bean！供Service注入查询对象
    @Bean
    public QueryApi queryApi(InfluxDBClient influxDBClient){
        return influxDBClient.getQueryApi();
    }
}