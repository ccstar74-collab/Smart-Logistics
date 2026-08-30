package com.smart_logistics.backend.service;

import com.smart_logistics.backend.dto.realtime.GpsSample;
import com.smart_logistics.backend.dto.TransportTaskStatusTransitionSnapshot;
import com.smart_logistics.backend.entity.TransportTask;
import com.smart_logistics.backend.entity.Vehicle;
import com.smart_logistics.backend.enums.TransportTaskStatus;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.mapper.TransportTaskMapper;
import com.smart_logistics.backend.mapper.VehicleMapper;
import com.smart_logistics.backend.security.BusinessDataScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskTrackQueryServiceTest {
    @Mock private TransportTaskMapper taskMapper;
    @Mock private VehicleMapper vehicleMapper;
    @Mock private BusinessDataScopeService dataScopeService;
    @Mock private GpsInfluxService gpsInfluxService;
    @Mock private TransportTaskStatusRecordService statusRecordService;
    private TaskTrackQueryService service;
    private final Instant now = Instant.parse("2026-08-25T04:00:00Z");

    @BeforeEach
    void setUp() {
        service = new TaskTrackQueryService(taskMapper, vehicleMapper, dataScopeService,
                gpsInfluxService, statusRecordService, Duration.ofMinutes(2),
                Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void rejectsMissingTask() {
        when(taskMapper.selectById(99L)).thenReturn(null);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getTrackPoints(99L));
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void rejectsUnauthorizedTaskBeforeProviderCall() {
        TransportTask task = task();
        when(taskMapper.selectById(10L)).thenReturn(task);
        doThrow(new BusinessException(ErrorCode.FORBIDDEN, "forbidden"))
                .when(dataScopeService).requireTaskAccess(task);
        assertThrows(BusinessException.class, () -> service.getTrackPoints(10L));
        verify(gpsInfluxService, never()).querySamples(any(), any(), any());
    }

    @Test
    void mapsMockedTrackPoints() {
        TransportTask task = task();
        Vehicle vehicle = new Vehicle();
        vehicle.setId(1L);
        vehicle.setPlateNumber("沪A00019");
        vehicle.setSimCode("sim_019");
        when(taskMapper.selectById(10L)).thenReturn(task);
        when(vehicleMapper.selectById(1L)).thenReturn(vehicle);
        when(gpsInfluxService.querySamples(any(), any(), any())).thenReturn(List.of(
                new GpsSample("sim_019", 121.5, 31.2, 20.0, 45.0,
                        Instant.parse("2026-08-25T02:00:00Z"))));

        var result = service.getTrackPoints(10L);

        assertEquals(1, result.size());
        assertEquals(10L, result.getFirst().getTaskId());
        assertEquals(45.0, result.getFirst().getDirection());
        verify(dataScopeService).requireTaskAccess(task);
    }

    @Test
    void completedTaskUsesTransportStatusRecordWindowAndExcludesOtherTaskGps() {
        TransportTask task = task();
        task.setStatus(TransportTaskStatus.COMPLETED.name());
        Vehicle vehicle = vehicle();
        Instant start = Instant.parse("2026-08-25T02:00:00Z");
        Instant end = Instant.parse("2026-08-25T03:00:00Z");
        when(taskMapper.selectById(10L)).thenReturn(task);
        when(vehicleMapper.selectById(1L)).thenReturn(vehicle);
        when(statusRecordService.findTaskTransitions(10L)).thenReturn(List.of(
                transition(1L, TransportTaskStatus.WAITING,
                        TransportTaskStatus.TRANSPORTING, start),
                transition(2L, TransportTaskStatus.TRANSPORTING,
                        TransportTaskStatus.COMPLETED, end)));
        when(gpsInfluxService.querySamples(eq(List.of("sim_019")),
                eq(start), eq(end.plusNanos(1)))).thenReturn(List.of(
                gps(start.minusSeconds(1)),
                gps(end),
                gps(start),
                gps(end.plusSeconds(1))));

        var result = service.getTaskTrack(10L);

        assertEquals(start, result.transportStart());
        assertEquals(end, result.transportEnd());
        assertEquals(List.of(start, end), result.points().stream()
                .map(GpsSample::collectedAt).toList());
    }

    @Test
    void transportingTaskUsesQueryTimeAsOpenWindowEnd() {
        TransportTask task = task();
        Vehicle vehicle = vehicle();
        Instant start = Instant.parse("2026-08-25T02:00:00Z");
        when(taskMapper.selectById(10L)).thenReturn(task);
        when(vehicleMapper.selectById(1L)).thenReturn(vehicle);
        when(statusRecordService.findTaskTransitions(10L)).thenReturn(List.of(
                transition(1L, TransportTaskStatus.WAITING,
                        TransportTaskStatus.TRANSPORTING, start)));
        when(gpsInfluxService.querySamples(eq(List.of("sim_019")),
                eq(start), eq(now.plusNanos(1)))).thenReturn(List.of());

        var result = service.getTaskTrack(10L);

        assertEquals(start, result.transportStart());
        assertEquals(now, result.transportEnd());
    }

    @Test
    void rejectsTaskWithoutVehicle() {
        TransportTask task = task();
        task.setVehicleId(null);
        when(taskMapper.selectById(10L)).thenReturn(task);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getTaskTrack(10L));

        assertEquals(ErrorCode.STATE_CONFLICT, exception.getErrorCode());
        verify(vehicleMapper, never()).selectById(any());
    }

    private Vehicle vehicle() {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(1L);
        vehicle.setPlateNumber("沪A00019");
        vehicle.setSimCode("sim_019");
        return vehicle;
    }

    private GpsSample gps(Instant collectedAt) {
        return new GpsSample("sim_019", 121.5, 31.2,
                20.0, 45.0, collectedAt);
    }

    private TransportTaskStatusTransitionSnapshot transition(
            Long id, TransportTaskStatus from, TransportTaskStatus to, Instant changedAt) {
        return new TransportTaskStatusTransitionSnapshot(
                id, 10L, from, to, OffsetDateTime.ofInstant(changedAt, ZoneOffset.UTC));
    }

    private TransportTask task() {
        TransportTask task = new TransportTask();
        task.setId(10L);
        task.setVehicleId(1L);
        task.setStatus(TransportTaskStatus.TRANSPORTING.name());
        task.setActualStartTime(LocalDateTime.of(2026, 8, 25, 9, 0));
        return task;
    }
}
