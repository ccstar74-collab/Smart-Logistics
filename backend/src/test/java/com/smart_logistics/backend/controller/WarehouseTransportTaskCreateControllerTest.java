package com.smart_logistics.backend.controller;

import com.smart_logistics.backend.dto.request.WarehouseTransportTaskCreateRequest;
import com.smart_logistics.backend.dto.response.TransportTaskResponse;
import com.smart_logistics.backend.enums.TransportTaskRouteStatus;
import com.smart_logistics.backend.enums.TransportTaskStatus;
import com.smart_logistics.backend.exception.GlobalExceptionHandler;
import com.smart_logistics.backend.service.OriginRecommendationService;
import com.smart_logistics.backend.service.TaskTrackQueryService;
import com.smart_logistics.backend.service.TransportTaskPlaybackService;
import com.smart_logistics.backend.service.TransportTaskReplanService;
import com.smart_logistics.backend.service.TransportTaskService;
import com.smart_logistics.backend.service.WarehouseTransportTaskCreateService;
import com.smart_logistics.backend.service.eta.EtaPlannedRouteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WarehouseTransportTaskCreateControllerTest {

    @Mock private TransportTaskService transportTaskService;
    @Mock private TaskTrackQueryService taskTrackQueryService;
    @Mock private EtaPlannedRouteService etaPlannedRouteService;
    @Mock private TransportTaskReplanService transportTaskReplanService;
    @Mock private TransportTaskPlaybackService transportTaskPlaybackService;
    @Mock private OriginRecommendationService originRecommendationService;
    @Mock private WarehouseTransportTaskCreateService warehouseTaskCreateService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        MethodValidationPostProcessor methodValidation = new MethodValidationPostProcessor();
        methodValidation.setProxyTargetClass(true);
        methodValidation.setValidator(validator);
        methodValidation.afterPropertiesSet();
        Object controller = methodValidation.postProcessAfterInitialization(
                new TransportTaskController(transportTaskService, taskTrackQueryService,
                        etaPlannedRouteService, transportTaskReplanService,
                        transportTaskPlaybackService, originRecommendationService,
                        warehouseTaskCreateService),
                "transportTaskController");
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void createsThroughStrictWarehouseContractAndReturnsOriginWarehouseId()
            throws Exception {
        when(warehouseTaskCreateService.createTransportTask(
                any(WarehouseTransportTaskCreateRequest.class), eq("confirm-key")))
                .thenReturn(response());

        mockMvc.perform(post("/api/v1/transport-tasks/from-warehouse")
                        .header("Idempotency-Key", "confirm-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJsonWithSpoofedUnknownFields()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.originWarehouseId").value(1))
                .andExpect(jsonPath("$.data.startLocation").value("Central Warehouse"))
                .andExpect(jsonPath("$.data.driverId").value(9))
                .andExpect(jsonPath("$.data.routeVersion").value(1))
                .andExpect(jsonPath("$.data.routeStatus").value("ACTIVE"));

        ArgumentCaptor<WarehouseTransportTaskCreateRequest> request =
                ArgumentCaptor.forClass(WarehouseTransportTaskCreateRequest.class);
        verify(warehouseTaskCreateService)
                .createTransportTask(request.capture(), eq("confirm-key"));
        assertEquals("decision-1", request.getValue().getRouteDecisionId());
        assertEquals("preview-route-1", request.getValue().getSelectedRouteId());
        assertEquals(1L, request.getValue().getOriginWarehouseId());
        assertEquals(40L, request.getValue().getCargoTypeId());
        assertEquals(OffsetDateTime.parse("2026-08-31T08:00:00+08:00").toInstant(),
                request.getValue().getPlanStartTime().toInstant());
    }

    @Test
    void rejectsMissingRequiredWarehouseField() throws Exception {
        mockMvc.perform(post("/api/v1/transport-tasks/from-warehouse")
                        .header("Idempotency-Key", "confirm-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJsonWithSpoofedUnknownFields()
                                .replace("\"originWarehouseId\":1,", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message")
                        .value("originWarehouseId must not be null"));
        verifyNoInteractions(warehouseTaskCreateService);
    }

    @Test
    void rejectsInvalidDestinationCoordinates() throws Exception {
        mockMvc.perform(post("/api/v1/transport-tasks/from-warehouse")
                        .header("Idempotency-Key", "confirm-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJsonWithSpoofedUnknownFields()
                                .replace("106.80", "180.000001")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        verifyNoInteractions(warehouseTaskCreateService);
    }

    private String validJsonWithSpoofedUnknownFields() {
        return """
                {"routeDecisionId":"decision-1","selectedRouteId":"preview-route-1",
                 "routeSelectionRemark":"仓库管理员人工确认",
                 "ownerId":30,"cargoTypeId":40,"originWarehouseId":1,
                 "cargoId":10,"vehicleId":20,"endLocation":"Destination",
                 "endLongitude":106.80,"endLatitude":29.70,
                 "plannedStartTime":"2026-08-31T08:00:00+08:00",
                 "planEndTime":"2026-08-31T10:00:00+08:00",
                 "startLocation":"Spoofed Warehouse","startLongitude":1.0,
                 "startLatitude":1.0,"driverId":999}
                """;
    }

    private TransportTaskResponse response() {
        return new TransportTaskResponse(
                100L, "T202608300001", 10L, 20L,
                "Central Warehouse", 106.735012, 29.610634,
                "Destination", 106.80, 29.70,
                null, null, null, null, TransportTaskStatus.WAITING,
                null, null, null, null, 9L, "Driver", "渝A10001",
                "route_v1", 1, TransportTaskRouteStatus.ACTIVE, 1L);
    }
}
