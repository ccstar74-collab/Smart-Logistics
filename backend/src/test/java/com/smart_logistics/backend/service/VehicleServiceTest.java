package com.smart_logistics.backend.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import com.smart_logistics.backend.security.BusinessDataScopeService;
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
import java.util.Map;
import java.util.Set;

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

    @Mock
    private UserDisplayNameService userDisplayNameService;

    @Mock
    private TransportTaskAvailabilityService availabilityService;

    @Mock
    private BusinessDataScopeService dataScopeService;

    @Mock
    private DriverService driverService;

    private VehicleService vehicleService;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "vehicle-test"),
                Vehicle.class
        );
        org.mockito.Mockito.lenient().when(userDisplayNameService.getDriverNames(any()))
                .thenReturn(Map.of());
        vehicleService = new VehicleService(
                vehicleMapper, userDisplayNameService, availabilityService, dataScopeService,
                driverService);
    }

    @Test
    void getVehicleReturnsExistingVehicleWithOffsetTime() {
        Vehicle vehicle = vehicle(1L, "沪A10001", VehicleStatus.IDLE);
        vehicle.setSimCode("sim_008");
        vehicle.setCreatedAt(LocalDateTime.of(2026, 8, 22, 10, 30));
        when(vehicleMapper.selectById(1L)).thenReturn(vehicle);

        VehicleResponse response = vehicleService.getVehicle(1L);

        assertEquals(1L, response.getId());
        assertEquals("沪A10001", response.getPlateNumber());
        assertEquals("sim_008", response.getSimCode());
        assertEquals("+08:00", response.getCreatedAt().getOffset().toString());
    }

    @Test
    void getVehicleAllowsHistoricalNullSimCode() {
        Vehicle vehicle = vehicle(1L, "沪A10001", VehicleStatus.IDLE);
        when(vehicleMapper.selectById(1L)).thenReturn(vehicle);

        VehicleResponse response = vehicleService.getVehicle(1L);

        assertEquals(null, response.getSimCode());
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
    void updateStatusForTransportUsesExpectedStatusGuard() {
        when(vehicleMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        vehicleService.updateStatusForTransport(
                1L, VehicleStatus.IDLE, VehicleStatus.TRANSPORTING);

        ArgumentCaptor<LambdaUpdateWrapper<Vehicle>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(vehicleMapper).update(isNull(), captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("status"));
        assertTrue(captor.getValue().getSqlSet().contains("updated_at"));
    }

    @Test
    void updateStatusForTransportRejectsConcurrentStatusChange() {
        when(vehicleMapper.update(isNull(), any(Wrapper.class))).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> vehicleService.updateStatusForTransport(
                        1L, VehicleStatus.IDLE, VehicleStatus.TRANSPORTING));

        assertEquals(ErrorCode.STATE_CONFLICT, exception.getErrorCode());
    }

    @Test
    void createVehicleUsesOnlyAllowedFieldsAndDefaultsToIdle() {
        VehicleCreateRequest request = createRequest(" 沪A10002 ", new BigDecimal("12.50"));
        request.setSimCode("sim_008");
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
        assertEquals("sim_008", inserted.getSimCode());
        assertEquals(VehicleStatus.IDLE.name(), inserted.getStatus());
        assertNotNull(inserted.getCreatedAt());
        assertEquals(null, inserted.getLastLongitude());
        assertEquals(VehicleStatus.IDLE, response.getStatus());
        assertEquals("sim_008", response.getSimCode());
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
    void createVehicleRejectsDuplicateSimCode() {
        VehicleCreateRequest request = createRequest("沪A10002", BigDecimal.TEN);
        when(vehicleMapper.selectCount(any())).thenReturn(0L, 1L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> vehicleService.createVehicle(request)
        );

        assertEquals(ErrorCode.DATA_CONFLICT, exception.getErrorCode());
        assertEquals("simCode is already assigned to another vehicle",
                exception.getMessage());
        verify(vehicleMapper, never()).insert(any(Vehicle.class));
    }

    @Test
    void createVehicleConvertsDatabaseSimCodeDuplicateRace() {
        VehicleCreateRequest request = createRequest("沪A10002", BigDecimal.TEN);
        when(vehicleMapper.selectCount(any())).thenReturn(0L);
        when(vehicleMapper.insert(any(Vehicle.class))).thenThrow(
                new DuplicateKeyException(
                        "Duplicate entry 'sim_008' for key 'uk_vehicle_sim_code'"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> vehicleService.createVehicle(request)
        );

        assertEquals(ErrorCode.DATA_CONFLICT, exception.getErrorCode());
        assertEquals("simCode is already assigned to another vehicle",
                exception.getMessage());
    }

    @Test
    void createVehicleRejectsEveryNonCanonicalSimCode() {
        for (String simCode : List.of("sim_8", "sim_08", "sim_1000", "abc")) {
            VehicleCreateRequest request = createRequest("沪A10002", BigDecimal.TEN);
            request.setSimCode(simCode);

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> vehicleService.createVehicle(request));

            assertEquals(ErrorCode.INVALID_PARAMETER, exception.getErrorCode());
            assertEquals("simCode must match ^sim_\\d{3}$", exception.getMessage());
        }
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
    void updateVehicleBindsValidSimCode() {
        Vehicle existing = vehicle(1L, "沪A10001", VehicleStatus.IDLE);
        existing.setSimCode("sim_008");
        Vehicle updated = vehicle(1L, "沪A10001", VehicleStatus.IDLE);
        updated.setSimCode("sim_009");
        when(vehicleMapper.selectById(1L)).thenReturn(existing, updated);
        when(vehicleMapper.selectCount(any())).thenReturn(0L, 0L);
        when(vehicleMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        VehicleUpdateRequest request = updateRequest("沪A10001", BigDecimal.TEN);
        request.setSimCode("sim_009");

        VehicleResponse response = vehicleService.updateVehicle(1L, request);

        assertEquals("sim_009", response.getSimCode());
        ArgumentCaptor<LambdaUpdateWrapper<Vehicle>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(vehicleMapper).update(isNull(), captor.capture());
        assertTrue(captor.getValue().getSqlSet().contains("sim_code"));
    }

    @Test
    void updateVehicleRejectsSimCodeAssignedToAnotherVehicle() {
        Vehicle existing = vehicle(1L, "沪A10001", VehicleStatus.IDLE);
        existing.setSimCode("sim_008");
        when(vehicleMapper.selectById(1L)).thenReturn(existing);
        when(vehicleMapper.selectCount(any())).thenReturn(0L, 1L);
        VehicleUpdateRequest request = updateRequest("沪A10001", BigDecimal.TEN);
        request.setSimCode("sim_009");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> vehicleService.updateVehicle(1L, request));

        assertEquals(ErrorCode.DATA_CONFLICT, exception.getErrorCode());
        assertEquals("simCode is already assigned to another vehicle",
                exception.getMessage());
        verify(vehicleMapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void updateVehiclePreservesSimCodeWhenLegacyClientOmitsField() {
        Vehicle existing = vehicle(1L, "沪A10001", VehicleStatus.IDLE);
        existing.setSimCode("sim_008");
        when(vehicleMapper.selectById(1L)).thenReturn(existing, existing);
        when(vehicleMapper.selectCount(any())).thenReturn(0L);
        when(vehicleMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        VehicleUpdateRequest request = updateRequest("沪A10001", BigDecimal.TEN);

        VehicleResponse response = vehicleService.updateVehicle(1L, request);

        assertEquals("sim_008", response.getSimCode());
    }

    @Test
    void availableSimCodesExcludeAssignmentsAndSupportAllKeywordForms() {
        Vehicle assigned = vehicle(1L, "沪A10001", VehicleStatus.IDLE);
        assigned.setSimCode("sim_108");
        when(vehicleMapper.selectList(any())).thenReturn(List.of(assigned));

        List<String> byShortDigit = vehicleService.listAvailableSimCodes("8");
        List<String> byPaddedDigits = vehicleService.listAvailableSimCodes("008");
        List<String> byFullCode = vehicleService.listAvailableSimCodes("sim_008");

        assertTrue(byShortDigit.contains("sim_008"));
        assertEquals(List.of("sim_008"), byPaddedDigits);
        assertEquals(List.of("sim_008"), byFullCode);
        assertTrue(!byShortDigit.contains("sim_108"));
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
        vehicle.setSimCode("sim_008");
        vehicle.setDriverId(3L);
        when(userDisplayNameService.getDriverNames(any()))
                .thenReturn(Map.of(3L, "Driver Name"));
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
        assertEquals("Driver Name", result.getRecords().getFirst().getDriverName());
        assertEquals("sim_008", result.getRecords().getFirst().getSimCode());

        ArgumentCaptor<LambdaQueryWrapper<Vehicle>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(vehicleMapper).selectPage(any(Page.class), wrapperCaptor.capture());
        assertTrue(wrapperCaptor.getValue().getSqlSegment().contains("status"));
        assertTrue(wrapperCaptor.getValue().getParamNameValuePairs().containsValue("IDLE"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listVehiclesAllowsHistoricalNullSimCode() {
        Vehicle historical = vehicle(1L, "沪A10001", VehicleStatus.IDLE);
        when(vehicleMapper.selectPage(any(Page.class), any(Wrapper.class)))
                .thenAnswer(invocation -> {
                    Page<Vehicle> page = invocation.getArgument(0);
                    page.setRecords(List.of(historical));
                    page.setTotal(1);
                    return page;
                });

        PageResult<VehicleResponse> result = vehicleService.listVehicles(
                1, 10, null, null);

        assertEquals(null, result.getRecords().getFirst().getSimCode());
    }

    @Test
    void getVehicleEnrichesDriverNameThroughRelationshipLookup() {
        Vehicle vehicle = vehicle(1L, "沪A10001", VehicleStatus.IDLE);
        vehicle.setDriverId(37L);
        when(vehicleMapper.selectById(1L)).thenReturn(vehicle);
        when(userDisplayNameService.getDriverNames(List.of(37L)))
                .thenReturn(Map.of(37L, "Current Driver"));
        assertEquals("Current Driver", vehicleService.getVehicle(1L).getDriverName());
    }

    @Test
    void availableReturnsIdleVehiclesWithoutActiveTaskAndIncludesDriverName() {
        Vehicle available = vehicle(1L, "沪A10001", VehicleStatus.IDLE);
        available.setDriverId(3L);
        Vehicle occupied = vehicle(2L, "沪A10002", VehicleStatus.IDLE);
        Vehicle historicalOnly = vehicle(3L, "沪A10003", VehicleStatus.IDLE);
        when(vehicleMapper.selectList(any())).thenReturn(
                List.of(available, occupied, historicalOnly));
        when(availabilityService.findActiveVehicleIds(List.of(1L, 2L, 3L)))
                .thenReturn(Set.of(2L));
        when(userDisplayNameService.getDriverNames(any()))
                .thenReturn(Map.of(3L, "Driver Name"));

        List<VehicleResponse> result = vehicleService.listAvailableVehicles();

        assertEquals(List.of(1L, 3L), result.stream().map(VehicleResponse::getId).toList());
        assertEquals("Driver Name", result.getFirst().getDriverName());
        ArgumentCaptor<LambdaQueryWrapper<Vehicle>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(vehicleMapper).selectList(captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("status"));
        assertTrue(captor.getValue().getParamNameValuePairs()
                .containsValue(VehicleStatus.IDLE.name()));
    }

    @Test
    void updateDriverBindingAcceptsActiveDriverAndSupportsUnbind() {
        Vehicle vehicle = vehicle(1L, "沪A10001", VehicleStatus.IDLE);
        vehicle.setDriverId(3L);
        Vehicle unbound = vehicle(1L, "沪A10001", VehicleStatus.IDLE);
        when(vehicleMapper.selectById(1L)).thenReturn(vehicle, unbound);
        when(vehicleMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        VehicleResponse response = vehicleService.updateDriverBinding(1L, null);

        assertEquals(null, response.getDriverId());
        verify(vehicleMapper).update(isNull(), any(Wrapper.class));
    }

    @Test
    void updateDriverBindingRejectsDriverChangeWhileTransporting() {
        Vehicle vehicle = vehicle(1L, "沪A10001", VehicleStatus.TRANSPORTING);
        vehicle.setDriverId(3L);
        when(vehicleMapper.selectById(1L)).thenReturn(vehicle);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> vehicleService.updateDriverBinding(1L, 4L));

        assertEquals(ErrorCode.STATE_CONFLICT, exception.getErrorCode());
        verify(driverService, never()).requireActiveDriver(4L);
        verify(vehicleMapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void listVehicleDriverFilterIsComposedWithSecurityScope() {
        when(vehicleMapper.selectPage(any(Page.class), any(Wrapper.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        vehicleService.listVehicles(1, 10, null, null, 9L);

        verify(dataScopeService).applyVehicleScope(any(), org.mockito.ArgumentMatchers.eq(9L));
        ArgumentCaptor<LambdaQueryWrapper<Vehicle>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(vehicleMapper).selectPage(any(Page.class), captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("driver_id"));
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue(9L));
    }

    @Test
    void transportSimCodeRequiresExactFormalFormat() {
        Vehicle vehicle = vehicle(1L, "沪A10001", VehicleStatus.IDLE);
        vehicle.setSimCode("sim_008");

        assertEquals("sim_008", vehicleService.requireTransportSimCode(vehicle));

        vehicle.setSimCode(" sim_008 ");
        BusinessException invalid = assertThrows(BusinessException.class,
                () -> vehicleService.requireTransportSimCode(vehicle));
        assertEquals(ErrorCode.STATE_CONFLICT, invalid.getErrorCode());
        assertEquals("vehicle simCode must match ^sim_\\d{3}$", invalid.getMessage());
    }

    @Test
    void transportSimCodeRejectsMissingBinding() {
        Vehicle vehicle = vehicle(1L, "沪A10001", VehicleStatus.IDLE);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> vehicleService.requireTransportSimCode(vehicle));

        assertEquals(ErrorCode.STATE_CONFLICT, exception.getErrorCode());
        assertEquals("vehicle has no simCode", exception.getMessage());
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
        request.setSimCode("sim_008");
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
