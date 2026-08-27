package com.smart_logistics.backend.service.anomaly;

import com.smart_logistics.backend.dto.realtime.GpsSample;
import com.smart_logistics.backend.entity.TransportTask;
import com.smart_logistics.backend.service.eta.EtaRouteProgress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RealtimeAnomalyDetectionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-27T03:00:00Z");

    @Mock private AnomalyAlarmLifecyclePort alarmLifecyclePort;

    private RealtimeAnomalyDetectionService service;

    @BeforeEach
    void setUp() {
        service = new RealtimeAnomalyDetectionService(
                alarmLifecyclePort,
                true,
                100,
                60,
                Duration.ofSeconds(30),
                1,
                20,
                Duration.ofMinutes(2),
                100);
        org.mockito.Mockito.lenient().when(alarmLifecyclePort.open(
                any(), any(), any(), any(), any())).thenReturn(101L, 102L, 103L);
        org.mockito.Mockito.lenient().when(alarmLifecyclePort.markConditionRecovered(
                any(), any())).thenReturn(true);
    }

    @Test
    void emitsOneDeviationAlarmPerEpisodeAndRearmerAfterRecovery() {
        evaluate(NOW, movingSample(NOW), new EtaRouteProgress(4_000, 120));
        evaluate(NOW.plusSeconds(29), movingSample(NOW.plusSeconds(29)),
                new EtaRouteProgress(3_900, 130));
        verify(alarmLifecyclePort, never()).open(any(), any(), any(), any(), any());

        evaluate(NOW.plusSeconds(30), movingSample(NOW.plusSeconds(30)),
                new EtaRouteProgress(3_800, 140));
        evaluate(NOW.plusSeconds(45), movingSample(NOW.plusSeconds(45)),
                new EtaRouteProgress(3_700, 150));
        verify(alarmLifecyclePort, times(1)).open(
                eq(12L), eq("real_001"), eq("偏航"), contains("偏离"), any());

        evaluate(NOW.plusSeconds(46), movingSample(NOW.plusSeconds(46)),
                new EtaRouteProgress(3_650, 50));
        evaluate(NOW.plusSeconds(47), movingSample(NOW.plusSeconds(47)),
                new EtaRouteProgress(3_600, 120));
        evaluate(NOW.plusSeconds(77), movingSample(NOW.plusSeconds(77)),
                new EtaRouteProgress(3_500, 130));

        verify(alarmLifecyclePort).markConditionRecovered(eq(101L), any());
        verify(alarmLifecyclePort, times(2)).open(
                eq(12L), eq("real_001"), eq("偏航"), contains("偏离"), any());
    }

    @Test
    void repeatedStaleGpsDoesNotCompleteDeviationDuration() {
        GpsSample stale = movingSample(NOW);
        evaluate(NOW, stale, new EtaRouteProgress(4_000, 120));
        evaluate(NOW.plusSeconds(60), stale, new EtaRouteProgress(4_000, 120));

        verify(alarmLifecyclePort, never()).open(any(), any(), any(), any(), any());
    }

    @Test
    void retriesSameAlarmRecoveryUntilBusinessAdapterIsAvailable() {
        when(alarmLifecyclePort.markConditionRecovered(any(), any())).thenReturn(false);
        evaluate(NOW, movingSample(NOW), new EtaRouteProgress(4_000, 120));
        evaluate(NOW.plusSeconds(30), movingSample(NOW.plusSeconds(30)),
                new EtaRouteProgress(3_900, 130));

        evaluate(NOW.plusSeconds(31), movingSample(NOW.plusSeconds(31)),
                new EtaRouteProgress(3_800, 50));
        evaluate(NOW.plusSeconds(32), movingSample(NOW.plusSeconds(32)),
                new EtaRouteProgress(3_800, 50));

        verify(alarmLifecyclePort, times(1)).open(any(), any(), any(), any(), any());
        verify(alarmLifecyclePort, times(2)).markConditionRecovered(eq(101L), any());
    }

    @Test
    void emitsOneAbnormalStopAlarmAndRearmerAfterMovement() {
        GpsSample first = stoppedSample(106.570000, 29.490000, NOW);
        evaluate(NOW, first, new EtaRouteProgress(2_000, 5), List.of(first));

        Instant triggerAt = NOW.plus(Duration.ofMinutes(2));
        GpsSample second = stoppedSample(106.570005, 29.490005, triggerAt);
        evaluate(triggerAt, second, new EtaRouteProgress(2_000, 5),
                List.of(first, second));

        GpsSample repeated = stoppedSample(
                106.570006, 29.490006, triggerAt.plusSeconds(10));
        evaluate(triggerAt.plusSeconds(10), repeated,
                new EtaRouteProgress(2_000, 5), List.of(first, second, repeated));

        verify(alarmLifecyclePort).open(
                eq(12L), eq("real_001"), eq("异常停留"), contains("持续停留"), any());

        GpsSample moving = new GpsSample("real_001", 106.5701, 29.4901,
                20.0, 90.0, triggerAt.plusSeconds(11));
        evaluate(triggerAt.plusSeconds(11), moving,
                new EtaRouteProgress(1_900, 5), List.of(moving));
        Instant secondEpisodeStart = triggerAt.plusSeconds(12);
        GpsSample third = stoppedSample(106.5702, 29.4902, secondEpisodeStart);
        evaluate(secondEpisodeStart, third,
                new EtaRouteProgress(1_800, 5), List.of(third));
        GpsSample fourth = stoppedSample(106.570205, 29.490205,
                secondEpisodeStart.plus(Duration.ofMinutes(2)));
        evaluate(secondEpisodeStart.plus(Duration.ofMinutes(2)), fourth,
                new EtaRouteProgress(1_800, 5), List.of(third, fourth));

        verify(alarmLifecyclePort).markConditionRecovered(eq(101L), any());
        verify(alarmLifecyclePort, times(2)).open(
                eq(12L), eq("real_001"), eq("异常停留"), contains("持续停留"), any());
    }

    @Test
    void doesNotTreatDestinationStopAsAbnormal() {
        GpsSample first = stoppedSample(106.570000, 29.490000, NOW);
        evaluate(NOW, first, new EtaRouteProgress(80, 5), List.of(first));
        GpsSample second = stoppedSample(
                106.570001, 29.490001, NOW.plus(Duration.ofMinutes(3)));
        evaluate(NOW.plus(Duration.ofMinutes(3)), second,
                new EtaRouteProgress(70, 5), List.of(first, second));

        verify(alarmLifecyclePort, never()).open(any(), any(), any(), any(), any());
    }

    @Test
    void movementBreaksStopEpisode() {
        GpsSample first = stoppedSample(106.570000, 29.490000, NOW);
        evaluate(NOW, first, new EtaRouteProgress(2_000, 5), List.of(first));
        GpsSample moved = stoppedSample(
                106.571000, 29.491000, NOW.plus(Duration.ofMinutes(2)));
        evaluate(NOW.plus(Duration.ofMinutes(2)), moved,
                new EtaRouteProgress(1_900, 5), List.of(first, moved));

        verify(alarmLifecyclePort, never()).open(any(), any(), any(), any(), any());
    }

    @Test
    void clearingInactiveTaskRemovesPendingEpisode() {
        evaluate(NOW, movingSample(NOW), new EtaRouteProgress(4_000, 120));
        service.retainTasks(Set.of(99L));
        evaluate(NOW.plusSeconds(31), movingSample(NOW.plusSeconds(31)),
                new EtaRouteProgress(3_900, 130));

        verify(alarmLifecyclePort, never()).open(any(), any(), any(), any(), any());
    }

    private void evaluate(Instant now, GpsSample latest, EtaRouteProgress progress) {
        evaluate(now, latest, progress, List.of(latest));
    }

    private void evaluate(Instant now, GpsSample latest, EtaRouteProgress progress,
                          List<GpsSample> samples) {
        service.evaluate(task(), "real_001", samples, latest, progress, now);
    }

    private TransportTask task() {
        TransportTask task = new TransportTask();
        task.setId(12L);
        return task;
    }

    private GpsSample movingSample(Instant collectedAt) {
        return new GpsSample("real_001", 106.57, 29.49,
                30.0, 90.0, collectedAt);
    }

    private GpsSample stoppedSample(double longitude, double latitude,
                                    Instant collectedAt) {
        return new GpsSample("real_001", longitude, latitude,
                0.0, 90.0, collectedAt);
    }
}
