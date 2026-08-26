package com.smart_logistics.backend.service.eta;

import com.smart_logistics.backend.dto.response.PlannedRouteResponse;
import com.smart_logistics.backend.dto.response.TransportTaskResponse;
import com.smart_logistics.backend.enums.TransportTaskStatus;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EtaPlannedRouteServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-26T08:00:00Z");

    @Mock private EtaRouteProvider routeProvider;

    @Test
    void returnsFrontendPolylineAndReusesSameTaskRoute() {
        EtaPlannedRoute route = new EtaPlannedRoute(List.of(
                new EtaCoordinate(106.5701, 29.4901),
                new EtaCoordinate(106.6101, 29.5201)),
                5_500, Duration.ofMinutes(12));
        when(routeProvider.plan(106.57, 29.49, 106.61, 29.52)).thenReturn(route);
        EtaPlannedRouteService service = new EtaPlannedRouteService(
                routeProvider, Clock.fixed(NOW, ZoneOffset.UTC));

        PlannedRouteResponse first = service.getResponse(taskResponse());
        PlannedRouteResponse second = service.getResponse(taskResponse());

        assertEquals("AMAP", first.provider());
        assertEquals("GCJ02", first.coordinateSystem());
        assertEquals(2, first.points().size());
        assertEquals(5_500, first.distanceMeters());
        assertEquals(first.generatedAt(), second.generatedAt());
        verify(routeProvider, times(1)).plan(106.57, 29.49, 106.61, 29.52);
    }

    @Test
    void rejectsTaskWithoutCompleteCoordinates() {
        TransportTaskResponse incomplete = new TransportTaskResponse(
                1L, "T1", 10L, 20L, "A", null, null,
                "B", 106.61, 29.52, null, null, null, null,
                TransportTaskStatus.WAITING, null, null, null, null);
        EtaPlannedRouteService service = new EtaPlannedRouteService(
                routeProvider, Clock.fixed(NOW, ZoneOffset.UTC));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getResponse(incomplete));

        assertEquals(ErrorCode.STATE_CONFLICT, exception.getErrorCode());
    }

    private TransportTaskResponse taskResponse() {
        OffsetDateTime now = OffsetDateTime.ofInstant(NOW, ZoneOffset.ofHours(8));
        return new TransportTaskResponse(
                1L, "T1", 10L, 20L, "A", 106.57, 29.49,
                "B", 106.61, 29.52, now, now.plusHours(1), null, null,
                TransportTaskStatus.WAITING, null, null, now, now);
    }
}
