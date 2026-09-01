package com.smart_logistics.backend.controller;

import com.smart_logistics.backend.dto.request.OriginRecommendationRequest;
import com.smart_logistics.backend.dto.response.OriginRecommendationResponse;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.exception.GlobalExceptionHandler;
import com.smart_logistics.backend.service.OriginRecommendationService;
import com.smart_logistics.backend.service.TaskTrackQueryService;
import com.smart_logistics.backend.service.TransportTaskPlaybackService;
import com.smart_logistics.backend.service.TransportTaskReplanService;
import com.smart_logistics.backend.service.TransportTaskService;
import com.smart_logistics.backend.service.eta.EtaPlannedRouteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OriginRecommendationControllerTest {

    @Mock private TransportTaskService transportTaskService;
    @Mock private TaskTrackQueryService taskTrackQueryService;
    @Mock private EtaPlannedRouteService etaPlannedRouteService;
    @Mock private TransportTaskReplanService transportTaskReplanService;
    @Mock private TransportTaskPlaybackService transportTaskPlaybackService;
    @Mock private OriginRecommendationService originRecommendationService;

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
                        transportTaskPlaybackService, originRecommendationService),
                "transportTaskController");
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void returnsRankedRecommendationContract() throws Exception {
        when(originRecommendationService.recommend(any(OriginRecommendationRequest.class)))
                .thenReturn(List.of(new OriginRecommendationResponse(
                        1L, "WH-001", "Central", "Chongqing",
                        106.71, 29.61, 9_000, 1_000,
                        1, 1, true)));

        mockMvc.perform(post("/api/v1/transport-tasks/origin-recommendation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].warehouseId").value(1))
                .andExpect(jsonPath("$.data[0].warehouseNo").value("WH-001"))
                .andExpect(jsonPath("$.data[0].distanceMeters").value(9000))
                .andExpect(jsonPath("$.data[0].durationSeconds").value(1000))
                .andExpect(jsonPath("$.data[0].availableCargoCount").value(1))
                .andExpect(jsonPath("$.data[0].availableVehicleCount").value(1))
                .andExpect(jsonPath("$.data[0].recommended").value(true));
    }

    @Test
    void emptyCandidateIsSuccessfulEmptyArray() throws Exception {
        when(originRecommendationService.recommend(any(OriginRecommendationRequest.class)))
                .thenReturn(List.of());

        mockMvc.perform(post("/api/v1/transport-tasks/origin-recommendation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void providerFailureUsesExistingServiceUnavailableMapping() throws Exception {
        when(originRecommendationService.recommend(any(OriginRecommendationRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.REALTIME_PROVIDER_UNAVAILABLE,
                        "planned route is unavailable"));

        mockMvc.perform(post("/api/v1/transport-tasks/origin-recommendation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(50301));
    }

    @Test
    void rejectsNullOwnerId() throws Exception {
        assertInvalid(jsonWithout("\"ownerId\":30,"), "ownerId must not be null");
    }

    @Test
    void rejectsNullCargoTypeId() throws Exception {
        assertInvalid(jsonWithout("\"cargoTypeId\":10,"),
                "cargoTypeId must not be null");
    }

    @Test
    void rejectsBlankEndLocation() throws Exception {
        assertInvalid(validJson().replace("\"Chongqing\"", "\" \""),
                "endLocation must not be blank");
    }

    @Test
    void rejectsInvalidLongitude() throws Exception {
        assertInvalid(validJson().replace("106.80", "180.000001"),
                "endLongitude must not exceed 180");
    }

    @Test
    void rejectsInvalidLatitude() throws Exception {
        assertInvalid(validJson().replace("29.70", "-90.000001"),
                "endLatitude must be at least -90");
    }

    private void assertInvalid(String body, String message) throws Exception {
        mockMvc.perform(post("/api/v1/transport-tasks/origin-recommendation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(message));
        verifyNoInteractions(originRecommendationService);
    }

    private String jsonWithout(String field) {
        return validJson().replace(field, "");
    }

    private String validJson() {
        return """
                {"ownerId":30,"cargoTypeId":10,"endLocation":"Chongqing",
                 "endLongitude":106.80,"endLatitude":29.70}
                """;
    }
}
