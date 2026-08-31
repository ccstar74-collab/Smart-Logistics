package com.smart_logistics.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart_logistics.backend.dto.TrafficSnapshot;
import com.smart_logistics.backend.dto.WeatherSnapshot;
import com.smart_logistics.backend.dto.request.InitialRouteDecisionCreateRequest;
import com.smart_logistics.backend.dto.response.InitialRouteDecisionResponse;
import com.smart_logistics.backend.dto.response.UserIdentityResponse;
import com.smart_logistics.backend.entity.InitialRouteCandidate;
import com.smart_logistics.backend.entity.InitialRouteDecision;
import com.smart_logistics.backend.entity.Warehouse;
import com.smart_logistics.backend.enums.InitialRouteDecisionStatus;
import com.smart_logistics.backend.enums.InitialRouteDegradationReason;
import com.smart_logistics.backend.enums.InitialRoutePlanningResult;
import com.smart_logistics.backend.enums.RecommendationSource;
import com.smart_logistics.backend.enums.TrafficLevel;
import com.smart_logistics.backend.enums.UserRole;
import com.smart_logistics.backend.enums.UserStatus;
import com.smart_logistics.backend.mapper.InitialRouteCandidateMapper;
import com.smart_logistics.backend.mapper.InitialRouteDecisionMapper;
import com.smart_logistics.backend.security.CurrentUserService;
import com.smart_logistics.backend.service.eta.EtaCoordinate;
import com.smart_logistics.backend.service.eta.EtaPlannedRoute;
import com.smart_logistics.backend.service.route.InitialRouteCandidateGenerator;
import com.smart_logistics.backend.service.route.InitialRouteExplanationPort;
import com.smart_logistics.backend.service.route.InitialRouteScoringService;
import com.smart_logistics.backend.service.weather.WeatherProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InitialRouteDecisionServiceTest {

    @Mock private InitialRouteDecisionMapper decisionMapper;
    @Mock private InitialRouteCandidateMapper candidateMapper;
    @Mock private WarehouseService warehouseService;
    @Mock private InitialRouteCandidateGenerator candidateGenerator;
    @Mock private InitialRouteScoringService scoringService;
    @Mock private WeatherProvider weatherProvider;
    @Mock private InitialRouteExplanationPort explanationPort;
    @Mock private CurrentUserService currentUserService;
    @Mock private TransactionOperations transactionOperations;

    private InitialRouteDecisionService service;
    private WeatherSnapshot weather;
    private InitialRouteCandidateGenerator.GeneratedInitialRoute generated;
    private InitialRouteScoringService.ScoredInitialRoute scored;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        weather = new WeatherSnapshot(
                "AMAP_WEATHER_V3", "500000", "重庆", "重庆市", "晴",
                new BigDecimal("25"), 60, "东", "2",
                OffsetDateTime.parse("2026-08-31T10:00:00+08:00"));
        EtaPlannedRoute route = new EtaPlannedRoute(List.of(
                new EtaCoordinate(106.735012, 29.610634),
                new EtaCoordinate(106.80, 29.70)), 9_000,
                Duration.ofSeconds(1_000),
                new TrafficSnapshot("AMAP_DRIVING_V3", "11", false,
                        8, 0, 9_000, 0, 0, 0));
        generated = new InitialRouteCandidateGenerator.GeneratedInitialRoute(
                "preview-route-1", route, TrafficLevel.FREE_FLOW);
        scored = new InitialRouteScoringService.ScoredInitialRoute(
                generated.previewRouteId(), generated.route(), generated.trafficLevel(),
                new com.smart_logistics.backend.dto.InitialRouteScoreDetails(
                        new BigDecimal("100.00"), new BigDecimal("100.00"),
                        new BigDecimal("100.00"), new BigDecimal("100.00")),
                new BigDecimal("100.00"), 1, List.of("综合评分最高"));

        org.mockito.Mockito.lenient().when(currentUserService.getCurrentUser())
                .thenReturn(new UserIdentityResponse(
                        99L, "warehouse_manager", "Warehouse Manager", null,
                        UserRole.WAREHOUSE_MANAGER, UserStatus.ACTIVE, null, null));
        org.mockito.Mockito.lenient().when(transactionOperations.execute(any()))
                .thenAnswer(invocation -> {
                    TransactionCallback<?> callback = invocation.getArgument(0);
                    return callback.doInTransaction(null);
                });

        service = new InitialRouteDecisionService(
                decisionMapper, candidateMapper, warehouseService, candidateGenerator,
                scoringService, weatherProvider, explanationPort, currentUserService,
                objectMapper, transactionOperations, Duration.ofMinutes(5));
    }

    @Test
    void createsAndPersistsSingleRouteDecisionWithoutCallingAgent() {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(1L);
        warehouse.setAddress("Central Warehouse");
        warehouse.setLongitude(106.735012);
        warehouse.setLatitude(29.610634);
        when(warehouseService.requireActiveWarehouse(1L)).thenReturn(warehouse);
        when(candidateGenerator.generate(
                106.735012, 29.610634, 106.80, 29.70, 3))
                .thenReturn(new InitialRouteCandidateGenerator.GenerationResult(
                        List.of(generated),
                        InitialRouteDegradationReason.ROUTE_PROVIDER_SINGLE_RESULT));
        when(weatherProvider.getCurrentWeather(106.80, 29.70)).thenReturn(weather);
        when(scoringService.score(List.of(generated), weather))
                .thenReturn(List.of(scored));
        when(decisionMapper.insert(any(InitialRouteDecision.class))).thenReturn(1);
        when(candidateMapper.insert(any(InitialRouteCandidate.class))).thenReturn(1);

        InitialRouteDecisionResponse response = service.createDecision(
                request(), " planning-key ");

        assertEquals(InitialRouteDecisionStatus.PENDING, response.status());
        assertEquals(InitialRoutePlanningResult.MULTI_ROUTE,
                response.planningResult());
        assertEquals(1, response.candidateCount());
        assertTrue(response.degraded());
        assertEquals(InitialRouteDegradationReason.ROUTE_PROVIDER_SINGLE_RESULT,
                response.degradedReason());
        assertEquals(RecommendationSource.SINGLE_ROUTE,
                response.recommendationSource());
        assertEquals("preview-route-1", response.recommendedRouteId());
        assertEquals(1, response.routes().size());
        assertEquals("AMAP_DRIVING_V3",
                response.routes().getFirst().trafficDataSource());
        assertEquals(Duration.ofMinutes(5),
                Duration.between(response.calculatedAt(), response.expiresAt()));

        ArgumentCaptor<InitialRouteDecision> decision =
                ArgumentCaptor.forClass(InitialRouteDecision.class);
        verify(decisionMapper).insert(decision.capture());
        assertEquals("planning-key", decision.getValue().getIdempotencyKey());
        assertEquals(99L, decision.getValue().getCreatedBy());
        assertEquals(1L, decision.getValue().getOriginWarehouseId());
        assertTrue(decision.getValue().getDecisionId().startsWith("ird_"));
        assertEquals(InitialRoutePlanningResult.MULTI_ROUTE.name(),
                decision.getValue().getPlanningResult());
        assertEquals(RecommendationSource.SINGLE_ROUTE.name(),
                decision.getValue().getRecommendationSource());
        assertTrue(decision.getValue().getInputSnapshot().contains(
                "ROUTE_PROVIDER_SINGLE_RESULT"));
        verifyNoInteractions(explanationPort);

        ArgumentCaptor<InitialRouteCandidate> candidate =
                ArgumentCaptor.forClass(InitialRouteCandidate.class);
        verify(candidateMapper).insert(candidate.capture());
        assertEquals("preview-route-1", candidate.getValue().getPreviewRouteId());
        assertEquals("规划路线", candidate.getValue().getDisplayName());
        assertEquals("AMAP", candidate.getValue().getProvider());
        assertEquals("GCJ02", candidate.getValue().getCoordinateSystem());

        when(decisionMapper.selectOne(any())).thenReturn(decision.getValue());
        when(candidateMapper.selectList(any())).thenReturn(
                List.of(candidate.getValue()));
        InitialRouteDecisionResponse restored = service.getDecision(
                decision.getValue().getDecisionId());
        assertEquals(1, restored.candidateCount());
        assertTrue(restored.degraded());
        assertEquals(InitialRouteDegradationReason.ROUTE_PROVIDER_SINGLE_RESULT,
                restored.degradedReason());
        assertEquals(RecommendationSource.SINGLE_ROUTE,
                restored.recommendationSource());
    }

    private InitialRouteDecisionCreateRequest request() {
        InitialRouteDecisionCreateRequest request =
                new InitialRouteDecisionCreateRequest();
        request.setOriginWarehouseId(1L);
        request.setEndLocation("Destination");
        request.setEndLongitude(106.80);
        request.setEndLatitude(29.70);
        request.setCoordinateSystem("GCJ02");
        request.setCandidateCount(3);
        request.setPlanningMode("INITIAL_MULTI_OBJECTIVE");
        return request;
    }
}
