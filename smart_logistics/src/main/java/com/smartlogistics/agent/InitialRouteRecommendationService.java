package com.smartlogistics.agent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

/** 创建运输任务前的确定性多路线评分与可追溯解释。 */
final class InitialRouteRecommendationService {
    static final String VERSION = "agent-initial-route-score-v1";
    private static final Map<String,Double> DEFAULT = Map.of("time",.4,"distance",.2,"traffic",.3,"weather",.1);

    Map<String,Object> score(Map<String,Object> request) {
        String decisionId = required(request.get("decisionId"), "decisionId 不能为空");
        String scenario = text(request.get("scenario"));
        if (!scenario.isEmpty() && !"INITIAL_ROUTE_SELECTION".equals(scenario)) throw new IllegalArgumentException("scenario 必须为 INITIAL_ROUTE_SELECTION");
        List<Map<String,Object>> routes = maps(request.get("routes"));
        if (routes.size()<2 || routes.size()>10) throw new IllegalArgumentException("routes 必须包含 2 至 10 条候选路线");
        Map<String,Double> configured = configured(map(request.get("scoringConfig")));
        Map<String,Object> commonWeather = firstMap(request.get("weather"), request.get("destinationWeather"));
        Set<String> ids=new LinkedHashSet<>(); double minTime=Double.MAX_VALUE,minDistance=Double.MAX_VALUE;
        for(Map<String,Object> route:routes){
            String id=required(route.get("routeId"),"候选路线缺少 routeId");
            if(!ids.add(id))throw new IllegalArgumentException("routeId 重复："+id);
            double time=positive(route.get("referenceDurationSeconds")),distance=positive(route.get("distanceMeters"));
            if(!Double.isFinite(time)||!Double.isFinite(distance))throw new IllegalArgumentException("路线 "+id+" 的距离或参考时长无效");
            minTime=Math.min(minTime,time);minDistance=Math.min(minDistance,distance);
        }
        List<Item> items=new ArrayList<>();
        for(Map<String,Object> route:routes){
            String id=text(route.get("routeId")),name=text(route.get("displayName"));if(name.isEmpty())name=id;
            double time=positive(route.get("referenceDurationSeconds")),distance=positive(route.get("distanceMeters"));
            Fact traffic=traffic(route,distance),weather=weather(firstMap(route.get("weather"), commonWeather));
            Map<String,Double> effective=effective(configured,traffic.available,weather.available);
            double timeScore=clamp(minTime/time*100),distanceScore=clamp(minDistance/distance*100);
            double total=timeScore*effective.get("time")+distanceScore*effective.get("distance")
                    +(traffic.available?traffic.score*effective.get("traffic"):0)+(weather.available?weather.score*effective.get("weather"):0);
            items.add(new Item(id,name,time,distance,round(timeScore),round(distanceScore),traffic,weather,weights(effective),round(total)));
        }
        items.sort(Comparator.comparingDouble((Item x)->x.total).reversed().thenComparingDouble(x->x.time)
                .thenComparing(Comparator.comparingDouble((Item x)->x.traffic.score).reversed()).thenComparingDouble(x->x.distance)
                .thenComparing(Comparator.comparingDouble((Item x)->x.weather.score).reversed()).thenComparing(x->x.id));
        Item best=items.get(0),second=items.get(1);List<Object> results=new ArrayList<>();
        for(int i=0;i<items.size();i++)results.add(result(items.get(i),i+1,best,items.size(),minTime,minDistance));
        Map<String,Object> data=new LinkedHashMap<>();data.put("decisionId",decisionId);data.put("recommendedRouteId",best.id);
        data.put("scoringRuleVersion",VERSION);data.put("calculatedAt",OffsetDateTime.now(ZoneOffset.ofHours(8)).toString());
        data.put("configuredWeights",weights(configured));data.put("effectiveWeights",best.effective);
        data.put("summary",summary(best,second));data.put("highlights",highlights(best,items,minTime,minDistance));
        data.put("cautions",cautions(best));data.put("routes",results);return data;
    }

