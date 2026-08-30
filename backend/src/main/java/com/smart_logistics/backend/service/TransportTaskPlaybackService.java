package com.smart_logistics.backend.service;

import com.smart_logistics.backend.dto.TaskTrackSnapshot;
import com.smart_logistics.backend.dto.TransportTaskStatusTransitionSnapshot;
import com.smart_logistics.backend.dto.response.AlarmResponse;
import com.smart_logistics.backend.dto.response.DispatchCommandResponse;
import com.smart_logistics.backend.dto.response.PlaybackActualTrackResponse;
import com.smart_logistics.backend.dto.response.PlaybackEventResponse;
import com.smart_logistics.backend.dto.response.PlaybackPositionResponse;
import com.smart_logistics.backend.dto.response.PlaybackTrackPointResponse;
import com.smart_logistics.backend.dto.response.TransportTaskPlaybackResponse;
import com.smart_logistics.backend.dto.response.TransportTaskResponse;
import com.smart_logistics.backend.dto.response.TransportTaskRouteResponse;
import com.smart_logistics.backend.enums.PlaybackEventType;
import com.smart_logistics.backend.enums.TransportTaskStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class TransportTaskPlaybackService {

    private static final String WGS84 = "WGS84";
    private static final ZoneId API_TIME_ZONE = ZoneId.of("Asia/Shanghai");

    private final TaskTrackQueryService taskTrackQueryService;
    private final TransportTaskService transportTaskService;
    private final TransportTaskStatusRecordService statusRecordService;
    private final AlarmService alarmService;
    private final DispatchCommandService dispatchCommandService;

    public TransportTaskPlaybackService(
            TaskTrackQueryService taskTrackQueryService,
            TransportTaskService transportTaskService,
            TransportTaskStatusRecordService statusRecordService,
            AlarmService alarmService,
            DispatchCommandService dispatchCommandService) {
        this.taskTrackQueryService = taskTrackQueryService;
        this.transportTaskService = transportTaskService;
        this.statusRecordService = statusRecordService;
        this.alarmService = alarmService;
        this.dispatchCommandService = dispatchCommandService;
    }

    public TransportTaskPlaybackResponse getPlayback(Long taskId) {
        TaskTrackSnapshot track = taskTrackQueryService.getTaskTrack(taskId);
        TransportTaskResponse task = transportTaskService.getTransportTask(taskId);
        List<PlaybackTrackPointResponse> trackPoints = track.points().stream()
                .map(point -> new PlaybackTrackPointResponse(
                        point.longitude(), point.latitude(), point.speed(),
                        point.direction(), point.collectedAt()
                        .atZone(API_TIME_ZONE).toOffsetDateTime()))
                .toList();
        PlaybackActualTrackResponse actualTrack =
                new PlaybackActualTrackResponse(WGS84, trackPoints);

        List<TransportTaskRouteResponse> routes =
                transportTaskService.listTransportTaskRoutes(taskId);
        List<AlarmResponse> alarms = alarmService.listTaskAlarmHistory(taskId);
        List<DispatchCommandResponse> commands =
                dispatchCommandService.listTaskCommandHistory(taskId);
        List<TransportTaskStatusTransitionSnapshot> transitions =
                statusRecordService.findTaskTransitions(taskId);

        return new TransportTaskPlaybackResponse(
                task, actualTrack, routes, alarms, commands,
                buildTimeline(transitions, routes, alarms, commands, trackPoints));
    }

    private List<PlaybackEventResponse> buildTimeline(
            List<TransportTaskStatusTransitionSnapshot> transitions,
            List<TransportTaskRouteResponse> routes,
            List<AlarmResponse> alarms,
            List<DispatchCommandResponse> commands,
            List<PlaybackTrackPointResponse> trackPoints) {
        List<PlaybackEventResponse> events = new ArrayList<>();

        for (TransportTaskStatusTransitionSnapshot transition : transitions) {
            if (transition.changedAt() == null) {
                continue;
            }
            if (transition.toStatus() == TransportTaskStatus.TRANSPORTING) {
                events.add(event(PlaybackEventType.TASK_STARTED,
                        transition.changedAt(), null, null, null, null, null));
            }
            if (transition.fromStatus() == TransportTaskStatus.TRANSPORTING
                    && transition.toStatus() == TransportTaskStatus.COMPLETED) {
                events.add(event(PlaybackEventType.TASK_COMPLETED,
                        transition.changedAt(), null, null, null, null, null));
            }
        }

        for (TransportTaskRouteResponse route : routes) {
            if (route.generatedAt() != null) {
                events.add(event(PlaybackEventType.ROUTE_GENERATED,
                        route.generatedAt(), null, null,
                        route.routeId(), route.routeVersion(), null));
            }
            if (route.activatedAt() != null) {
                events.add(event(PlaybackEventType.ROUTE_ACTIVATED,
                        route.activatedAt(), null, null,
                        route.routeId(), route.routeVersion(),
                        nearestTrackPosition(route.activatedAt(), trackPoints)));
            }
        }

        for (AlarmResponse alarm : alarms) {
            if (alarm.getOccurredAt() != null) {
                events.add(event(PlaybackEventType.ALARM_TRIGGERED,
                        alarm.getOccurredAt(), alarm.getId(), null,
                        null, null, null));
            }
            if (alarm.getRecoveredAt() != null) {
                events.add(event(PlaybackEventType.ALARM_RECOVERED,
                        alarm.getRecoveredAt(), alarm.getId(), null,
                        null, null, null));
            }
            if (alarm.getResolvedAt() != null) {
                events.add(event(PlaybackEventType.ALARM_RESOLVED,
                        alarm.getResolvedAt(), alarm.getId(), null,
                        null, null, null));
            }
        }

        for (DispatchCommandResponse command : commands) {
            if (command.getSentAt() != null) {
                events.add(event(PlaybackEventType.COMMAND_SENT,
                        command.getSentAt(), command.getAlarmId(), command.getId(),
                        command.getRouteId(), command.getRouteVersion(), null));
            }
            if (command.getAcknowledgedAt() != null) {
                events.add(event(PlaybackEventType.COMMAND_ACKNOWLEDGED,
                        command.getAcknowledgedAt(), command.getAlarmId(), command.getId(),
                        command.getRouteId(), command.getRouteVersion(), null));
            }
            if (command.getExecutingAt() != null) {
                events.add(event(PlaybackEventType.COMMAND_EXECUTING,
                        command.getExecutingAt(), command.getAlarmId(), command.getId(),
                        command.getRouteId(), command.getRouteVersion(), null));
            }
            if (command.getCompletedAt() != null) {
                events.add(event(PlaybackEventType.COMMAND_COMPLETED,
                        command.getCompletedAt(), command.getAlarmId(), command.getId(),
                        command.getRouteId(), command.getRouteVersion(), null));
            }
        }

        events.sort(Comparator.comparing(PlaybackEventResponse::time)
                .thenComparing(event -> event.type().ordinal()));
        return List.copyOf(events);
    }

    private PlaybackPositionResponse nearestTrackPosition(
            OffsetDateTime activatedAt,
            List<PlaybackTrackPointResponse> trackPoints) {
        return trackPoints.stream()
                .filter(point -> point.timestamp() != null)
                .min(Comparator
                        .comparing((PlaybackTrackPointResponse point) ->
                                Duration.between(activatedAt.toInstant(),
                                        point.timestamp().toInstant()).abs())
                        .thenComparing(PlaybackTrackPointResponse::timestamp))
                .map(point -> new PlaybackPositionResponse(
                        point.longitude(), point.latitude(), WGS84))
                .orElse(null);
    }

    private PlaybackEventResponse event(
            PlaybackEventType type,
            OffsetDateTime time,
            Long alarmId,
            Long commandId,
            String routeId,
            Integer routeVersion,
            PlaybackPositionResponse position) {
        return new PlaybackEventResponse(type, time, alarmId, commandId,
                routeId, routeVersion, position);
    }
}
