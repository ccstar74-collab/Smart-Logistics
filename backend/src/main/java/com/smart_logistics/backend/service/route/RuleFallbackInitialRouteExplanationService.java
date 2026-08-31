package com.smart_logistics.backend.service.route;

import com.smart_logistics.backend.enums.RecommendationSource;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RuleFallbackInitialRouteExplanationService
        implements InitialRouteExplanationPort {

    @Override
    public ExplanationResult explain(ExplanationRequest request) {
        InitialRouteScoringService.ScoredInitialRoute recommended =
                request.routes().getFirst();
        String explanation = "综合评分推荐" + displayName(recommended.rank()) + "。"
                + String.join("，", recommended.reasons()) + "。";
        Map<String, java.util.List<String>> reasons = request.routes().stream()
                .collect(Collectors.toUnmodifiableMap(
                        InitialRouteScoringService.ScoredInitialRoute::previewRouteId,
                        InitialRouteScoringService.ScoredInitialRoute::reasons));
        return new ExplanationResult(
                RecommendationSource.RULE_FALLBACK, explanation, reasons);
    }

    private String displayName(int rank) {
        return "候选路线 " + (char) ('A' + rank - 1);
    }
}
