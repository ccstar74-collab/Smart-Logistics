package com.smartlogistics.agent;

import java.util.List;
import java.util.Map;

public final class BackendWeatherTrafficCompatibilitySelfTest {
    public static void main(String[] args) {
        InitialRouteRecommendationService service=new InitialRouteRecommendationService();
        Map<String,Object> weather=Map.of("source","AMAP_WEATHER_V3","city","重庆市","weather","阴",
                "temperature","29","humidity","72","windDirection","东南","windPower","≤3",
                "reportTime","2026-08-31T16:00:00+08:00");
        Map<String,Object> smooth=Map.of("source","AMAP_DRIVING_V3","restriction",false,"trafficLights",8,
                "unknownDistanceMeters",100,"smoothDistanceMeters",13000,"slowDistanceMeters",500,
                "congestedDistanceMeters",300,"severeCongestedDistanceMeters",100);
        Map<String,Object> congested=Map.of("source","AMAP_DRIVING_V3","restriction",false,"trafficLights",12,
                "unknownDistanceMeters",170,"smoothDistanceMeters",6500,"slowDistanceMeters",2500,
                "congestedDistanceMeters",3000,"severeCongestedDistanceMeters",1200);
        Map<String,Object> request=Map.of("decisionId","backend-shape-test","scenario","INITIAL_ROUTE_SELECTION",
                "weather",weather,"routes",List.of(
                        Map.of("routeId","route_smooth","displayName","畅通路线","distanceMeters",14000,
                                "referenceDurationSeconds",2200,"traffic",smooth),
                        Map.of("routeId","route_congested","displayName","拥堵路线","distanceMeters",13376,
                                "referenceDurationSeconds",2306,"traffic",congested)));
        Map<String,Object> result=service.score(request);List<?> routes=(List<?>)result.get("routes");
        ok(routes.size()==2,"路线数量错误");
        for(Object value:routes){Map<?,?> route=(Map<?,?>)value;ok(Boolean.TRUE.equals(route.get("trafficDataAvailable")),"未识别 AMAP_DRIVING_V3");ok(Boolean.TRUE.equals(route.get("weatherDataAvailable")),"未识别 AMAP_WEATHER_V3");Map<?,?> scores=(Map<?,?>)route.get("scoreDetails");ok(scores.get("traffic") instanceof Number,"路况分缺失");ok(scores.get("weather") instanceof Number,"天气分缺失");}
        Map<?,?> first=(Map<?,?>)routes.get(0);ok("route_smooth".equals(first.get("routeId")),"路况更优路线未获推荐");
        ok(String.valueOf(first.get("trafficText")).contains("缓行及拥堵"),"路况解释未使用分段快照");
        ok(String.valueOf(first.get("weatherText")).contains("重庆市：阴"),"天气解释未使用高德事实");
        System.out.println("后端高德天气与路况结构兼容自检通过（8/8）");
    }
    private static void ok(boolean value,String message){if(!value)throw new AssertionError(message);}
}
