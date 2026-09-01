package com.smart_logistics.backend.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart_logistics.backend.common.PageResult;
import com.smart_logistics.backend.dto.request.CargoTypeCreateRequest;
import com.smart_logistics.backend.dto.response.CargoTypeResponse;
import com.smart_logistics.backend.entity.CargoType;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.mapper.CargoTypeMapper;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CargoTypeServiceTest {

    @Mock
    private CargoTypeMapper cargoTypeMapper;

    private CargoTypeService cargoTypeService;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "cargo-type-test"),
                CargoType.class);
        cargoTypeService = new CargoTypeService(cargoTypeMapper);
    }

    @Test
    void createTrimsNameAndOptionalTextAndReturnsAllFields() {
        CargoTypeCreateRequest request = request(" Medical ");
        request.setUnit(" box ");
        request.setUnitWeight(new BigDecimal("12.50"));
        request.setUnitVolume(new BigDecimal("3.20"));
        request.setDescription(" Fragile ");
        CargoType[] inserted = new CargoType[1];
        when(cargoTypeMapper.selectCount(any())).thenReturn(0L);
        when(cargoTypeMapper.insert(any(CargoType.class))).thenAnswer(invocation -> {
            inserted[0] = invocation.getArgument(0);
            inserted[0].setId(10L);
            return 1;
        });
        when(cargoTypeMapper.selectById(10L)).thenAnswer(invocation -> inserted[0]);

        CargoTypeResponse response = cargoTypeService.createCargoType(request);

        assertEquals(10L, response.getId());
        assertEquals("Medical", response.getName());
        assertEquals("box", response.getUnit());
        assertEquals(new BigDecimal("12.50"), response.getUnitWeight());
        assertEquals(new BigDecimal("3.20"), response.getUnitVolume());
        assertEquals("Fragile", response.getDescription());
        assertNotNull(response.getCreatedAt());
        assertNotNull(response.getUpdatedAt());
    }

    @Test
    void createNormalizesBlankOptionalTextToNull() {
        CargoTypeCreateRequest request = request("Medical");
        request.setUnit(" ");
        request.setDescription(" ");
        CargoType[] inserted = new CargoType[1];
        when(cargoTypeMapper.selectCount(any())).thenReturn(0L);
        when(cargoTypeMapper.insert(any(CargoType.class))).thenAnswer(invocation -> {
            inserted[0] = invocation.getArgument(0);
            inserted[0].setId(11L);
            return 1;
        });
        when(cargoTypeMapper.selectById(11L)).thenAnswer(invocation -> inserted[0]);

        CargoTypeResponse response = cargoTypeService.createCargoType(request);

        assertNull(response.getUnit());
        assertNull(response.getDescription());
    }

    @Test
    void createRejectsBlankTrimmedNameAtServiceBoundary() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> cargoTypeService.createCargoType(request("   ")));

        assertEquals(ErrorCode.INVALID_PARAMETER, exception.getErrorCode());
        verify(cargoTypeMapper, never()).insert(any(CargoType.class));
    }

    @Test
    void createRejectsExistingNameAsDataConflict() {
        when(cargoTypeMapper.selectCount(any())).thenReturn(1L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> cargoTypeService.createCargoType(request("Medical")));

        assertEquals(ErrorCode.DATA_CONFLICT, exception.getErrorCode());
        assertEquals("cargo type name already exists", exception.getMessage());
        verify(cargoTypeMapper, never()).insert(any(CargoType.class));
    }

    @Test
    void createConvertsDuplicateKeyRaceToBusinessConflict() {
        when(cargoTypeMapper.selectCount(any())).thenReturn(0L);
        when(cargoTypeMapper.insert(any(CargoType.class)))
                .thenThrow(new DuplicateKeyException("uk_cargo_type_name"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> cargoTypeService.createCargoType(request("Medical")));

        assertEquals(ErrorCode.DATA_CONFLICT, exception.getErrorCode());
        assertTrue(exception.getCause() instanceof DuplicateKeyException);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listUsesFixedIdDescendingOrderAndKeywordPagination() {
        CargoType cargoType = cargoType(12L, "Medical supplies");
        when(cargoTypeMapper.selectPage(any(Page.class), any(Wrapper.class)))
                .thenAnswer(invocation -> {
                    Page<CargoType> page = invocation.getArgument(0);
                    page.setRecords(List.of(cargoType));
                    page.setTotal(6);
                    return page;
                });

        PageResult<CargoTypeResponse> result = cargoTypeService.listCargoTypes(
                2, 5, " Medical ");

        assertEquals(6, result.getTotal());
        assertEquals(2, result.getPage());
        assertEquals(5, result.getPageSize());
        assertEquals("Medical supplies", result.getRecords().getFirst().getName());
        ArgumentCaptor<LambdaQueryWrapper<CargoType>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(cargoTypeMapper).selectPage(any(Page.class), captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("name"));
        assertTrue(captor.getValue().getSqlSegment().contains("ORDER BY id DESC"));
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue("%Medical%"));
    }

    private CargoTypeCreateRequest request(String name) {
        CargoTypeCreateRequest request = new CargoTypeCreateRequest();
        request.setName(name);
        return request;
    }

    private CargoType cargoType(Long id, String name) {
        CargoType cargoType = new CargoType();
        cargoType.setId(id);
        cargoType.setName(name);
        cargoType.setUnit("box");
        cargoType.setUnitWeight(new BigDecimal("12.50"));
        cargoType.setUnitVolume(new BigDecimal("3.20"));
        cargoType.setDescription("Fragile");
        cargoType.setCreatedAt(LocalDateTime.of(2026, 8, 30, 10, 0));
        cargoType.setUpdatedAt(LocalDateTime.of(2026, 8, 30, 10, 0));
        return cargoType;
    }
}
