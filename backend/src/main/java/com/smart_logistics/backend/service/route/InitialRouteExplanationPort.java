package com.smart_logistics.backend.service.route;

import com.smart_logistics.backend.dto.WeatherSnapshot;
import com.smart_logistics.backend.enums.RecommendationSource;

import java.util.List;
import java.util.Map;

public interface InitialRouteExplanationPort {

    ExplanationResult explain(ExplanationRequest request);

    record ExplanationRequest(String requestId,
                              String planningMode,
                              String scoringRuleVersion,
                              WeatherSnapshot weather,
                              List<InitialRouteScoringService.ScoredInitialRoute> routes) {
        public ExplanationRequest {
            routes = List.copyOf(routes);
        }
    }

    record ExplanationResult(RecommendationSource source,
                             String explanation,
                             Map<String, List<String>> reasonsByRouteId) {
        public ExplanationResult {
            reasonsByRouteId = Map.copyOf(reasonsByRouteId);
        }
    }
}
