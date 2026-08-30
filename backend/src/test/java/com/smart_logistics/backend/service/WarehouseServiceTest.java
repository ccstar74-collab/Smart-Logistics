package com.smart_logistics.backend.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart_logistics.backend.common.PageResult;
import com.smart_logistics.backend.dto.response.WarehouseResponse;
import com.smart_logistics.backend.entity.Warehouse;
import com.smart_logistics.backend.enums.WarehouseStatus;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.mapper.WarehouseMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseServiceTest {

    @Mock
    private WarehouseMapper warehouseMapper;

    private WarehouseService warehouseService;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "warehouse-test"),
                Warehouse.class);
        warehouseService = new WarehouseService(warehouseMapper);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listPaginatesAndMatchesWarehouseNumberOrNameWithoutHidingInactive() {
        Warehouse inactive = warehouse(2L, "WH-002", "Secondary",
                WarehouseStatus.INACTIVE);
        when(warehouseMapper.selectPage(any(Page.class), any(Wrapper.class)))
                .thenAnswer(invocation -> {
                    Page<Warehouse> page = invocation.getArgument(0);
                    page.setRecords(List.of(inactive));
                    page.setTotal(1);
                    return page;
                });

        PageResult<WarehouseResponse> result = warehouseService.listWarehouses(
                1, 10, " WH-002 ");

        assertEquals(WarehouseStatus.INACTIVE,
                result.getRecords().getFirst().getStatus());
        ArgumentCaptor<LambdaQueryWrapper<Warehouse>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(warehouseMapper).selectPage(any(Page.class), captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.contains("warehouse_no"));
        assertTrue(sql.contains("name"));
        assertTrue(sql.contains("ORDER BY id DESC"));
        assertTrue(!sql.contains("status"));
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue("%WH-002%"));
    }

    @Test
    void getByIdReturnsCoordinatesStatusAndTimestamps() {
        when(warehouseMapper.selectById(1L)).thenReturn(
                warehouse(1L, "WH-001", "Central", WarehouseStatus.ACTIVE));

        WarehouseResponse response = warehouseService.getWarehouse(1L);

        assertEquals(106.735012, response.getLongitude());
        assertEquals(29.610634, response.getLatitude());
        assertEquals(WarehouseStatus.ACTIVE, response.getStatus());
        assertEquals("+08:00", response.getCreatedAt().getOffset().toString());
    }

    @Test
    void getByIdRejectsMissingWarehouse() {
        when(warehouseMapper.selectById(999L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> warehouseService.getWarehouse(999L));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void requireActiveWarehouseRejectsInactiveWarehouse() {
        when(warehouseMapper.selectById(2L)).thenReturn(
                warehouse(2L, "WH-002", "Secondary", WarehouseStatus.INACTIVE));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> warehouseService.requireActiveWarehouse(2L));

        assertEquals(ErrorCode.STATE_CONFLICT, exception.getErrorCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void batchCandidateLookupAppliesIdsAndActiveStatusInOneQuery() {
        Warehouse active = warehouse(1L, "WH-001", "Central", WarehouseStatus.ACTIVE);
        when(warehouseMapper.selectList(any(Wrapper.class))).thenReturn(List.of(active));

        List<Warehouse> result = warehouseService.listActiveWarehousesByIds(
                List.of(1L, 2L, 1L));

        assertEquals(List.of(active), result);
        ArgumentCaptor<LambdaQueryWrapper<Warehouse>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(warehouseMapper).selectList(captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.contains("id IN"));
        assertTrue(sql.contains("status"));
        assertTrue(sql.contains("ORDER BY id ASC"));
        assertTrue(captor.getValue().getParamNameValuePairs()
                .containsValue(WarehouseStatus.ACTIVE.name()));
    }

    private Warehouse warehouse(Long id, String warehouseNo, String name,
                                WarehouseStatus status) {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(id);
        warehouse.setWarehouseNo(warehouseNo);
        warehouse.setName(name);
        warehouse.setAddress("Chongqing");
        warehouse.setLongitude(106.735012);
        warehouse.setLatitude(29.610634);
        warehouse.setContactName("Manager");
        warehouse.setContactPhone("13800000000");
        warehouse.setStatus(status.name());
        warehouse.setCreatedAt(LocalDateTime.of(2026, 8, 30, 10, 0));
        warehouse.setUpdatedAt(LocalDateTime.of(2026, 8, 30, 10, 0));
        return warehouse;
    }
}
