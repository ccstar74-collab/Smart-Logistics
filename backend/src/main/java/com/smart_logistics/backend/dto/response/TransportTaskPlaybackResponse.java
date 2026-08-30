package com.smart_logistics.backend.dto.response;

import java.util.List;

public record TransportTaskPlaybackResponse(
        TransportTaskResponse task,
        PlaybackActualTrackResponse actualTrack,
        List<TransportTaskRouteResponse> routeVersions,
        List<AlarmResponse> alarms,
        List<DispatchCommandResponse> dispatchCommands,
        List<PlaybackEventResponse> events) {

    public TransportTaskPlaybackResponse {
        routeVersions = List.copyOf(routeVersions);
        alarms = List.copyOf(alarms);
        dispatchCommands = List.copyOf(dispatchCommands);
        events = List.copyOf(events);
    }
}
