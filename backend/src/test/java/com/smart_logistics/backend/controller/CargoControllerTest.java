package com.smart_logistics.backend.controller;

import com.smart_logistics.backend.common.PageResult;
import com.smart_logistics.backend.dto.request.CargoCreateRequest;
import com.smart_logistics.backend.dto.response.CargoResponse;
import com.smart_logistics.backend.enums.CargoStatus;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.exception.GlobalExceptionHandler;
import com.smart_logistics.backend.service.CargoService;
import com.smart_logistics.backend.service.TransportTaskStatusRecordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CargoControllerTest {

    @Mock
    private CargoService cargoService;
    @Mock
    private TransportTaskStatusRecordService statusRecordService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CargoController(cargoService, statusRecordService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void createReturnsStandardResponseWithWaitingStatus() throws Exception {
        when(cargoService.createCargo(any(CargoCreateRequest.class))).thenReturn(response());

        mockMvc.perform(post("/api/v1/cargos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.cargoNo").value("CGO-001"))
                .andExpect(jsonPath("$.data.status").value("WAITING"));

        verify(cargoService).createCargo(any(CargoCreateRequest.class));
    }

    @Test
    void createAllowsOwnerIdToBeOmitted() throws Exception {
        when(cargoService.createCargo(any(CargoCreateRequest.class)))
                .thenReturn(unassignedResponse());

        mockMvc.perform(post("/api/v1/cargos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cargoNo":"CGO-002","name":"Unassigned cargo",
                                 "weight":12.5,"volume":3.2}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ownerId").doesNotExist())
                .andExpect(jsonPath("$.data.ownerName").doesNotExist());
    }

    @Test
    void createAllowsExplicitNullOwnerId() throws Exception {
        when(cargoService.createCargo(any(CargoCreateRequest.class)))
                .thenReturn(unassignedResponse());

        mockMvc.perform(post("/api/v1/cargos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cargoNo":"CGO-003","name":"Unassigned cargo",
                                 "weight":12.5,"volume":3.2,"ownerId":null}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ownerId").doesNotExist());
    }

    @Test
    void deleteReturnsUnifiedSuccess() throws Exception {
        mockMvc.perform(delete("/api/v1/cargos/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
        verify(cargoService).deleteCargo(1L);
    }

    @Test
    void createRejectsBlankCargoNumber() throws Exception {
        mockMvc.perform(post("/api/v1/cargos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cargoNo":" ","name":"Medical supplies","weight":12.5,
                                 "volume":3.2,"ownerId":100}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value("cargoNo must not be blank"));
    }

    @Test
    void createRejectsBlankName() throws Exception {
        mockMvc.perform(post("/api/v1/cargos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cargoNo":"CGO-001","name":" ","weight":12.5,
                                 "volume":3.2,"ownerId":100}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value("name must not be blank"));
    }

    @Test
    void createRejectsNegativeWeight() throws Exception {
        mockMvc.perform(post("/api/v1/cargos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cargoNo":"CGO-001","name":"Medical supplies","weight":-1,
                                 "volume":3.2,"ownerId":100}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message")
                        .value("weight must be greater than or equal to 0"));
    }

    @Test
    void createRejectsNegativeVolume() throws Exception {
        mockMvc.perform(post("/api/v1/cargos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cargoNo":"CGO-001","name":"Medical supplies","weight":12.5,
                                 "volume":-1,"ownerId":100}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message")
                        .value("volume must be greater than or equal to 0"));
    }

    @Test
    void getCargoReturnsStandardResponse() throws Exception {
        when(cargoService.getCargo(1L)).thenReturn(response());

        mockMvc.perform(get("/api/v1/cargos/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.createdAt")
                        .value("2026-08-23T10:30:00+08:00"));
    }

    @Test
    void getMissingCargoReturnsUnifiedNotFoundResponse() throws Exception {
        when(cargoService.getCargo(99999L)).thenThrow(
                new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "cargo not found")
        );

        mockMvc.perform(get("/api/v1/cargos/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401))
                .andExpect(jsonPath("$.message").value("cargo not found"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void listReturnsStandardPageStructureAndPassesFilters() throws Exception {
        CargoResponse cargo = response();
        when(cargoService.listCargos(2, 5, "Medical", CargoStatus.WAITING,
                null, null, null))
                .thenReturn(new PageResult<>(List.of(cargo), 6, 2, 5));

        mockMvc.perform(get("/api/v1/cargos")
                        .param("page", "2")
                        .param("pageSize", "5")
                        .param("keyword", "Medical")
                        .param("status", "WAITING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.records[0].cargoNo").value("CGO-001"))
                .andExpect(jsonPath("$.data.records[0].status").value("WAITING"))
                .andExpect(jsonPath("$.data.total").value(6))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.pageSize").value(5));

        verify(cargoService).listCargos(2, 5, "Medical", CargoStatus.WAITING,
                null, null, null);
    }

    @Test
    void listRejectsInvalidCargoStatus() throws Exception {
        mockMvc.perform(get("/api/v1/cargos").param("status", "IN_TRANSIT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value("invalid request parameter or body"));
    }

    @Test
    void availableEndpointReturnsEnrichedCargos() throws Exception {
        when(cargoService.listAvailableCargos(null, null, null))
                .thenReturn(List.of(response()));
        mockMvc.perform(get("/api/v1/cargos/available"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].ownerName").value("Owner Name"));
    }

    @Test
    void listPassesMultiWarehouseFiltersAndReturnsIds() throws Exception {
        CargoResponse cargo = multiWarehouseResponse();
        when(cargoService.listCargos(1, 10, "Medical", CargoStatus.WAITING,
                3L, 10L, 2L)).thenReturn(new PageResult<>(List.of(cargo), 1, 1, 10));

        mockMvc.perform(get("/api/v1/cargos")
                        .param("keyword", "Medical")
                        .param("status", "WAITING")
                        .param("ownerId", "3")
                        .param("cargoTypeId", "10")
                        .param("warehouseId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].cargoTypeId").value(10))
                .andExpect(jsonPath("$.data.records[0].warehouseId").value(2));

        verify(cargoService).listCargos(1, 10, "Medical", CargoStatus.WAITING,
                3L, 10L, 2L);
    }

    @Test
    void availablePassesCargoTypeWarehouseAndOwnerFilters() throws Exception {
        when(cargoService.listAvailableCargos(10L, 2L, 3L))
                .thenReturn(List.of(multiWarehouseResponse()));

        mockMvc.perform(get("/api/v1/cargos/available")
                        .param("cargoTypeId", "10")
                        .param("warehouseId", "2")
                        .param("ownerId", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].cargoTypeId").value(10))
                .andExpect(jsonPath("$.data[0].warehouseId").value(2));

        verify(cargoService).listAvailableCargos(10L, 2L, 3L);
    }

    @Test
    void createAcceptsOptionalMultiWarehouseIds() throws Exception {
        when(cargoService.createCargo(any(CargoCreateRequest.class)))
                .thenReturn(multiWarehouseResponse());

        mockMvc.perform(post("/api/v1/cargos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cargoNo":"CGO-001","name":"Medical supplies",
                                 "cargoTypeId":10,"warehouseId":2}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cargoTypeId").value(10))
                .andExpect(jsonPath("$.data.warehouseId").value(2));

        ArgumentCaptor<CargoCreateRequest> captor =
                ArgumentCaptor.forClass(CargoCreateRequest.class);
        verify(cargoService).createCargo(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(10L,
                captor.getValue().getCargoTypeId());
        org.junit.jupiter.api.Assertions.assertEquals(2L,
                captor.getValue().getWarehouseId());
    }

    private String validRequestJson() {
        return """
                {"cargoNo":"CGO-001","name":"Medical supplies","description":"Fragile",
                 "weight":12.5,"volume":3.2,"ownerId":100}
                """;
    }

    private CargoResponse response() {
        return new CargoResponse(
                1L,
                "CGO-001",
                "Medical supplies",
                "Fragile",
                new BigDecimal("12.50"),
                new BigDecimal("3.20"),
                100L,
                "Owner Name",
                CargoStatus.WAITING,
                OffsetDateTime.parse("2026-08-23T10:30:00+08:00"),
                OffsetDateTime.parse("2026-08-23T10:30:00+08:00")
        );
    }

    private CargoResponse unassignedResponse() {
        return new CargoResponse(
                2L, "CGO-002", "Unassigned cargo", null,
                new BigDecimal("12.50"), new BigDecimal("3.20"),
                null, null, CargoStatus.WAITING,
                OffsetDateTime.parse("2026-08-23T10:30:00+08:00"),
                OffsetDateTime.parse("2026-08-23T10:30:00+08:00"));
    }

    private CargoResponse multiWarehouseResponse() {
        return new CargoResponse(
                1L, "CGO-001", "Medical supplies", "Fragile",
                new BigDecimal("12.50"), new BigDecimal("3.20"),
                10L, 2L, 3L, "Owner Name", CargoStatus.WAITING,
                OffsetDateTime.parse("2026-08-23T10:30:00+08:00"),
                OffsetDateTime.parse("2026-08-23T10:30:00+08:00"));
    }
}
