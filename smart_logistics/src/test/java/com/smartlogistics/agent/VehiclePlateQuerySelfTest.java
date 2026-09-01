package com.smartlogistics.agent;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class VehiclePlateQuerySelfTest {
    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/vehicles", exchange -> {
            byte[] body = """
                    {"code":200,"message":"success","data":{"records":[
                      {"id":16,"plateNumber":"渝A11111","simCode":"sim_001","driverName":"李四","type":"VAN","status":"TRANSPORTING"},
                      {"id":20,"plateNumber":"渝A66666","simCode":null,"driverName":null,"type":"VAN","status":"IDLE"}
                    ],"total":2}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            BusinessDataService service = new BusinessDataService(
                    "http://127.0.0.1:" + server.getAddress().getPort(), "", 3000);

            BusinessDataService.BusinessAnswer profile = service.answerBySelection(
                    selection("GET_VEHICLE_PROFILE", "渝A11111"), "");
            require(profile != null && "get_vehicle_profile".equals(profile.toolData.get("tool")), "车牌资料没有命中车辆工具");
            require(profile.answer.contains("李四") && profile.answer.contains("TRANSPORTING"), "车牌资料缺少司机或状态");

            BusinessDataService.BusinessAnswer location = service.answerBySelection(
                    selection("GET_VEHICLE_LOCATION", "渝A11111"), "");
            require(location != null && "realtime_vehicle_websocket".equals(location.toolData.get("tool")), "车牌位置没有转到 WebSocket 工具");
            require("sim_001".equals(location.toolData.get("simCode")), "车牌没有解析出正确 simCode");
            require("渝A11111".equals(location.toolData.get("plateNumber")), "位置工具没有保留车牌号");
            require(!"guardrail".equals(location.toolData.get("tool")), "车牌位置错误落入 guardrail");

            BusinessDataService.BusinessAnswer fallbackProfile = service.answerIfBusinessQuery("渝A11111当前是什么状态？", "");
            require(fallbackProfile != null && fallbackProfile.answer.contains("TRANSPORTING"), "规则兜底不能按车牌查询状态");

            BusinessDataService.BusinessAnswer fallbackLocation = service.answerIfBusinessQuery("查询一下渝 A11111现在在哪", "");
            require(fallbackLocation != null && "sim_001".equals(fallbackLocation.toolData.get("simCode")), "规则兜底不能处理带空格的车牌位置查询");

            BusinessDataService.BusinessAnswer unbound = service.answerBySelection(
                    selection("GET_VEHICLE_LOCATION", "渝A66666"), "");
            require(unbound.answer.contains("尚未绑定 GPS"), "未绑定 GPS 的车辆提示不正确");

            BusinessDataService.BusinessAnswer missing = service.answerBySelection(
                    selection("GET_VEHICLE_PROFILE", "渝A99999"), "");
            require(missing.answer.contains("未找到车牌号"), "未知车牌提示不正确");

            System.out.println("车牌号车辆查询自检通过（7/7）");
        } finally {
            server.stop(0);
        }
    }

    private static ToolSelection selection(String intent, String plate) {
        return new ToolSelection(intent, 0.99, Map.of("plateNumber", plate), false, "");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
