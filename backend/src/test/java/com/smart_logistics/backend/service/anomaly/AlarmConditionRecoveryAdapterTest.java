package com.smart_logistics.backend.service.anomaly;

import com.smart_logistics.backend.service.AlarmResolutionService;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AlarmConditionRecoveryAdapterTest {

    @Test
    void delegatesRecoveryToBusinessAlarmService() {
        AlarmResolutionService alarmResolutionService = mock(AlarmResolutionService.class);
        AlarmConditionRecoveryAdapter adapter =
                new AlarmConditionRecoveryAdapter(alarmResolutionService);
        Instant recoveredAt = Instant.parse("2026-08-27T09:30:00Z");

        adapter.markConditionRecovered(42L, recoveredAt);

        verify(alarmResolutionService).markConditionRecovered(42L, recoveredAt);
    }
}
