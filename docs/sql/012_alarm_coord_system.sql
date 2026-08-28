-- Alarm coordinate system tagging.
-- Apply after 011_alarm_dispatch_resolution.sql.
-- GPS data ingested from MQTT (CARLA / real devices) uses WGS84 natively;
-- route data from Amap uses GCJ-02. The frontend must know which coordinate
-- system alarm longitude/latitude belong to before rendering on a map.

ALTER TABLE alarm
    ADD COLUMN coord_system VARCHAR(20) NULL
        COMMENT '坐标系: WGS84 (GPS原始) / GCJ02 (高德) / NULL (无位置)'
        AFTER latitude;
