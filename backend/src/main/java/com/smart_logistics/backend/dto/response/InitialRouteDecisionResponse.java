package com.smart_logistics.backend.dto.response;

import com.smart_logistics.backend.dto.InitialRouteLocationSnapshot;
import com.smart_logistics.backend.dto.WeatherSnapshot;
import com.smart_logistics.backend.enums.InitialRouteDecisionStatus;
import com.smart_logistics.backend.enums.InitialRouteDegradationReason;
import com.smart_logistics.backend.enums.InitialRoutePlanningMode;
import com.smart_logistics.backend.enums.InitialRoutePlanningResult;
import com.smart_logistics.backend.enums.RecommendationSource;

import java.time.OffsetDateTime;
import java.util.List;

public record InitialRouteDecisionResponse(String decisionId,
                                           InitialRouteDecisionStatus status,
                                           InitialRoutePlanningMode planningMode,
                                           InitialRoutePlanningResult planningResult,
                                           int candidateCount,
                                           boolean degraded,
                                           InitialRouteDegradationReason degradedReason,
                                           String degradedMessage,
                                           InitialRouteLocationSnapshot start,
                                           InitialRouteLocationSnapshot destination,
                                           String recommendedRouteId,
                                           String selectedRouteId,
                                           String scoringRuleVersion,
                                           RecommendationSource recommendationSource,
                                           OffsetDateTime calculatedAt,
                                           OffsetDateTime expiresAt,
                                           OffsetDateTime confirmedAt,
                                           Long taskId,
                                           WeatherSnapshot weatherSnapshot,
                                           List<InitialRouteCandidateResponse> routes,
                                           String explanation) {

    public InitialRouteDecisionResponse {
        routes = List.copyOf(routes);
    }
}
