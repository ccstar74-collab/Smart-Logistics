package com.smart_logistics.backend.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart_logistics.backend.common.PageResult;
import com.smart_logistics.backend.dto.request.VehicleCreateRequest;
import com.smart_logistics.backend.dto.request.VehicleUpdateRequest;
import com.smart_logistics.backend.dto.response.VehicleResponse;
import com.smart_logistics.backend.entity.Vehicle;
import com.smart_logistics.backend.enums.VehicleStatus;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.mapper.VehicleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {

    @Mock
    private VehicleMapper vehicleMapper;

    private VehicleService vehicleService;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "vehicle-test"),
                Vehicle.class
        );
        vehicleService = new VehicleService(vehicleMapper);
    }

    @Test
    void getVehicleReturnsExistingVehicleWithOffsetTime() {
        Vehicle vehicle = vehicle(1L, "沪A10001", VehicleStatus.IDLE);
        vehicle.setCreatedAt(LocalDateTime.of(2026, 8, 22, 10, 30));
        when(vehicleMapper.selectById(1L)).thenReturn(vehicle);

        VehicleResponse response = vehicleService.getVehicle(1L);

        assertEquals(1L, response.getId());
        assertEquals("沪A10001", response.getPlateNumber());
        assertEquals("+08:00", response.getCreatedAt().getOffset().toString());
    }

    @Test
    void getVehicleThrowsNotFoundForMissingVehicle() {
        when(vehicleMapper.selectById(99999L)).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> vehicleService.getVehicle(99999L)
        );

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
        assertEquals("vehicle not found", exception.getMessage());
    }

    @Test
    void createVehicleUsesOnlyAllowedFieldsAndDefaultsToIdle() {
        VehicleCreateRequest request = createRequest(" 沪A10002 ", new BigDecimal("12.50"));
        Vehicle[] insertedHolder = new Vehicle[1];
        when(vehicleMapper.selectCount(any())).thenReturn(0L);
        when(vehicleMapper.insert(any(Vehicle.class))).thenAnswer(invocation -> {
            Vehicle inserted = invocation.getArgument(0);
            inserted.setId(2L);
            insertedHolder[0] = inserted;
            return 1;
        });
        when(vehicleMapper.selectById(2L)).thenAnswer(invocation -> insertedHolder[0]);

        VehicleResponse response = vehicleService.createVehicle(request);

        ArgumentCaptor<Vehicle> captor = ArgumentCaptor.forClass(Vehicle.class);
        verify(vehicleMapper).insert(captor.capture());
        Vehicle inserted = captor.getValue();
        assertEquals("沪A10002", inserted.getPlateNumber());
        assertEquals(VehicleStatus.IDLE.name(), inserted.getStatus());
        assertNotNull(inserted.getCreatedAt());
        assertEquals(null, inserted.getLastLongitude());
        assertEquals(VehicleStatus.IDLE, response.getStatus());
    }

    @Test
    void createVehicleRejectsDuplicatePlateNumber() {
        VehicleCreateRequest request = createRequest("沪A10001", BigDecimal.TEN);
        when(vehicleMapper.selectCount(any())).thenReturn(1L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> vehicleService.createVehicle(request)
        );

        assertEquals(ErrorCode.DATA_CONFLICT, exception.getErrorCode());
        assertEquals("plate number already exists", exception.getMessage());
        verify(vehicleMapper, never()).insert(any(Vehicle.class));
    }

    @Test
    void createVehicleConvertsDatabaseDuplicateKeyRace() {
        VehicleCreateRequest request = createRequest("沪A10001", BigDecimal.TEN);
        when(vehicleMapper.selectCount(any())).thenReturn(0L);
        when(vehicleMapper.insert(any(Vehicle.class)))
                .thenThrow(new DuplicateKeyException("duplicate plate"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> vehicleService.createVehicle(request)
        );

        assertEquals(ErrorCode.DATA_CONFLICT, exception.getErrorCode());
        assertEquals("plate number already exists", exception.getMessage());
    }

    @Test
    void updateVehicleReturnsUpdatedBusinessData() {
        Vehicle existing = vehicle(1L, "沪A10001", VehicleStatus.IDLE);
        Vehicle updated = vehicle(1L, "沪A20001", VehicleStatus.IDLE);
        updated.setCapacity(new BigDecimal("20"));
        when(vehicleMapper.selectById(1L)).thenReturn(existing, updated);
        when(vehicleMapper.selectCount(any())).thenReturn(0L);
        when(vehicleMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        VehicleUpdateRequest request = updateRequest("沪A20001", new BigDecimal("20"));

        VehicleResponse response = vehicleService.updateVehicle(1L, request);

        assertEquals("沪A20001", response.getPlateNumber());
        assertEquals(new BigDecimal("20"), response.getCapacity());
        verify(vehicleMapper).update(isNull(), any(Wrapper.class));
    }

    @Test
    void disableVehiclePerformsSoftDisable() {
        when(vehicleMapper.selectById(1L)).thenReturn(vehicle(1L, "沪A10001", VehicleStatus.IDLE));
        when(vehicleMapper.updateById(any(Vehicle.class))).thenReturn(1);

        vehicleService.disableVehicle(1L);

        ArgumentCaptor<Vehicle> captor = ArgumentCaptor.forClass(Vehicle.class);
        verify(vehicleMapper).updateById(captor.capture());
        assertEquals(1L, captor.getValue().getId());
        assertEquals(VehicleStatus.DISABLED.name(), captor.getValue().getStatus());
        assertEquals(null, captor.getValue().getLastLongitude());
    }

    @Test
    void disableVehicleRejectsTransportingVehicle() {
        when(vehicleMapper.selectById(1L))
                .thenReturn(vehicle(1L, "沪A10001", VehicleStatus.TRANSPORTING));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> vehicleService.disableVehicle(1L)
        );

        assertEquals(ErrorCode.STATE_CONFLICT, exception.getErrorCode());
        verify(vehicleMapper, never()).updateById(any(Vehicle.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listVehiclesReturnsPageAndAppliesStatusFilter() {
        Vehicle vehicle = vehicle(1L, "沪A10001", VehicleStatus.IDLE);
        when(vehicleMapper.selectPage(any(Page.class), any(Wrapper.class)))
                .thenAnswer(invocation -> {
                    Page<Vehicle> page = invocation.getArgument(0);
                    page.setRecords(List.of(vehicle));
                    page.setTotal(1);
                    return page;
                });

        PageResult<VehicleResponse> result = vehicleService.listVehicles(
                1, 10, "沪A", VehicleStatus.IDLE
        );

        assertEquals(1, result.getRecords().size());
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getPage());
        assertEquals(10, result.getPageSize());

        ArgumentCaptor<LambdaQueryWrapper<Vehicle>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(vehicleMapper).selectPage(any(Page.class), wrapperCaptor.capture());
        assertTrue(wrapperCaptor.getValue().getSqlSegment().contains("status"));
        assertTrue(wrapperCaptor.getValue().getParamNameValuePairs().containsValue("IDLE"));
    }

    private Vehicle vehicle(Long id, String plateNumber, VehicleStatus status) {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(id);
        vehicle.setPlateNumber(plateNumber);
        vehicle.setType("VAN");
        vehicle.setCapacity(BigDecimal.TEN);
        vehicle.setStatus(status.name());
        return vehicle;
    }

    private VehicleCreateRequest createRequest(String plateNumber, BigDecimal capacity) {
        VehicleCreateRequest request = new VehicleCreateRequest();
        request.setPlateNumber(plateNumber);
        request.setType("VAN");
        request.setCapacity(capacity);
        return request;
    }

    private VehicleUpdateRequest updateRequest(String plateNumber, BigDecimal capacity) {
        VehicleUpdateRequest request = new VehicleUpdateRequest();
        request.setPlateNumber(plateNumber);
        request.setType("TRUCK");
        request.setCapacity(capacity);
        return request;
    }
}
