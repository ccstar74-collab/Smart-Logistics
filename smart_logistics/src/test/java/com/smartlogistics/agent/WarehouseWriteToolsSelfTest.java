package com.smartlogistics.agent;
import com.sun.net.httpserver.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.*;

public final class WarehouseWriteToolsSelfTest {
 public static void main(String[] a)throws Exception{
  AtomicReference<Map<String,Object>> cargo=new AtomicReference<>(),task=new AtomicReference<>(),vehicle=new AtomicReference<>();AtomicInteger writes=new AtomicInteger();
  HttpServer s=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  s.createContext("/api/v1/users/me",e->{String role="Bearer driver".equals(e.getRequestHeaders().getFirst("Authorization"))?"DRIVER":"WAREHOUSE_MANAGER";reply(e,Map.of("code",200,"data",Map.of("role",role)));});
  s.createContext("/api/v1/warehouses",e->reply(e,Map.of("code",200,"data",Map.of("records",List.of(Map.of("id",1,"warehouseNo","WH-CQ-001","name","重庆一号仓")),"total",1))));
  s.createContext("/api/v1/cargo-types",e->reply(e,Map.of("code",200,"data",Map.of("records",List.of(Map.of("id",10,"name","生鲜水果")),"total",1))));
  s.createContext("/api/v1/cargos",e->{if("GET".equals(e.getRequestMethod())){reply(e,Map.of("code",200,"data",Map.of("records",List.of(Map.of("id",41,"cargoNo","CG20260830001","ownerId",5)),"total",1)));return;}writes.incrementAndGet();cargo.set(read(e));reply(e,Map.of("code",200,"data",Map.of("id",41,"cargoNo","CG20260830001","name","生鲜水果")));});
  s.createContext("/api/v1/vehicles",e->{if("GET".equals(e.getRequestMethod())){reply(e,Map.of("code",200,"data",Map.of("records",List.of(Map.of("id",28,"plateNumber","渝A88888","driverId",12,"status","IDLE"),Map.of("id",30,"plateNumber","渝A66666","status","IDLE")),"total",2)));return;}writes.incrementAndGet();vehicle.set(read(e));reply(e,Map.of("code",200,"data",Map.of("id",29,"plateNumber",vehicle.get().get("plateNumber"),"simCode",vehicle.get().get("simCode"),"type",vehicle.get().get("type"),"capacity",vehicle.get().get("capacity"),"status","IDLE")));});
  s.createContext("/geo",e->reply(e,Map.of("status","1","count","1","geocodes",List.of(Map.of("formatted_address","重庆市测试地址","province","重庆市","city","重庆市","district","渝北区","location","106.55187,29.572965","level","兴趣点")))));
  s.createContext("/api/v1/transport-tasks",e->{writes.incrementAndGet();task.set(read(e));reply(e,Map.of("code",200,"data",Map.of("id",72,"taskNo","T20260830001")));});s.start();
  try{
   BusinessDataService b=new BusinessDataService("http://127.0.0.1:"+s.getAddress().getPort(),"",3000);
   b.configureAmap("test-key","","http://127.0.0.1:"+s.getAddress().getPort()+"/geo");
   var in=b.answerIfBusinessQuery("将货物CG20260830001生鲜水果办理入库，货物种类ID为10，入库仓库ID为1，重量780公斤，体积5.2立方米","warehouse");
   ok(in.answer.contains("已办理入库")&&"生鲜水果".equals(cargo.get().get("name"))&&((Number)cargo.get().get("cargoTypeId")).longValue()==10&&((Number)cargo.get().get("warehouseId")).longValue()==1,"多仓入库失败");
   var added=b.answerIfBusinessQuery("新增一辆渝A99999冷链车，GPS设备sim_009，载重3.5吨，归属仓库ID为1","warehouse");
   ok(added.answer.contains("已添加")&&"REFRIGERATED".equals(vehicle.get().get("type"))&&((Number)vehicle.get().get("warehouseId")).longValue()==1&&new java.math.BigDecimal("3500").compareTo(new java.math.BigDecimal(String.valueOf(vehicle.get().get("capacity"))))==0,"多仓新增车辆失败");
   Map<String,Object> p=Map.ofEntries(Map.entry("cargoNo","CG20260830001"),Map.entry("plateNumber","渝A88888"),Map.entry("startLocation","重庆仓库A"),Map.entry("startCity","重庆"),Map.entry("endLocation","重庆北站"),Map.entry("endCity","重庆"),Map.entry("planStartTime","2026-08-30T12:00:00+08:00"),Map.entry("planEndTime","2026-08-30T14:00:00+08:00"));
   var order=b.answerBySelection(new ToolSelection("CREATE_TRANSPORT_TASK",.99,p,false,""),"warehouse");
   ok(order.answer.contains("T20260830001")&&((Number)task.get().get("cargoId")).longValue()==41&&((Number)task.get().get("vehicleId")).longValue()==28&&Math.abs(((Number)task.get().get("startLongitude")).doubleValue()-106.55187)<0.000001&&Math.abs(((Number)task.get().get("endLatitude")).doubleValue()-29.572965)<0.000001,"地址自动解析或订单创建失败");
   int afterOrder=writes.get();Map<String,Object> noDriver=new LinkedHashMap<>(p);noDriver.put("plateNumber","渝A66666");var rejected=b.answerBySelection(new ToolSelection("CREATE_TRANSPORT_TASK",.99,noDriver,false,""),"warehouse");ok(rejected.answer.contains("尚未绑定司机")&&writes.get()==afterOrder,"未绑定司机车辆未被安全拦截");
   int before=writes.get();try{b.answerBySelection(new ToolSelection("VEHICLE_CREATE",.99,Map.of("plateNumber","渝A77777","simCode","sim_007","vehicleType","VAN","capacity",500),false,""),"driver");throw new AssertionError("越权写入");}catch(BusinessDataService.BusinessApiException x){ok(x.status==403,"权限状态错误");}ok(before==writes.get(),"越权请求已写入");
   var missing=b.answerIfBusinessQuery("把CG20260830001安排渝A88888出库","warehouse");ok(Boolean.TRUE.equals(((Map<?,?>)missing.toolData.get("data")).get("requiresInput"))&&before==writes.get(),"缺字段保护失败");
   var missingVehicle=b.answerIfBusinessQuery("新增车辆渝A66666，GPS设备sim_006，厢式车，载重500公斤","warehouse");ok(missingVehicle.answer.contains("warehouseId")&&Boolean.TRUE.equals(((Map<?,?>)missingVehicle.toolData.get("data")).get("requiresInput"))&&before==writes.get(),"车辆缺仓库保护失败");
   System.out.println("仓库管理员写操作自检通过（7/7）");
  }finally{s.stop(0);}
 }
 static Map<String,Object> read(HttpExchange e)throws java.io.IOException{return Json.object(new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));}
 static void reply(HttpExchange e,Object value)throws java.io.IOException{byte[] b=Json.stringify(value).getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,b.length);e.getResponseBody().write(b);e.close();}
 static void ok(boolean v,String m){if(!v)throw new AssertionError(m);}
}
