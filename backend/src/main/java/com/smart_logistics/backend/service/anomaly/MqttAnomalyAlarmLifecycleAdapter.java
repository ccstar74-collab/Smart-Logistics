package com.smart_logistics.backend.service.anomaly;

import com.smart_logistics.backend.dto.mqtt.MqttAlertPayload;
import com.smart_logistics.backend.service.MqttAlertIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;

@Component
public class MqttAnomalyAlarmLifecycleAdapter implements AnomalyAlarmLifecyclePort {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(MqttAnomalyAlarmLifecycleAdapter.class);

    private final MqttAlertIngestionService alertIngestionService;
    private final ObjectProvider<AlarmConditionRecoveryPort> recoveryPortProvider;

    public MqttAnomalyAlarmLifecycleAdapter(
            MqttAlertIngestionService alertIngestionService,
            ObjectProvider<AlarmConditionRecoveryPort> recoveryPortProvider) {
        this.alertIngestionService = alertIngestionService;
        this.recoveryPortProvider = recoveryPortProvider;
    }

    @Override
    public Long open(Long taskId, String vehicleDeviceCode, String alertType,
                     String description, Instant occurredAt) {
        MqttAlertIngestionService.AlarmIngestionResult result =
                alertIngestionService.ingestWithIdentity(
                        new MqttAlertPayload(
                                "1.0",
                                vehicleDeviceCode,
                                alertType,
                                description,
                                occurredAt.atOffset(ZoneOffset.UTC).toString(),
                                "backend"),
                        taskId);
        if (result.alarmId() == null) {
            throw new IllegalStateException("stored alarm id is unavailable");
        }
        LOGGER.info("Realtime anomaly {} taskId={} vehicle={} alarmId={} created={}",
                alertType, taskId, vehicleDeviceCode, result.alarmId(), result.created());
        return result.alarmId();
    }

    @Override
    public boolean markConditionRecovered(Long alarmId, Instant recoveredAt) {
        AlarmConditionRecoveryPort recoveryPort = recoveryPortProvider.getIfAvailable();
        if (recoveryPort == null) {
            LOGGER.warn("Alarm condition recovery adapter is not available alarmId={}", alarmId);
            return false;
        }
        recoveryPort.markConditionRecovered(alarmId, recoveredAt);
        return true;
    }
}
