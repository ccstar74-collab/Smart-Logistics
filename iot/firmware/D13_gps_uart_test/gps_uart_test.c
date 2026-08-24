#include <stdio.h>
#include <unistd.h>

#include "cmsis_os2.h"
#include "ohos_init.h"
#include "E53_ST1.h"

static void GpsTestTask(void)
{
    E53_ST1_Data_TypeDef data = {0};

    printf("\r\n========================================\r\n");
    printf("E53_ST1 GPS UART TEST\r\n");
    printf("UART1=9600 8N1; waiting for L80-R data\r\n");
    printf("Move the board outdoors for first fix.\r\n");
    printf("========================================\r\n");

    Init_E53_ST1();
    Beep_StatusSet(OFF);

    while (1) {
        E53_ST1_Read_Data(&data);
        if ((data.Longitude == 0.0f) || (data.Latitude == 0.0f)) {
            printf("[GPS] waiting for fix... longitude=%.5f latitude=%.5f\r\n",
                   data.Longitude, data.Latitude);
        } else {
            printf("[GPS] FIX longitude=%.5f latitude=%.5f\r\n",
                   data.Longitude, data.Latitude);
        }
        sleep(1);
    }
}

static void GpsTestEntry(void)
{
    osThreadAttr_t attr = {0};

    attr.name = "GpsTestTask";
    attr.stack_size = 4096;
    attr.priority = 25;

    if (osThreadNew((osThreadFunc_t)GpsTestTask, NULL, &attr) == NULL) {
        printf("Failed to create GPS test task.\r\n");
    }
}

APP_FEATURE_INIT(GpsTestEntry);
