package com.smart_logistics.backend.service.route;

import com.smart_logistics.backend.dto.WeatherSnapshot;
import com.smart_logistics.backend.enums.TrafficLevel;
import com.smart_logistics.backend.service.eta.EtaCoordinate;
import com.smart_logistics.backend.service.eta.EtaPlannedRoute;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InitialRouteScoringServiceTest {

    private final InitialRouteScoringService service =
            new InitialRouteScoringService();

    @Test
    void appliesFixedFortyTwentyThirtyTenWeightsAndRanksDeterministically() {
        List<InitialRouteScoringService.ScoredInitialRoute> result = service.score(
                List.of(candidate("route-b", 1_500, 200, TrafficLevel.SEVERE),
                        candidate("route-a", 1_000, 100, TrafficLevel.FREE_FLOW)),
                weather("晴", "2"));

        assertEquals("route-a", result.get(0).previewRouteId());
        assertEquals(1, result.get(0).rank());
        assertEquals(new BigDecimal("100.00"), result.get(0).totalScore());
        assertEquals(new BigDecimal("49.33"), result.get(1).totalScore());
        assertEquals(new BigDecimal("50.00"),
                result.get(1).scoreDetails().time());
        assertEquals(new BigDecimal("66.67"),
                result.get(1).scoreDetails().distance());
        assertEquals(new BigDecimal("20.00"),
                result.get(1).scoreDetails().traffic());
        assertEquals(new BigDecimal("100.00"),
                result.get(1).scoreDetails().weather());
    }

    @Test
    void lightFogUsesLightFogBandAndStrongWindAppliesCorrection() {
        InitialRouteScoringService.ScoredInitialRoute result = service.score(
                List.of(candidate("route-a", 1_000, 100, TrafficLevel.FREE_FLOW)),
                weather("轻雾", "6级")).getFirst();

        assertEquals(new BigDecimal("75.00"),
                result.scoreDetails().weather());
        assertEquals(new BigDecimal("97.50"), result.totalScore());
    }

    @Test
    void tiesUseDurationThenDistanceThenRouteId() {
        List<InitialRouteScoringService.ScoredInitialRoute> result = service.score(
                List.of(candidate("route-z", 1_000, 100, TrafficLevel.FREE_FLOW),
                        candidate("route-a", 1_000, 100, TrafficLevel.FREE_FLOW)),
                weather("晴", "1"));

        assertEquals(List.of("route-a", "route-z"), result.stream()
                .map(InitialRouteScoringService.ScoredInitialRoute::previewRouteId)
                .toList());
    }

    private InitialRouteCandidateGenerator.GeneratedInitialRoute candidate(
            String id, long distance, long seconds, TrafficLevel level) {
        return new InitialRouteCandidateGenerator.GeneratedInitialRoute(
                id,
                new EtaPlannedRoute(List.of(
                        new EtaCoordinate(106.50, 29.50),
                        new EtaCoordinate(106.60, 29.60)),
                        distance, Duration.ofSeconds(seconds)),
                level);
    }

    private WeatherSnapshot weather(String description, String windPower) {
        return new WeatherSnapshot(
                "AMAP_WEATHER_V3", "500000", "重庆", "重庆市", description,
                new BigDecimal("25"), 60, "东", windPower,
                OffsetDateTime.parse("2026-08-31T10:00:00+08:00"));
    }
}
