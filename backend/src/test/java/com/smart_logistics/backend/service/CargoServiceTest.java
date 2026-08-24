package com.smart_logistics.backend.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart_logistics.backend.common.PageResult;
import com.smart_logistics.backend.dto.request.CargoCreateRequest;
import com.smart_logistics.backend.dto.response.CargoResponse;
import com.smart_logistics.backend.entity.Cargo;
import com.smart_logistics.backend.enums.CargoStatus;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.mapper.CargoMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class CargoServiceTest {

    @Mock
    private CargoMapper cargoMapper;

    @Mock
    private UserDisplayNameService userDisplayNameService;

    @Mock
    private TransportTaskAvailabilityService availabilityService;

    private CargoService cargoService;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "cargo-test"),
                Cargo.class
        );
        org.mockito.Mockito.lenient().when(userDisplayNameService.getOwnerNames(any()))
                .thenReturn(Map.of());
        cargoService = new CargoService(
                cargoMapper, userDisplayNameService, availabilityService);
    }

    @Test
    void getCargoReturnsExistingCargoWithOffsetTime() {
        Cargo cargo = cargo(1L, "CGO-001", "Medical supplies", CargoStatus.WAITING);
        cargo.setCreatedAt(LocalDateTime.of(2026, 8, 23, 10, 30));
        when(cargoMapper.selectById(1L)).thenReturn(cargo);

        CargoResponse response = cargoService.getCargo(1L);

        assertEquals(1L, response.getId());
        assertEquals("CGO-001", response.getCargoNo());
        assertEquals("Medical supplies", response.getName());
        assertEquals("+08:00", response.getCreatedAt().getOffset().toString());
    }

    @Test
    void getCargoThrowsNotFoundForMissingCargo() {
        when(cargoMapper.selectById(99999L)).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> cargoService.getCargo(99999L)
        );

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
        assertEquals("cargo not found", exception.getMessage());
    }

    @Test
    void updateStatusForTransportUsesExpectedStatusGuard() {
        when(cargoMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        cargoService.updateStatusForTransport(
                1L, CargoStatus.WAITING, CargoStatus.TRANSPORTING);

        ArgumentCaptor<LambdaUpdateWrapper<Cargo>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(cargoMapper).update(isNull(), captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("status"));
        assertTrue(captor.getValue().getSqlSet().contains("updated_at"));
    }

    @Test
    void updateStatusForTransportRejectsConcurrentStatusChange() {
        when(cargoMapper.update(isNull(), any(Wrapper.class))).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> cargoService.updateStatusForTransport(
                        1L, CargoStatus.WAITING, CargoStatus.TRANSPORTING));

        assertEquals(ErrorCode.STATE_CONFLICT, exception.getErrorCode());
    }

    @Test
    void createCargoUsesOnlyAllowedFieldsAndDefaultsToWaiting() {
        CargoCreateRequest request = createRequest(" CGO-002 ", " Medical supplies ");
        request.setDescription(" Fragile ");
        Cargo[] insertedHolder = new Cargo[1];
        when(cargoMapper.selectCount(any())).thenReturn(0L);
        when(cargoMapper.insert(any(Cargo.class))).thenAnswer(invocation -> {
            Cargo inserted = invocation.getArgument(0);
            inserted.setId(2L);
            insertedHolder[0] = inserted;
            return 1;
        });
        when(cargoMapper.selectById(2L)).thenAnswer(invocation -> insertedHolder[0]);

        CargoResponse response = cargoService.createCargo(request);

        ArgumentCaptor<Cargo> captor = ArgumentCaptor.forClass(Cargo.class);
        verify(cargoMapper).insert(captor.capture());
        Cargo inserted = captor.getValue();
        assertEquals("CGO-002", inserted.getCargoNo());
        assertEquals("Medical supplies", inserted.getName());
        assertEquals("Fragile", inserted.getDescription());
        assertEquals(CargoStatus.WAITING.name(), inserted.getStatus());
        assertNotNull(inserted.getCreatedAt());
        assertEquals(CargoStatus.WAITING, response.getStatus());
    }

    @Test
    void createCargoRejectsDuplicateCargoNumber() {
        CargoCreateRequest request = createRequest("CGO-001", "Medical supplies");
        when(cargoMapper.selectCount(any())).thenReturn(1L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> cargoService.createCargo(request)
        );

        assertEquals(ErrorCode.DATA_CONFLICT, exception.getErrorCode());
        assertEquals("cargo number already exists", exception.getMessage());
        verify(cargoMapper, never()).insert(any(Cargo.class));
    }

    @Test
    void createCargoConvertsDatabaseDuplicateKeyRace() {
        CargoCreateRequest request = createRequest("CGO-001", "Medical supplies");
        when(cargoMapper.selectCount(any())).thenReturn(0L);
        when(cargoMapper.insert(any(Cargo.class)))
                .thenThrow(new DuplicateKeyException("duplicate cargo number"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> cargoService.createCargo(request)
        );

        assertEquals(ErrorCode.DATA_CONFLICT, exception.getErrorCode());
        assertEquals("cargo number already exists", exception.getMessage());
    }

    @Test
    @SuppressWarnings("unchecked")
    void listCargosReturnsPageAndAppliesKeywordAndStatusFilters() {
        Cargo cargo = cargo(1L, "CGO-001", "Medical supplies", CargoStatus.WAITING);
        when(userDisplayNameService.getOwnerNames(any()))
                .thenReturn(Map.of(100L, "Owner Name"));
        when(cargoMapper.selectPage(any(Page.class), any(Wrapper.class)))
                .thenAnswer(invocation -> {
                    Page<Cargo> page = invocation.getArgument(0);
                    page.setRecords(List.of(cargo));
                    page.setTotal(1);
                    return page;
                });

        PageResult<CargoResponse> result = cargoService.listCargos(
                2, 5, " Medical ", CargoStatus.WAITING
        );

        assertEquals(1, result.getRecords().size());
        assertEquals(1, result.getTotal());
        assertEquals(2, result.getPage());
        assertEquals(5, result.getPageSize());
        assertEquals("Owner Name", result.getRecords().getFirst().getOwnerName());

        ArgumentCaptor<LambdaQueryWrapper<Cargo>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(cargoMapper).selectPage(any(Page.class), wrapperCaptor.capture());
        LambdaQueryWrapper<Cargo> query = wrapperCaptor.getValue();
        assertTrue(query.getSqlSegment().contains("cargo_no"));
        assertTrue(query.getSqlSegment().contains("name"));
        assertTrue(query.getSqlSegment().contains("status"));
        assertTrue(query.getParamNameValuePairs().containsValue("%Medical%"));
        assertTrue(query.getParamNameValuePairs().containsValue(CargoStatus.WAITING.name()));
    }

    @Test
    void getCargoEnrichesOwnerNameThroughRelationshipLookup() {
        Cargo cargo = cargo(1L, "CGO-001", "Medical supplies", CargoStatus.WAITING);
        when(cargoMapper.selectById(1L)).thenReturn(cargo);
        when(userDisplayNameService.getOwnerNames(List.of(100L)))
                .thenReturn(Map.of(100L, "Current Owner"));
        assertEquals("Current Owner", cargoService.getCargo(1L).getOwnerName());
    }

    @Test
    void availableReturnsWaitingCargosWithoutActiveTaskAndIncludesOwnerName() {
        Cargo available = cargo(1L, "CGO-001", "One", CargoStatus.WAITING);
        Cargo occupied = cargo(2L, "CGO-002", "Two", CargoStatus.WAITING);
        Cargo historicalOnly = cargo(3L, "CGO-003", "Three", CargoStatus.WAITING);
        when(cargoMapper.selectList(any())).thenReturn(
                List.of(available, occupied, historicalOnly));
        when(availabilityService.findActiveCargoIds(List.of(1L, 2L, 3L)))
                .thenReturn(Set.of(2L));
        when(userDisplayNameService.getOwnerNames(any()))
                .thenReturn(Map.of(100L, "Owner Name"));

        List<CargoResponse> result = cargoService.listAvailableCargos();

        assertEquals(List.of(1L, 3L), result.stream().map(CargoResponse::getId).toList());
        assertEquals("Owner Name", result.getFirst().getOwnerName());
        ArgumentCaptor<LambdaQueryWrapper<Cargo>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(cargoMapper).selectList(captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("status"));
        assertTrue(captor.getValue().getParamNameValuePairs()
                .containsValue(CargoStatus.WAITING.name()));
    }

    private Cargo cargo(Long id, String cargoNo, String name, CargoStatus status) {
        Cargo cargo = new Cargo();
        cargo.setId(id);
        cargo.setCargoNo(cargoNo);
        cargo.setName(name);
        cargo.setDescription("Fragile");
        cargo.setWeight(new BigDecimal("12.50"));
        cargo.setVolume(new BigDecimal("3.20"));
        cargo.setOwnerId(100L);
        cargo.setStatus(status.name());
        return cargo;
    }

    private CargoCreateRequest createRequest(String cargoNo, String name) {
        CargoCreateRequest request = new CargoCreateRequest();
        request.setCargoNo(cargoNo);
        request.setName(name);
        request.setWeight(new BigDecimal("12.50"));
        request.setVolume(new BigDecimal("3.20"));
        request.setOwnerId(100L);
        return request;
    }
}
