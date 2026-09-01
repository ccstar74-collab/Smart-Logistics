package com.smartlogistics.agent;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Deterministic multi-objective route scoring with optional model-generated explanation. */
final class RouteRecommendationService {
    private static final String VERSION = "route-score-v1";
    private final ModelClient model;

    RouteRecommendationService(ModelClient model) { this.model = model; }

    Map<String,Object> score(Map<String,Object> request) throws IOException {
        String recommendationId = text(request.get("recommendationId"));
        Map<String,Object> context = map(request.get("businessContext"));
        Map<String,Object> weather = firstMap(request.get("weather"), request.get("destinationWeather"),
                context == null ? null : context.get("weather"));
        List<Map<String,Object>> candidates = maps(request.get("candidates"));
        if (candidates.size() < 2 || candidates.size() > 10) throw new IllegalArgumentException("candidates 必须包含 2 至 10 条候选路线");

        String priority = priority(context, request);
        Map<String,Double> weights = weights(priority);
        double minDuration = Double.MAX_VALUE, minDistance = Double.MAX_VALUE;
        Set<String> ids = new LinkedHashSet<>();
        for (Map<String,Object> candidate : candidates) {
            String id = candidateId(candidate);
            if (id.isEmpty()) throw new IllegalArgumentException("候选路线缺少 routeId 或 candidateId");
            if (!ids.add(id)) throw new IllegalArgumentException("候选路线 ID 重复：" + id);
            double duration = positive(candidate.get("referenceDurationSeconds"));
            double distance = positive(candidate.get("distanceMeters"));
            if (!Double.isFinite(duration) || !Double.isFinite(distance)) throw new IllegalArgumentException("路线 " + id + " 的距离或参考时长无效");
            minDuration = Math.min(minDuration, duration); minDistance = Math.min(minDistance, distance);
        }

        double weatherScore = weatherScore(weather);
        List<Scored> scored = new ArrayList<>();
        for (Map<String,Object> candidate : candidates) {
            String id = candidateId(candidate);
            double duration = positive(candidate.get("referenceDurationSeconds"));
            double distance = positive(candidate.get("distanceMeters"));
            Map<String,Object> traffic = map(candidate.get("traffic"));
            double timeScore = clamp(100.0 * minDuration / duration);
            double distanceScore = clamp(100.0 * minDistance / distance);
            TrafficEvaluation trafficEvaluation = trafficScore(traffic, distance);
            double safetyScore = clamp(trafficEvaluation.score * 0.60 + weatherScore * 0.40 - (trafficEvaluation.restriction ? 12 : 0));
            double total = timeScore * weights.get("time") + distanceScore * weights.get("distance")
                    + trafficEvaluation.score * weights.get("traffic") + weatherScore * weights.get("weather")
                    + safetyScore * weights.get("safety");
            List<String> reasons = reasons(duration,minDuration,distance,minDistance,trafficEvaluation,weather,weatherScore);
            List<String> warnings = warnings(trafficEvaluation,weather,weatherScore);
            Map<String,Object> components = new LinkedHashMap<>();
            components.put("time",round(timeScore)); components.put("distance",round(distanceScore));
            components.put("traffic",round(trafficEvaluation.score)); components.put("weather",round(weatherScore));
            components.put("safety",round(safetyScore));
            scored.add(new Scored(id,round(total),duration,distance,components,reasons,warnings,traffic==null));
        }
        scored.sort(Comparator.comparingDouble((Scored item)->item.score).reversed().thenComparingDouble(item->item.duration).thenComparing(item->item.id));

        List<Object> results = new ArrayList<>();
        for(int i=0;i<scored.size();i++){
            Scored item=scored.get(i); Map<String,Object> result=new LinkedHashMap<>();
            result.put("routeId",item.id); result.put("candidateId",item.id); result.put("rank",i+1);
            result.put("eligible",true); result.put("score",item.score); result.put("label",label(item,i,minDuration,minDistance));
            result.put("componentScores",item.components); result.put("reasons",item.reasons); result.put("warnings",item.warnings);
            result.put("trafficSnapshotAvailable",!item.trafficMissing); results.add(result);
        }

        Scored best=scored.get(0); Map<String,Object> response=new LinkedHashMap<>();
        if(!recommendationId.isEmpty())response.put("recommendationId",recommendationId);
        response.put("algorithmVersion",VERSION); response.put("priority",priority); response.put("weights",decimalWeights(weights));
        response.put("recommendedRouteId",best.id); response.put("recommendedCandidateId",best.id);
        response.put("candidates",results); response.put("weatherSummary",weatherSummary(weather,weatherScore));
        String deterministic=deterministicSummary(best,priority), summary=deterministic, mode="DETERMINISTIC_FALLBACK";
        if(model.enabled())try{summary=modelSummary(context,weather,response,deterministic);mode="MODEL_AUGMENTED";}catch(Exception ignored){}
        response.put("analysisMode",mode); response.put("summary",summary); return response;
    }

