package com.smart_logistics.backend.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.smart_logistics.backend.dto.TransportTaskRouteSnapshot;
import com.smart_logistics.backend.entity.TransportTaskRoute;
import com.smart_logistics.backend.enums.TransportTaskRouteStatus;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.mapper.TransportTaskRouteMapper;
import com.smart_logistics.backend.service.eta.EtaCoordinate;
import com.smart_logistics.backend.service.eta.EtaPlannedRoute;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransportTaskRouteServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-26T08:00:00Z");

    @Mock private TransportTaskRouteMapper routeMapper;

    private TransportTaskRouteService service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "route-test"),
                TransportTaskRoute.class);
        service = new TransportTaskRouteService(
                routeMapper, Clock.fixed(NOW, ZoneOffset.UTC), () -> "route_fixed");
    }

    @Test
    void persistsInitialRouteAsVersionOneActiveSnapshot() {
        when(routeMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(routeMapper.insert(any(TransportTaskRoute.class))).thenAnswer(invocation -> {
            TransportTaskRoute route = invocation.getArgument(0);
            route.setId(7L);
            return 1;
        });

        TransportTaskRouteSnapshot result =
                service.persistInitialActiveRoute(1L, plannedRoute());

        assertEquals(7L, result.id());
        assertEquals("route_fixed", result.routeId());
        assertEquals(1, result.routeVersion());
        assertEquals(TransportTaskRouteStatus.ACTIVE, result.status());
        assertEquals("AMAP", result.provider());
        assertEquals("GCJ02", result.coordinateSystem());
        assertEquals(720, result.durationSeconds());
        assertEquals(List.of(106.570123456789, 29.490987654321),
                result.routePoints().getFirst());

        ArgumentCaptor<TransportTaskRoute> captor =
                ArgumentCaptor.forClass(TransportTaskRoute.class);
        verify(routeMapper).insert(captor.capture());
        assertEquals(1L, captor.getValue().getTaskId());
        assertEquals("ACTIVE", captor.getValue().getStatus());
    }

    @Test
    void readsCurrentActiveRouteByTaskAndStatus() {
        when(routeMapper.selectOne(any(Wrapper.class))).thenReturn(entity());

        TransportTaskRouteSnapshot result = service.getActiveRoute(1L).orElseThrow();

        assertEquals("route_fixed", result.routeId());
        ArgumentCaptor<Wrapper<TransportTaskRoute>> captor =
                ArgumentCaptor.forClass(Wrapper.class);
        verify(routeMapper).selectOne(captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("task_id"));
        assertTrue(captor.getValue().getSqlSegment().contains("status"));
    }

    @Test
    void listsTaskRoutesInVersionOrder() {
        TransportTaskRoute first = entity();
        TransportTaskRoute second = entity();
        second.setId(8L);
        second.setRouteId("route_second");
        second.setRouteVersion(2);
        second.setStatus(TransportTaskRouteStatus.READY.name());
        when(routeMapper.selectList(any(Wrapper.class))).thenReturn(List.of(first, second));

        List<TransportTaskRouteSnapshot> result = service.findRoutesByTaskId(1L);

        assertEquals(List.of(1, 2), result.stream()
                .map(TransportTaskRouteSnapshot::routeVersion).toList());
        assertEquals(TransportTaskRouteStatus.READY, result.get(1).status());
    }

    @Test
    void routeIdUniqueViolationIsExplicitConflict() {
        when(routeMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(routeMapper.insert(any(TransportTaskRoute.class))).thenThrow(
                new DuplicateKeyException("Duplicate key uk_transport_task_route_id"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.persistInitialActiveRoute(1L, plannedRoute()));

        assertEquals(ErrorCode.DATA_CONFLICT, exception.getErrorCode());
        assertEquals("routeId already exists", exception.getMessage());
    }

    @Test
    void taskVersionUniqueViolationIsExplicitConflict() {
        when(routeMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(routeMapper.insert(any(TransportTaskRoute.class))).thenThrow(
                new DuplicateKeyException("Duplicate key uk_transport_task_route_version"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.persistInitialActiveRoute(1L, plannedRoute()));

        assertEquals(ErrorCode.DATA_CONFLICT, exception.getErrorCode());
        assertEquals("route version already exists for transport task", exception.getMessage());
    }

    @Test
    void concurrentInitialInsertReturnsWinningActiveRoute() {
        when(routeMapper.selectOne(any(Wrapper.class))).thenReturn(null, entity());
        when(routeMapper.insert(any(TransportTaskRoute.class))).thenThrow(
                new DuplicateKeyException("Duplicate key uk_transport_task_route_version"));

        TransportTaskRouteSnapshot result =
                service.persistInitialActiveRoute(1L, plannedRoute());

        assertEquals("route_fixed", result.routeId());
        assertEquals(1, result.routeVersion());
    }

    private EtaPlannedRoute plannedRoute() {
        return new EtaPlannedRoute(List.of(
                new EtaCoordinate(106.570123456789, 29.490987654321),
                new EtaCoordinate(106.610987654321, 29.520123456789)),
                5_500, Duration.ofSeconds(720));
    }

    private TransportTaskRoute entity() {
        TransportTaskRoute route = new TransportTaskRoute();
        route.setId(7L);
        route.setRouteId("route_fixed");
        route.setTaskId(1L);
        route.setProvider("AMAP");
        route.setCoordinateSystem("GCJ02");
        route.setRoutePoints(List.of(
                List.of(106.570123456789, 29.490987654321),
                List.of(106.610987654321, 29.520123456789)));
        route.setDistanceMeters(5_500L);
        route.setDurationSeconds(720L);
        route.setRouteVersion(1);
        route.setStatus(TransportTaskRouteStatus.ACTIVE.name());
        route.setCreatedAt(LocalDateTime.of(2026, 8, 26, 16, 0));
        route.setUpdatedAt(LocalDateTime.of(2026, 8, 26, 16, 0));
        return route;
    }
}
