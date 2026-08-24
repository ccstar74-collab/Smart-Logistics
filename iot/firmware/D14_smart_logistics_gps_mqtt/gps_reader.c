#include "gps_reader.h"

#include <ctype.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "wifiiot_errno.h"
#include "wifiiot_gpio.h"
#include "wifiiot_gpio_ex.h"
#include "wifiiot_uart.h"

#define GPS_UART_INDEX WIFI_IOT_UART_IDX_1
#define GPS_PENDING_SIZE 1200
#define GPS_READ_CHUNK 256

static char g_pending[GPS_PENDING_SIZE];
static unsigned int g_pending_len;

static int CopyField(const char *line, int index, char *output, size_t output_size)
{
    const char *start = line;
    const char *end;
    int current = 0;
    size_t length;

    while (current < index) {
        start = strchr(start, ',');
        if (start == NULL) {
            return 0;
        }
        start++;
        current++;
    }

    end = start;
    while (*end != '\0' && *end != ',' && *end != '*') {
        end++;
    }
    length = (size_t)(end - start);
    if (length == 0 || length >= output_size) {
        return 0;
    }
    memcpy(output, start, length);
    output[length] = '\0';
    return 1;
}

static int IsDigits(const char *value, size_t count)
{
    size_t i;
    if (strlen(value) < count) {
        return 0;
    }
    for (i = 0; i < count; i++) {
        if (!isdigit((unsigned char)value[i])) {
            return 0;
        }
    }
    return 1;
}

static double NmeaCoordinateToDegrees(const char *value, char hemisphere)
{
    double raw = atof(value);
    int degrees = (int)(raw / 100.0);
    double minutes = raw - (double)degrees * 100.0;
    double result = (double)degrees + minutes / 60.0;

    if (hemisphere == 'S' || hemisphere == 'W') {
        result = -result;
    }
    return result;
}

static int ParseRmc(const char *line, SlGpsData *data)
{
    char utc[20];
    char status[3];
    char latitude[20];
    char north_south[3];
    char longitude[20];
    char east_west[3];
    char speed_knots[20];
    char heading[20];
    char date[12];
    int day;
    int month;
    int year;
    int hour;
    int minute;
    int second;

    if (strncmp(line, "$GPRMC,", 7) != 0 && strncmp(line, "$GNRMC,", 7) != 0) {
        return 0;
    }
    if (!CopyField(line, 1, utc, sizeof(utc)) ||
        !CopyField(line, 2, status, sizeof(status)) ||
        !CopyField(line, 3, latitude, sizeof(latitude)) ||
        !CopyField(line, 4, north_south, sizeof(north_south)) ||
        !CopyField(line, 5, longitude, sizeof(longitude)) ||
        !CopyField(line, 6, east_west, sizeof(east_west)) ||
        !CopyField(line, 9, date, sizeof(date))) {
        return 0;
    }
    if (!CopyField(line, 7, speed_knots, sizeof(speed_knots))) {
        strcpy(speed_knots, "0");
    }
    if (!CopyField(line, 8, heading, sizeof(heading))) {
        strcpy(heading, "0");
    }
    if (status[0] != 'A' || !IsDigits(utc, 6) || !IsDigits(date, 6)) {
        return 0;
    }

    hour = (utc[0] - '0') * 10 + utc[1] - '0';
    minute = (utc[2] - '0') * 10 + utc[3] - '0';
    second = (utc[4] - '0') * 10 + utc[5] - '0';
    day = (date[0] - '0') * 10 + date[1] - '0';
    month = (date[2] - '0') * 10 + date[3] - '0';
    year = (date[4] - '0') * 10 + date[5] - '0';
    year += (year >= 80) ? 1900 : 2000;

    data->latitude = NmeaCoordinateToDegrees(latitude, north_south[0]);
    data->longitude = NmeaCoordinateToDegrees(longitude, east_west[0]);
    data->speed_kmh = atof(speed_knots) * 1.852;
    data->heading = atof(heading);
    while (data->heading >= 360.0) {
        data->heading -= 360.0;
    }
    if (data->heading < 0.0) {
        data->heading = 0.0;
    }
    snprintf(data->timestamp, sizeof(data->timestamp),
             "%04d-%02d-%02dT%02d:%02d:%02dZ",
             year, month, day, hour, minute, second);
    data->valid = 1;
    return 1;
}

int SlGpsInit(void)
{
    WifiIotUartAttribute uart_attr = {
        .baudRate = 9600,
        .dataBits = 8,
        .stopBits = 1,
        .parity = 0,
    };
    unsigned int result;

    GpioInit();
    IoSetFunc(WIFI_IOT_IO_NAME_GPIO_4, WIFI_IOT_IO_FUNC_GPIO_4_GPIO);
    GpioSetDir(WIFI_IOT_IO_NAME_GPIO_4, WIFI_IOT_GPIO_DIR_OUT);
    GpioSetOutputVal(WIFI_IOT_IO_NAME_GPIO_4, 0);
    IoSetFunc(WIFI_IOT_IO_NAME_GPIO_2, WIFI_IOT_IO_FUNC_GPIO_2_GPIO);
    GpioSetDir(WIFI_IOT_IO_NAME_GPIO_2, WIFI_IOT_GPIO_DIR_OUT);
    GpioSetOutputVal(WIFI_IOT_IO_NAME_GPIO_2, 0);

    result = UartInit(GPS_UART_INDEX, &uart_attr, NULL);
    if (result != WIFI_IOT_SUCCESS) {
        printf("[GPS] UART init failed: %u\r\n", result);
        return -1;
    }
    g_pending_len = 0;
    return 0;
}

int SlGpsRead(SlGpsData *data)
{
    unsigned char chunk[GPS_READ_CHUNK];
    int read_count;
    unsigned int consumed = 0;
    int got_fix = 0;

    read_count = UartRead(GPS_UART_INDEX, chunk, sizeof(chunk));
    if (read_count <= 0) {
        return 0;
    }

    if (g_pending_len + (unsigned int)read_count >= sizeof(g_pending)) {
        g_pending_len = 0;
    }
    memcpy(g_pending + g_pending_len, chunk, (size_t)read_count);
    g_pending_len += (unsigned int)read_count;
    g_pending[g_pending_len] = '\0';

    while (consumed < g_pending_len) {
        char *line_start = g_pending + consumed;
        char *line_end = strchr(line_start, '\n');
        size_t line_length;

        if (line_end == NULL) {
            break;
        }
        line_length = (size_t)(line_end - line_start);
        if (line_length > 0 && line_start[line_length - 1] == '\r') {
            line_length--;
        }
        line_start[line_length] = '\0';
        if (ParseRmc(line_start, data)) {
            got_fix = 1;
        }
        consumed = (unsigned int)(line_end - g_pending) + 1;
    }

    if (consumed > 0) {
        memmove(g_pending, g_pending + consumed, g_pending_len - consumed);
        g_pending_len -= consumed;
        g_pending[g_pending_len] = '\0';
    }
    return got_fix;
}