    private static Map<String,Object> result(Item x,int rank,Item best,int count,double minTime,double minDistance){
        Map<String,Object> scores=new LinkedHashMap<>();scores.put("time",x.timeScore);scores.put("distance",x.distanceScore);
        scores.put("traffic",x.traffic.available?round(x.traffic.score):null);scores.put("weather",x.weather.available?round(x.weather.score):null);
        Map<String,Object> dimensions=new LinkedHashMap<>();
        dimensions.put("time",x.time<=minTime*1.001?count+"条候选中预计用时最短":"预计比推荐路线多用约"+minutes(x.time-best.time)+"分钟");
        dimensions.put("distance",x.distance<=minDistance*1.001?count+"条候选中总里程最短":"比推荐路线多行驶约"+km(x.distance-best.distance)+"公里");
        dimensions.put("traffic",x.traffic.text);dimensions.put("weather",x.weather.text);
        Map<String,Object> out=new LinkedHashMap<>();out.put("routeId",x.id);out.put("displayName",x.name);out.put("rank",rank);
        out.put("totalScore",x.total);out.put("recommended",rank==1);out.put("scoreDetails",scores);out.put("effectiveWeights",x.effective);
        out.put("dimensionSummaries",dimensions);out.put("highlights",routeHighlights(x,best,minTime,minDistance));out.put("cautions",routeCautions(x));
        out.put("explanation",explanation(x,rank,best));out.put("trafficDataAvailable",x.traffic.available);out.put("weatherDataAvailable",x.weather.available);
        out.put("trafficText",x.traffic.text);out.put("weatherText",x.weather.text);return out;
    }

    private static String summary(Item best,Item second){double gap=round(best.total-second.total);StringBuilder s=new StringBuilder("综合推荐").append(best.name).append("，综合得分").append(fmt(best.total)).append("分。");
        s.append(gap<1?"前两条路线差异较小，人工选择不会造成明显影响。":"比第二名高"+fmt(gap)+"分。");
        if(!best.traffic.available)s.append("路况数据暂不完整，本次未计入路况因素。");if(!best.weather.available)s.append("天气数据暂不完整，本次未计入天气因素。");return s.toString();}
    private static List<String> highlights(Item best,List<Item> all,double minTime,double minDistance){List<String> out=new ArrayList<>();double maxT=all.stream().mapToDouble(x->x.time).max().orElse(best.time),maxD=all.stream().mapToDouble(x->x.distance).max().orElse(best.distance);
        if(maxT-best.time>=60)out.add("比最慢候选预计节省约"+minutes(maxT-best.time)+"分钟");if(maxD-best.distance>=1000)out.add("比最长候选少行驶约"+km(maxD-best.distance)+"公里");
        if(best.time<=minTime*1.001)out.add("预计用时为候选路线中最短");if(best.distance<=minDistance*1.001)out.add("总里程为候选路线中最短");return limit(out,3);}
    private static List<String> cautions(Item x){List<String> out=new ArrayList<>();if(!x.traffic.available)out.add("实时路况暂不完整，出发后应继续关注调度提醒");else if(x.traffic.score<72)out.add("存在较明显拥堵风险，出发前建议刷新路况");
        if(!x.weather.available)out.add("沿途天气数据暂不完整，建议出发前复核天气");else if(x.weather.score<80)out.add("沿途存在天气风险事件，请做好安全准备");if(out.isEmpty())out.add("实时路况和天气可能变化，出发后应继续关注调度提醒");return limit(out,2);}
    private static List<String> routeHighlights(Item x,Item best,double minTime,double minDistance){List<String> out=new ArrayList<>();if(x.time<=minTime*1.001)out.add("预计用时最短");else if(x.time-best.time<300)out.add("预计用时与推荐路线接近");
        if(x.distance<=minDistance*1.001)out.add("总里程最短");else if(x.distance-best.distance<5000)out.add("里程与推荐路线接近");if(x.traffic.available&&x.traffic.score>=88)out.add("整体路况较稳定");if(x.weather.available&&x.weather.score>=90)out.add("未发现明显高风险天气");if(out.isEmpty())out.add("各项指标表现相对均衡");return limit(out,3);}
    private static List<String> routeCautions(Item x){List<String> out=new ArrayList<>();if(!x.traffic.available)out.add("暂未获得完整实时路况");else if(x.traffic.score<72)out.add("部分路段可能存在明显拥堵或缓行");if(!x.weather.available)out.add("沿途天气数据暂不完整");else if(x.weather.score<80)out.add("沿途天气可能对运输产生影响");return limit(out,2);}
    private static String explanation(Item x,int rank,Item best){if(rank==1)return x.name+"在各项有效数据综合比较中排名第一。";StringBuilder s=new StringBuilder(x.name).append("综合排名第").append(rank).append("。");
        if(x.time-best.time>=60)s.append("预计多用约").append(minutes(x.time-best.time)).append("分钟；");if(x.distance-best.distance>=1000)s.append("里程多约").append(km(x.distance-best.distance)).append("公里；");s.append(best.total-x.total<1?"两者差异较小，可人工选择。":"因此综合得分低于推荐路线。");return s.toString().replace("；因此","，因此");}

