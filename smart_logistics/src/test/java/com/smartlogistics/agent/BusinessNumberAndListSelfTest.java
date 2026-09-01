package com.smartlogistics.agent;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
public class BusinessNumberAndListSelfTest {
 public static void main(String[] args)throws Exception{
  HttpServer s=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  add(s,"/api/v1/cargos","{\"code\":200,\"data\":{\"records\":[{\"id\":8,\"cargoNo\":\"CARGO-88\",\"name\":\"钢材\",\"weight\":1200,\"volume\":9.5}],\"total\":1}}");
  add(s,"/api/v1/cargos/8","{\"code\":200,\"data\":{\"id\":8,\"cargoNo\":\"CARGO-88\",\"name\":\"钢材\",\"weight\":1200,\"volume\":9.5}}");
  add(s,"/api/v1/transport-tasks","{\"code\":200,\"data\":{\"records\":[{\"id\":12,\"taskNo\":\"TASK-12\",\"driverName\":\"张三\",\"estimatedArrivalTime\":\"2026-08-28T18:00:00+08:00\"}],\"total\":1}}");
  add(s,"/api/v1/transport-tasks/12","{\"code\":200,\"data\":{\"id\":12,\"taskNo\":\"TASK-12\",\"driverName\":\"张三\",\"estimatedArrivalTime\":\"2026-08-28T18:00:00+08:00\"}}");
  add(s,"/api/v1/vehicles","{\"code\":200,\"data\":{\"records\":[{\"plateNumber\":\"渝A12345\",\"simCode\":\"sim_019\",\"driverName\":\"李四\",\"type\":\"TRUCK\",\"status\":\"TRANSPORTING\"}],\"total\":1}}");s.start();
  try{BusinessDataService b=new BusinessDataService("http://127.0.0.1:"+s.getAddress().getPort(),"",3000);
   var cargo=b.answerBySelection(new ToolSelection("GET_CARGO_DETAIL",.99,Map.of("cargoNo","CARGO-88"),false,""),"");if(!cargo.answer.contains("CARGO-88"))throw new AssertionError(cargo.answer);
   var task=b.answerBySelection(new ToolSelection("GET_TASK_DETAIL",.99,Map.of("taskNo","TASK-12"),false,""),"");if(!task.answer.contains("TASK-12"))throw new AssertionError(task.answer);
   var cargos=b.answerBySelection(new ToolSelection("QUERY_CARGOS",.99,Map.of(),false,""),"");if(!cargos.answer.contains("货物编号：CARGO-88")||!cargos.answer.contains("物品名称：钢材"))throw new AssertionError(cargos.answer);
   var tasks=b.answerBySelection(new ToolSelection("QUERY_TASKS",.99,Map.of(),false,""),"");if(!tasks.answer.contains("运单编号：TASK-12")||!tasks.answer.contains("司机：张三"))throw new AssertionError(tasks.answer);
   var vehicles=b.answerBySelection(new ToolSelection("QUERY_VEHICLES",.99,Map.of(),false,""),"");if(!vehicles.answer.contains("车牌号：渝A12345")||!vehicles.answer.contains("编号：sim_019"))throw new AssertionError(vehicles.answer);
   System.out.println("业务编号查询与详细列表自检通过（5/5）");
  }finally{s.stop(0);}
 }
 static void add(HttpServer s,String path,String json){s.createContext(path,e->{byte[] b=json.getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,b.length);e.getResponseBody().write(b);e.close();});}
}
