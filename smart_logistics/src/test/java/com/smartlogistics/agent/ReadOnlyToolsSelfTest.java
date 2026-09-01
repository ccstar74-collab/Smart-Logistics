package com.smartlogistics.agent;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class ReadOnlyToolsSelfTest {
 public static void main(String[] args)throws Exception{
  HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/api/v1/vehicles",e->reply(e,"""
    {"code":200,"message":"success","data":{"records":[{"id":23,"plateNumber":"渝A12345","simCode":"sim_019","driverName":"张三","status":"TRANSPORTING","type":"TRUCK"}],"total":1}}
    """));
  server.createContext("/api/v1/users/me",e->reply(e,"""
    {"code":200,"message":"success","data":{"id":9,"role":"DISPATCHER"}}
    """));
  server.createContext("/api/v1/notifications/unread-count",e->reply(e,"""
    {"code":0,"message":"success","data":{"count":2}}
    """));
  server.start();
  try{
   BusinessDataService service=new BusinessDataService("http://127.0.0.1:"+server.getAddress().getPort(),"",3000);
   var driver=service.answerIfBusinessQuery("sim_019 的司机是谁？","");if(!driver.answer.contains("张三")||!"get_vehicle_profile".equals(driver.toolData.get("tool")))throw new AssertionError(driver.answer+driver.toolData);
   var me=service.answerIfBusinessQuery("我现在是什么身份？","");if(!"get_current_user".equals(me.toolData.get("tool")))throw new AssertionError(me.toolData);
   var notice=service.answerIfBusinessQuery("我有几条未读消息？","");if(!notice.answer.contains("2 条未读通知"))throw new AssertionError(notice.answer);
   System.out.println("只读工具路由自检通过：司机、身份、通知未读数");
  }finally{server.stop(0);}
 }
 private static void reply(HttpExchange e,String json)throws java.io.IOException{byte[] body=json.getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().set("Content-Type","application/json; charset=UTF-8");e.sendResponseHeaders(200,body.length);e.getResponseBody().write(body);e.close();}
}
