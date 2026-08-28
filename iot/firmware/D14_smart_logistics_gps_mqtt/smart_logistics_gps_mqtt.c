#include <stdio.h>
#include <stdint.h>
#include <string.h>
#include <sys/time.h>
#include <time.h>
#include <unistd.h>

#include "cmsis_os2.h"
#include "lwip/netdb.h"
#include "lwip/sockets.h"
#include "ohos_init.h"
#include "MQTTClient.h"
#include "wifiiot_gpio.h"
#include "wifiiot_gpio_ex.h"

#include "gps_reader.h"
#include "smart_logistics_config.h"
#include "wifi_connect.h"

#define MQTT_BUFFER_SIZE 1200
#define MQTT_COMMAND_TIMEOUT_MS 3000
#define MQTT_KEEP_ALIVE_SECONDS 30
#define VALID_UTC_EPOCH_MIN 1704067200L
#define NTP_PACKET_SIZE 48
#define NTP_UNIX_EPOCH_DELTA 2208988800UL

static unsigned char g_send_buffer[MQTT_BUFFER_SIZE];
static unsigned char g_read_buffer[MQTT_BUFFER_SIZE];
static volatile unsigned char g_f1_pressed;
static volatile unsigned char g_f2_pressed;
static unsigned char g_box_open_latched;
static unsigned char g_open_alert_pending;
static unsigned char g_open_alert_published;
static unsigned char g_close_recovery_pending;
static char g_open_alert_timestamp[25];
static time_t g_utc_base;
static uint32_t g_utc_tick_base;
static unsigned char g_utc_ready;

static void F1Pressed(char *arg)
{
    (void)arg;
    g_f1_pressed = 1;
}

static void F2Pressed(char *arg)
{
    (void)arg;
    g_f2_pressed = 1;
}

static void InitBoxButtons(void)
{
    GpioInit();

    IoSetFunc(WIFI_IOT_IO_NAME_GPIO_11, WIFI_IOT_IO_FUNC_GPIO_11_GPIO);
    GpioSetDir(WIFI_IOT_IO_NAME_GPIO_11, WIFI_IOT_GPIO_DIR_IN);
    IoSetPull(WIFI_IOT_IO_NAME_GPIO_11, WIFI_IOT_IO_PULL_UP);
    GpioRegisterIsrFunc(WIFI_IOT_IO_NAME_GPIO_11,
                        WIFI_IOT_INT_TYPE_EDGE,
                        WIFI_IOT_GPIO_EDGE_FALL_LEVEL_LOW,
                        F1Pressed, NULL);

    IoSetFunc(WIFI_IOT_IO_NAME_GPIO_12, WIFI_IOT_IO_FUNC_GPIO_12_GPIO);
    GpioSetDir(WIFI_IOT_IO_NAME_GPIO_12, WIFI_IOT_GPIO_DIR_IN);
    IoSetPull(WIFI_IOT_IO_NAME_GPIO_12, WIFI_IOT_IO_PULL_UP);
    GpioRegisterIsrFunc(WIFI_IOT_IO_NAME_GPIO_12,
                        WIFI_IOT_INT_TYPE_EDGE,
                        WIFI_IOT_GPIO_EDGE_FALL_LEVEL_LOW,
                        F2Pressed, NULL);

    printf("[BOX] F1=open alert, F2=close/reset.\r\n");
}

static void HandleButtonEvents(void)
{
    if (g_f2_pressed) {
        g_f2_pressed = 0;
        if (!g_box_open_latched) {
            printf("[BOX] F2 ignored: box is already marked closed.\r\n");
        } else {
            g_box_open_latched = 0;
            if (g_open_alert_pending && !g_open_alert_published) {
                g_open_alert_pending = 0;
                g_open_alert_timestamp[0] = '\0';
                printf("[BOX] F2 pressed before open alert upload; pending alert cancelled.\r\n");
            } else if (g_open_alert_published) {
                g_close_recovery_pending = 1;
                printf("[BOX] F2 pressed: box marked closed; recovery queued.\r\n");
            } else {
                printf("[BOX] F2 pressed: box marked closed; no cloud alert to recover.\r\n");
            }
        }
    }

    if (g_f1_pressed) {
        g_f1_pressed = 0;
        if (g_close_recovery_pending) {
            printf("[BOX] F1 ignored: waiting for previous recovery upload.\r\n");
        } else if (!g_box_open_latched) {
            g_box_open_latched = 1;
            g_open_alert_pending = 1;
            g_open_alert_published = 0;
            g_open_alert_timestamp[0] = '\0';
            printf("[BOX] F1 pressed: abnormal open detected.\r\n");
        } else {
            printf("[BOX] F1 ignored: box is already marked open.\r\n");
        }
    }
}