    private static Fact traffic(Map<String,Object> route,double distance){
        Map<String,Object> t=map(route.get("traffic"));String level=text(route.get("trafficLevel")).toUpperCase(Locale.ROOT);if(level.isEmpty()&&t!=null)level=text(t.get("level")).toUpperCase(Locale.ROOT);
        boolean known=Set.of("SMOOTH","NORMAL","SLOW","CONGESTED","SEVERE_CONGESTION").contains(level);
        Double ratio=ratio(t==null?null:t.get("congestionRatio")),slowRoad=nonNegative(t==null?null:t.get("slowRoadLengthMeters"));
        Double unknown=nonNegative(t==null?null:t.get("unknownDistanceMeters")),smooth=nonNegative(t==null?null:t.get("smoothDistanceMeters"));
        Double slow=nonNegative(t==null?null:t.get("slowDistanceMeters")),congested=nonNegative(t==null?null:t.get("congestedDistanceMeters")),severe=nonNegative(t==null?null:t.get("severeCongestedDistanceMeters"));
        boolean amap="AMAP_DRIVING_V3".equalsIgnoreCase(text(t==null?null:t.get("source")));
        boolean segments=unknown!=null||smooth!=null||slow!=null||congested!=null||severe!=null;
        if(!known&&ratio==null&&slowRoad==null&&!amap&&!segments)return new Fact(60,false,"暂未获得完整实时路况，本次主要依据距离和预计用时进行比较");
        double score;
        if(segments||amap){double u=value(unknown),s=value(smooth),sl=value(slow),c=value(congested),sv=value(severe);double observed=u+s+sl+c+sv,denominator=Math.max(1,observed>0?observed:distance);double risk=(u*.25+sl*.35+c*.75+sv)/denominator;double lights=value(nonNegative(t.get("trafficLights")))/Math.max(1,distance/1000);boolean restriction=Boolean.TRUE.equals(t.get("restriction"));score=100-risk*100-Math.min(15,lights*3)-(restriction?8:0);}
        else{score=switch(level){case"SMOOTH"->100;case"NORMAL"->88;case"SLOW"->72;case"CONGESTED"->50;case"SEVERE_CONGESTION"->25;default->80;};if(ratio!=null)score-=ratio*55;if(slowRoad!=null)score-=Math.min(1,slowRoad/distance)*20;}
        String desc=text(t==null?null:t.get("description"));if(desc.isEmpty()&&(segments||amap)){double affected=value(slow)+value(congested)+value(severe),observed=value(unknown)+value(smooth)+affected;desc="路况快照：缓行及拥堵约"+km(affected)+"公里"+(observed>0?"，占已识别路段"+fmt(affected/observed*100)+"%":"");if(value(severe)>0)desc+="，其中严重拥堵约"+km(value(severe))+"公里";}
        if(desc.isEmpty())desc=ratio!=null?"拥堵路段占比约"+fmt(ratio*100)+"%":switch(level){case"SMOOTH"->"大部分路段通行正常";case"NORMAL"->"整体路况正常";case"SLOW"->"部分路段行驶缓慢";case"CONGESTED"->"存在明显拥堵路段";default->"存在严重拥堵风险";};return new Fact(clamp(score),true,desc);}
    private static Fact weather(Map<String,Object> w){
        if(w==null)return new Fact(60,false,"沿途天气数据暂不完整，未将天气作为主要推荐依据");String level=text(w.get("level")).toUpperCase(Locale.ROOT);boolean known=Set.of("NORMAL","GOOD","NOTICE","WARNING","SEVERE").contains(level);List<?> events=w.get("riskEvents") instanceof List<?> list?list:null;String condition=text(w.get("weather"));boolean amap="AMAP_WEATHER_V3".equalsIgnoreCase(text(w.get("source")));
        if(!known&&events==null&&condition.isEmpty()&&!amap)return new Fact(60,false,"沿途天气数据暂不完整，未将天气作为主要推荐依据");double score;
        if(!condition.isEmpty()||amap){double risk=5;if(contains(condition,"暴雨","特大暴雨","冰雹","冻雨"))risk=85;else if(contains(condition,"大雨","暴雪","大雪","沙尘暴"))risk=65;else if(contains(condition,"中雨","中雪","雾","霾"))risk=42;else if(contains(condition,"小雨","阵雨","雷阵雨","小雪"))risk=24;else if(condition.contains("阴"))risk=10;double humidity=value(nonNegative(w.get("humidity")));if(humidity>=95)risk+=5;double wind=firstNumber(text(w.get("windPower")));if(wind>=8)risk+=25;else if(wind>=6)risk+=15;else if(wind>=4)risk+=6;score=100-risk;}
        else{score=switch(level){case"NORMAL","GOOD"->100;case"NOTICE"->80;case"WARNING"->55;case"SEVERE"->25;default->85;};}
        int count=events==null?0:events.size();score-=Math.min(30,count*10);String desc=text(w.get("description"));if(desc.isEmpty()&&!condition.isEmpty()){String city=text(w.get("city"));desc=(city.isEmpty()?"":city+"：")+condition;String temperature=text(w.get("temperature"));if(!temperature.isEmpty())desc+="，"+temperature+"℃";String wind=text(w.get("windPower"));if(!wind.isEmpty())desc+="，风力"+wind+"级";}
        if(desc.isEmpty())desc=count==0?"沿途未发现已上报的高风险天气事件":"沿途发现"+count+"项天气风险事件";return new Fact(clamp(score),true,desc);}

