#ifndef SMART_LOGISTICS_GPS_READER_H
#define SMART_LOGISTICS_GPS_READER_H

typedef struct {
    int valid;
    double latitude;
    double longitude;
    double speed_kmh;
    double heading;
    char timestamp[25];
} SlGpsData;

int SlGpsInit(void);
int SlGpsRead(SlGpsData *data);

#endif
