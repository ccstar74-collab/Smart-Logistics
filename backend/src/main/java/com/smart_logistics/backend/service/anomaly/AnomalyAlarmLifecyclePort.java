package com.smart_logistics.backend.service.anomaly;

import java.time.Instant;

public interface AnomalyAlarmLifecyclePort {

    Long open(Long taskId, String vehicleDeviceCode, String alertType,
              String description, Instant occurredAt);

    boolean markConditionRecovered(Long alarmId, Instant recoveredAt);
}
