package com.smart_logistics.backend.controller;

import com.smart_logistics.backend.common.PageResult;
import com.smart_logistics.backend.dto.response.VehicleResponse;
import com.smart_logistics.backend.dto.response.VehicleLocationResponse;
import com.smart_logistics.backend.enums.VehicleStatus;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.exception.GlobalExceptionHandler;
import com.smart_logistics.backend.service.VehicleService;
import com.smart_logistics.backend.service.VehicleLocationQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class VehicleControllerTest {

    @Mock
    private VehicleService vehicleService;
    @Mock
    private VehicleLocationQueryService vehicleLocationQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new VehicleController(vehicleService, vehicleLocationQueryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void createRejectsBlankPlateNumber() throws Exception {
        mockMvc.perform(post("/api/v1/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plateNumber":" ","type":"VAN","capacity":10}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void createRejectsNegativeCapacity() throws Exception {
        mockMvc.perform(post("/api/v1/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plateNumber":"沪A10003","type":"VAN","capacity":-1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value("capacity must be greater than or equal to 0"));
    }

    @Test
    void createRejectsPlateNumberLongerThanDatabaseColumn() throws Exception {
        mockMvc.perform(post("/api/v1/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plateNumber":"123456789012345678901","capacity":10}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message")
                        .value("plateNumber must not exceed 20 characters"));
    }

    @Test
    void getMissingVehicleReturnsUnifiedNotFoundResponse() throws Exception {
        when(vehicleService.getVehicle(99999L)).thenThrow(
                new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "vehicle not found")
        );

        mockMvc.perform(get("/api/v1/vehicles/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401))
                .andExpect(jsonPath("$.message").value("vehicle not found"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void listReturnsStandardPageStructureAndPassesStatusFilter() throws Exception {
        VehicleResponse vehicle = response();
        when(vehicleService.listVehicles(2, 5, "沪A", VehicleStatus.IDLE, null))
                .thenReturn(new PageResult<>(List.of(vehicle), 6, 2, 5));

        mockMvc.perform(get("/api/v1/vehicles")
                        .param("page", "2")
                        .param("pageSize", "5")
                        .param("keyword", "沪A")
                        .param("status", "IDLE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.records[0].plateNumber").value("沪A10001"))
                .andExpect(jsonPath("$.data.records[0].createdAt")
                        .value("2026-08-22T10:30:00+08:00"))
                .andExpect(jsonPath("$.data.total").value(6))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.pageSize").value(5));

        verify(vehicleService).listVehicles(2, 5, "沪A", VehicleStatus.IDLE, null);
    }

    @Test
    void availableEndpointReturnsEnrichedVehicles() throws Exception {
        when(vehicleService.listAvailableVehicles()).thenReturn(List.of(response()));
        mockMvc.perform(get("/api/v1/vehicles/available"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].driverName").value("Driver Name"));
    }

    @Test
    void latestLocationUsesOfficialContractAndCamelCaseFields() throws Exception {
        when(vehicleLocationQueryService.getLatestLocation(1L)).thenReturn(
                new VehicleLocationResponse(1L, "沪A10001", 121.5, 31.2,
                        40.0, 90.0, OffsetDateTime.parse("2026-08-25T10:00:00+08:00"),
                        true, 10L));

        mockMvc.perform(get("/api/v1/vehicles/1/location/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.vehicleId").value(1))
                .andExpect(jsonPath("$.data.longitude").value(121.5))
                .andExpect(jsonPath("$.data.direction").value(90.0))
                .andExpect(jsonPath("$.data.collectedAt")
                        .value("2026-08-25T10:00:00+08:00"))
                .andExpect(jsonPath("$.data.taskId").value(10));
    }

    private VehicleResponse response() {
        return new VehicleResponse(
                1L,
                "沪A10001",
                "VAN",
                BigDecimal.TEN,
                VehicleStatus.IDLE,
                null,
                "Driver Name",
                OffsetDateTime.parse("2026-08-22T10:30:00+08:00"),
                OffsetDateTime.parse("2026-08-22T10:30:00+08:00"),
                null,
                null,
                null
        );
    }
}
