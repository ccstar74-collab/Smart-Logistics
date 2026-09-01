package com.smartlogistics.agent;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ReadOnlyBusinessTools {
    interface Getter { Object get(String path, String token) throws IOException; }
    private final Getter getter;
    ReadOnlyBusinessTools(Getter getter) { this.getter=getter; }

    BusinessDataService.BusinessAnswer execute(ToolSelection selection, String token) throws IOException {
        if (selection == null) return null;
        Map<String,Object> p=selection.parameters; Long taskId=positiveLong(p.get("taskId")), vehicleId=positiveLong(p.get("vehicleId")); String sim=validSim(p.get("simCode")); String plate=validPlate(p.get("plateNumber")); String taskNo=text(p.get("taskNo")); String cargoNo=text(p.get("cargoNo"));
        return switch(selection.intent) {
            case "GET_CURRENT_USER" -> single("get_current_user","/api/v1/users/me",token,"当前登录用户信息");
            case "QUERY_TASKS" -> page("query_tasks","/api/v1/transport-tasks?page=1&pageSize=100",token,"运输任务");
            case "GET_CURRENT_TASK" -> single("get_current_task","/api/v1/transport-tasks/current",token,"当前运输任务");
            case "GET_CURRENT_TASK_ETA" -> single("get_current_task_eta","/api/v1/transport-tasks/current",token,"当前任务的预计到达信息");
            case "GET_TASK_DETAIL" -> taskDetail(taskId,taskNo,token,"get_task_detail");
            case "GET_TASK_ETA" -> taskDetail(taskId,taskNo,token,"get_task_eta");
            case "GET_PLANNED_ROUTE" -> plannedRoute(taskId,taskNo,token);
            case "GET_VEHICLE_PROFILE" -> vehicleId!=null?vehicleById(vehicleId,token):(sim!=null?vehicleBySim(sim,token):(plate!=null?vehicleByPlate(plate,token):null));
            case "GET_VEHICLE_TRAJECTORY" -> { Long id=vehicleId!=null?vehicleId:(sim!=null?vehicleIdBySim(sim,token):(plate==null?null:vehicleIdByPlate(plate,token))); yield id==null?null:trajectory(id,token); }
            case "GET_CARGO_DETAIL" -> cargoDetail(cargoNo,token);
            case "QUERY_CARGOS" -> page("query_cargos","/api/v1/cargos?page=1&pageSize=100",token,"货物");
            case "QUERY_AVAILABLE_CARGOS" -> page("available_cargos","/api/v1/cargos/available?page=1&pageSize=100",token,"可用货物");
            case "QUERY_VEHICLES" -> page("query_vehicles","/api/v1/vehicles?page=1&pageSize=100",token,"车辆");
            case "QUERY_AVAILABLE_VEHICLES" -> page("available_vehicles","/api/v1/vehicles/available?page=1&pageSize=100",token,"可用车辆");
            case "QUERY_ALARMS" -> page("query_alarms","/api/v1/alarms?page=1&pageSize=100",token,"告警");
            case "QUERY_DISPATCH_COMMANDS" -> page("query_dispatch_commands","/api/v1/dispatch-commands?page=1&pageSize=100",token,"调度指令");
            case "QUERY_NOTIFICATIONS" -> page("query_notifications","/api/v1/notifications?page=1&pageSize=100",token,"通知");
            case "QUERY_UNREAD_NOTIFICATIONS" -> page("query_unread_notifications","/api/v1/notifications?page=1&pageSize=100&read=false",token,"未读通知");
            case "GET_NOTIFICATION_UNREAD_COUNT" -> single("get_notification_unread_count","/api/v1/notifications/unread-count",token,"未读通知数量");
            case "SUMMARIZE_OPERATIONS" -> summary(token);
            default -> null;
        };
    }
    BusinessDataService.BusinessAnswer answer(String question, String token) throws IOException {
        String q=question==null?"":question.toLowerCase(Locale.ROOT);
        String sim=match(q,"sim_\\d+"); String plate=plateFromQuestion(question); Long id=numberId(q);
        if (has(q,"身份","角色","我是谁","当前用户","current user")) return single("get_current_user","/api/v1/users/me",token,"当前登录用户信息");
        if (has(q,"未读数","未读数量","几条未读","多少未读","未读消息数量","未读通知数量")) return single("get_notification_unread_count","/api/v1/notifications/unread-count",token,"未读通知数量");
        if (has(q,"未读消息","未读通知")) return page("query_unread_notifications","/api/v1/notifications?page=1&pageSize=100&read=false",token,"未读通知");
        if (has(q,"消息中心","通知","消息列表")) return page("query_notifications","/api/v1/notifications?page=1&pageSize=100",token,"通知");
        if (has(q,"整体运营","运营情况","总体情况","运营汇总")) return summary(token);
        if (has(q,"规划路线","计划路线","怎么走") && id!=null) return single("get_planned_route","/api/v1/transport-tasks/"+id+"/planned-route",token,"任务 "+id+" 的规划路线");
        if (has(q,"轨迹","去过哪里","去过哪些地方","走过哪些地方")) { if(id!=null) return trajectory(id,token); if(sim!=null) { Long vehicleId=vehicleIdBySim(sim,token); if(vehicleId!=null) return trajectory(vehicleId,token); } }
        if (has(q,"预计到达","eta","多久到","几点到","什么时候到","还有多久","还要多久","多久完成","何时完成")) {
            if(id!=null) return single("get_task_eta","/api/v1/transport-tasks/"+id,token,"任务 "+id+" 的预计到达信息");
            if(has(q,"我","我的","当前","负责")) return single("get_current_task_eta","/api/v1/transport-tasks/current",token,"当前任务的预计到达信息");
        }
        if (has(q,"运输任务","任务详情","运单详情") && id!=null) return single("get_task_detail","/api/v1/transport-tasks/"+id,token,"任务 "+id+" 的详情");
        if (has(q,"当前任务","我的任务")) return single("get_current_task","/api/v1/transport-tasks/current",token,"当前运输任务");
        if (has(q,"运输任务","任务列表","有哪些任务","运单")) return page("query_tasks","/api/v1/transport-tasks?page=1&pageSize=100",token,"运输任务");
        if (has(q,"位置","在哪里","在哪","坐标","经纬度","速度","方向","实时") && (sim!=null||plate!=null)) return null;
        if (has(q,"司机","驾驶员","车牌","车型","载重","状态","车辆信息","车辆档案","绑定") && (id!=null||sim!=null||plate!=null)) return id!=null?vehicleById(id,token):(sim!=null?vehicleBySim(sim,token):vehicleByPlate(plate,token));
        if (has(q,"可用车辆","空闲车辆")) return page("available_vehicles","/api/v1/vehicles/available?page=1&pageSize=100",token,"可用车辆");
        if (has(q,"车辆列表","有哪些车","几辆车","所有车辆")) return page("query_vehicles","/api/v1/vehicles?page=1&pageSize=100",token,"车辆");
        if (has(q,"可用货物","待分配货物")) return page("available_cargos","/api/v1/cargos/available?page=1&pageSize=100",token,"可用货物");
        if (has(q,"货物","货品","cargo")) return page("query_cargos","/api/v1/cargos?page=1&pageSize=100",token,"货物");
        if (has(q,"告警","报警","异常")) return page("query_alarms","/api/v1/alarms?page=1&pageSize=100",token,"告警");
        if (has(q,"调度指令","调度命令","未确认指令")) return page("query_dispatch_commands","/api/v1/dispatch-commands?page=1&pageSize=100",token,"调度指令");
        return null;
    }

    private BusinessDataService.BusinessAnswer taskDetail(Long taskId,String taskNo,String token,String tool)throws IOException{
        Long id=taskId!=null?taskId:recordIdByBusinessNo("/api/v1/transport-tasks?page=1&pageSize=100","taskNo",taskNo,token);
        if(id==null) return result(tool,"/api/v1/transport-tasks","未找到运单 "+fallback(taskNo,"未知")+"。",Map.of("taskNo",fallback(taskNo,"")));
        String label="get_task_eta".equals(tool)?"运单 "+fallback(taskNo,String.valueOf(id))+" 的预计到达信息":"运单 "+fallback(taskNo,String.valueOf(id))+" 的详情";
        return single(tool,"/api/v1/transport-tasks/"+id,token,label);
    }
    private BusinessDataService.BusinessAnswer plannedRoute(Long taskId,String taskNo,String token)throws IOException{
        Long id=taskId!=null?taskId:recordIdByBusinessNo("/api/v1/transport-tasks?page=1&pageSize=100","taskNo",taskNo,token);
        return id==null?result("get_planned_route","/api/v1/transport-tasks","未找到运单 "+fallback(taskNo,"未知")+"。",Map.of("taskNo",fallback(taskNo,""))):single("get_planned_route","/api/v1/transport-tasks/"+id+"/planned-route",token,"运单 "+fallback(taskNo,String.valueOf(id))+" 的规划路线");
    }
    private BusinessDataService.BusinessAnswer cargoDetail(String cargoNo,String token)throws IOException{
        Map<String,Object> page=envelope("/api/v1/cargos?page=1&pageSize=100",token); Map<String,Object> record=findByField(page,"cargoNo",cargoNo);
        if(record==null) return result("get_cargo_detail","/api/v1/cargos","未找到货物 "+fallback(cargoNo,"未知")+"。",Map.of("cargoNo",fallback(cargoNo,"")));
        Long id=positiveLong(record.get("id")); return id==null?result("get_cargo_detail","/api/v1/cargos",formatCargo(record),record):single("get_cargo_detail","/api/v1/cargos/"+id,token,"货物 "+fallback(record.get("cargoNo"),cargoNo)+" 的详情");
    }
    private Long recordIdByBusinessNo(String path,String field,String value,String token)throws IOException{Map<String,Object> record=findByField(envelope(path,token),field,value);return record==null?null:positiveLong(record.get("id"));}
    private static Map<String,Object> findByField(Map<String,Object> page,String field,String value){if(value==null||value.isBlank())return null;for(Object item:list(page.get("records"))){Map<String,Object> record=map(item);if(record!=null&&value.equalsIgnoreCase(text(record.get(field))))return record;}return null;}
    private Long vehicleIdBySim(String sim,String token)throws IOException{Map<String,Object> v=vehicleRecordBySim(sim,token);return v==null?null:positiveLong(v.get("id"));}
    private Long vehicleIdByPlate(String plate,String token)throws IOException{Map<String,Object> v=vehicleRecordByPlate(plate,token);return v==null?null:positiveLong(v.get("id"));}
    Map<String,Object> vehicleRecordByPlate(String plate,String token)throws IOException{
        Map<String,Object> page=envelope("/api/v1/vehicles?page=1&pageSize=100",token);
        for(Object item:list(page.get("records"))){Map<String,Object> v=map(item);if(v!=null&&samePlate(plate,text(v.get("plateNumber"))))return v;}
        return null;
    }
    Map<String,Object> vehicleRecordBySim(String sim,String token)throws IOException{
        Map<String,Object> page=envelope("/api/v1/vehicles?page=1&pageSize=100",token);
        for(Object item:list(page.get("records"))){Map<String,Object> v=map(item);if(v!=null&&sim.equalsIgnoreCase(text(v.get("simCode"))))return v;}
        return null;
    }
    private BusinessDataService.BusinessAnswer vehicleBySim(String sim,String token)throws IOException{
        Map<String,Object> page=envelope("/api/v1/vehicles?page=1&pageSize=100",token);
        for(Object item:list(page.get("records"))){Map<String,Object> v=map(item);if(v!=null&&sim.equalsIgnoreCase(text(v.get("simCode")))){
            String answer="设备 "+sim+" 对应车辆 "+fallback(v.get("plateNumber"),"未命名")+"（车辆 ID "+fallback(v.get("id"),"未知")+"）"+
                    "，司机："+fallback(v.get("driverName"),"暂未绑定")+"，状态："+fallback(v.get("status"),"未知")+"，车型："+fallback(v.get("type"),"未知")+"。";
            return result("get_vehicle_profile","/api/v1/vehicles",answer,v);
        }}
        return result("get_vehicle_profile","/api/v1/vehicles","未找到设备 "+sim+" 对应的车辆档案。",Map.of("simCode",sim));
    }
    private BusinessDataService.BusinessAnswer vehicleByPlate(String plate,String token)throws IOException{
        Map<String,Object> v=vehicleRecordByPlate(plate,token);
        if(v==null)return result("get_vehicle_profile","/api/v1/vehicles","未找到车牌号为 "+plate+" 的车辆。",Map.of("plateNumber",plate));
        String answer="车辆 "+fallback(v.get("plateNumber"),plate)+"，设备编号："+fallback(v.get("simCode"),"暂未绑定")+
                "，司机："+fallback(v.get("driverName"),"暂未绑定")+"，状态："+fallback(v.get("status"),"未知")+
                "，车型："+fallback(v.get("type"),"未知")+"。";
        return result("get_vehicle_profile","/api/v1/vehicles",answer,v);
    }
    private BusinessDataService.BusinessAnswer vehicleById(long id,String token)throws IOException{
        Object data=envelopeValue("/api/v1/vehicles/"+id,token);Map<String,Object> v=map(data);
        if(v==null)return result("get_vehicle_profile","/api/v1/vehicles/"+id,"未找到车辆 "+id+"。",Map.of("vehicleId",id));
        return result("get_vehicle_profile","/api/v1/vehicles/"+id,formatVehicle(v),v);
    }
    private BusinessDataService.BusinessAnswer trajectory(long id,String token)throws IOException{
        OffsetDateTime end=OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1), start=end.minusHours(24);
        String path="/api/v1/vehicles/"+id+"/location-history?startTime="+enc(start.toString())+"&endTime="+enc(end.toString());
        return single("get_vehicle_trajectory",path,token,"车辆 "+id+" 最近 24 小时轨迹");
    }
    private BusinessDataService.BusinessAnswer summary(String token)throws IOException{
        Map<String,Object> data=new LinkedHashMap<>();
        data.put("vehicles",envelope("/api/v1/vehicles?page=1&pageSize=1",token));
        data.put("tasks",envelope("/api/v1/transport-tasks?page=1&pageSize=1",token));
        data.put("cargos",envelope("/api/v1/cargos?page=1&pageSize=1",token));
        data.put("alarms",envelope("/api/v1/alarms?page=1&pageSize=1",token));
        String answer="当前运营概况：车辆 "+total(data.get("vehicles"))+" 辆，运输任务 "+total(data.get("tasks"))+" 条，货物 "+total(data.get("cargos"))+" 条，告警 "+total(data.get("alarms"))+" 条。";
        return result("summarize_operations","multiple-read-only-endpoints",answer,data);
    }
    private BusinessDataService.BusinessAnswer page(String tool,String path,String token,String label)throws IOException{
        Map<String,Object> data=envelope(path,token); List<Object> records=list(data.get("records")); StringBuilder answer=new StringBuilder(label+"共 "+total(data)+" 条。");
        int index=1; for(Object item:records){Map<String,Object> record=map(item);if(record==null)continue;answer.append('\n').append(index++).append(". ");
            if("query_cargos".equals(tool)||"available_cargos".equals(tool)) answer.append(formatCargo(record));
            else if("query_tasks".equals(tool)) answer.append(formatTask(record));
            else if("query_vehicles".equals(tool)||"available_vehicles".equals(tool)) answer.append(formatVehicle(record));
            else if("query_alarms".equals(tool)) answer.append(formatAlarm(record));
            else if("query_notifications".equals(tool)||"query_unread_notifications".equals(tool)) answer.append(formatNotification(record));
            else answer.append(Json.stringify(record));
        }
        return result(tool,path,answer.toString(),data);
    }
    private static String formatCargo(Map<String,Object> r){return "货物编号："+fallback(r.get("cargoNo"),"未知")+"；物品名称："+fallback(r.get("name"),"未知")+"；重量："+fallback(r.get("weight"),"未知")+"；体积："+fallback(r.get("volume"),"未知")+"。";}
    private static String formatTask(Map<String,Object> r){return "运单编号："+fallback(r.get("taskNo"),"未知")+"；司机："+fallback(r.get("driverName"),"暂未分配")+"；预计到达时间："+fallback(r.get("estimatedArrivalTime"),"暂未提供")+"。";}
    private static String formatVehicle(Map<String,Object> r){return "车牌号："+fallback(r.get("plateNumber"),"未知")+"；编号："+fallback(r.get("simCode"),"暂未绑定")+"；司机："+fallback(r.get("driverName"),"暂未绑定")+"；类型："+fallback(r.get("type"),"未知")+"；状态："+fallback(r.get("status"),"未知")+"。";}
    private static String formatAlarm(Map<String,Object> r){return "告警ID："+fallback(r.get("id"),"未知")+"；车辆："+fallback(r.get("plateNumber"),fallback(r.get("deviceCode"),"未知"))+"；类型："+fallback(r.get("type"),fallback(r.get("alarmType"),"未知"))+"；级别："+fallback(r.get("level"),"未知")+"；状态："+fallback(r.get("status"),"未知")+"；描述："+fallback(r.get("description"),fallback(r.get("message"),"无"))+"；发生时间："+fallback(r.get("occurredAt"),fallback(r.get("createdAt"),"未知"))+"。";}
    private static String formatNotification(Map<String,Object> r){return "通知ID："+fallback(r.get("id"),"未知")+"；标题："+fallback(r.get("title"),"无标题")+"；内容："+fallback(r.get("content"),"无")+"；级别："+fallback(r.get("level"),"INFO")+"；状态："+(Boolean.TRUE.equals(r.get("read"))?"已读":"未读")+"；时间："+fallback(r.get("createdAt"),"未知")+"。";}
    private BusinessDataService.BusinessAnswer single(String tool,String path,String token,String label)throws IOException{
        Object data=envelopeValue(path,token); Map<String,Object> m=map(data); String answer=label+"已查询成功。";
        if(m!=null && "get_current_user".equals(tool)) answer="当前用户："+fallback(m.get("name"),fallback(m.get("username"),"未知"))+"，角色："+fallback(m.get("role"),"未知")+"，状态："+fallback(m.get("status"),"未知")+"。";
        if(m!=null && ("get_task_detail".equals(tool)||"get_current_task".equals(tool))) answer="任务 "+fallback(m.get("taskNo"),fallback(m.get("id"),"未知"))+"，状态："+fallback(m.get("status"),"未知")+"，车辆："+fallback(m.get("vehicleId"),"未分配")+"，货物："+fallback(m.get("cargoId"),"未分配")+"，预计到达："+fallback(m.get("estimatedArrivalTime"),"暂未提供")+"。";
        if(m!=null && ("get_task_eta".equals(tool)||"get_current_task_eta".equals(tool))) answer=etaAnswer(m);
        if(m!=null && "get_notification_unread_count".equals(tool)) answer="当前有 "+intValue(m.get("count"),0)+" 条未读通知。";
        return result(tool,path,answer,data);
    }    private static String etaAnswer(Map<String,Object> task){
        String taskNo=fallback(task.get("taskNo"),fallback(task.get("id"),"未知"));
        String rawEta=fallback(task.get("estimatedArrivalTime"),"");
        String destination=fallback(task.get("endLocation"),"暂未提供");
        if(rawEta.isEmpty()) return "任务 "+taskNo+"\n预计到达时间：暂未计算\n目的地："+destination+"。";
        String formattedEta=rawEta;
        try { formattedEta=java.time.OffsetDateTime.parse(rawEta).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd-HH:mm:ss")); }
        catch(java.time.format.DateTimeParseException ignored) { }
        return "任务 "+taskNo+"\n预计到达时间为 "+formattedEta+"\n目的地："+destination+"。";
    }    private BusinessDataService.BusinessAnswer unavailable(String tool,String message){return result(tool,"unavailable",message,Map.of("available",false,"reason","BACKEND_API_MISSING"));}
    private BusinessDataService.BusinessAnswer result(String tool,String endpoint,String answer,Object data){
        Map<String,Object> meta=new LinkedHashMap<>();meta.put("tool",tool);meta.put("sourceType","CLOUD_SPRING_BOOT_MYSQL");meta.put("readOnly",true);meta.put("endpoint",endpoint);meta.put("data",data);return new BusinessDataService.BusinessAnswer(answer,meta);
    }
    private Map<String,Object> envelope(String path,String token)throws IOException{Object d=envelopeValue(path,token);if(!(d instanceof Map))throw new IOException("业务接口 data 不是对象");return map(d);}
    private Object envelopeValue(String path,String token)throws IOException{Object raw=getter.get(path,token);Map<String,Object> root=map(raw);if(root==null)throw new IOException("业务接口返回格式错误");int code=intValue(root.get("code"),-1);if(code!=0&&code!=200)throw new IOException("业务接口返回失败："+fallback(root.get("message"),"code="+code));return root.get("data");}
    private static int total(Object value){Map<String,Object> m=map(value);return m==null?0:intValue(m.get("total"),list(m.get("records")).size());}
    private static Long numberId(String q){Matcher m=Pattern.compile("(?:任务|运单|车辆|车)\\s*(?:id\\s*)?(\\d+)",Pattern.CASE_INSENSITIVE).matcher(q);return m.find()?Long.valueOf(m.group(1)):null;}
    private static String match(String q,String regex){Matcher m=Pattern.compile(regex,Pattern.CASE_INSENSITIVE).matcher(q);return m.find()?m.group():null;}
    private static boolean has(String q,String... terms){for(String t:terms)if(q.contains(t.toLowerCase(Locale.ROOT)))return true;return false;}
    private static String enc(String s){return URLEncoder.encode(s,StandardCharsets.UTF_8);}
    private static String fallback(Object v,String f){String s=text(v);return s.isEmpty()?f:s;}
    private static String text(Object v){return v==null?"":String.valueOf(v).trim();}
    private static Long positiveLong(Object v){if(v==null)return null;try{long n=v instanceof Number?((Number)v).longValue():Long.parseLong(String.valueOf(v));return n>0?n:null;}catch(Exception e){return null;}}
    private static String validSim(Object v){String s=text(v).toLowerCase(Locale.ROOT);return s.matches("sim_\\d+")?s:null;}
    private static String validPlate(Object v){String s=normalizePlate(text(v));return s.matches("[京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼使领][A-Z][A-Z0-9]{5,6}")?s:null;}
    private static String plateFromQuestion(String question){if(question==null)return null;Matcher m=Pattern.compile("([京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼使领]\\s*[A-Za-z]\\s*[A-Za-z0-9]{5,6})").matcher(question);return m.find()?normalizePlate(m.group(1)):null;}
    private static boolean samePlate(String a,String b){return !normalizePlate(a).isEmpty()&&normalizePlate(a).equals(normalizePlate(b));}
    private static String normalizePlate(String value){return value==null?"":value.replaceAll("\\s+","").toUpperCase(Locale.ROOT);}
    private static int intValue(Object v,int f){if(v instanceof Number)return ((Number)v).intValue();try{return Integer.parseInt(String.valueOf(v));}catch(Exception e){return f;}}
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object v){return v instanceof Map?(Map<String,Object>)v:null;}
    @SuppressWarnings("unchecked") private static List<Object> list(Object v){return v instanceof List?(List<Object>)v:new ArrayList<>();}
}
