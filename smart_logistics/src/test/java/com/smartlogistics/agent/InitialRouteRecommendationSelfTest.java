package com.smartlogistics.agent;

import java.util.List;
import java.util.Map;

public final class InitialRouteRecommendationSelfTest {
    public static void main(String[] args) {
        InitialRouteRecommendationService service = new InitialRouteRecommendationService();
        Map<String,Object> request = Map.of(
                "decisionId", "ird_test_001", "scenario", "INITIAL_ROUTE_SELECTION",
                "scoringConfig", Map.of("timeWeight", .4, "distanceWeight", .2, "trafficWeight", .3, "weatherWeight", .1),
                "routes", List.of(
                        Map.of("routeId", "route_a", "displayName", "候选路线 A", "distanceMeters", 10000,
                                "referenceDurationSeconds", 1200, "trafficLevel", "SMOOTH",
                                "traffic", Map.of("congestionRatio", .03, "description", "大部分路段通行正常"),
                                "weather", Map.of("level", "NORMAL", "description", "沿途天气稳定", "riskEvents", List.of())),
                        Map.of("routeId", "route_b", "displayName", "候选路线 B", "distanceMeters", 11000,
                                "referenceDurationSeconds", 1260, "trafficLevel", "SLOW",
                                "traffic", Map.of("congestionRatio", .18, "description", "部分路段缓行"),
                                "weather", Map.of("level", "NOTICE", "description", "局部阵雨", "riskEvents", List.of("RAIN"))),
                        Map.of("routeId", "route_unknown", "displayName", "候选路线 C", "distanceMeters", 10500,
                                "referenceDurationSeconds", 1300, "trafficLevel", "UNKNOWN")));
        Map<String,Object> result = service.score(request);
        ok("ird_test_001".equals(result.get("decisionId")), "decisionId 未原样返回");
        ok("route_a".equals(result.get("recommendedRouteId")), "推荐路线错误");
        ok(InitialRouteRecommendationService.VERSION.equals(result.get("scoringRuleVersion")), "规则版本错误");
        List<?> routes = (List<?>) result.get("routes");
        ok(routes.size() == 3, "未返回全部路线");
        for (int i=0;i<routes.size();i++) ok(((Number)((Map<?,?>)routes.get(i)).get("rank")).intValue()==i+1, "排名不连续");
        Map<?,?> unknown = routes.stream().map(Map.class::cast).filter(x -> "route_unknown".equals(x.get("routeId"))).findFirst().orElseThrow();
        ok(Boolean.FALSE.equals(unknown.get("trafficDataAvailable")), "未知路况标记错误");
        ok(Boolean.FALSE.equals(unknown.get("weatherDataAvailable")), "未知天气标记错误");
        Map<?,?> details=(Map<?,?>)unknown.get("scoreDetails"),effective=(Map<?,?>)unknown.get("effectiveWeights");
        ok(details.get("traffic")==null&&details.get("weather")==null, "未知维度不应伪造分数");
        ok(((Number)effective.get("traffic")).doubleValue()==0&&((Number)effective.get("weather")).doubleValue()==0, "缺失维度权重未归零");
        Map<String,Object> repeated=service.score(request);
        ok(Json.stringify(result.get("routes")).equals(Json.stringify(repeated.get("routes"))), "相同输入评分不稳定");
        try { service.score(Map.of("decisionId","bad","routes",List.of(Map.of("routeId","one")))); throw new AssertionError("单候选未拒绝"); }
        catch (IllegalArgumentException expected) { }
        System.out.println("初始多路线评分与推荐解释自检通过（10/10）");
    }
    private static void ok(boolean value,String message){if(!value)throw new AssertionError(message);}
}
