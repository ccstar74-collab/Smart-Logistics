package com.smart_logistics.backend.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart_logistics.backend.common.PageResult;
import com.smart_logistics.backend.dto.TransportTaskRouteSnapshot;
import com.smart_logistics.backend.dto.request.DispatchCommandCreateRequest;
import com.smart_logistics.backend.dto.request.DispatchCommandStatusUpdateRequest;
import com.smart_logistics.backend.dto.response.DispatchCommandResponse;
import com.smart_logistics.backend.dto.response.UserIdentityResponse;
import com.smart_logistics.backend.entity.DispatchCommand;
import com.smart_logistics.backend.entity.Driver;
import com.smart_logistics.backend.entity.TransportTask;
import com.smart_logistics.backend.entity.Vehicle;
import com.smart_logistics.backend.enums.DispatchCommandStatus;
import com.smart_logistics.backend.enums.DispatchCommandType;
import com.smart_logistics.backend.enums.TransportTaskRouteStatus;
import com.smart_logistics.backend.enums.TransportTaskStatus;
import com.smart_logistics.backend.enums.UserRole;
import com.smart_logistics.backend.enums.UserStatus;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.mapper.DispatchCommandMapper;
import com.smart_logistics.backend.mapper.TransportTaskMapper;
import com.smart_logistics.backend.mapper.VehicleMapper;
import com.smart_logistics.backend.security.CurrentUserService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DispatchCommandServiceTest {

    @Mock private DispatchCommandMapper commandMapper;
    @Mock private TransportTaskMapper taskMapper;
    @Mock private VehicleMapper vehicleMapper;
    @Mock private DriverService driverService;
    @Mock private UserDisplayNameService userDisplayNameService;
    @Mock private CurrentUserService currentUserService;
    @Mock private TransportTaskRouteService routeService;

    private DispatchCommandService service;

    @BeforeEach
    void setUp() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, "command"),
                DispatchCommand.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, "task"),
                TransportTask.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, "vehicle"),
                Vehicle.class);
        service = new DispatchCommandService(commandMapper, taskMapper, vehicleMapper,
                driverService, userDisplayNameService, currentUserService, routeService);
        org.mockito.Mockito.lenient().when(currentUserService.getCurrentUser())
                .thenReturn(identity(UserRole.DISPATCHER, null));
        org.mockito.Mockito.lenient().when(taskMapper.selectOne(any(Wrapper.class)))
                .thenReturn(task());
        org.mockito.Mockito.lenient().when(vehicleMapper.selectOne(any(Wrapper.class)))
                .thenReturn(vehicle());
        org.mockito.Mockito.lenient().when(driverService.requireActiveDriver(2L))
                .thenReturn(driver());
        org.mockito.Mockito.lenient().when(userDisplayNameService.getDriverNames(List.of(2L)))
                .thenReturn(Map.of(2L, "Li Si"));
        org.mockito.Mockito.lenient().when(commandMapper.insert(any(DispatchCommand.class)))
                .thenAnswer(invocation -> {
                    DispatchCommand command = invocation.getArgument(0);
                    command.setId(101L);
                    return 1;
                });
        org.mockito.Mockito.lenient().when(taskMapper.selectById(15L)).thenReturn(task());
        org.mockito.Mockito.lenient().when(vehicleMapper.selectById(16L)).thenReturn(vehicle());
    }

    @Test
    void textCreationDerivesDriverAndVehicleFromTask() {
        DispatchCommandResponse response = service.createCommand(request(DispatchCommandType.TEXT));

        ArgumentCaptor<DispatchCommand> captor = ArgumentCaptor.forClass(DispatchCommand.class);
        verify(commandMapper).insert(captor.capture());
        DispatchCommand saved = captor.getValue();
        assertEquals(2L, saved.getTargetDriverId());
        assertEquals(16L, saved.getVehicleId());
        assertEquals(7L, saved.getCreatedBy());
        assertEquals(DispatchCommandStatus.SENT.name(), saved.getStatus());
        assertNull(saved.getTargetRouteId());
        assertEquals(DispatchCommandStatus.SENT, response.getStatus());
        verify(routeService, never()).activateReadyRoute(any(), any());
    }

    @Test
    void routeChangeCreationStoresReadyRouteWithoutActivatingIt() {
        TransportTaskRouteSnapshot ready = route("route_v2", 15L,
                TransportTaskRouteStatus.READY, 2);
        when(routeService.getRouteByRouteId("route_v2")).thenReturn(Optional.of(ready));
        DispatchCommandCreateRequest request = request(DispatchCommandType.ROUTE_CHANGE);
        request.setRouteId("route_v2");

        DispatchCommandResponse response = service.createCommand(request);

        assertEquals("route_v2", response.getRouteId());
        assertEquals(TransportTaskRouteStatus.READY, response.getRouteStatus());
        verify(routeService, never()).activateReadyRoute(any(), any());
    }

    @Test
    void routeChangeRejectsRouteFromAnotherTask() {
        when(routeService.getRouteByRouteId("route_v2")).thenReturn(Optional.of(
                route("route_v2", 99L, TransportTaskRouteStatus.READY, 2)));
        DispatchCommandCreateRequest request = request(DispatchCommandType.ROUTE_CHANGE);
        request.setRouteId("route_v2");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createCommand(request));

        assertEquals(ErrorCode.STATE_CONFLICT, exception.getErrorCode());
        verify(commandMapper, never()).insert(any(DispatchCommand.class));
    }

    @Test
    void driverInboxIsAlwaysScopedToJwtDriver() {
        when(currentUserService.getCurrentUser()).thenReturn(identity(UserRole.DRIVER, 2L));
        when(commandMapper.selectPage(any(Page.class), any(Wrapper.class)))
                .thenAnswer(invocation -> {
                    Page<DispatchCommand> page = invocation.getArgument(0);
                    page.setRecords(List.of(command(DispatchCommandStatus.SENT,
                            DispatchCommandType.TEXT)));
                    page.setTotal(1);
                    return page;
                });

        PageResult<DispatchCommandResponse> result = service.listMyCommands(1, 10, null);

        assertEquals(1, result.getTotal());
        ArgumentCaptor<LambdaQueryWrapper<DispatchCommand>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(commandMapper).selectPage(any(Page.class), captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("target_driver_id"));
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue(2L));
    }

    @Test
    void acknowledgedTransitionSucceedsButSentToCompletedIsRejected() {
        when(currentUserService.getCurrentUser()).thenReturn(identity(UserRole.DRIVER, 2L));
        DispatchCommand sent = command(DispatchCommandStatus.SENT, DispatchCommandType.TEXT);
        when(commandMapper.selectOne(any(Wrapper.class))).thenReturn(sent);
        when(commandMapper.updateById(sent)).thenReturn(1);

        DispatchCommandResponse acknowledged = service.updateStatus(101L,
                update(DispatchCommandStatus.ACKNOWLEDGED));
        assertEquals(DispatchCommandStatus.ACKNOWLEDGED, acknowledged.getStatus());

        sent.setStatus(DispatchCommandStatus.SENT.name());
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateStatus(101L, update(DispatchCommandStatus.COMPLETED)));
        assertEquals(ErrorCode.STATE_CONFLICT, exception.getErrorCode());
    }

    @Test
    void anotherDriverCannotUpdateCommand() {
        when(currentUserService.getCurrentUser()).thenReturn(identity(UserRole.DRIVER, 3L));
        when(commandMapper.selectOne(any(Wrapper.class))).thenReturn(
                command(DispatchCommandStatus.SENT, DispatchCommandType.TEXT));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateStatus(101L,
                        update(DispatchCommandStatus.ACKNOWLEDGED)));

        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
        verify(commandMapper, never()).updateById(any(DispatchCommand.class));
    }

    @Test
    void routeChangeActivatesRouteBeforeAdvancingToExecuting() {
        when(currentUserService.getCurrentUser()).thenReturn(identity(UserRole.DRIVER, 2L));
        DispatchCommand command = command(DispatchCommandStatus.ACKNOWLEDGED,
                DispatchCommandType.ROUTE_CHANGE);
        command.setTargetRouteId("route_v2");
        when(commandMapper.selectOne(any(Wrapper.class))).thenReturn(command);
        when(commandMapper.updateById(command)).thenReturn(1);
        TransportTaskRouteSnapshot ready = route("route_v2", 15L,
                TransportTaskRouteStatus.READY, 2);
        when(routeService.getRouteByRouteId("route_v2")).thenReturn(Optional.of(ready));
        when(routeService.activateReadyRoute(15L, "route_v2")).thenReturn(
                route("route_v2", 15L, TransportTaskRouteStatus.ACTIVE, 2));

        DispatchCommandResponse response = service.updateStatus(101L,
                update(DispatchCommandStatus.EXECUTING));

        assertEquals(DispatchCommandStatus.EXECUTING, response.getStatus());
        InOrder order = inOrder(routeService, commandMapper);
        order.verify(routeService).activateReadyRoute(15L, "route_v2");
        order.verify(commandMapper).updateById(command);
    }

    @Test
    void routeActivationFailureDoesNotUpdateCommand() {
        when(currentUserService.getCurrentUser()).thenReturn(identity(UserRole.DRIVER, 2L));
        DispatchCommand command = command(DispatchCommandStatus.ACKNOWLEDGED,
                DispatchCommandType.ROUTE_CHANGE);
        command.setTargetRouteId("route_v2");
        when(commandMapper.selectOne(any(Wrapper.class))).thenReturn(command);
        when(routeService.getRouteByRouteId("route_v2")).thenReturn(Optional.of(
                route("route_v2", 15L, TransportTaskRouteStatus.READY, 2)));
        when(routeService.activateReadyRoute(15L, "route_v2")).thenThrow(
                new BusinessException(ErrorCode.STATE_CONFLICT, "ready route status conflict"));

        assertThrows(BusinessException.class, () -> service.updateStatus(101L,
                update(DispatchCommandStatus.EXECUTING)));

        assertEquals(DispatchCommandStatus.ACKNOWLEDGED.name(), command.getStatus());
        verify(commandMapper, never()).updateById(any(DispatchCommand.class));
    }

    @Test
    void rejectingRouteChangeNeverActivatesRoute() {
        when(currentUserService.getCurrentUser()).thenReturn(identity(UserRole.DRIVER, 2L));
        DispatchCommand command = command(DispatchCommandStatus.ACKNOWLEDGED,
                DispatchCommandType.ROUTE_CHANGE);
        command.setTargetRouteId("route_v2");
        when(commandMapper.selectOne(any(Wrapper.class))).thenReturn(command);
        when(commandMapper.updateById(command)).thenReturn(1);

        DispatchCommandResponse response = service.updateStatus(101L,
                update(DispatchCommandStatus.REJECTED));

        assertEquals(DispatchCommandStatus.REJECTED, response.getStatus());
        verify(routeService, never()).activateReadyRoute(any(), any());
    }

    private DispatchCommandCreateRequest request(DispatchCommandType type) {
        DispatchCommandCreateRequest request = new DispatchCommandCreateRequest();
        request.setTaskId(15L);
        request.setCommandType(type);
        request.setContent("Proceed carefully");
        return request;
    }

    private DispatchCommandStatusUpdateRequest update(DispatchCommandStatus status) {
        DispatchCommandStatusUpdateRequest request = new DispatchCommandStatusUpdateRequest();
        request.setStatus(status);
        request.setFeedback("Received");
        return request;
    }

    private DispatchCommand command(DispatchCommandStatus status, DispatchCommandType type) {
        DispatchCommand command = new DispatchCommand();
        command.setId(101L);
        command.setTaskId(15L);
        command.setTargetDriverId(2L);
        command.setVehicleId(16L);
        command.setCreatedBy(7L);
        command.setCommandType(type.name());
        command.setContent("Proceed carefully");
        command.setStatus(status.name());
        command.setSentAt(java.time.LocalDateTime.of(2026, 8, 27, 10, 30));
        command.setCreatedAt(java.time.LocalDateTime.of(2026, 8, 27, 10, 30));
        return command;
    }

    private TransportTask task() {
        TransportTask task = new TransportTask();
        task.setId(15L);
        task.setTaskNo("T20260826001");
        task.setVehicleId(16L);
        task.setStatus(TransportTaskStatus.TRANSPORTING.name());
        return task;
    }

    private Vehicle vehicle() {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(16L);
        vehicle.setDriverId(2L);
        vehicle.setPlateNumber("YuA8888");
        return vehicle;
    }

    private Driver driver() {
        Driver driver = new Driver();
        driver.setId(2L);
        driver.setUserId(22L);
        return driver;
    }

    private UserIdentityResponse identity(UserRole role, Long driverId) {
        return new UserIdentityResponse(7L, "user", "User", null, role,
                UserStatus.ACTIVE, driverId, null);
    }

    private TransportTaskRouteSnapshot route(String routeId, Long taskId,
                                             TransportTaskRouteStatus status, int version) {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-27T10:30:00+08:00");
        return new TransportTaskRouteSnapshot(20L, routeId, taskId, "AMAP", "GCJ02",
                List.of(List.of(106.7, 29.6), List.of(106.8, 29.7)),
                1000L, 600L, version, status, now, now);
    }
}
