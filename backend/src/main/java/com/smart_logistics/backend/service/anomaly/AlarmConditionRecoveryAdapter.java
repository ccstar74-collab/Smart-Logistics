package com.smart_logistics.backend.service.anomaly;

import com.smart_logistics.backend.service.AlarmResolutionService;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Bridges realtime physical-condition recovery to the business alarm workflow.
 */
@Component
public class AlarmConditionRecoveryAdapter implements AlarmConditionRecoveryPort {

    private final AlarmResolutionService alarmResolutionService;

    public AlarmConditionRecoveryAdapter(AlarmResolutionService alarmResolutionService) {
        this.alarmResolutionService = alarmResolutionService;
    }

    @Override
    public void markConditionRecovered(Long alarmId, Instant recoveredAt) {
        alarmResolutionService.markConditionRecovered(alarmId, recoveredAt);
    }
}
