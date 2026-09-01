package com.smart_logistics.backend.controller;

import com.smart_logistics.backend.common.PageResult;
import com.smart_logistics.backend.dto.request.VehicleCreateRequest;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
                                {"plateNumber":" ","type":"VAN","capacity":10,"simCode":"sim_001"}
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
                                {"plateNumber":"沪A10003","type":"VAN","capacity":-1,"simCode":"sim_003"}
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
                                {"plateNumber":"123456789012345678901","capacity":10,"simCode":"sim_004"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message")
                        .value("plateNumber must not exceed 20 characters"));
    }

    @Test
    void createAcceptsSimCodeAndReturnsOnlyCamelCaseField() throws Exception {
        when(vehicleService.createVehicle(any(VehicleCreateRequest.class)))
                .thenReturn(response("sim_008"));

        mockMvc.perform(post("/api/v1/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plateNumber":"粤B23008","type":"厢式货车","capacity":10.5,
                                 "driverId":null,"simCode":"sim_008"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.simCode").value("sim_008"))
                .andExpect(jsonPath("$.data.sim_code").doesNotExist());

        ArgumentCaptor<VehicleCreateRequest> captor =
                ArgumentCaptor.forClass(VehicleCreateRequest.class);
        verify(vehicleService).createVehicle(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("sim_008",
                captor.getValue().getSimCode());
    }

    @Test
    void createAcceptsSnakeCaseSimCodeAlias() throws Exception {
        when(vehicleService.createVehicle(any(VehicleCreateRequest.class)))
                .thenReturn(response("sim_008"));

        mockMvc.perform(post("/api/v1/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plateNumber":"粤B23008","type":"厢式货车","capacity":10.5,
                                 "sim_code":"sim_008"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.simCode").value("sim_008"))
                .andExpect(jsonPath("$.data.sim_code").doesNotExist());

        ArgumentCaptor<VehicleCreateRequest> captor =
                ArgumentCaptor.forClass(VehicleCreateRequest.class);
        verify(vehicleService).createVehicle(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("sim_008",
                captor.getValue().getSimCode());
    }

    @Test
    void createRejectsMissingSimCode() throws Exception {
        mockMvc.perform(post("/api/v1/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plateNumber":"粤B23008","type":"厢式货车","capacity":10.5}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("simCode must not be blank"));
    }

    @Test
    void createRejectsBlankSimCode() throws Exception {
        mockMvc.perform(post("/api/v1/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plateNumber":"粤B23008","type":"厢式货车","capacity":10.5,
                                 "simCode":"   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("simCode must not be blank"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"sim_8", "sim_08", "sim_1000", "abc"})
    void createRejectsInvalidSimCodeFormat(String simCode) throws Exception {
        mockMvc.perform(post("/api/v1/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plateNumber":"粤B23008","type":"厢式货车","capacity":10.5,
                                 "simCode":"%s"}
                                """.formatted(simCode)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message")
                        .value("simCode must match ^sim_\\d{3}$"));
    }

    @Test
    void updateAcceptsValidSimCode() throws Exception {
        when(vehicleService.updateVehicle(
                org.mockito.ArgumentMatchers.eq(1L), any()))
                .thenReturn(response("sim_009"));

        mockMvc.perform(put("/api/v1/vehicles/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plateNumber":"粤B23009","type":"厢式货车","capacity":10.5,
                                 "driverId":null,"simCode":"sim_009"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.simCode").value("sim_009"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"8", "008", "sim_008"})
    void availableSimCodeSearchPassesSupportedKeywordForms(String keyword) throws Exception {
        when(vehicleService.listAvailableSimCodes(keyword))
                .thenReturn(List.of("sim_008"));

        mockMvc.perform(get("/api/v1/vehicles/sim-codes/available")
                        .param("keyword", keyword))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0]").value("sim_008"));

        verify(vehicleService).listAvailableSimCodes(keyword);
    }

    @Test
    void duplicateSimCodeReturnsConflictResponse() throws Exception {
        when(vehicleService.createVehicle(any(VehicleCreateRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.DATA_CONFLICT,
                        "simCode is already assigned to another vehicle"));

        mockMvc.perform(post("/api/v1/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plateNumber":"粤B23008","capacity":10.5,
                                 "simCode":"sim_008"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901))
                .andExpect(jsonPath("$.message")
                        .value("simCode is already assigned to another vehicle"));
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
        VehicleResponse vehicle = response("sim_008");
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
                .andExpect(jsonPath("$.data.records[0].simCode").value("sim_008"))
                .andExpect(jsonPath("$.data.records[0].createdAt")
                        .value("2026-08-22T10:30:00+08:00"))
                .andExpect(jsonPath("$.data.total").value(6))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.pageSize").value(5));

        verify(vehicleService).listVehicles(2, 5, "沪A", VehicleStatus.IDLE, null);
    }

    @Test
    void detailReturnsSimCode() throws Exception {
        when(vehicleService.getVehicle(1L)).thenReturn(response("sim_008"));

        mockMvc.perform(get("/api/v1/vehicles/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.simCode").value("sim_008"));
    }

    @Test
    void availableEndpointReturnsEnrichedVehicles() throws Exception {
        when(vehicleService.listAvailableVehicles(null))
                .thenReturn(List.of(response("sim_008")));
        mockMvc.perform(get("/api/v1/vehicles/available"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].driverName").value("Driver Name"));
    }

    @Test
    void availablePassesWarehouseFilterAndReturnsWarehouseId() throws Exception {
        when(vehicleService.listAvailableVehicles(2L))
                .thenReturn(List.of(multiWarehouseResponse()));

        mockMvc.perform(get("/api/v1/vehicles/available")
                        .param("warehouseId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].warehouseId").value(2));

        verify(vehicleService).listAvailableVehicles(2L);
    }

    @Test
    void createAcceptsOptionalWarehouseId() throws Exception {
        when(vehicleService.createVehicle(any(VehicleCreateRequest.class)))
                .thenReturn(multiWarehouseResponse());

        mockMvc.perform(post("/api/v1/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plateNumber":"粤B23008","type":"厢式货车",
                                 "capacity":10.5,"simCode":"sim_008","warehouseId":2}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.warehouseId").value(2));

        ArgumentCaptor<VehicleCreateRequest> captor =
                ArgumentCaptor.forClass(VehicleCreateRequest.class);
        verify(vehicleService).createVehicle(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(2L,
                captor.getValue().getWarehouseId());
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
                .andExpect(jsonPath("$.data.coordinateSystem").value("WGS84"))
                .andExpect(jsonPath("$.data.collectedAt")
                        .value("2026-08-25T10:00:00+08:00"))
                .andExpect(jsonPath("$.data.taskId").value(10));
    }

    private VehicleResponse response(String simCode) {
        return new VehicleResponse(
                1L,
                "沪A10001",
                "VAN",
                BigDecimal.TEN,
                VehicleStatus.IDLE,
                null,
                "Driver Name",
                simCode,
                OffsetDateTime.parse("2026-08-22T10:30:00+08:00"),
                OffsetDateTime.parse("2026-08-22T10:30:00+08:00"),
                null,
                null,
                null
        );
    }
    private VehicleResponse multiWarehouseResponse() {
        return new VehicleResponse(
                1L, "沪A10001", "VAN", BigDecimal.TEN, VehicleStatus.IDLE,
                3L, "Driver Name", 2L, "sim_008",
                OffsetDateTime.parse("2026-08-22T10:30:00+08:00"),
                 OffsetDateTime.parse("2026-08-22T10:30:00+08:00"),
                 null, null, null);
    }
}
