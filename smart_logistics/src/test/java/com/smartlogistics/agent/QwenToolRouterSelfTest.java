package com.smartlogistics.agent;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;

public final class QwenToolRouterSelfTest {
    public static void main(String[] args) throws Exception {
        HttpServer api=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        api.createContext("/api/v1/transport-tasks/current", exchange -> {
            byte[] body="{\"code\":200,\"message\":\"success\",\"data\":{\"taskNo\":\"T-100\",\"endLocation\":\"重庆北站\",\"estimatedArrivalTime\":\"2026-08-28T15:00:00+08:00\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        api.start();
        try {
            KnowledgeBase knowledge=new KnowledgeBase(Paths.get("knowledge")); knowledge.reload();
            AppConfig config=new AppConfig(58082,Paths.get("knowledge"),"chat_completions","http://unused","test","qwen-plus","",4,2000);
            ModelClient routerModel=new ModelClient(){
                public boolean enabled(){return true;}
                public String answer(String instructions,String input){return "{\"intent\":\"GET_CURRENT_TASK_ETA\",\"confidence\":0.98,\"parameters\":{},\"needsClarification\":false,\"clarificationQuestion\":null}";}
            };
            BusinessDataService business=new BusinessDataService("http://127.0.0.1:"+api.getAddress().getPort(),"",3000);
            LogisticsAgent agent=new LogisticsAgent(knowledge,routerModel,business,config);
            LogisticsAgent.AgentResponse response=agent.chat("router-test","我这趟什么时候能送完？","driver-token");
            if(!"tool".equals(response.mode)) throw new AssertionError(response.mode);
            if(!"get_current_task_eta".equals(response.toolData.get("tool"))) throw new AssertionError(response.toolData);
            if(!response.answer.contains("T-100")) throw new AssertionError(response.answer);
            System.out.println("千问结构化工具路由自检通过");
        } finally { api.stop(0); }
    }
}