    private String modelSummary(Map<String,Object> context,Map<String,Object> weather,Map<String,Object> scoring,String fallback)throws IOException{
        Map<String,Object> compact=new LinkedHashMap<>(); compact.put("businessContext",context); compact.put("weather",weather);
        compact.put("recommendedRouteId",scoring.get("recommendedRouteId")); compact.put("priority",scoring.get("priority")); compact.put("candidates",scoring.get("candidates"));
        String instructions="你是物流路线推荐解释器。只能根据输入JSON中的事实，用中文输出一段不超过180字的推荐说明。必须说明推荐路线、主要优势和一个必要风险；禁止改分、改排名、虚构事故、封路、天气或耗时；不要输出Markdown。";
        String answer=text(model.answer(instructions,Json.stringify(compact))).replaceAll("\\s+"," ");
        if(answer.isEmpty())return fallback; return answer.length()>220?answer.substring(0,220):answer;
    }

    private static String deterministicSummary(Scored best,String priority){
        StringBuilder out=new StringBuilder("推荐路线 ").append(best.id).append("，综合评分 ").append(best.score).append(" 分，当前采用").append(priorityName(priority)).append("权重。");
        if(!best.reasons.isEmpty())out.append(best.reasons.get(0)).append("。"); if(!best.warnings.isEmpty())out.append("注意：").append(best.warnings.get(0)).append("。"); return out.toString();
    }
    private static Map<String,Object> weatherSummary(Map<String,Object> weather,double score){Map<String,Object> out=new LinkedHashMap<>();out.put("available",weather!=null);out.put("score",round(score));if(weather!=null){for(String key:new String[]{"source","province","city","weather","temperature","humidity","windDirection","windPower","reportTime"})put(out,key,weather.get(key));}return out;}
    private static List<String> reasons(double duration,double minDuration,double distance,double minDistance,TrafficEvaluation traffic,Map<String,Object> weather,double weatherScore){List<String> out=new ArrayList<>();if(duration<=minDuration*1.001)out.add("参考耗时最短");if(distance<=minDistance*1.001)out.add("总里程最短");if(!traffic.missing&&traffic.affectedRatio<.05)out.add("缓行及拥堵路段占比较低");if(weather!=null&&weatherScore>=80)out.add("目的地天气风险较低");if(out.isEmpty())out.add("时效、里程、路况与天气表现较均衡");return out;}
    private static List<String> warnings(TrafficEvaluation traffic,Map<String,Object> weather,double weatherScore){List<String> out=new ArrayList<>();if(traffic.missing)out.add("暂无该路线生成时的路况快照");else{if(traffic.restriction)out.add("路线快照包含限行策略标记，请调度员复核车辆适用性");if(traffic.severeMeters>0)out.add("存在 "+km(traffic.severeMeters)+" 公里严重拥堵路段");else if(traffic.congestedMeters>0)out.add("存在 "+km(traffic.congestedMeters)+" 公里拥堵路段");if(traffic.unknownRatio>.15)out.add("部分路段缺少可靠路况数据");}if(weather==null)out.add("目的地天气数据暂不可用");else if(weatherScore<65)out.add("目的地天气为“"+text(weather.get("weather"))+"”，需关注运输安全");return out;}

