package com.smartlogistics.agent;

import java.util.List;
import java.util.Map;

public final class RouteRecommendationSelfTest {
    public static void main(String[] args) throws Exception {
        ModelClient disabled = new ModelClient() {
            public String answer(String instructions, String input) { throw new AssertionError("禁用模型不应被调用"); }
            public boolean enabled() { return false; }
        };
        RouteRecommendationService service = new RouteRecommendationService(disabled);
        Map<String,Object> smooth = Map.of("source","AMAP_DRIVING_V3","restriction",false,"trafficLights",5,
                "unknownDistanceMeters",0,"smoothDistanceMeters",9900,"slowDistanceMeters",100,
                "congestedDistanceMeters",0,"severeCongestedDistanceMeters",0);
        Map<String,Object> congested = Map.of("source","AMAP_DRIVING_V3","restriction",false,"trafficLights",20,
                "unknownDistanceMeters",0,"smoothDistanceMeters",5000,"slowDistanceMeters",1500,
                "congestedDistanceMeters",2500,"severeCongestedDistanceMeters",1000);
        Map<String,Object> request = Map.of(
                "recommendationId","rr-test-1",
                "businessContext",Map.of("cargo",Map.of("name","冷链生鲜","category","FRESH_FOOD"),
                        "preferences",Map.of("priority","SAFEST")),
                "weather",Map.of("source","AMAP_WEATHER_V3","city","雨花区","weather","中雨",
                        "humidity",90,"windPower","5","reportTime","2026-08-31T10:34:12+08:00"),
                "candidates",List.of(
                        Map.of("routeId","route_fast_congested","distanceMeters",9000,"referenceDurationSeconds",1200,"traffic",congested),
                        Map.of("routeId","route_safe","distanceMeters",10000,"referenceDurationSeconds",1400,"traffic",smooth),
                        Map.of("routeId","route_no_snapshot","distanceMeters",11000,"referenceDurationSeconds",1500)
                ));
        Map<String,Object> result = service.score(request);
        ok("route_safe".equals(result.get("recommendedRouteId")),"安全优先排序错误");
        ok("SAFEST".equals(result.get("priority")),"优先级错误");
        ok("DETERMINISTIC_FALLBACK".equals(result.get("analysisMode")),"降级模式错误");
        List<?> candidates = (List<?>) result.get("candidates");
        Map<?,?> first = (Map<?,?>) candidates.get(0);
        ok(((Number)first.get("rank")).intValue()==1&&((Number)first.get("score")).doubleValue()>0,"评分结果错误");
        Map<?,?> noSnapshot = (Map<?,?>) candidates.stream().map(Map.class::cast)
                .filter(item -> "route_no_snapshot".equals(item.get("routeId"))).findFirst().orElseThrow();
        ok(Boolean.FALSE.equals(noSnapshot.get("trafficSnapshotAvailable")),"缺路况标记错误");
        try {
            service.score(Map.of("candidates",List.of(Map.of("routeId","one","distanceMeters",1,"referenceDurationSeconds",1))));
            throw new AssertionError("单候选未被拒绝");
        } catch (IllegalArgumentException expected) { }
        System.out.println("多目标路线评分自检通过（5/5）");
    }
    private static void ok(boolean value,String message){if(!value)throw new AssertionError(message);}
}
