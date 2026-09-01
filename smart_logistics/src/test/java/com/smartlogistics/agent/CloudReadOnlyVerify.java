package com.smartlogistics.agent;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
public class CloudReadOnlyVerify {
  public static void main(String[] args) throws Exception {
    String[] questions={"我现在是什么身份？","我的当前任务是什么？","运输任务 1 的详情是什么？","运输任务 1 预计多久到？","运输任务 1 的规划路线是什么？","车辆 23 今天去过哪些地方？","有哪些可用车辆？","有哪些可用货物？","有哪些未确认调度指令？"};
    HttpClient client=HttpClient.newHttpClient();
    for(int i=0;i<questions.length;i++){
      Map<String,Object> request=new LinkedHashMap<>(); request.put("sessionId","cloud-verify-"+i); request.put("question",questions[i]);
      HttpRequest http=HttpRequest.newBuilder(URI.create("http://111.170.148.177:58081/api/chat")).header("Content-Type","application/json; charset=UTF-8").POST(HttpRequest.BodyPublishers.ofString(Json.stringify(request),StandardCharsets.UTF_8)).build();
      Map<String,Object> response=Json.object(client.send(http,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body());
      Object meta=response.get("toolData"); Object tool=meta instanceof Map ? ((Map<?,?>)meta).get("tool") : "-";
      System.out.println(questions[i]+" => "+response.get("answer")+" | "+response.get("mode")+" | "+tool);
    }
  }
}
