package com.smart_logistics.backend.dto;

import com.smart_logistics.backend.enums.TransportTaskStatus;

import java.time.OffsetDateTime;

public record TransportTaskStatusTransitionSnapshot(
        Long id,
        Long taskId,
        TransportTaskStatus fromStatus,
        TransportTaskStatus toStatus,
        OffsetDateTime changedAt) {
}
