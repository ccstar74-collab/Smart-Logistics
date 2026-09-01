package com.smartlogistics.agent;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
public class CurrentTaskEtaSelfTest {
 public static void main(String[] args)throws Exception{
  HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/api/v1/transport-tasks/current",e->{byte[] b=("{\"code\":200,\"message\":\"success\",\"data\":{\"id\":12,\"taskNo\":\"TASK-012\",\"status\":\"TRANSPORTING\",\"endLocation\":\"重庆西站\",\"estimatedArrivalTime\":\"2026-08-27T18:30:00+08:00\"}}").getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,b.length);e.getResponseBody().write(b);e.close();});server.start();
  try{BusinessDataService service=new BusinessDataService("http://127.0.0.1:"+server.getAddress().getPort(),"",3000);String[] qs={"我当前任务还有多久到达？","我的货物还要多久送到？","我负责的任务多久完成？","当前任务预计到达时间是什么？"};for(String q:qs){var a=service.answerIfBusinessQuery(q,"driver-token");if(!"get_current_task_eta".equals(a.toolData.get("tool")))throw new AssertionError(q+" => "+a.toolData);if(!a.answer.contains("2026-08-27-18:30:00")||a.answer.contains("货物共"))throw new AssertionError(q+" => "+a.answer);}System.out.println("当前司机任务 ETA 路由自检通过（4/4）");}finally{server.stop(0);}
 }
}
