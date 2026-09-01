package com.smartlogistics.agent;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class VehicleDriverAssignmentSelfTest {
 public static void main(String[] args)throws Exception{
  AtomicInteger writes=new AtomicInteger();AtomicReference<Map<String,Object>> body=new AtomicReference<>();
  HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/api/v1/users/me",e->reply(e,Map.of("code",0,"data",Map.of("role","Bearer driver-token".equals(e.getRequestHeaders().getFirst("Authorization"))?"DRIVER":"WAREHOUSE_MANAGER"))));
  server.createContext("/api/v1/vehicles/28/driver",e->{writes.incrementAndGet();body.set(Json.object(new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8)));reply(e,Map.of("code",0,"message","success","data",Map.of("id",28,"plateNumber","渝A88888","driverId",body.get().get("driverId"),"driverName","张三")));});
  server.createContext("/api/v1/vehicles",e->reply(e,Map.of("code",0,"data",Map.of("records",List.of(Map.of("id",28,"plateNumber","渝A88888","simCode","sim_008","driverId",12,"status","IDLE")),"total",1))));
  server.createContext("/api/v1/drivers/options",e->reply(e,Map.of("code",0,"data",List.of(Map.of("driverId",7,"name","张三","username","driver7"),Map.of("driverId",8,"name","李四","username","driver8"),Map.of("driverId",9,"name","王伟","username","driver9"),Map.of("driverId",10,"name","王伟","username","driver10")))));
  server.start();
  try{
   BusinessDataService service=new BusinessDataService("http://127.0.0.1:"+server.getAddress().getPort(),"",3000);
   var byName=service.answerBySelection(new ToolSelection("ASSIGN_VEHICLE_DRIVER",.99,Map.of("plateNumber","渝A88888","driverName","张三"),false,""),"warehouse-token");
   ok(byName.answer.contains("已绑定司机 张三")&&((Number)body.get().get("driverId")).longValue()==7,"按姓名绑定失败");
   var byId=service.answerBySelection(new ToolSelection("ASSIGN_VEHICLE_DRIVER",.99,Map.of("vehicleId",28,"driverId",8),false,""),"warehouse-token");
   ok(byId.answer.contains("李四")&&((Number)body.get().get("driverId")).longValue()==8,"按ID绑定失败");
   int before=writes.get();var duplicate=service.answerBySelection(new ToolSelection("ASSIGN_VEHICLE_DRIVER",.99,Map.of("plateNumber","渝A88888","driverName","王伟"),false,""),"warehouse-token");
   ok(duplicate.answer.contains("匹配多条")&&writes.get()==before,"重名司机未拦截");
   var missing=service.answerBySelection(new ToolSelection("ASSIGN_VEHICLE_DRIVER",.99,Map.of("plateNumber","渝A88888"),false,""),"warehouse-token");
   ok(missing.answer.contains("driverId或driverName")&&writes.get()==before,"缺少司机未拦截");
   try{service.answerBySelection(new ToolSelection("ASSIGN_VEHICLE_DRIVER",.99,Map.of("plateNumber","渝A88888","driverId",7),false,""),"driver-token");throw new AssertionError("司机越权绑定未拒绝");}catch(BusinessDataService.BusinessApiException e){ok(e.status==403,"越权状态错误");}
   var fallback=WarehouseWriteTools.fallbackSelection("把车辆渝A88888绑定给司机张三");ok(fallback!=null&&"ASSIGN_VEHICLE_DRIVER".equals(fallback.intent)&&"张三".equals(fallback.parameters.get("driverName")),"自然语言兜底解析失败："+(fallback==null?null:fallback.parameters));
   System.out.println("车辆绑定司机自检通过（6/6）");
  }finally{server.stop(0);}
 }
 private static void reply(HttpExchange e,Object value)throws java.io.IOException{byte[] bytes=Json.stringify(value).getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().set("Content-Type","application/json; charset=UTF-8");e.sendResponseHeaders(200,bytes.length);e.getResponseBody().write(bytes);e.close();}
 private static void ok(boolean value,String message){if(!value)throw new AssertionError(message);}
}
