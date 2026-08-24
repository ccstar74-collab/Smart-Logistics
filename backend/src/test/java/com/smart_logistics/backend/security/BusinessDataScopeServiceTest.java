package com.smart_logistics.backend.security;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.smart_logistics.backend.dto.response.UserIdentityResponse;
import com.smart_logistics.backend.entity.Alarm;
import com.smart_logistics.backend.entity.Cargo;
import com.smart_logistics.backend.entity.TransportTask;
import com.smart_logistics.backend.entity.Vehicle;
import com.smart_logistics.backend.enums.UserRole;
import com.smart_logistics.backend.enums.UserStatus;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BusinessDataScopeServiceTest {

    @Mock private CurrentUserService currentUserService;
    @Mock private CargoMapper cargoMapper;
    @Mock private VehicleMapper vehicleMapper;
    @Mock private TransportTaskMapper transportTaskMapper;

    private BusinessDataScopeService service;

    @BeforeEach
    void setUp() {
        init(Cargo.class, "scope-cargo");
        init(Vehicle.class, "scope-vehicle");
        init(TransportTask.class, "scope-task");
        init(Alarm.class, "scope-alarm");
        service = new BusinessDataScopeService(
                currentUserService, cargoMapper, vehicleMapper, transportTaskMapper);
    }

    @Test
    void ownerCargoScopeForcesCurrentOwnerAndRejectsConflictingFilter() {
        when(currentUserService.getCurrentUser()).thenReturn(identity(UserRole.OWNER, null, 3L));
        LambdaQueryWrapper<Cargo> query = new LambdaQueryWrapper<>();

        service.applyCargoScope(query, 3L);

        assertTrue(query.getSqlSegment().contains("owner_id"));
        assertTrue(query.getParamNameValuePairs().containsValue(3L));
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.applyCargoScope(new LambdaQueryWrapper<>(), 999L));
        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
    }

    @Test
    void ownerCannotReadAnotherOwnersCargo() {
        when(currentUserService.getCurrentUser()).thenReturn(identity(UserRole.OWNER, null, 3L));
        Cargo cargo = new Cargo();
        cargo.setId(8L);
        cargo.setOwnerId(4L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.requireCargoAccess(cargo));

        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
    }

    @Test
    void driverCargoScopeUsesOnlyCargosFromOwnVehicleTasks() {
        when(currentUserService.getCurrentUser()).thenReturn(identity(UserRole.DRIVER, 9L, null));
        Vehicle ownVehicle = new Vehicle();
        ownVehicle.setId(20L);
        TransportTask ownTask = new TransportTask();
        ownTask.setId(30L);
        ownTask.setVehicleId(20L);
        ownTask.setCargoId(40L);
        when(vehicleMapper.selectList(any())).thenReturn(List.of(ownVehicle));
        when(transportTaskMapper.selectList(any())).thenReturn(List.of(ownTask));
        LambdaQueryWrapper<Cargo> query = new LambdaQueryWrapper<>();

        service.applyCargoScope(query, null);

        assertTrue(query.getSqlSegment().contains("id"));
        assertTrue(query.getParamNameValuePairs().containsValue(40L));
    }

    @Test
    void driverTaskScopeRejectsAnotherDriverFilterAndTask() {
        when(currentUserService.getCurrentUser()).thenReturn(identity(UserRole.DRIVER, 9L, null));
        BusinessException filterException = assertThrows(BusinessException.class,
                () -> service.applyTaskScope(new LambdaQueryWrapper<>(), 10L, null));
        assertEquals(ErrorCode.FORBIDDEN, filterException.getErrorCode());

        when(vehicleMapper.selectList(any())).thenReturn(List.of());
        TransportTask otherTask = new TransportTask();
        otherTask.setId(88L);
        BusinessException accessException = assertThrows(BusinessException.class,
                () -> service.requireTaskAccess(otherTask));
        assertEquals(ErrorCode.FORBIDDEN, accessException.getErrorCode());
    }

    @Test
    void ownerVehicleAndAlarmScopesDeriveFromOwnedCargoTasks() {
        when(currentUserService.getCurrentUser()).thenReturn(identity(UserRole.OWNER, null, 3L));
        Cargo cargo = new Cargo();
        cargo.setId(40L);
        TransportTask task = new TransportTask();
        task.setId(30L);
        task.setCargoId(40L);
        task.setVehicleId(20L);
        when(cargoMapper.selectList(any())).thenReturn(List.of(cargo));
        when(transportTaskMapper.selectList(any())).thenReturn(List.of(task));
        when(transportTaskMapper.selectBatchIds(any())).thenReturn(List.of(task));
        LambdaQueryWrapper<Vehicle> vehicleQuery = new LambdaQueryWrapper<>();
        LambdaQueryWrapper<Alarm> alarmQuery = new LambdaQueryWrapper<>();

        service.applyVehicleScope(vehicleQuery, null);
        service.applyAlarmScope(alarmQuery, 3L);

        vehicleQuery.getSqlSegment();
        alarmQuery.getSqlSegment();
        assertTrue(vehicleQuery.getParamNameValuePairs().containsValue(20L));
        assertTrue(alarmQuery.getParamNameValuePairs().containsValue(30L));
    }

    @Test
    void warehouseDispatcherAndAdminDoNotShareAnImplicitSuperRoleCondition() {
        for (UserRole role : List.of(UserRole.WAREHOUSE_MANAGER,
                UserRole.DISPATCHER, UserRole.ADMIN)) {
            when(currentUserService.getCurrentUser()).thenReturn(identity(role, null, null));
            LambdaQueryWrapper<Cargo> query = new LambdaQueryWrapper<>();
            service.applyCargoScope(query, null);
            assertTrue(query.isEmptyOfWhere());
        }
    }

    private UserIdentityResponse identity(UserRole role, Long driverId, Long ownerId) {
        return new UserIdentityResponse(1L, "user", "User", null,
                role, UserStatus.ACTIVE, driverId, ownerId);
    }

    private void init(Class<?> type, String namespace) {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), namespace), type);
    }
}
