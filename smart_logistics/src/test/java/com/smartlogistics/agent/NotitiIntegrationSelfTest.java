package com.smartlogistics.agent;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public final class NotitiIntegrationSelfTest {
    public static void main(String[] args)throws Exception{
        HttpServer server=HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/v1/notifications",e->reply(e,Map.of("code",0,"message","success","data",Map.of("records",List.of(Map.of("id",13,"type","ALARM_CREATED","title","新告警通知","content","车辆异常停留","level","WARNING","read",false,"createdAt","2026-08-31T11:59:44+08:00")),"total",1,"page",1,"pageSize",20))));
        server.createContext("/api/v1/notifications/unread-count",e->reply(e,Map.of("code",0,"message","success","data",Map.of("count",3))));
        server.createContext("/api/v1/alarms",e->reply(e,Map.of("code",0,"message","success","data",Map.of("records",List.of(Map.of("id",6,"deviceCode","sim_005","type","ROUTE_DEVIATION","level","HIGH","description","偏航告警","status","UNHANDLED","occurredAt","2026-08-31T12:13:11+08:00")),"total",1,"page",1,"pageSize",20))));
        server.createContext("/api/v1/transport-tasks/current",e->reply(e,Map.of("code",0,"message","success","data",Map.of("id",9,"taskNo","T20260831009","estimatedArrivalTime","2026-08-31T18:30:00+08:00","endLocation","重庆北站"))));
        server.createContext("/api/v1/vehicles/12/location/latest",e->reply(e,Map.of("code",200,"message","success","data",Map.of("vehicleId",12,"plateNumber","渝A55555","latitude",29.50,"longitude",106.58,"speed",30.0,"direction",90.0,"collectedAt","2026-08-31T14:30:00+08:00","coordinateSystem","WGS84"))));
        server.createContext("/api/v1/vehicles",e->reply(e,Map.of("code",200,"message","success","data",Map.of("records",List.of(Map.of("id",12,"plateNumber","渝A55555","simCode","sim_005")),"total",1,"page",1,"pageSize",100))));
        server.start();
        try{
            BusinessDataService service=new BusinessDataService("http://127.0.0.1:"+server.getAddress().getPort(),"",3000);
            var notifications=service.answerBySelection(new ToolSelection("QUERY_NOTIFICATIONS",.99,Map.of(),false,""),"token");
            ok(notifications.answer.contains("新告警通知")&&notifications.answer.contains("未读"),"通知列表解析失败");
            var unreadList=service.answerBySelection(new ToolSelection("QUERY_UNREAD_NOTIFICATIONS",.99,Map.of(),false,""),"token");
            ok(unreadList.answer.contains("未读通知共 1 条"),"未读通知列表失败");
            var unreadCount=service.answerBySelection(new ToolSelection("GET_NOTIFICATION_UNREAD_COUNT",.99,Map.of(),false,""),"token");
            ok(unreadCount.answer.contains("3 条未读通知"),"未读数取值失败："+unreadCount.answer);
            var alarms=service.answerBySelection(new ToolSelection("QUERY_ALARMS",.99,Map.of(),false,""),"token");
            ok(alarms.answer.contains("偏航告警")&&alarms.answer.contains("UNHANDLED"),"告警格式失败");
            var eta=service.answerBySelection(new ToolSelection("GET_CURRENT_TASK_ETA",.99,Map.of(),false,""),"token");
            ok(eta.answer.contains("2026-08-31-18:30:00")&&eta.answer.contains("重庆北站"),"ETA 解析失败");
            var location=service.answerBySelection(new ToolSelection("GET_VEHICLE_LOCATION",.99,Map.of("simCode","sim_005"),false,""),"token");
            ok(Boolean.TRUE.equals(location.toolData.get("restFallback")),"位置未使用 REST 兜底");
            ok("BACKEND_REST_LATEST_LOCATION".equals(location.toolData.get("sourceType")),"位置来源标记错误");
            ok("/api/v1/vehicles/12/location/latest".equals(location.toolData.get("endpoint")),"未使用 Vue 同款单车最新位置接口");
            Map<?,?> data=(Map<?,?>)location.toolData.get("data"),point=(Map<?,?>)data.get("location");
            ok("WGS84".equals(point.get("coordSystem"))&&((Number)point.get("longitude")).doubleValue()==106.58,"位置字段错误");
            System.out.println("notiti.md 实时位置、ETA、告警、通知合同自检通过（8/8）");
        }finally{server.stop(0);}
    }
    private static void reply(HttpExchange e,Object body)throws java.io.IOException{byte[] bytes=Json.stringify(body).getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().set("Content-Type","application/json; charset=UTF-8");e.sendResponseHeaders(200,bytes.length);e.getResponseBody().write(bytes);e.close();}
    private static void ok(boolean value,String message){if(!value)throw new AssertionError(message);}
}