static int QueryNtpServer(const char *server_name, time_t *utc_result)
{
    unsigned char packet[NTP_PACKET_SIZE] = {0};
    struct hostent *host;
    struct sockaddr_in server = {0};
    struct timeval timeout = {5, 0};
    uint32_t ntp_seconds;
    int socket_fd;
    int received;

    host = gethostbyname(server_name);
    if (host == NULL || host->h_addr_list == NULL || host->h_addr_list[0] == NULL) {
        return -1;
    }

    server.sin_family = AF_INET;
    server.sin_port = htons(123);
    memcpy(&server.sin_addr.s_addr, host->h_addr_list[0], sizeof(server.sin_addr.s_addr));
    socket_fd = socket(AF_INET, SOCK_DGRAM, 0);
    if (socket_fd < 0) {
        return -1;
    }
    if (setsockopt(socket_fd, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout)) < 0) {
        lwip_close(socket_fd);
        return -1;
    }

    packet[0] = 0x1B;
    if (sendto(socket_fd, packet, sizeof(packet), 0,
               (struct sockaddr *)&server, sizeof(server)) != (int)sizeof(packet)) {
        lwip_close(socket_fd);
        return -1;
    }
    received = recvfrom(socket_fd, packet, sizeof(packet), 0, NULL, NULL);
    lwip_close(socket_fd);
    if (received < NTP_PACKET_SIZE) {
        return -1;
    }

    ntp_seconds = ((uint32_t)packet[40] << 24) |
                  ((uint32_t)packet[41] << 16) |
                  ((uint32_t)packet[42] << 8) |
                  (uint32_t)packet[43];
    if (ntp_seconds <= NTP_UNIX_EPOCH_DELTA) {
        return -1;
    }
    *utc_result = (time_t)(ntp_seconds - NTP_UNIX_EPOCH_DELTA);
    return 0;
}

static int SyncUtcClock(void)
{
    const char *servers[] = {"ntp.aliyun.com", "pool.ntp.org"};
    time_t utc = 0;
    unsigned int i;

    printf("[TIME] synchronizing UTC with NTP ...\r\n");
    for (i = 0; i < sizeof(servers) / sizeof(servers[0]); i++) {
        if (QueryNtpServer(servers[i], &utc) == 0 && utc >= VALID_UTC_EPOCH_MIN) {
            g_utc_base = utc;
            g_utc_tick_base = osKernelGetTickCount();
            g_utc_ready = 1;
            printf("[TIME] UTC synchronized via %s: epoch=%ld.\r\n",
                   servers[i], (long)g_utc_base);
            return 0;
        }
        printf("[TIME] NTP server failed: %s.\r\n", servers[i]);
    }
    printf("[TIME] NTP unavailable; GPS time fallback enabled.\r\n");
    return -1;
}

