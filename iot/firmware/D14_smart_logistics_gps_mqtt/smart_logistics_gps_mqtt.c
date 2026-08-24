#include <stdio.h>
#include <string.h>
#include <unistd.h>

#include "cmsis_os2.h"
#include "ohos_init.h"
#include "MQTTClient.h"

#include "gps_reader.h"
#include "smart_logistics_config.h"
#include "wifi_connect.h"

#define MQTT_BUFFER_SIZE 1200
#define MQTT_COMMAND_TIMEOUT_MS 3000
#define MQTT_KEEP_ALIVE_SECONDS 30

static unsigned char g_send_buffer[MQTT_BUFFER_SIZE];
static unsigned char g_read_buffer[MQTT_BUFFER_SIZE];

static int PublishMessage(MQTTClient *client, const char *topic,
                          const char *payload, int retained)
{
    MQTTMessage message = {0};
    message.qos = QOS1;
    message.retained = (unsigned char)retained;
    message.payload = (void *)payload;
    message.payloadlen = strlen(payload);
    return MQTTPublish(client, topic, &message);
}

static int ConnectMqtt(Network *network, MQTTClient *client)
{
    MQTTPacket_connectData connect_data = MQTTPacket_connectData_initializer;
    char client_id[96];
    int result;

    NetworkInit(network);
    printf("[MQTT] TCP connect %s:%d ...\r\n", SL_MQTT_HOST, SL_MQTT_PORT);
    result = NetworkConnect(network, SL_MQTT_HOST, SL_MQTT_PORT);
    if (result != 0) {
        printf("[MQTT] TCP connect failed: %d\r\n", result);
        return result;
    }

    MQTTClientInit(client, network, MQTT_COMMAND_TIMEOUT_MS,
                   g_send_buffer, sizeof(g_send_buffer),
                   g_read_buffer, sizeof(g_read_buffer));
    snprintf(client_id, sizeof(client_id), "bearpi-%s", SL_VEHICLE_ID);
    connect_data.clientID.cstring = client_id;
    connect_data.username.cstring = SL_MQTT_USERNAME;
    connect_data.password.cstring = SL_MQTT_PASSWORD;
    connect_data.MQTTVersion = 4;
    connect_data.keepAliveInterval = MQTT_KEEP_ALIVE_SECONDS;
    connect_data.cleansession = 1;

    result = MQTTConnect(client, &connect_data);
    if (result != 0) {
        printf("[MQTT] login failed: %d\r\n", result);
        NetworkDisconnect(network);
        return result;
    }
    printf("[MQTT] connected as %s\r\n", SL_VEHICLE_ID);
    return 0;
}

static void SmartLogisticsTask(void)
{
    SlGpsData gps = {0};
    char gps_topic[128];
    char status_topic[128];
    char payload[512];
    unsigned int waiting_count = 0;

    printf("\r\n================================================\r\n");
    printf("Smart Logistics E53_ST1 GPS -> Cloud MQTT\r\n");
    printf("vehicle=%s broker=%s:%d\r\n", SL_VEHICLE_ID, SL_MQTT_HOST, SL_MQTT_PORT);
    printf("================================================\r\n");

    if (SlGpsInit() != 0) {
        printf("[FATAL] GPS initialization failed.\r\n");
        return;
    }
    printf("[GPS] UART1 ready, waiting for L80-R data.\r\n");

    printf("[WIFI] connecting to %s ...\r\n", SL_WIFI_SSID);
    if (WifiConnect(SL_WIFI_SSID, SL_WIFI_PASSWORD) != 0) {
        printf("[FATAL] Wi-Fi connection failed.\r\n");
        return;
    }

    snprintf(gps_topic, sizeof(gps_topic),
             "iot/carla/vehicle/%s/gps", SL_VEHICLE_ID);
    snprintf(status_topic, sizeof(status_topic),
             "iot/carla/vehicle/%s/status", SL_VEHICLE_ID);

    while (1) {
        Network network;
        MQTTClient client;
        int status_published = 0;

        if (ConnectMqtt(&network, &client) != 0) {
            printf("[MQTT] retry in 5 seconds.\r\n");
            sleep(5);
            continue;
        }

        while (MQTTIsConnected(&client)) {
            int fresh_fix = SlGpsRead(&gps);
            int result;

            MQTTYield(&client, 50);
            if (!fresh_fix) {
                waiting_count++;
                if ((waiting_count % 5) == 0) {
                    printf("[GPS] waiting for valid RMC fix ...\r\n");
                }
                continue;
            }

            waiting_count = 0;
            printf("[GPS] FIX lon=%.6f lat=%.6f speed=%.1f heading=%.1f UTC=%s\r\n",
                   gps.longitude, gps.latitude, gps.speed_kmh,
                   gps.heading, gps.timestamp);

            if (!status_published) {
                snprintf(payload, sizeof(payload),
                         "{\"schema_version\":\"1.0\","
                         "\"vehicle_id\":\"%s\","
                         "\"timestamp\":\"%s\","
                         "\"online\":true,"
                         "\"transport_status\":\"\\u8fd0\\u8f93\\u4e2d\"}",
                         SL_VEHICLE_ID, gps.timestamp);
                result = PublishMessage(&client, status_topic, payload, 1);
                if (result != 0) {
                    printf("[MQTT] status publish failed: %d\r\n", result);
                    break;
                }
                status_published = 1;
                printf("[MQTT] status published (retain).\r\n");
            }

            snprintf(payload, sizeof(payload),
                     "{\"schema_version\":\"1.0\","
                     "\"vehicle_id\":\"%s\","
                     "\"timestamp\":\"%s\","
                     "\"lat\":%.6f,\"lon\":%.6f,"
                     "\"speed_kmh\":%.2f,\"heading\":%.2f,"
                     "\"transport_status\":\"\\u8fd0\\u8f93\\u4e2d\","
                     "\"coordinate_system\":\"WGS84\"}",
                     SL_VEHICLE_ID, gps.timestamp,
                     gps.latitude, gps.longitude,
                     gps.speed_kmh, gps.heading);
            result = PublishMessage(&client, gps_topic, payload, 0);
            if (result != 0) {
                printf("[MQTT] GPS publish failed: %d\r\n", result);
                break;
            }
            printf("[MQTT] GPS published topic=%s\r\n", gps_topic);
        }

        if (MQTTIsConnected(&client)) {
            MQTTDisconnect(&client);
        }
        NetworkDisconnect(&network);
        printf("[MQTT] disconnected; retry in 5 seconds.\r\n");
        sleep(5);
    }
}

static void SmartLogisticsEntry(void)
{
    osThreadAttr_t attr = {0};
    attr.name = "SmartLogisticsTask";
    attr.stack_size = 12288;
    attr.priority = osPriorityNormal;

    if (osThreadNew((osThreadFunc_t)SmartLogisticsTask, NULL, &attr) == NULL) {
        printf("[FATAL] Failed to create SmartLogisticsTask.\r\n");
    }
}

APP_FEATURE_INIT(SmartLogisticsEntry);