    private static TrafficEvaluation trafficScore(Map<String,Object> traffic,double routeDistance){
        if(traffic==null)return new TrafficEvaluation(55,true,false,0,0,0,0);
        double unknown=nonNegative(traffic.get("unknownDistanceMeters")),smooth=nonNegative(traffic.get("smoothDistanceMeters")),slow=nonNegative(traffic.get("slowDistanceMeters")),congested=nonNegative(traffic.get("congestedDistanceMeters")),severe=nonNegative(traffic.get("severeCongestedDistanceMeters"));
        double observed=unknown+smooth+slow+congested+severe, denominator=Math.max(1,observed>0?observed:routeDistance);
        double weightedRisk=(unknown*.25+slow*.35+congested*.75+severe)/denominator,lights=nonNegative(traffic.get("trafficLights")),lightsPerKm=lights/Math.max(1,routeDistance/1000.0);boolean restriction=Boolean.TRUE.equals(traffic.get("restriction"));
        double score=clamp(100-weightedRisk*100-Math.min(15,lightsPerKm*3)-(restriction?8:0));
        return new TrafficEvaluation(score,false,restriction,(slow+congested+severe)/denominator,unknown/denominator,congested,severe);
    }
    private static double weatherScore(Map<String,Object> weather){if(weather==null)return 65;String condition=text(weather.get("weather"));double risk=5;if(contains(condition,"暴雨","特大暴雨","冰雹","冻雨"))risk=85;else if(contains(condition,"大雨","暴雪","大雪","沙尘暴"))risk=65;else if(contains(condition,"中雨","中雪","雾","霾"))risk=42;else if(contains(condition,"小雨","阵雨","雷阵雨","小雪"))risk=24;else if(condition.contains("阴"))risk=10;double humidity=nonNegative(weather.get("humidity"));if(humidity>=95)risk+=5;double wind=firstNumber(text(weather.get("windPower")));if(wind>=8)risk+=25;else if(wind>=6)risk+=15;else if(wind>=4)risk+=6;return clamp(100-risk);}
    private static String priority(Map<String,Object> context,Map<String,Object> request){Map<String,Object> preferences=context==null?null:map(context.get("preferences"));String priority=text(preferences==null?request.get("priority"):preferences.get("priority")).toUpperCase(Locale.ROOT);if(Set.of("BALANCED","FASTEST","SAFEST","ECONOMICAL").contains(priority))return priority;String cargoText=context==null?"":Json.stringify(context.get("cargo")).toUpperCase(Locale.ROOT);if(contains(cargoText,"FRESH","COLD","生鲜","冷链","冷藏","易腐"))return "SAFEST";return "BALANCED";}
    private static Map<String,Double> weights(String priority){Map<String,Double> out=new LinkedHashMap<>();switch(priority){case "FASTEST"->{out.put("time",.50);out.put("distance",.10);out.put("traffic",.20);out.put("weather",.05);out.put("safety",.15);}case "SAFEST"->{out.put("time",.20);out.put("distance",.10);out.put("traffic",.20);out.put("weather",.25);out.put("safety",.25);}case "ECONOMICAL"->{out.put("time",.20);out.put("distance",.40);out.put("traffic",.15);out.put("weather",.10);out.put("safety",.15);}default->{out.put("time",.30);out.put("distance",.15);out.put("traffic",.25);out.put("weather",.15);out.put("safety",.15);}}return out;}
    private static Map<String,Object> decimalWeights(Map<String,Double> weights){Map<String,Object> out=new LinkedHashMap<>();weights.forEach((key,value)->out.put(key,round(value)));return out;}
    private static String label(Scored item,int index,double minDuration,double minDistance){if(index==0)return "综合推荐";if(item.duration<=minDuration*1.001)return "最快路线";if(item.distance<=minDistance*1.001)return "最短路线";return "备选路线";}
    private static String candidateId(Map<String,Object> candidate){String id=text(candidate.get("routeId"));return id.isEmpty()?text(candidate.get("candidateId")):id;}
    private static String priorityName(String value){return switch(value){case "FASTEST"->"时效优先";case "SAFEST"->"安全优先";case "ECONOMICAL"->"经济优先";default->"综合均衡";};}
    private static String km(double meters){return BigDecimal.valueOf(meters/1000).setScale(1,RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();}
    private static boolean contains(String value,String...parts){for(String part:parts)if(value.contains(part))return true;return false;}
    private static double firstNumber(String value){java.util.regex.Matcher m=java.util.regex.Pattern.compile("\\d+(?:\\.\\d+)?").matcher(value);return m.find()?Double.parseDouble(m.group()):0;}
    private static double positive(Object value){double n=number(value);return Double.isFinite(n)&&n>0?n:Double.NaN;}
    private static double nonNegative(Object value){double n=number(value);return Double.isFinite(n)&&n>=0?n:0;}
    private static double number(Object value){try{return value instanceof Number?((Number)value).doubleValue():Double.parseDouble(String.valueOf(value));}catch(Exception e){return Double.NaN;}}
    private static double clamp(double value){return Math.max(0,Math.min(100,value));}
    private static double round(double value){return BigDecimal.valueOf(value).setScale(2,RoundingMode.HALF_UP).doubleValue();}
    private static String text(Object value){return value==null?"":String.valueOf(value).trim();}
    private static void put(Map<String,Object> out,String key,Object value){if(value!=null&&!text(value).isEmpty())out.put(key,value);}
    @SuppressWarnings("unchecked")private static Map<String,Object> map(Object value){return value instanceof Map?(Map<String,Object>)value:null;}
    private static Map<String,Object> firstMap(Object...values){for(Object value:values){Map<String,Object> map=map(value);if(map!=null)return map;}return null;}
    private static List<Map<String,Object>> maps(Object value){List<Map<String,Object>> out=new ArrayList<>();if(value instanceof List<?> list)for(Object item:list){Map<String,Object> map=map(item);if(map!=null)out.add(map);}return out;}
    private record TrafficEvaluation(double score,boolean missing,boolean restriction,double affectedRatio,double unknownRatio,double congestedMeters,double severeMeters){}
    private record Scored(String id,double score,double duration,double distance,Map<String,Object> components,List<String> reasons,List<String> warnings,boolean trafficMissing){}
}
