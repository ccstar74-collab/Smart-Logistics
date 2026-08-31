package com.smart_logistics.backend.controller;

import com.smart_logistics.backend.dto.InitialRouteLocationSnapshot;
import com.smart_logistics.backend.dto.InitialRouteScoreDetails;
import com.smart_logistics.backend.dto.TrafficSnapshot;
import com.smart_logistics.backend.dto.WeatherSnapshot;
import com.smart_logistics.backend.dto.request.InitialRouteDecisionCreateRequest;
import com.smart_logistics.backend.dto.response.InitialRouteCandidateResponse;
import com.smart_logistics.backend.dto.response.InitialRouteDecisionResponse;
import com.smart_logistics.backend.enums.InitialRouteDecisionStatus;
import com.smart_logistics.backend.enums.InitialRoutePlanningMode;
import com.smart_logistics.backend.enums.InitialRoutePlanningResult;
import com.smart_logistics.backend.enums.RecommendationSource;
import com.smart_logistics.backend.enums.TrafficLevel;
import com.smart_logistics.backend.exception.GlobalExceptionHandler;
import com.smart_logistics.backend.service.InitialRouteDecisionService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InitialRouteDecisionControllerTest {

    @Mock private InitialRouteDecisionService decisionService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new InitialRouteDecisionController(decisionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void createsPreTaskDecisionAndReturnsFrontendReadyCandidates() throws Exception {
        when(decisionService.createDecision(
                any(InitialRouteDecisionCreateRequest.class), eq("planning-key")))
                .thenReturn(response());

        mockMvc.perform(post("/api/v1/initial-route-decisions")
                        .header("Idempotency-Key", "planning-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.decisionId").value("decision-1"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.planningMode")
                        .value("INITIAL_MULTI_OBJECTIVE"))
                .andExpect(jsonPath("$.data.recommendedRouteId")
                        .value("preview-route-1"))
                .andExpect(jsonPath("$.data.routes[0].rank").value(1))
                .andExpect(jsonPath("$.data.routes[0].totalScore").value(100.0))
                .andExpect(jsonPath("$.data.routes[0].trafficDataSource")
                        .value("AMAP_DRIVING_V3"));
    }

    @Test
    void readsPersistedDecisionById() throws Exception {
        when(decisionService.getDecision("decision-1")).thenReturn(response());

        mockMvc.perform(get("/api/v1/initial-route-decisions/decision-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.routes.length()").value(1));

        verify(decisionService).getDecision("decision-1");
    }

    @Test
    void rejectsMissingIdempotencyKeyAndInvalidCandidateCount() throws Exception {
        mockMvc.perform(post("/api/v1/initial-route-decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/initial-route-decisions")
                        .header("Idempotency-Key", "planning-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson().replace("\"candidateCount\":3",
                                "\"candidateCount\":4")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("candidateCount must not exceed 3"));
        verifyNoInteractions(decisionService);
    }

    private String validJson() {
        return """
                {"originWarehouseId":1,"endLocation":"Destination",
                 "endLongitude":106.80,"endLatitude":29.70,
                 "coordinateSystem":"GCJ02","candidateCount":3,
                 "planningMode":"INITIAL_MULTI_OBJECTIVE"}
                """;
    }

    private InitialRouteDecisionResponse response() {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-31T10:00:00+08:00");
        InitialRouteLocationSnapshot start = new InitialRouteLocationSnapshot(
                "Central Warehouse", 106.735012, 29.610634, "GCJ02");
        InitialRouteLocationSnapshot destination = new InitialRouteLocationSnapshot(
                "Destination", 106.80, 29.70, "GCJ02");
        WeatherSnapshot weather = new WeatherSnapshot(
                "AMAP_WEATHER_V3", "500000", "重庆", "重庆市", "晴",
                new BigDecimal("25"), 60, "东", "2", now);
        TrafficSnapshot traffic = new TrafficSnapshot(
                "AMAP_DRIVING_V3", "11", false, 8,
                0, 9_000, 0, 0, 0);
        InitialRouteCandidateResponse route = new InitialRouteCandidateResponse(
                "preview-route-1", "候选路线 A", 1, new BigDecimal("100.00"),
                9_000, 1_000, TrafficLevel.FREE_FLOW,
                "AMAP_DRIVING_V3", "AMAP", "GCJ02",
                List.of(List.of(106.735012, 29.610634),
                        List.of(106.80, 29.70)),
                traffic, weather,
                new InitialRouteScoreDetails(
                        new BigDecimal("100.00"), new BigDecimal("100.00"),
                        new BigDecimal("100.00"), new BigDecimal("100.00")),
                List.of("综合评分最高"));
        return new InitialRouteDecisionResponse(
                "decision-1", InitialRouteDecisionStatus.PENDING,
                InitialRoutePlanningMode.INITIAL_MULTI_OBJECTIVE,
                InitialRoutePlanningResult.MULTI_ROUTE,
                start, destination, "preview-route-1", null,
                "initial-route-score-v1", RecommendationSource.RULE_FALLBACK,
                now, now.plusMinutes(5), null, null, weather,
                List.of(route), "综合评分推荐候选路线 A。");
    }
}
