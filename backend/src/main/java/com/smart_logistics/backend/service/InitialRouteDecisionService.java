package com.smart_logistics.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart_logistics.backend.dto.InitialRouteLocationSnapshot;
import com.smart_logistics.backend.dto.InitialRouteScoreDetails;
import com.smart_logistics.backend.dto.TrafficSnapshot;
import com.smart_logistics.backend.dto.WeatherSnapshot;
import com.smart_logistics.backend.dto.request.InitialRouteDecisionCreateRequest;
import com.smart_logistics.backend.dto.response.InitialRouteCandidateResponse;
import com.smart_logistics.backend.dto.response.InitialRouteDecisionResponse;
import com.smart_logistics.backend.dto.response.UserIdentityResponse;
import com.smart_logistics.backend.entity.InitialRouteCandidate;
import com.smart_logistics.backend.entity.InitialRouteDecision;
import com.smart_logistics.backend.entity.Warehouse;
import com.smart_logistics.backend.enums.InitialRouteDecisionStatus;
import com.smart_logistics.backend.enums.InitialRouteDegradationReason;
import com.smart_logistics.backend.enums.InitialRoutePlanningMode;
import com.smart_logistics.backend.enums.InitialRoutePlanningResult;
import com.smart_logistics.backend.enums.RecommendationSource;
import com.smart_logistics.backend.enums.TrafficLevel;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.mapper.InitialRouteCandidateMapper;
import com.smart_logistics.backend.mapper.InitialRouteDecisionMapper;
import com.smart_logistics.backend.security.CurrentUserService;
import com.smart_logistics.backend.service.eta.EtaCoordinate;
import com.smart_logistics.backend.service.route.InitialRouteCandidateGenerator;
import com.smart_logistics.backend.service.route.InitialRouteExplanationPort;
import com.smart_logistics.backend.service.route.InitialRouteScoringService;
import com.smart_logistics.backend.service.weather.WeatherProvider;
import com.smart_logistics.backend.service.weather.WeatherProviderException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class InitialRouteDecisionService {

    private static final ZoneId API_TIME_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String PROVIDER = "AMAP";
    private static final String COORDINATE_SYSTEM = "GCJ02";

    private final InitialRouteDecisionMapper decisionMapper;
    private final InitialRouteCandidateMapper candidateMapper;
    private final WarehouseService warehouseService;
    private final InitialRouteCandidateGenerator candidateGenerator;
    private final InitialRouteScoringService scoringService;
    private final WeatherProvider weatherProvider;
    private final InitialRouteExplanationPort explanationPort;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper;
    private final TransactionOperations transactionOperations;
    private final Duration decisionTtl;

    @Autowired
    public InitialRouteDecisionService(
            InitialRouteDecisionMapper decisionMapper,
            InitialRouteCandidateMapper candidateMapper,
            WarehouseService warehouseService,
            InitialRouteCandidateGenerator candidateGenerator,
            InitialRouteScoringService scoringService,
            WeatherProvider weatherProvider,
            InitialRouteExplanationPort explanationPort,
            CurrentUserService currentUserService,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager,
            @Value("${app.initial-route.decision-ttl:PT5M}") Duration decisionTtl) {
        this(decisionMapper, candidateMapper, warehouseService, candidateGenerator,
                scoringService, weatherProvider, explanationPort, currentUserService,
                objectMapper, new TransactionTemplate(transactionManager), decisionTtl);
    }

    InitialRouteDecisionService(
            InitialRouteDecisionMapper decisionMapper,
            InitialRouteCandidateMapper candidateMapper,
            WarehouseService warehouseService,
            InitialRouteCandidateGenerator candidateGenerator,
            InitialRouteScoringService scoringService,
            WeatherProvider weatherProvider,
            InitialRouteExplanationPort explanationPort,
            CurrentUserService currentUserService,
            ObjectMapper objectMapper,
            TransactionOperations transactionOperations,
            Duration decisionTtl) {
        this.decisionMapper = decisionMapper;
        this.candidateMapper = candidateMapper;
        this.warehouseService = warehouseService;
        this.candidateGenerator = candidateGenerator;
        this.scoringService = scoringService;
        this.weatherProvider = weatherProvider;
        this.explanationPort = explanationPort;
        this.currentUserService = currentUserService;
        this.objectMapper = objectMapper;
        this.transactionOperations = transactionOperations;
        this.decisionTtl = decisionTtl;
    }

    public InitialRouteDecisionResponse createDecision(
            InitialRouteDecisionCreateRequest request,
            String idempotencyKey) {
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        UserIdentityResponse current = currentUserService.getCurrentUser();
        InitialRouteDecision existing = findByIdempotencyKey(normalizedKey);
        if (existing != null) {
            requireOwned(existing, current.getId());
            return toResponse(existing, listCandidates(existing.getDecisionId()));
        }

        Warehouse warehouse = validatedWarehouse(
                warehouseService.requireActiveWarehouse(request.getOriginWarehouseId()));
        requireDifferentCoordinates(warehouse.getLongitude(), warehouse.getLatitude(),
                request.getEndLongitude(), request.getEndLatitude());

        InitialRouteCandidateGenerator.GenerationResult generation =
                candidateGenerator.generate(
                        warehouse.getLongitude(), warehouse.getLatitude(),
                        request.getEndLongitude(), request.getEndLatitude(),
                        request.getCandidateCount());
        List<InitialRouteCandidateGenerator.GeneratedInitialRoute> generated =
                generation.routes();
        WeatherSnapshot weather = destinationWeather(
                request.getEndLongitude(), request.getEndLatitude());
        List<InitialRouteScoringService.ScoredInitialRoute> scored =
                scoringService.score(generated, weather);

        String decisionId = "ird_" + UUID.randomUUID().toString().replace("-", "");
        InitialRouteExplanationPort.ExplanationResult explanation = generation.degraded()
                ? singleRouteExplanation(scored.getFirst(),
                generation.degradationReason())
                : explanationPort.explain(
                new InitialRouteExplanationPort.ExplanationRequest(
                        decisionId, InitialRoutePlanningMode.INITIAL_MULTI_OBJECTIVE.name(),
                        InitialRouteScoringService.RULE_VERSION, weather, scored));
        LocalDateTime now = LocalDateTime.now(API_TIME_ZONE);
        InitialRouteLocationSnapshot start = new InitialRouteLocationSnapshot(
                warehouse.getAddress(), warehouse.getLongitude(), warehouse.getLatitude(),
                COORDINATE_SYSTEM);
        InitialRouteLocationSnapshot destination = new InitialRouteLocationSnapshot(
                request.getEndLocation().trim(), request.getEndLongitude(),
                request.getEndLatitude(), COORDINATE_SYSTEM);

        InitialRouteDecision decision = new InitialRouteDecision();
        decision.setDecisionId(decisionId);
        decision.setCreatedBy(current.getId());
        decision.setOriginWarehouseId(warehouse.getId());
        decision.setStatus(InitialRouteDecisionStatus.PENDING.name());
        decision.setPlanningMode(InitialRoutePlanningMode.INITIAL_MULTI_OBJECTIVE.name());
        decision.setPlanningResult(InitialRoutePlanningResult.MULTI_ROUTE.name());
        decision.setStartSnapshot(writeJson(start));
        decision.setDestinationSnapshot(writeJson(destination));
        decision.setRecommendedRouteId(scored.getFirst().previewRouteId());
        decision.setScoringRuleVersion(InitialRouteScoringService.RULE_VERSION);
        decision.setRecommendationSource(explanation.source().name());
        decision.setInputSnapshot(writeJson(inputSnapshot(
                warehouse, request, start, destination, generation)));
        decision.setWeatherSnapshot(writeJson(weather));
        decision.setExplanation(explanation.explanation());
        decision.setIdempotencyKey(normalizedKey);
        decision.setCalculatedAt(now);
        decision.setExpiresAt(now.plus(decisionTtl));
        decision.setCreatedAt(now);
        decision.setUpdatedAt(now);

        List<InitialRouteCandidate> candidates = toEntities(
                decisionId, scored, weather, explanation.reasonsByRouteId(),
                now, generated.size());
        try {
            InitialRouteDecisionResponse response = transactionOperations.execute(status -> {
                if (decisionMapper.insert(decision) != 1) {
                    throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                            "failed to persist initial route decision");
                }
                for (InitialRouteCandidate candidate : candidates) {
                    if (candidateMapper.insert(candidate) != 1) {
                        throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                                "failed to persist initial route candidate");
                    }
                }
                return toResponse(decision, candidates);
            });
            if (response == null) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                        "initial route decision transaction returned no result");
            }
            return response;
        } catch (DuplicateKeyException exception) {
            InitialRouteDecision concurrent = findByIdempotencyKey(normalizedKey);
            if (concurrent != null && Objects.equals(concurrent.getCreatedBy(), current.getId())) {
                return toResponse(concurrent, listCandidates(concurrent.getDecisionId()));
            }
            throw new BusinessException(ErrorCode.DATA_CONFLICT,
                    "idempotency key is already in use");
        }
    }

    public InitialRouteDecisionResponse getDecision(String decisionId) {
        Long currentUserId = currentUserService.getCurrentUser().getId();
        InitialRouteDecision decision = decisionMapper.selectOne(
                new LambdaQueryWrapper<InitialRouteDecision>()
                        .eq(InitialRouteDecision::getDecisionId, decisionId)
                        .eq(InitialRouteDecision::getCreatedBy, currentUserId));
        if (decision == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                    "initial route decision not found");
        }
        expireIfNecessary(decision);
        return toResponse(decision, listCandidates(decisionId));
    }

    private List<InitialRouteCandidate> toEntities(
            String decisionId,
            List<InitialRouteScoringService.ScoredInitialRoute> scored,
            WeatherSnapshot weather,
            Map<String, List<String>> reasonsByRouteId,
            LocalDateTime createdAt,
            int candidateCount) {
        List<InitialRouteCandidate> candidates = new ArrayList<>();
        for (InitialRouteScoringService.ScoredInitialRoute route : scored) {
            InitialRouteCandidate candidate = new InitialRouteCandidate();
            candidate.setDecisionId(decisionId);
            candidate.setPreviewRouteId(route.previewRouteId());
            candidate.setDisplayName(displayName(route.rank(), candidateCount));
            candidate.setProvider(PROVIDER);
            candidate.setCoordinateSystem(COORDINATE_SYSTEM);
            candidate.setDistanceMeters(route.route().distanceMeters());
            candidate.setDurationSeconds(route.route().referenceDuration().toSeconds());
            candidate.setTrafficLevel(route.trafficLevel().name());
            candidate.setTrafficSnapshot(writeJson(trafficOrUnknown(
                    route.route().trafficSnapshot())));
            candidate.setWeatherSnapshot(writeJson(weather));
            candidate.setPoints(writeJson(toPoints(route.route().polyline())));
            candidate.setRankNo(route.rank());
            candidate.setTotalScore(route.totalScore());
            candidate.setScoreDetails(writeJson(route.scoreDetails()));
            candidate.setReasons(writeJson(reasonsByRouteId.getOrDefault(
                    route.previewRouteId(), route.reasons())));
            candidate.setCreatedAt(createdAt);
            candidates.add(candidate);
        }
        return List.copyOf(candidates);
    }

    private WeatherSnapshot destinationWeather(double longitude, double latitude) {
        try {
            return weatherProvider.getCurrentWeather(longitude, latitude);
        } catch (WeatherProviderException exception) {
            return new WeatherSnapshot(
                    "RULE_FALLBACK", "UNKNOWN", "", "", "UNKNOWN",
                    BigDecimal.ZERO, 0, "UNKNOWN", "UNKNOWN",
                    OffsetDateTime.now(API_TIME_ZONE));
        }
    }

    private TrafficSnapshot trafficOrUnknown(TrafficSnapshot traffic) {
        return traffic == null
                ? new TrafficSnapshot("RULE_FALLBACK", "UNKNOWN", false,
                0, 0, 0, 0, 0, 0)
                : traffic;
    }

    private List<List<Double>> toPoints(List<EtaCoordinate> polyline) {
        return polyline.stream()
                .map(point -> List.of(point.longitude(), point.latitude()))
                .toList();
    }

    private InitialRouteDecisionResponse toResponse(
            InitialRouteDecision decision,
            List<InitialRouteCandidate> candidates) {
        DegradationMetadata degradation = degradationMetadata(decision, candidates);
        return new InitialRouteDecisionResponse(
                decision.getDecisionId(),
                parseStatus(decision.getStatus()),
                InitialRoutePlanningMode.valueOf(decision.getPlanningMode()),
                InitialRoutePlanningResult.valueOf(decision.getPlanningResult()),
                candidates.size(), degradation.degraded(), degradation.reason(),
                degradation.message(),
                readJson(decision.getStartSnapshot(), InitialRouteLocationSnapshot.class),
                readJson(decision.getDestinationSnapshot(),
                        InitialRouteLocationSnapshot.class),
                decision.getRecommendedRouteId(), decision.getSelectedRouteId(),
                decision.getScoringRuleVersion(),
                RecommendationSource.valueOf(decision.getRecommendationSource()),
                toOffsetDateTime(decision.getCalculatedAt()),
                toOffsetDateTime(decision.getExpiresAt()),
                toOffsetDateTime(decision.getConfirmedAt()),
                decision.getTaskId(),
                readJson(decision.getWeatherSnapshot(), WeatherSnapshot.class),
                candidates.stream().map(this::toCandidateResponse).toList(),
                decision.getExplanation());
    }

    private InitialRouteCandidateResponse toCandidateResponse(
            InitialRouteCandidate candidate) {
        TrafficSnapshot traffic = readJson(
                candidate.getTrafficSnapshot(), TrafficSnapshot.class);
        return new InitialRouteCandidateResponse(
                candidate.getPreviewRouteId(), candidate.getDisplayName(),
                candidate.getRankNo(), candidate.getTotalScore(),
                candidate.getDistanceMeters(), candidate.getDurationSeconds(),
                TrafficLevel.valueOf(candidate.getTrafficLevel()),
                traffic.source(), candidate.getProvider(), candidate.getCoordinateSystem(),
                readJson(candidate.getPoints(), new TypeReference<>() {}),
                traffic,
                readJson(candidate.getWeatherSnapshot(), WeatherSnapshot.class),
                readJson(candidate.getScoreDetails(), InitialRouteScoreDetails.class),
                readJson(candidate.getReasons(), new TypeReference<>() {}));
    }

    private List<InitialRouteCandidate> listCandidates(String decisionId) {
        return candidateMapper.selectList(
                new LambdaQueryWrapper<InitialRouteCandidate>()
                        .eq(InitialRouteCandidate::getDecisionId, decisionId)
                        .orderByAsc(InitialRouteCandidate::getRankNo));
    }

    private void expireIfNecessary(InitialRouteDecision decision) {
        if (parseStatus(decision.getStatus()) != InitialRouteDecisionStatus.PENDING
                || decision.getExpiresAt().isAfter(LocalDateTime.now(API_TIME_ZONE))) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(API_TIME_ZONE);
        decisionMapper.update(null, new LambdaUpdateWrapper<InitialRouteDecision>()
                .eq(InitialRouteDecision::getId, decision.getId())
                .eq(InitialRouteDecision::getStatus,
                        InitialRouteDecisionStatus.PENDING.name())
                .set(InitialRouteDecision::getStatus,
                        InitialRouteDecisionStatus.EXPIRED.name())
                .set(InitialRouteDecision::getUpdatedAt, now));
        decision.setStatus(InitialRouteDecisionStatus.EXPIRED.name());
        decision.setUpdatedAt(now);
    }

    private InitialRouteDecision findByIdempotencyKey(String idempotencyKey) {
        return decisionMapper.selectOne(new LambdaQueryWrapper<InitialRouteDecision>()
                .eq(InitialRouteDecision::getIdempotencyKey, idempotencyKey));
    }

    private Warehouse validatedWarehouse(Warehouse warehouse) {
        if (!StringUtils.hasText(warehouse.getAddress())
                || warehouse.getLongitude() == null || warehouse.getLatitude() == null
                || warehouse.getLongitude() < -180 || warehouse.getLongitude() > 180
                || warehouse.getLatitude() < -90 || warehouse.getLatitude() > 90) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "warehouse route snapshot is incomplete");
        }
        return warehouse;
    }

    private void requireDifferentCoordinates(double startLongitude, double startLatitude,
                                             double endLongitude, double endLatitude) {
        if (Math.abs(startLongitude - endLongitude) < 0.000001
                && Math.abs(startLatitude - endLatitude) < 0.000001) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "route start and destination must be different");
        }
    }

    private void requireOwned(InitialRouteDecision decision, Long currentUserId) {
        if (!Objects.equals(decision.getCreatedBy(), currentUserId)) {
            throw new BusinessException(ErrorCode.DATA_CONFLICT,
                    "idempotency key belongs to another user");
        }
    }

    private String normalizeIdempotencyKey(String value) {
        if (!StringUtils.hasText(value) || value.trim().length() > 128) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "Idempotency-Key must contain 1 to 128 characters");
        }
        return value.trim();
    }

    private Map<String, Object> inputSnapshot(
            Warehouse warehouse,
            InitialRouteDecisionCreateRequest request,
            InitialRouteLocationSnapshot start,
            InitialRouteLocationSnapshot destination,
            InitialRouteCandidateGenerator.GenerationResult generation) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("originWarehouseId", warehouse.getId());
        snapshot.put("requestedCandidateCount", request.getCandidateCount());
        snapshot.put("candidateCount", generation.routes().size());
        snapshot.put("planningMode", request.getPlanningMode());
        snapshot.put("start", start);
        snapshot.put("destination", destination);
        snapshot.put("degraded", generation.degraded());
        if (generation.degraded()) {
            snapshot.put("degradedReason", generation.degradationReason().name());
            snapshot.put("degradedMessage", generation.degradationReason().message());
        }
        return snapshot;
    }

    private InitialRouteExplanationPort.ExplanationResult singleRouteExplanation(
            InitialRouteScoringService.ScoredInitialRoute route,
            InitialRouteDegradationReason reason) {
        return new InitialRouteExplanationPort.ExplanationResult(
                RecommendationSource.SINGLE_ROUTE,
                reason.message(),
                Map.of(route.previewRouteId(), List.of(reason.message())));
    }

    private DegradationMetadata degradationMetadata(
            InitialRouteDecision decision,
            List<InitialRouteCandidate> candidates) {
        if (candidates.size() != 1) {
            return new DegradationMetadata(false, null, null);
        }
        InitialRouteDegradationReason reason =
                InitialRouteDegradationReason.ROUTE_PROVIDER_SINGLE_RESULT;
        String message = reason.message();
        try {
            JsonNode input = objectMapper.readTree(decision.getInputSnapshot());
            String storedReason = input.path("degradedReason").asText();
            if (StringUtils.hasText(storedReason)) {
                reason = InitialRouteDegradationReason.valueOf(storedReason);
            }
            String storedMessage = input.path("degradedMessage").asText();
            message = StringUtils.hasText(storedMessage)
                    ? storedMessage : reason.message();
        } catch (JsonProcessingException | IllegalArgumentException
                 | NullPointerException ignored) {
            // Legacy single-route decisions did not persist degradation metadata.
        }
        return new DegradationMetadata(true, reason, message);
    }

    private String displayName(int rank, int candidateCount) {
        return candidateCount == 1
                ? "规划路线"
                : "候选路线 " + (char) ('A' + rank - 1);
    }

    private InitialRouteDecisionStatus parseStatus(String value) {
        try {
            return InitialRouteDecisionStatus.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "invalid initial route decision status in database");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "failed to serialize initial route decision snapshot");
        }
    }

    private <T> T readJson(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "invalid initial route decision snapshot in database");
        }
    }

    private <T> T readJson(String value, TypeReference<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "invalid initial route decision snapshot in database");
        }
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        return value == null ? null : value.atZone(API_TIME_ZONE).toOffsetDateTime();
    }

    private record DegradationMetadata(boolean degraded,
                                       InitialRouteDegradationReason reason,
                                       String message) {
    }
}
