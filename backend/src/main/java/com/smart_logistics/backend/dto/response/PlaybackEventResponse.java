package com.smart_logistics.backend.dto.response;

import com.smart_logistics.backend.enums.PlaybackEventType;

import java.time.OffsetDateTime;

public record PlaybackEventResponse(
        PlaybackEventType type,
        OffsetDateTime time,
        Long alarmId,
        Long commandId,
        String routeId,
        Integer routeVersion,
        PlaybackPositionResponse position) {
}