static int FormatUtcNow(char *output, size_t output_size)
{
    uint32_t tick_frequency;
    uint32_t elapsed_ticks;
    time_t now;
    struct tm utc;

    if (!g_utc_ready || output == NULL || output_size < 25) {
        return -1;
    }

    tick_frequency = osKernelGetTickFreq();
    if (tick_frequency == 0) {
        return -1;
    }
    elapsed_ticks = osKernelGetTickCount() - g_utc_tick_base;
    now = g_utc_base + (time_t)(elapsed_ticks / tick_frequency);
    if (gmtime_r(&now, &utc) == NULL) {
        return -1;
    }

    snprintf(output, output_size, "%04d-%02d-%02dT%02d:%02d:%02d.000Z",
             utc.tm_year + 1900, utc.tm_mon + 1, utc.tm_mday,
             utc.tm_hour, utc.tm_min, utc.tm_sec);
    return 0;
}

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
    char alert_topic[128];
    char alert_recovery_topic[128];
    char payload[512];
    char event_timestamp[25];
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
    InitBoxButtons();

    printf("[WIFI] connecting to %s ...\r\n", SL_WIFI_SSID);
    if (WifiConnect(SL_WIFI_SSID, SL_WIFI_PASSWORD) != 0) {
        printf("[FATAL] Wi-Fi connection failed.\r\n");
        return;
    }
    (void)SyncUtcClock();

    snprintf(gps_topic, sizeof(gps_topic),
             "iot/carla/vehicle/%s/gps", SL_VEHICLE_ID);
    snprintf(status_topic, sizeof(status_topic),
             "iot/carla/vehicle/%s/status", SL_VEHICLE_ID);
    snprintf(alert_topic, sizeof(alert_topic), "iot/carla/alert");
    snprintf(alert_recovery_topic, sizeof(alert_recovery_topic),
             "iot/carla/alert/recovery");

    while (1) {
        Network network;
        MQTTClient client;
        int status_published = 0;

        if (ConnectMqtt(&network, &client) != 0) {
            printf("[MQTT] retry in 5 seconds.\r\n");
            sleep(5);
            continue;
        }

        if (FormatUtcNow(event_timestamp, sizeof(event_timestamp)) == 0) {
            snprintf(payload, sizeof(payload),
                     "{\"schema_version\":\"1.0\","
                     "\"vehicle_id\":\"%s\","
                     "\"timestamp\":\"%s\","
                     "\"online\":true,"
                     "\"transport_status\":\"\\u8fd0\\u8f93\\u4e2d\"}",
                     SL_VEHICLE_ID, event_timestamp);
            if (PublishMessage(&client, status_topic, payload, 1) == 0) {
                status_published = 1;
                printf("[MQTT] status published (retain).\r\n");
            }
        }

        while (MQTTIsConnected(&client)) {
            int fresh_fix = SlGpsRead(&gps);
            int result;

            MQTTYield(&client, 50);
            HandleButtonEvents();

            if (g_open_alert_pending) {
                const char *alert_timestamp = NULL;
                if (FormatUtcNow(event_timestamp, sizeof(event_timestamp)) == 0) {
                    alert_timestamp = event_timestamp;
                } else if (gps.valid && gps.timestamp[0] != '\0') {
                    alert_timestamp = gps.timestamp;
                }

                if (alert_timestamp != NULL) {
                    snprintf(payload, sizeof(payload),
                             "{\"schema_version\":\"1.0\","
                             "\"vehicle_id\":\"%s\","
                             "\"alert_type\":\"\\u5f02\\u5e38\\u5f00\\u7bb1\","
                             "\"description\":\"\\u8fd0\\u8f93\\u9014\\u4e2d\\u68c0\\u6d4b\\u5230\\u7bb1\\u95e8\\u5f00\\u542f\","
                             "\"timestamp\":\"%s\","
                             "\"source\":\"device\"}",
                             SL_VEHICLE_ID, alert_timestamp);
                    result = PublishMessage(&client, alert_topic, payload, 0);
                    if (result != 0) {
                        printf("[MQTT] abnormal-open alert publish failed: %d\r\n", result);
                        break;
                    }
                    g_open_alert_pending = 0;
                    g_open_alert_published = 1;
                    snprintf(g_open_alert_timestamp, sizeof(g_open_alert_timestamp),
                             "%s", alert_timestamp);
                    printf("[MQTT] abnormal-open alert published topic=%s\r\n", alert_topic);
                }
            }

            if (g_close_recovery_pending) {
                const char *recovered_at = NULL;
                if (FormatUtcNow(event_timestamp, sizeof(event_timestamp)) == 0) {
                    recovered_at = event_timestamp;
                } else if (gps.valid && gps.timestamp[0] != '\0') {
                    recovered_at = gps.timestamp;
                }

                if (recovered_at != NULL && g_open_alert_timestamp[0] != '\0') {
                    snprintf(payload, sizeof(payload),
                             "{\"schema_version\":\"1.0\","
                             "\"vehicle_id\":\"%s\","
                             "\"alert_type\":\"\\u5f02\\u5e38\\u5f00\\u7bb1\","
                             "\"condition_status\":\"RECOVERED\","
                             "\"triggered_at\":\"%s\","
                             "\"recovered_at\":\"%s\","
                             "\"source\":\"device\"}",
                             SL_VEHICLE_ID, g_open_alert_timestamp, recovered_at);
                    result = PublishMessage(&client, alert_recovery_topic, payload, 0);
                    if (result != 0) {
                        printf("[MQTT] abnormal-open recovery publish failed: %d\r\n", result);
                        break;
                    }
                    g_close_recovery_pending = 0;
                    g_open_alert_published = 0;
                    g_open_alert_timestamp[0] = '\0';
                    printf("[MQTT] abnormal-open recovery published topic=%s\r\n",
                           alert_recovery_topic);
                }
            }

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
