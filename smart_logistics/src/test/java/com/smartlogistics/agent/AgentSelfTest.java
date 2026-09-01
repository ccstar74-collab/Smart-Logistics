package com.smartlogistics.agent;

import com.sun.net.httpserver.HttpServer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

public final class AgentSelfTest {
    public static void main(String[] args) throws Exception {
        testJsonRoundTrip();
        testChineseRetrieval();
        testChatCompletionsResponseParsing();
        testCloudBusinessDataTool();
        testUserTokenPermissionDenied();
        testRealtimeGuardrail();
        testGeneralKnowledgeFallback();
        testWebPageContract();
        System.out.println("全部自检通过（7/7）");
    }

    private static void testJsonRoundTrip() {
        Map<String, Object> parsed = Json.object("{\"question\":\"货物在哪？\",\"ok\":true}");
        require("货物在哪？".equals(parsed.get("question")), "JSON 中文字符串解析失败");
        require(Json.stringify(parsed).contains("\"question\""), "JSON 序列化失败");
    }

    private static void testChineseRetrieval() throws Exception {
        Path directory = Files.createTempDirectory("logistics-agent-test-");
        try {
            Files.write(directory.resolve("guide.md"),
                    "车辆偏航时，调度员应联系司机并核实路线。".getBytes(StandardCharsets.UTF_8));
            KnowledgeBase knowledge = new KnowledgeBase(directory);
            knowledge.reload();
            List<KnowledgeBase.SearchResult> results = knowledge.search("车辆偏航怎么处理", 3);
            require(!results.isEmpty(), "中文知识检索没有命中");
            require("guide.md".equals(results.get(0).source), "知识来源不正确");
        } finally {
            Files.deleteIfExists(directory.resolve("guide.md"));
            Files.deleteIfExists(directory);
        }
    }

    private static void testRealtimeGuardrail() throws Exception {
        Path knowledgePath = Paths.get("knowledge").toAbsolutePath().normalize();
        KnowledgeBase knowledge = new KnowledgeBase(knowledgePath);
        knowledge.reload();
        AppConfig config = new AppConfig(8080, knowledgePath, "chat_completions",
                "https://dashscope.aliyuncs.com/compatible-mode/v1",
                "", "qwen-plus", "", 4, 2000);
        LogisticsAgent agent = new LogisticsAgent(knowledge, new DisabledModelClient(), config);
        LogisticsAgent.AgentResponse response = agent.chat("test-session", "我的货物现在在哪？");
        require("guardrail".equals(response.mode), "实时数据问题没有触发防幻觉策略");
        require(response.answer.contains("没有连接"), "实时数据响应缺少能力边界说明：" + response.answer);
    }

