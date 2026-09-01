package com.smartlogistics.agent;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
public class AmapPlaceSelfTest {
 public static void main(String[] args)throws Exception{
  HttpServer s=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  s.createContext("/regeo",e->{byte[] b=("{\"status\":\"1\",\"regeocode\":{\"formatted_address\":\"重庆市九龙坡区测试大道\",\"addressComponent\":{\"district\":\"九龙坡区\"},\"pois\":[{\"name\":\"远处公园\",\"distance\":\"500\"},{\"name\":\"测试物流园\",\"distance\":\"88\",\"direction\":\"东\"}],\"roads\":[{\"name\":\"测试大道\",\"distance\":\"20\"}]}}").getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,b.length);e.getResponseBody().write(b);e.close();});s.start();
  try{AmapPlaceService a=new AmapPlaceService("test", "http://127.0.0.1:"+s.getAddress().getPort()+"/regeo"); Map<String,Object> p=a.reverse(106.4,29.5); if(!"测试物流园".equals(p.get("landmark")))throw new AssertionError(p.toString()); String d=AmapPlaceService.describe("sim_019",p); if(!d.contains("测试物流园")||d.contains("106.4"))throw new AssertionError(d); System.out.println(d+"\n"+Json.stringify(p));}finally{s.stop(0);}
 }
}