    private static Map<String,Double> configured(Map<String,Object> c){Map<String,Double> out=new LinkedHashMap<>();out.put("time",weight(c,"timeWeight",.4));out.put("distance",weight(c,"distanceWeight",.2));out.put("traffic",weight(c,"trafficWeight",.3));out.put("weather",weight(c,"weatherWeight",.1));double sum=out.values().stream().mapToDouble(Double::doubleValue).sum();if(sum<=0)throw new IllegalArgumentException("scoringConfig 权重之和必须大于 0");out.replaceAll((k,v)->v/sum);return out;}
    private static Map<String,Double> effective(Map<String,Double> base,boolean traffic,boolean weather){Map<String,Double> out=new LinkedHashMap<>(base);if(!traffic)out.put("traffic",0d);if(!weather)out.put("weather",0d);double sum=out.values().stream().mapToDouble(Double::doubleValue).sum();out.replaceAll((k,v)->v/sum);return out;}
    private static double weight(Map<String,Object> c,String key,double fallback){if(c==null||!c.containsKey(key))return fallback;double n=number(c.get(key));if(!Double.isFinite(n)||n<0||n>1)throw new IllegalArgumentException(key+" 必须在 0～1 之间");return n;}
    private static Map<String,Object> weights(Map<String,Double> values){Map<String,Object> out=new LinkedHashMap<>();values.forEach((k,v)->out.put(k,round(v)));return out;}
    private static List<String> limit(List<String> values,int max){return values.size()<=max?values:new ArrayList<>(values.subList(0,max));}
    private static String required(Object value,String message){String s=text(value);if(s.isEmpty())throw new IllegalArgumentException(message);return s;}
    private static Double ratio(Object value){Double n=nonNegative(value);return n!=null&&n<=1?n:null;}private static Double nonNegative(Object value){double n=number(value);return Double.isFinite(n)&&n>=0?n:null;}private static double value(Double n){return n==null?0:n;}
    private static double positive(Object value){double n=number(value);return n>0?n:Double.NaN;}private static double number(Object value){try{return value instanceof Number n?n.doubleValue():Double.parseDouble(String.valueOf(value));}catch(Exception e){return Double.NaN;}}
    private static double clamp(double n){return Math.max(0,Math.min(100,n));}private static double round(double n){return BigDecimal.valueOf(n).setScale(2,RoundingMode.HALF_UP).doubleValue();}private static String fmt(double n){return BigDecimal.valueOf(n).setScale(2,RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();}
    private static String minutes(double seconds){return fmt(Math.abs(seconds)/60);}private static String km(double meters){return fmt(Math.abs(meters)/1000);}private static String text(Object value){return value==null?"":String.valueOf(value).trim();}
    private static boolean contains(String value,String...parts){for(String part:parts)if(value.contains(part))return true;return false;}private static double firstNumber(String value){java.util.regex.Matcher m=java.util.regex.Pattern.compile("\\d+(?:\\.\\d+)?").matcher(value);return m.find()?Double.parseDouble(m.group()):0;}
    @SuppressWarnings("unchecked")private static Map<String,Object> map(Object value){return value instanceof Map?(Map<String,Object>)value:null;}private static Map<String,Object> firstMap(Object...values){for(Object value:values){Map<String,Object> m=map(value);if(m!=null)return m;}return null;}private static List<Map<String,Object>> maps(Object value){List<Map<String,Object>> out=new ArrayList<>();if(value instanceof List<?> list)for(Object item:list){Map<String,Object> m=map(item);if(m!=null)out.add(m);}return out;}
    private record Fact(double score,boolean available,String text){}private record Item(String id,String name,double time,double distance,double timeScore,double distanceScore,Fact traffic,Fact weather,Map<String,Object> effective,double total){}
}