    private static void testCloudBusinessDataTool() throws Exception {
        HttpServer server = HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/vehicles", exchange -> {
            Map<String, Object> vehicle = new java.util.LinkedHashMap<String, Object>();
            vehicle.put("id", 1);
            vehicle.put("plateNumber", "TEST-A001");
            vehicle.put("type", "VAN");
            vehicle.put("capacity", 1000);
            vehicle.put("status", "IDLE");
            vehicle.put("driverId", null);
            vehicle.put("lastLongitude", null);
            vehicle.put("lastLatitude", null);
            Map<String, Object> page = new java.util.LinkedHashMap<String, Object>();
            page.put("records", java.util.Collections.singletonList(vehicle));
            page.put("total", 1);
            page.put("page", 1);
            page.put("pageSize", 100);
            Map<String, Object> envelope = new java.util.LinkedHashMap<String, Object>();
            envelope.put("code", 200);
            envelope.put("message", "success");
            envelope.put("data", page);
            byte[] body = Json.stringify(envelope).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            Path knowledgePath = Paths.get("knowledge").toAbsolutePath().normalize();
            KnowledgeBase knowledge = new KnowledgeBase(knowledgePath);
            knowledge.reload();
            AppConfig config = new AppConfig(8080, knowledgePath, "chat_completions",
                    "https://dashscope.aliyuncs.com/compatible-mode/v1", "", "qwen-plus", "", 4, 2000);
            BusinessDataService business = new BusinessDataService(
                    "http://127.0.0.1:" + server.getAddress().getPort(), "", 3000);
            LogisticsAgent agent = new LogisticsAgent(knowledge, new DisabledModelClient(), business, config);
            LogisticsAgent.AgentResponse response = agent.chat("business-test", "TEST-A001现在在哪？");
            require("tool".equals(response.mode), "云端车辆问题没有进入业务工具模式");
            require(response.answer.contains("TEST-A001"), "云端车辆回答缺少真实记录");
            require(response.answer.contains("最新定位 暂无"), "云端没有坐标时必须明确回答暂无定位");
            require(response.toolData != null && "vehicles".equals(response.toolData.get("resource")),
                    "云端车辆回答缺少结构化业务数据");
        } finally {
            server.stop(0);
        }
    }

    private static void testUserTokenPermissionDenied() throws Exception {
        HttpServer server = HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/vehicles", exchange -> {
            require("Bearer user-token-123".equals(exchange.getRequestHeaders().getFirst("Authorization")),
                    "用户 JWT 没有透传到业务 API");
            byte[] body = "{\"code\":40301,\"message\":\"forbidden\",\"data\":null}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(403, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            Path knowledgePath = Paths.get("knowledge").toAbsolutePath().normalize();
            KnowledgeBase knowledge = new KnowledgeBase(knowledgePath);
            knowledge.reload();
            AppConfig config = new AppConfig(8080, knowledgePath, "chat_completions",
                    "https://dashscope.aliyuncs.com/compatible-mode/v1", "", "qwen-plus", "", 4, 2000);
            BusinessDataService business = new BusinessDataService(
                    "http://127.0.0.1:" + server.getAddress().getPort(), "", 3000);
            LogisticsAgent agent = new LogisticsAgent(knowledge, new DisabledModelClient(), business, config);
            LogisticsAgent.AgentResponse response = agent.chat(
                    "permission-test", "云端车辆列表有哪些？", "user-token-123");
            require("permission_denied".equals(response.mode), "403 没有转换为权限拒答模式");
            require(response.answer.contains("没有权限"), "权限拒答没有明确提示");
            require(response.toolData != null, "权限拒答缺少工具元数据");
        } finally {
            server.stop(0);
        }
    }
    private static void testGeneralKnowledgeFallback() throws Exception {
        Path emptyKnowledge = Files.createTempDirectory("empty-logistics-knowledge-");
        try {
            KnowledgeBase knowledge = new KnowledgeBase(emptyKnowledge);
            knowledge.reload();
            AppConfig config = new AppConfig(8080, emptyKnowledge, "chat_completions",
                    "https://dashscope.aliyuncs.com/compatible-mode/v1",
                    "test-key", "qwen-plus", "", 4, 2000);
            RecordingModelClient model = new RecordingModelClient(
                    "以下回答基于通用物流知识：共同配送是多个客户共享运输资源的配送方式。\n\n参考：无");
            LogisticsAgent agent = new LogisticsAgent(knowledge, model, config);

            LogisticsAgent.AgentResponse response =
                    agent.chat("general-knowledge-test", "什么是共同配送？");

            require("model".equals(response.mode), "无知识库命中时没有调用模型回答通用知识");
            require(response.sources.isEmpty(), "通用知识回答不应伪造知识库来源");
            require(response.answer.contains("共同配送"), "通用知识回答内容丢失");
            require(!response.answer.contains("通用物流知识"), "回答不应暴露内部来源说明");
            require(!response.answer.contains("参考：无"), "通用知识回答不应保留无意义的参考标记");
            require(model.instructions.contains("不要添加来源"), "通用知识提示缺少直接回答规则");
            require(model.input.contains("未找到直接相关资料"), "模型输入没有说明知识库未命中");
        } finally {
            Files.deleteIfExists(emptyKnowledge);
        }
    }

    private static void testChatCompletionsResponseParsing() throws Exception {
        Map<String, Object> response = Json.object(
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"偏航处理建议\"}}]}");
        String text = ChatCompletionsApiModelClient.extractText(response);
        require("偏航处理建议".equals(text), "Chat Completions 响应解析失败");
    }

    private static void testWebPageContract() throws Exception {
        Path page = Paths.get("web", "index.html");
        require(Files.isRegularFile(page), "聊天网页不存在");
        String html = new String(Files.readAllBytes(page), StandardCharsets.UTF_8);
        require(html.contains("fetch('/api/chat'"), "聊天网页没有调用智能体接口");
        require(html.contains("textContent = text"), "聊天网页没有使用安全文本渲染");
        require(!html.contains("innerHTML"), "聊天网页不应使用 innerHTML 渲染模型内容");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class DisabledModelClient implements ModelClient {
        public String answer(String instructions, String input) { throw new AssertionError("不应调用模型"); }
        public boolean enabled() { return false; }
    }

    private static final class RecordingModelClient implements ModelClient {
        final String response;
        String instructions;
        String input;
        RecordingModelClient(String response) { this.response = response; }
        public String answer(String instructions, String input) {
            this.instructions = instructions;
            this.input = input;
            return response;
        }
        public boolean enabled() { return true; }
    }
}
