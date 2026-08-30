package com.smart_logistics.backend.controller;

import com.smart_logistics.backend.common.PageResult;
import com.smart_logistics.backend.dto.response.WarehouseResponse;
import com.smart_logistics.backend.enums.WarehouseStatus;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.exception.GlobalExceptionHandler;
import com.smart_logistics.backend.service.WarehouseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertFalse;

@ExtendWith(MockitoExtension.class)
class WarehouseControllerTest {

    @Mock
    private WarehouseService warehouseService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new WarehouseController(warehouseService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listReturnsPageAndPassesKeyword() throws Exception {
        when(warehouseService.listWarehouses(2, 5, "WH-001"))
                .thenReturn(new PageResult<>(List.of(response()), 6, 2, 5));

        mockMvc.perform(get("/api/v1/warehouses")
                        .param("page", "2")
                        .param("pageSize", "5")
                        .param("keyword", "WH-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].warehouseNo").value("WH-001"))
                .andExpect(jsonPath("$.data.total").value(6));

        verify(warehouseService).listWarehouses(2, 5, "WH-001");
    }

    @Test
    void getByIdReturnsCoordinatesAndStatus() throws Exception {
        when(warehouseService.getWarehouse(1L)).thenReturn(response());

        mockMvc.perform(get("/api/v1/warehouses/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.longitude").value(106.735012))
                .andExpect(jsonPath("$.data.latitude").value(29.610634))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void getByIdReturnsUnifiedNotFound() throws Exception {
        when(warehouseService.getWarehouse(999L)).thenThrow(
                new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "warehouse not found"));

        mockMvc.perform(get("/api/v1/warehouses/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    void writeEndpointsDoNotExist() {
        for (java.lang.reflect.Method method : WarehouseController.class.getDeclaredMethods()) {
            assertFalse(method.isAnnotationPresent(PostMapping.class));
            assertFalse(method.isAnnotationPresent(PutMapping.class));
            assertFalse(method.isAnnotationPresent(DeleteMapping.class));
        }
    }

    private WarehouseResponse response() {
        return new WarehouseResponse(1L, "WH-001", "Central", "Chongqing",
                106.735012, 29.610634, "Manager", "13800000000",
                WarehouseStatus.ACTIVE,
                OffsetDateTime.parse("2026-08-30T10:00:00+08:00"),
                OffsetDateTime.parse("2026-08-30T10:00:00+08:00"));
    }
}
