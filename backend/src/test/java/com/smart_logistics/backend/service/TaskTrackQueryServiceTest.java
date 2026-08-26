package com.smart_logistics.backend.service;

import com.smart_logistics.backend.dto.realtime.GpsSample;
import com.smart_logistics.backend.entity.TransportTask;
import com.smart_logistics.backend.entity.Vehicle;
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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
    private TaskTrackQueryService service;

    @BeforeEach
    void setUp() {
        service = new TaskTrackQueryService(taskMapper, vehicleMapper, dataScopeService,
                gpsInfluxService, Duration.ofMinutes(2));
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

    private TransportTask task() {
        TransportTask task = new TransportTask();
        task.setId(10L);
        task.setVehicleId(1L);
        task.setCreatedAt(LocalDateTime.of(2026, 8, 25, 9, 0));
        return task;
    }
}
