package com.smart_logistics.backend.service.anomaly;

import java.time.Instant;

/**
 * Stable boundary for the alarm/dispatch module's fourth-round resolution service.
 * The concrete adapter will be added after that module publishes its final contract.
 */
public interface AlarmConditionRecoveryPort {

    void markConditionRecovered(Long alarmId, Instant recoveredAt);
}
