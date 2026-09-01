package com.smart_logistics.backend.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.smart_logistics.backend.dto.TransportTaskRouteSnapshot;
import com.smart_logistics.backend.dto.TrafficSnapshot;
import com.smart_logistics.backend.entity.TransportTask;
import com.smart_logistics.backend.entity.TransportTaskRoute;
import com.smart_logistics.backend.enums.TransportTaskRouteStatus;
import com.smart_logistics.backend.enums.TransportTaskStatus;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.mapper.TransportTaskRouteMapper;
import com.smart_logistics.backend.mapper.TransportTaskMapper;
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
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransportTaskRouteServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-26T08:00:00Z");

    @Mock private TransportTaskRouteMapper routeMapper;
    @Mock private TransportTaskMapper taskMapper;

    private TransportTaskRouteService service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "route-test"),
                TransportTaskRoute.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "route-task-test"),
                TransportTask.class);
        org.mockito.Mockito.lenient().when(taskMapper.selectOne(any(Wrapper.class)))
                .thenReturn(task(TransportTaskStatus.WAITING));
        service = new TransportTaskRouteService(
                routeMapper, taskMapper, Clock.fixed(NOW, ZoneOffset.UTC),
                () -> "route_fixed");
    }

    @Test
    void persistsInitialRouteAsVersionOneActiveSnapshot() {
        when(routeMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
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
        assertEquals(LocalDateTime.of(2026, 8, 26, 16, 0),
                captor.getValue().getActivatedAt());
        assertEquals(result.createdAt(), result.activatedAt());
    }

    @Test
    void persistsTrafficFactsTogetherWithImmutableRouteSnapshot() {
        TrafficSnapshot traffic = new TrafficSnapshot(
                "AMAP_DRIVING_V3", "躲避拥堵", false, 4,
                20, 5_000, 300, 150, 30);
        EtaPlannedRoute planned = new EtaPlannedRoute(
                plannedRoute().polyline(), 5_500, Duration.ofSeconds(720), traffic);
        when(routeMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(routeMapper.insert(any(TransportTaskRoute.class))).thenAnswer(invocation -> {
            TransportTaskRoute route = invocation.getArgument(0);
            route.setId(7L);
            return 1;
        });

        TransportTaskRouteSnapshot result =
                service.persistInitialActiveRoute(1L, planned);

        assertEquals(traffic, result.trafficSnapshot());
        ArgumentCaptor<TransportTaskRoute> captor =
                ArgumentCaptor.forClass(TransportTaskRoute.class);
        verify(routeMapper).insert(captor.capture());
        assertEquals(traffic, captor.getValue().getTrafficSnapshot());
    }

    @Test
    void persistsCandidateBatchWithConsecutiveReadyVersions() {
        AtomicInteger routeSequence = new AtomicInteger();
        service = new TransportTaskRouteService(
                routeMapper, taskMapper, Clock.fixed(NOW, ZoneOffset.UTC),
                () -> "route_" + routeSequence.incrementAndGet());
        TransportTaskRoute latest = entity();
        latest.setRouteVersion(4);
        when(routeMapper.selectList(any(Wrapper.class))).thenReturn(List.of(entity()));
        when(routeMapper.selectOne(any(Wrapper.class))).thenReturn(latest);
        AtomicInteger idSequence = new AtomicInteger(10);
        when(routeMapper.insert(any(TransportTaskRoute.class))).thenAnswer(invocation -> {
            TransportTaskRoute route = invocation.getArgument(0);
            route.setId((long) idSequence.incrementAndGet());
            return 1;
        });

        List<TransportTaskRouteSnapshot> result = service.persistReadyRoutes(
                1L, List.of(plannedRoute(), plannedRoute()));

        assertEquals(List.of(5, 6), result.stream()
                .map(TransportTaskRouteSnapshot::routeVersion).toList());
        assertEquals(List.of("route_1", "route_2"), result.stream()
                .map(TransportTaskRouteSnapshot::routeId).toList());
        assertTrue(result.stream().allMatch(route ->
                route.status() == TransportTaskRouteStatus.READY));
        ArgumentCaptor<TransportTaskRoute> captor =
                ArgumentCaptor.forClass(TransportTaskRoute.class);
        verify(routeMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertEquals(List.of(5, 6), captor.getAllValues().stream()
                .map(TransportTaskRoute::getRouteVersion).toList());
    }

    @Test
    void readsCurrentActiveRouteByTaskAndStatus() {
        when(routeMapper.selectList(any(Wrapper.class))).thenReturn(List.of(entity()));

        TransportTaskRouteSnapshot result = service.getActiveRoute(1L).orElseThrow();

        assertEquals("route_fixed", result.routeId());
        ArgumentCaptor<Wrapper<TransportTaskRoute>> captor =
                ArgumentCaptor.forClass(Wrapper.class);
        verify(routeMapper).selectList(captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("task_id"));
        assertTrue(captor.getValue().getSqlSegment().contains("status"));
    }

    @Test
    void multipleActiveRoutesFailFastInsteadOfSelectingLatestVersion() {
        TransportTaskRoute first = entity();
        TransportTaskRoute second = entity();
        second.setId(8L);
        second.setRouteId("route_active_v2");
        second.setRouteVersion(2);
        when(routeMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(second, first));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getActiveRoute(1L));

        assertEquals(ErrorCode.DATA_CONFLICT, exception.getErrorCode());
        assertEquals("multiple active routes found for transport task",
                exception.getMessage());
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
        when(routeMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(routeMapper.insert(any(TransportTaskRoute.class))).thenThrow(
                new DuplicateKeyException("Duplicate key uk_transport_task_route_id"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.persistInitialActiveRoute(1L, plannedRoute()));

        assertEquals(ErrorCode.DATA_CONFLICT, exception.getErrorCode());
        assertEquals("routeId already exists", exception.getMessage());
    }

    @Test
    void taskVersionUniqueViolationIsExplicitConflict() {
        when(routeMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(routeMapper.insert(any(TransportTaskRoute.class))).thenThrow(
                new DuplicateKeyException("Duplicate key uk_transport_task_route_version"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.persistInitialActiveRoute(1L, plannedRoute()));

        assertEquals(ErrorCode.DATA_CONFLICT, exception.getErrorCode());
        assertEquals("route version already exists for transport task", exception.getMessage());
    }

    @Test
    void concurrentInitialInsertReturnsWinningActiveRoute() {
        when(routeMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(), List.of(entity()));
        when(routeMapper.insert(any(TransportTaskRoute.class))).thenThrow(
                new DuplicateKeyException("Duplicate key uk_transport_task_route_version"));

        TransportTaskRouteSnapshot result =
                service.persistInitialActiveRoute(1L, plannedRoute());

        assertEquals("route_fixed", result.routeId());
        assertEquals(1, result.routeVersion());
    }

    @Test
    void persistsReadyRouteWithNextVersionWithoutReplacingActive() {
        when(routeMapper.selectList(any(Wrapper.class))).thenReturn(List.of(entity()));
        when(routeMapper.selectOne(any(Wrapper.class))).thenReturn(entity());
        when(routeMapper.insert(any(TransportTaskRoute.class))).thenAnswer(invocation -> {
            TransportTaskRoute route = invocation.getArgument(0);
            route.setId(8L);
            return 1;
        });

        TransportTaskRouteSnapshot result = service.persistReadyRoute(1L, plannedRoute());

        assertEquals(2, result.routeVersion());
        assertEquals(TransportTaskRouteStatus.READY, result.status());
        ArgumentCaptor<TransportTaskRoute> captor =
                ArgumentCaptor.forClass(TransportTaskRoute.class);
        verify(routeMapper).insert(captor.capture());
        assertEquals("READY", captor.getValue().getStatus());
    }

    @Test
    void subsequentReadyRouteUsesMonotonicallyIncreasingVersion() {
        TransportTaskRoute latest = readyEntity();
        when(routeMapper.selectList(any(Wrapper.class))).thenReturn(List.of(entity()));
        when(routeMapper.selectOne(any(Wrapper.class))).thenReturn(latest);
        when(routeMapper.insert(any(TransportTaskRoute.class))).thenReturn(1);

        TransportTaskRouteSnapshot result = service.persistReadyRoute(1L, plannedRoute());

        assertEquals(3, result.routeVersion());
        assertEquals(TransportTaskRouteStatus.READY, result.status());
    }

    @Test
    void readyVersionAllocationLocksTaskRowForConcurrentRequests() {
        when(routeMapper.selectList(any(Wrapper.class))).thenReturn(List.of(entity()));
        when(routeMapper.selectOne(any(Wrapper.class))).thenReturn(entity());
        when(routeMapper.insert(any(TransportTaskRoute.class))).thenReturn(1);

        service.persistReadyRoute(1L, plannedRoute());

        ArgumentCaptor<Wrapper<TransportTask>> captor =
                ArgumentCaptor.forClass(Wrapper.class);
        verify(taskMapper).selectOne(captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("FOR UPDATE"));
    }

    @Test
    void duplicateReadyVersionIsExplicitConflict() {
        when(routeMapper.selectList(any(Wrapper.class))).thenReturn(List.of(entity()));
        when(routeMapper.selectOne(any(Wrapper.class))).thenReturn(entity());
        when(routeMapper.insert(any(TransportTaskRoute.class))).thenThrow(
                new DuplicateKeyException("Duplicate key uk_transport_task_route_version"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.persistReadyRoute(1L, plannedRoute()));

        assertEquals(ErrorCode.DATA_CONFLICT, exception.getErrorCode());
        assertEquals("route version already exists for transport task", exception.getMessage());
    }

    @Test
    void completedTaskCannotCreateReadyRoute() {
        when(taskMapper.selectOne(any(Wrapper.class)))
                .thenReturn(task(TransportTaskStatus.COMPLETED));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.persistReadyRoute(1L, plannedRoute()));

        assertEquals(ErrorCode.STATE_CONFLICT, exception.getErrorCode());
        verify(routeMapper, never()).insert(any(TransportTaskRoute.class));
    }

    @Test
    void activatesReadyRouteAndInactivatesOldActiveRoute() {
        TransportTaskRoute ready = readyEntity();
        when(routeMapper.selectOne(any(Wrapper.class))).thenReturn(ready);
        when(routeMapper.selectList(any(Wrapper.class))).thenReturn(List.of(entity()));
        when(routeMapper.update(org.mockito.ArgumentMatchers.isNull(), any(Wrapper.class)))
                .thenReturn(1, 1);

        TransportTaskRouteSnapshot result = service.activateReadyRoute(1L, "route_ready");

        assertEquals(TransportTaskRouteStatus.ACTIVE, result.status());
        assertEquals(2, result.routeVersion());
        ArgumentCaptor<LambdaUpdateWrapper<TransportTaskRoute>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(routeMapper, org.mockito.Mockito.times(2)).update(
                org.mockito.ArgumentMatchers.isNull(), captor.capture());
        assertTrue(captor.getAllValues().get(0).getParamNameValuePairs()
                .containsValue("INACTIVE"));
        assertTrue(captor.getAllValues().get(1).getParamNameValuePairs()
                .containsValue("ACTIVE"));
        assertTrue(captor.getAllValues().get(0).getSqlSet()
                .contains("deactivated_at"));
        assertTrue(captor.getAllValues().get(1).getSqlSet()
                .contains("activated_at"));
        assertEquals(result.updatedAt(), result.activatedAt());
    }

    @Test
    void activatingRouteFromAnotherTaskIsNotFound() {
        when(routeMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.activateReadyRoute(1L, "route_other_task"));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
        verify(routeMapper, never()).update(
                org.mockito.ArgumentMatchers.isNull(), any(Wrapper.class));
    }

    @Test
    void inactiveRouteCannotBeActivated() {
        TransportTaskRoute inactive = readyEntity();
        inactive.setStatus(TransportTaskRouteStatus.INACTIVE.name());
        when(routeMapper.selectOne(any(Wrapper.class))).thenReturn(inactive);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.activateReadyRoute(1L, "route_ready"));

        assertEquals(ErrorCode.STATE_CONFLICT, exception.getErrorCode());
        assertEquals("only READY route can be activated", exception.getMessage());
    }

    @Test
    void alreadyActiveRouteActivationReturnsExplicitConflict() {
        when(routeMapper.selectOne(any(Wrapper.class))).thenReturn(entity());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.activateReadyRoute(1L, "route_fixed"));

        assertEquals(ErrorCode.STATE_CONFLICT, exception.getErrorCode());
        assertEquals("only READY route can be activated", exception.getMessage());
    }

    @Test
    void targetActivationFailureEscapesTransactionSoOldActiveRollsBack() {
        when(routeMapper.selectOne(any(Wrapper.class))).thenReturn(readyEntity());
        when(routeMapper.selectList(any(Wrapper.class))).thenReturn(List.of(entity()));
        when(routeMapper.update(org.mockito.ArgumentMatchers.isNull(), any(Wrapper.class)))
                .thenReturn(1, 0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.activateReadyRoute(1L, "route_ready"));

        assertEquals(ErrorCode.STATE_CONFLICT, exception.getErrorCode());
        assertEquals("ready route status conflict", exception.getMessage());
        verify(routeMapper, org.mockito.Mockito.times(2)).update(
                org.mockito.ArgumentMatchers.isNull(), any(Wrapper.class));
    }

    @Test
    void replanCreatesNextVersionFromMaximumAndAtomicallyReplacesActive() {
        TransportTaskRoute active = entity();
        active.setRouteVersion(2);
        TransportTaskRoute latest = readyEntity();
        latest.setRouteVersion(3);
        when(taskMapper.selectOne(any(Wrapper.class)))
                .thenReturn(task(TransportTaskStatus.TRANSPORTING));
        when(routeMapper.selectList(any(Wrapper.class))).thenReturn(List.of(active));
        when(routeMapper.selectOne(any(Wrapper.class))).thenReturn(latest);
        when(routeMapper.insert(any(TransportTaskRoute.class))).thenAnswer(invocation -> {
            TransportTaskRoute route = invocation.getArgument(0);
            assertEquals(TransportTaskRouteStatus.READY.name(), route.getStatus());
            route.setId(9L);
            return 1;
        });
        when(routeMapper.update(org.mockito.ArgumentMatchers.isNull(), any(Wrapper.class)))
                .thenReturn(1, 1);

        TransportTaskRouteSnapshot result = service.replaceActiveRouteFromReplan(
                1L, 20L, plannedRoute());

        assertEquals(4, result.routeVersion());
        assertEquals(TransportTaskRouteStatus.ACTIVE, result.status());
        verify(routeMapper).insert(any(TransportTaskRoute.class));
        ArgumentCaptor<LambdaUpdateWrapper<TransportTaskRoute>> updates =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(routeMapper, org.mockito.Mockito.times(2)).update(
                org.mockito.ArgumentMatchers.isNull(), updates.capture());
        assertTrue(updates.getAllValues().get(0).getParamNameValuePairs()
                .containsValue("INACTIVE"));
        assertTrue(updates.getAllValues().get(1).getParamNameValuePairs()
                .containsValue("ACTIVE"));
        assertTrue(updates.getAllValues().get(0).getSqlSet()
                .contains("deactivated_at"));
        assertTrue(updates.getAllValues().get(1).getSqlSet()
                .contains("activated_at"));
        assertEquals(result.updatedAt(), result.activatedAt());
    }

    @Test
    void replanActivationFailureEscapesTransactionForFullRollback() {
        TransportTaskRoute active = entity();
        TransportTaskRoute latest = readyEntity();
        when(taskMapper.selectOne(any(Wrapper.class)))
                .thenReturn(task(TransportTaskStatus.TRANSPORTING));
        when(routeMapper.selectList(any(Wrapper.class))).thenReturn(List.of(active));
        when(routeMapper.selectOne(any(Wrapper.class))).thenReturn(latest);
        when(routeMapper.insert(any(TransportTaskRoute.class))).thenAnswer(invocation -> {
            TransportTaskRoute route = invocation.getArgument(0);
            route.setId(9L);
            return 1;
        });
        when(routeMapper.update(org.mockito.ArgumentMatchers.isNull(), any(Wrapper.class)))
                .thenReturn(1, 0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.replaceActiveRouteFromReplan(1L, 20L, plannedRoute()));

        assertEquals("replanned route activation conflict", exception.getMessage());
        verify(routeMapper).insert(any(TransportTaskRoute.class));
        verify(routeMapper, org.mockito.Mockito.times(2)).update(
                org.mockito.ArgumentMatchers.isNull(), any(Wrapper.class));
    }

    @Test
    void replanRejectsTaskOrVehicleChangedDuringAmapCall() {
        TransportTask changed = task(TransportTaskStatus.COMPLETED);
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(changed);

        assertThrows(BusinessException.class,
                () -> service.replaceActiveRouteFromReplan(1L, 20L, plannedRoute()));

        changed.setStatus(TransportTaskStatus.TRANSPORTING.name());
        changed.setVehicleId(21L);
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(changed);
        assertThrows(BusinessException.class,
                () -> service.replaceActiveRouteFromReplan(1L, 20L, plannedRoute()));
        verify(routeMapper, never()).insert(any(TransportTaskRoute.class));
    }

    @Test
    void oldActiveUpdateFailureLeavesReadyRouteUntouched() {
        when(routeMapper.selectOne(any(Wrapper.class))).thenReturn(readyEntity());
        when(routeMapper.selectList(any(Wrapper.class))).thenReturn(List.of(entity()));
        when(routeMapper.update(org.mockito.ArgumentMatchers.isNull(), any(Wrapper.class)))
                .thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.activateReadyRoute(1L, "route_ready"));

        assertEquals("active route status conflict", exception.getMessage());
        verify(routeMapper, org.mockito.Mockito.times(1)).update(
                org.mockito.ArgumentMatchers.isNull(), any(Wrapper.class));
    }

    @Test
    void completedTaskCannotActivateRoute() {
        when(taskMapper.selectOne(any(Wrapper.class)))
                .thenReturn(task(TransportTaskStatus.COMPLETED));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.activateReadyRoute(1L, "route_ready"));

        assertEquals(ErrorCode.STATE_CONFLICT, exception.getErrorCode());
        verify(routeMapper, never()).update(
                org.mockito.ArgumentMatchers.isNull(), any(Wrapper.class));
    }

    @Test
    void routeMutationMethodsDeclareTransactionalBoundaries() throws Exception {
        Method ready = TransportTaskRouteService.class.getMethod(
                "persistReadyRoute", Long.class, EtaPlannedRoute.class);
        Method activate = TransportTaskRouteService.class.getMethod(
                "activateReadyRoute", Long.class, String.class);
        Method replan = TransportTaskRouteService.class.getMethod(
                "replaceActiveRouteFromReplan", Long.class, Long.class,
                EtaPlannedRoute.class);

        assertTrue(ready.getAnnotation(Transactional.class) != null);
        assertTrue(activate.getAnnotation(Transactional.class) != null);
        assertTrue(replan.getAnnotation(Transactional.class) != null);
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

    private TransportTaskRoute readyEntity() {
        TransportTaskRoute route = entity();
        route.setId(8L);
        route.setRouteId("route_ready");
        route.setRouteVersion(2);
        route.setStatus(TransportTaskRouteStatus.READY.name());
        return route;
    }

    private TransportTask task(TransportTaskStatus status) {
        TransportTask task = new TransportTask();
        task.setId(1L);
        task.setStatus(status.name());
        task.setVehicleId(20L);
        return task;
    }
}
