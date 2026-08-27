package com.smart_logistics.backend.security;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.smart_logistics.backend.dto.response.UserIdentityResponse;
import com.smart_logistics.backend.entity.Cargo;
import com.smart_logistics.backend.entity.TransportTask;
import com.smart_logistics.backend.entity.Vehicle;
import com.smart_logistics.backend.enums.UserRole;
import com.smart_logistics.backend.enums.UserStatus;
import com.smart_logistics.backend.mapper.CargoMapper;
import com.smart_logistics.backend.mapper.TransportTaskMapper;
import com.smart_logistics.backend.mapper.VehicleMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * WebSocket推送车辆权限范围规则：
 * 管理员全部、调度员活动任务车辆、司机本人车辆、货主本人订单活动任务车辆，
 * 其他角色与未绑定业务车辆的模拟设备不可见
 */
@ExtendWith(MockitoExtension.class)
class WebSocketScopeServiceTest {

    @Mock private VehicleMapper vehicleMapper;
    @Mock private TransportTaskMapper transportTaskMapper;
    @Mock private CargoMapper cargoMapper;

    private WebSocketScopeService service;

    @BeforeEach
    void setUp() {
        init(Cargo.class, "ws-scope-cargo");
        init(Vehicle.class, "ws-scope-vehicle");
        init(TransportTask.class, "ws-scope-task");
        service = new WebSocketScopeService(vehicleMapper, transportTaskMapper, cargoMapper);
    }

    @Test
    void adminSeesAllRegisteredVehiclesWithoutQueryingScope() {
        WebSocketScopeService.VehicleScope scope =
                service.resolve(identity(UserRole.ADMIN, null, null));

        assertTrue(scope.allowAll());
        verifyNoInteractions(vehicleMapper, transportTaskMapper, cargoMapper);
    }

    @Test
    void dispatcherSeesSimCodesOfActiveTaskVehicles() {
        TransportTask waiting = task(1L, 10L);
        TransportTask transporting = task(2L, 11L);
        TransportTask missingVehicle = task(3L, null);
        when(transportTaskMapper.selectList(any()))
                .thenReturn(List.of(waiting, transporting, missingVehicle));
        when(vehicleMapper.selectBatchIds(any())).thenReturn(List.of(
                vehicle(10L, "sim_001"), vehicle(11L, "sim_002"), vehicle(12L, " ")));

        WebSocketScopeService.VehicleScope scope =
                service.resolve(identity(UserRole.DISPATCHER, null, null));

        assertFalse(scope.allowAll());
        assertEquals(Set.of("sim_001", "sim_002"), scope.allowedSimCodes());
    }

    @Test
    void driverSeesOwnAssignedVehicles() {
        when(vehicleMapper.selectList(any()))
                .thenReturn(List.of(vehicle(20L, "sim_driver_1")));

        WebSocketScopeService.VehicleScope scope =
                service.resolve(identity(UserRole.DRIVER, 9L, null));

        assertEquals(Set.of("sim_driver_1"), scope.allowedSimCodes());
    }

    @Test
    void driverWithoutDriverIdentitySeesNothing() {
        WebSocketScopeService.VehicleScope scope =
                service.resolve(identity(UserRole.DRIVER, null, null));

        assertFalse(scope.allowAll());
        assertTrue(scope.allowedSimCodes().isEmpty());
        verifyNoInteractions(vehicleMapper, transportTaskMapper, cargoMapper);
    }

    @Test
    void ownerSeesVehiclesOfOwnActiveTasksOnly() {
        Cargo cargo = new Cargo();
        cargo.setId(40L);
        cargo.setOwnerId(3L);
        when(cargoMapper.selectList(any())).thenReturn(List.of(cargo));
        when(transportTaskMapper.selectList(any()))
                .thenReturn(List.of(task(30L, 21L)));
        when(vehicleMapper.selectBatchIds(any()))
                .thenReturn(List.of(vehicle(21L, "sim_owner_1")));

        WebSocketScopeService.VehicleScope scope =
                service.resolve(identity(UserRole.OWNER, null, 3L));

        assertEquals(Set.of("sim_owner_1"), scope.allowedSimCodes());
    }

    @Test
    void ownerWithoutCargoSeesNothing() {
        when(cargoMapper.selectList(any())).thenReturn(List.of());

        WebSocketScopeService.VehicleScope scope =
                service.resolve(identity(UserRole.OWNER, null, 3L));

        assertTrue(scope.allowedSimCodes().isEmpty());
    }

    @Test
    void warehouseManagerSeesNothing() {
        WebSocketScopeService.VehicleScope scope =
                service.resolve(identity(UserRole.WAREHOUSE_MANAGER, null, null));

        assertFalse(scope.allowAll());
        assertTrue(scope.allowedSimCodes().isEmpty());
        verifyNoInteractions(vehicleMapper, transportTaskMapper, cargoMapper);
    }

    private UserIdentityResponse identity(UserRole role, Long driverId, Long ownerId) {
        return new UserIdentityResponse(
                1L, "user", "用户", "13800000000", role, UserStatus.ACTIVE,
                driverId, ownerId);
    }

    private TransportTask task(Long id, Long vehicleId) {
        TransportTask task = new TransportTask();
        task.setId(id);
        task.setVehicleId(vehicleId);
        return task;
    }

    private Vehicle vehicle(Long id, String simCode) {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(id);
        vehicle.setSimCode(simCode);
        return vehicle;
    }

    private void init(Class<?> entityClass, String namespace) {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), namespace),
                entityClass);
    }
}
