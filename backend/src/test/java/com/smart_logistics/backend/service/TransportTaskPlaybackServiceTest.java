package com.smart_logistics.backend.service;

import com.smart_logistics.backend.dto.TaskTrackSnapshot;
import com.smart_logistics.backend.dto.TransportTaskStatusTransitionSnapshot;
import com.smart_logistics.backend.dto.realtime.GpsSample;
import com.smart_logistics.backend.dto.response.AlarmResponse;
import com.smart_logistics.backend.dto.response.DispatchCommandResponse;
import com.smart_logistics.backend.dto.response.TransportTaskResponse;
import com.smart_logistics.backend.dto.response.TransportTaskRouteResponse;
import com.smart_logistics.backend.enums.AlarmConditionStatus;
import com.smart_logistics.backend.enums.AlarmLevel;
import com.smart_logistics.backend.enums.AlarmStatus;
import com.smart_logistics.backend.enums.AlarmType;
import com.smart_logistics.backend.enums.DispatchCommandStatus;
import com.smart_logistics.backend.enums.DispatchCommandType;
import com.smart_logistics.backend.enums.PlaybackEventType;
import com.smart_logistics.backend.enums.TransportTaskRouteStatus;
import com.smart_logistics.backend.enums.TransportTaskStatus;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransportTaskPlaybackServiceTest {

    @Mock private TaskTrackQueryService taskTrackQueryService;
    @Mock private TransportTaskService transportTaskService;
    @Mock private TransportTaskStatusRecordService statusRecordService;
    @Mock private AlarmService alarmService;
    @Mock private DispatchCommandService dispatchCommandService;

    private TransportTaskPlaybackService service;

    @BeforeEach
    void setUp() {
        service = new TransportTaskPlaybackService(
                taskTrackQueryService, transportTaskService, statusRecordService,
                alarmService, dispatchCommandService);
    }

    @Test
    void composesTaskScopedPlaybackAndSortsReliableTimeline() {
        Instant trackStart = Instant.parse("2026-08-30T02:00:00Z");
        Instant trackEnd = Instant.parse("2026-08-30T02:08:00Z");
        GpsSample earlier = gps(106.1, 29.1,
                Instant.parse("2026-08-30T02:03:50Z"));
        GpsSample nearest = gps(106.2, 29.2,
                Instant.parse("2026-08-30T02:04:05Z"));
        when(taskTrackQueryService.getTaskTrack(1L)).thenReturn(
                new TaskTrackSnapshot(trackStart, trackEnd, List.of(earlier, nearest)));
        when(transportTaskService.getTransportTask(1L)).thenReturn(task());

        TransportTaskRouteResponse v1 = route(
                "route_v1", 1, TransportTaskRouteStatus.INACTIVE,
                time("2026-08-30T09:55:00+08:00"),
                time("2026-08-30T10:00:00+08:00"),
                time("2026-08-30T10:04:00+08:00"));
        TransportTaskRouteResponse v2 = route(
                "route_v2", 2, TransportTaskRouteStatus.ACTIVE,
                time("2026-08-30T10:03:30+08:00"),
                time("2026-08-30T10:04:00+08:00"), null);
        when(transportTaskService.listTransportTaskRoutes(1L))
                .thenReturn(List.of(v1, v2));

        AlarmResponse alarm = alarm();
        when(alarmService.listTaskAlarmHistory(1L)).thenReturn(List.of(alarm));
        DispatchCommandResponse command = command();
        when(dispatchCommandService.listTaskCommandHistory(1L))
                .thenReturn(List.of(command));
        when(statusRecordService.findTaskTransitions(1L)).thenReturn(List.of(
                transition(1L, TransportTaskStatus.WAITING,
                        TransportTaskStatus.TRANSPORTING,
                        "2026-08-30T10:00:00+08:00"),
                transition(2L, TransportTaskStatus.TRANSPORTING,
                        TransportTaskStatus.COMPLETED,
                        "2026-08-30T10:08:00+08:00")));

        var result = service.getPlayback(1L);

        assertEquals("WGS84", result.actualTrack().coordinateSystem());
        assertEquals(List.of(earlier.collectedAt(), nearest.collectedAt()),
                result.actualTrack().points().stream()
                        .map(point -> point.timestamp().toInstant()).toList());
        assertEquals(List.of(1, 2), result.routeVersions().stream()
                .map(TransportTaskRouteResponse::routeVersion).toList());
        assertEquals(List.of(List.of(106.0, 29.0), List.of(106.3, 29.3)),
                result.routeVersions().get(1).points());
        assertEquals(1L, result.alarms().getFirst().getId());
        assertNull(result.alarms().getFirst().getLongitude());
        assertNull(result.alarms().getFirst().getCoordSystem());
        assertEquals(9L, result.dispatchCommands().getFirst().getId());
        assertEquals("route_v2",
                result.dispatchCommands().getFirst().getRouteId());

        List<OffsetDateTime> eventTimes = result.events().stream()
                .map(event -> event.time()).toList();
        assertEquals(eventTimes.stream().sorted().toList(), eventTimes);
        assertEquals(2, result.events().stream()
                .filter(event -> event.type() == PlaybackEventType.ROUTE_ACTIVATED)
                .count());
        var switched = result.events().stream()
                .filter(event -> event.type() == PlaybackEventType.ROUTE_ACTIVATED)
                .filter(event -> "route_v2".equals(event.routeId()))
                .findFirst().orElseThrow();
        assertEquals(106.2, switched.position().longitude());
        assertEquals(29.2, switched.position().latitude());
        assertEquals("WGS84", switched.position().coordinateSystem());
        assertEquals(0, result.events().stream()
                .filter(event -> event.type()
                        == PlaybackEventType.COMMAND_ACKNOWLEDGED)
                .count());
    }

    @Test
    void routeActivationMarkerIsNullWhenActualTrackIsUnavailable() {
        when(taskTrackQueryService.getTaskTrack(1L)).thenReturn(
                new TaskTrackSnapshot(Instant.parse("2026-08-30T02:00:00Z"),
                        Instant.parse("2026-08-30T02:08:00Z"), List.of()));
        when(transportTaskService.getTransportTask(1L)).thenReturn(task());
        when(transportTaskService.listTransportTaskRoutes(1L)).thenReturn(List.of(
                route("route_v1", 1, TransportTaskRouteStatus.ACTIVE,
                        time("2026-08-30T09:55:00+08:00"),
                        time("2026-08-30T10:00:00+08:00"), null)));
        when(alarmService.listTaskAlarmHistory(1L)).thenReturn(List.of());
        when(dispatchCommandService.listTaskCommandHistory(1L)).thenReturn(List.of());
        when(statusRecordService.findTaskTransitions(1L)).thenReturn(List.of());

        var result = service.getPlayback(1L);

        assertNull(result.events().stream()
                .filter(event -> event.type() == PlaybackEventType.ROUTE_ACTIVATED)
                .findFirst().orElseThrow().position());
    }

    @Test
    void taskFailuresStopBeforeCrossAggregateHistoryQueries() {
        when(taskTrackQueryService.getTaskTrack(404L)).thenThrow(
                new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "transport task not found"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getPlayback(404L));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(transportTaskService, statusRecordService,
                alarmService, dispatchCommandService);
    }

    private TransportTaskResponse task() {
        return new TransportTaskResponse(
                1L, "T1", 10L, 20L, "A", 106.0, 29.0,
                "B", 106.3, 29.3, null, null,
                time("2026-08-30T10:00:00+08:00"),
                time("2026-08-30T10:08:00+08:00"),
                TransportTaskStatus.COMPLETED, null, null,
                time("2026-08-30T09:50:00+08:00"),
                time("2026-08-30T10:08:00+08:00"));
    }

    private TransportTaskRouteResponse route(
            String routeId, int version, TransportTaskRouteStatus status,
            OffsetDateTime generatedAt, OffsetDateTime activatedAt,
            OffsetDateTime deactivatedAt) {
        return new TransportTaskRouteResponse(
                routeId, 1L, version, status, "AMAP", "GCJ02",
                5_500, 720, generatedAt, activatedAt, deactivatedAt,
                List.of(List.of(106.0, 29.0), List.of(106.3, 29.3)));
    }

    private AlarmResponse alarm() {
        return new AlarmResponse(
                1L, 20L, "沪A1", 1L, "T1", "sim_019",
                AlarmType.ROUTE_DEVIATION, AlarmLevel.HIGH, "deviation",
                AlarmStatus.RESOLVED, AlarmConditionStatus.RECOVERED,
                "device", time("2026-08-30T10:01:00+08:00"),
                time("2026-08-30T10:05:00+08:00"), null,
                null, time("2026-08-30T10:01:00+08:00"),
                time("2026-08-30T10:07:00+08:00"), null,
                null, null, null);
    }

    private DispatchCommandResponse command() {
        return new DispatchCommandResponse(
                9L, 1L, 1L, "T1", 30L, "driver",
                20L, "沪A1", "route_v2", 2,
                TransportTaskRouteStatus.ACTIVE, DispatchCommandType.ROUTE_CHANGE,
                "switch", DispatchCommandStatus.COMPLETED, "done", 40L,
                time("2026-08-30T10:02:00+08:00"),
                time("2026-08-30T10:02:00+08:00"),
                null, time("2026-08-30T10:03:00+08:00"),
                time("2026-08-30T10:06:00+08:00"), null);
    }

    private TransportTaskStatusTransitionSnapshot transition(
            Long id, TransportTaskStatus from, TransportTaskStatus to, String changedAt) {
        return new TransportTaskStatusTransitionSnapshot(
                id, 1L, from, to, time(changedAt));
    }

    private GpsSample gps(double longitude, double latitude, Instant timestamp) {
        return new GpsSample("sim_019", longitude, latitude, 0.0, 90.0, timestamp);
    }

    private OffsetDateTime time(String value) {
        return OffsetDateTime.parse(value);
    }
}
