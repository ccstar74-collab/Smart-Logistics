package com.smartlogistics.agent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;

public final class AgentSelfTest {
    public static void main(String[] args) throws Exception {
        testJsonRoundTrip();
        testChineseRetrieval();
        testChatCompletionsResponseParsing();
        testCarlaVehicleData();
        testCarlaHotReload();
        testRealtimeVehicleAgentTool();
        testRealtimeGuardrail();
        testWebPageContract();
        System.out.println("全部自检通过（8/8）");
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
        Path realtimePath = Paths.get("planning_guoyuan_20v_30tasks").toAbsolutePath().normalize();
        KnowledgeBase knowledge = new KnowledgeBase(knowledgePath);
        knowledge.reload();
        AppConfig config = new AppConfig(8080, knowledgePath, realtimePath, "chat_completions",
                "https://dashscope.aliyuncs.com/compatible-mode/v1",
                "", "qwen-plus", "", 4, 2000);
        LogisticsAgent agent = new LogisticsAgent(
                knowledge, new DisabledModelClient(), new VehicleRealtimeService(realtimePath), config);
        LogisticsAgent.AgentResponse response = agent.chat("test-session", "我的货物现在在哪？");
        require("guardrail".equals(response.mode), "实时数据问题没有触发防幻觉策略");
        require(response.answer.contains("没有连接"), "实时数据响应缺少能力边界说明");
    }

    private static void testCarlaVehicleData() throws Exception {
        Path realtimePath = Paths.get("planning_guoyuan_20v_30tasks").toAbsolutePath().normalize();
        VehicleRealtimeService service = new VehicleRealtimeService(realtimePath);
        require(service.available(), "CARLA 实时车辆数据不可用：" + service.error());
        require(service.vehicleCount() == 20, "CARLA 车辆数量应为 20");
        VehicleRealtimeService.VehicleSnapshot vehicle = service.find("sim_000");
        require(vehicle != null, "没有找到 sim_000");
        require("渝A10000".equals(vehicle.plateNumber), "sim_000 车牌不正确");
        require(Math.abs(vehicle.longitude - 106.730553) < 0.000001, "sim_000 经度不正确");
        require(service.find("渝A10000") == vehicle, "无法按车牌查询车辆");
    }

    private static void testRealtimeVehicleAgentTool() throws Exception {
        Path knowledgePath = Paths.get("knowledge").toAbsolutePath().normalize();
        Path realtimePath = Paths.get("planning_guoyuan_20v_30tasks").toAbsolutePath().normalize();
        KnowledgeBase knowledge = new KnowledgeBase(knowledgePath);
        knowledge.reload();
        AppConfig config = new AppConfig(8080, knowledgePath, realtimePath, "chat_completions",
                "https://dashscope.aliyuncs.com/compatible-mode/v1",
                "", "qwen-plus", "", 4, 2000);
        LogisticsAgent agent = new LogisticsAgent(
                knowledge, new DisabledModelClient(), new VehicleRealtimeService(realtimePath), config);
        LogisticsAgent.AgentResponse response = agent.chat("vehicle-test", "渝A10000现在在哪？");
        require("tool".equals(response.mode), "车辆位置问题没有进入实时工具模式");
        require(response.answer.contains("106.730553"), "车辆位置回答缺少经度");
        require(response.toolData != null, "车辆位置回答缺少结构化工具数据");
    }

    private static void testCarlaHotReload() throws Exception {
        Path source = Paths.get("planning_guoyuan_20v_30tasks").toAbsolutePath().normalize();
        Path directory = Files.createTempDirectory("carla-hot-reload-test-");
        Path vehicles = directory.resolve("vehicles_latest_api.json");
        Path locations = directory.resolve("locations.json");
        try {
            Files.copy(source.resolve("vehicles_latest_api.json"), vehicles, StandardCopyOption.REPLACE_EXISTING);
            Files.copy(source.resolve("locations.json"), locations, StandardCopyOption.REPLACE_EXISTING);
            VehicleRealtimeService service = new VehicleRealtimeService(directory);
            require(Math.abs(service.find("sim_000").longitude - 106.730553) < 0.000001,
                    "热加载测试初始位置不正确");

            String json = new String(Files.readAllBytes(vehicles), StandardCharsets.UTF_8);
            json = json.replaceFirst("106\\.730553", "106.700001");
            Files.write(vehicles, json.getBytes(StandardCharsets.UTF_8));
            Files.setLastModifiedTime(vehicles, FileTime.fromMillis(System.currentTimeMillis() + 2000));

            require(Math.abs(service.find("sim_000").longitude - 106.700001) < 0.000001,
                    "CARLA 快照文件修改后没有自动热加载");
        } finally {
            Files.deleteIfExists(vehicles);
            Files.deleteIfExists(locations);
            Files.deleteIfExists(directory);
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
}
