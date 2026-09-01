package com.smartlogistics.agent;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

final class QwenToolRouter {
    private static final Set<String> ALLOWED = Set.of(
            "GET_CURRENT_USER", "QUERY_TASKS", "GET_CURRENT_TASK", "GET_CURRENT_TASK_ETA",
            "GET_TASK_DETAIL", "GET_TASK_ETA", "GET_CARGO_DETAIL", "GET_VEHICLE_LOCATION", "GET_VEHICLE_PROFILE",
            "GET_VEHICLE_TRAJECTORY", "GET_PLANNED_ROUTE", "QUERY_CARGOS",
            "QUERY_AVAILABLE_CARGOS", "QUERY_VEHICLES", "QUERY_AVAILABLE_VEHICLES",
            "QUERY_ALARMS", "QUERY_DISPATCH_COMMANDS", "QUERY_NOTIFICATIONS", "QUERY_UNREAD_NOTIFICATIONS", "GET_NOTIFICATION_UNREAD_COUNT",
            "SUMMARIZE_OPERATIONS", "VEHICLE_CREATE", "ASSIGN_VEHICLE_DRIVER", "CARGO_INBOUND", "CARGO_OUTBOUND", "CREATE_TRANSPORT_TASK",
            "GENERAL_LOGISTICS_QUESTION", "UNKNOWN");
    private static final String INSTRUCTIONS = """
            你是智慧物流系统的工具路由器。你不回答用户问题，只选择一个工具并提取参数。
            只允许 intent 使用以下值：
            GET_CURRENT_USER, QUERY_TASKS, GET_CURRENT_TASK, GET_CURRENT_TASK_ETA,
            GET_TASK_DETAIL, GET_TASK_ETA, GET_CARGO_DETAIL, GET_VEHICLE_LOCATION, GET_VEHICLE_PROFILE,
            GET_VEHICLE_TRAJECTORY, GET_PLANNED_ROUTE, QUERY_CARGOS, QUERY_AVAILABLE_CARGOS,
            QUERY_VEHICLES, QUERY_AVAILABLE_VEHICLES, QUERY_ALARMS, QUERY_DISPATCH_COMMANDS,
            QUERY_NOTIFICATIONS, QUERY_UNREAD_NOTIFICATIONS, GET_NOTIFICATION_UNREAD_COUNT, SUMMARIZE_OPERATIONS, VEHICLE_CREATE, CARGO_INBOUND, CARGO_OUTBOUND,
            ASSIGN_VEHICLE_DRIVER, CREATE_TRANSPORT_TASK, GENERAL_LOGISTICS_QUESTION, UNKNOWN。
            只输出 JSON，不要 Markdown、解释、URL、HTTP 方法、SQL 或代码。
            parameters 还允许使用 cargoTypeId、cargoTypeName、warehouseId、warehouseName、warehouseNo。
            输出格式：{"intent":"...","confidence":0.0,"parameters":{"taskId":null,"taskNo":null,"cargoId":null,"cargoNo":null,"cargoName":null,"description":null,"weight":null,"volume":null,"ownerId":null,"ownerName":null,"vehicleId":null,"simCode":null,"plateNumber":null,"vehicleType":null,"capacity":null,"driverId":null,"driverName":null,"startLocation":null,"startCity":null,"startLongitude":null,"startLatitude":null,"endLocation":null,"endCity":null,"endLongitude":null,"endLatitude":null,"planStartTime":null,"planEndTime":null},"needsClarification":false,"clarificationQuestion":null}
            用户询问自己当前任务/货物还有多久完成、到达、送达，选择 GET_CURRENT_TASK_ETA。
            用户查询全部通知或消息中心选择 QUERY_NOTIFICATIONS；查询未读通知列表选择 QUERY_UNREAD_NOTIFICATIONS；只问未读通知有几条选择 GET_NOTIFICATION_UNREAD_COUNT。
            查询具体货物时选择 GET_CARGO_DETAIL 并提取 cargoNo；不得要求用户提供货物内部 ID。
            查询具体运单、任务详情、ETA、规划路线时优先提取 taskNo；不得要求用户提供任务内部 ID。
            用户给出明确任务 ID 并询问 ETA，选择 GET_TASK_ETA。
            车辆当前位置、附近地点、速度、方向选择 GET_VEHICLE_LOCATION。
            司机、车牌、车型、载重、状态、车辆档案选择 GET_VEHICLE_PROFILE。
            用户可以用 simCode、车辆内部 ID 或车牌号指定车辆；出现车牌号时必须原样提取到 plateNumber。
            用户明确要求把车辆绑定、分配、更换给某位司机时选择 ASSIGN_VEHICLE_DRIVER。车辆提取 vehicleId、plateNumber 或 simCode；司机优先提取 driverId，没有 ID 时提取 driverName。不得把“查询车辆司机是谁”识别为绑定操作。
            例如“渝A11111现在在哪”选择 GET_VEHICLE_LOCATION 并设置 plateNumber=渝A11111；“渝A11111的状态/司机/车型”选择 GET_VEHICLE_PROFILE。
            历史位置、去过哪里、轨迹选择 GET_VEHICLE_TRAJECTORY。
            只有用户明确要求立即办理、创建、执行时才选择写操作；询问流程、规则、能否办理时不得选择写操作。
            用户明确要求新增、添加、录入车辆时选择 VEHICLE_CREATE，必须提取 plateNumber、simCode、vehicleType、capacity，以及车辆归属仓库 warehouseId/warehouseName/warehouseNo 中至少一项。capacity 统一换算为公斤；车型统一为 TRUCK、VAN、REFRIGERATED 之一，货车/卡车为 TRUCK，厢式车/面包车为 VAN，冷链车/冷藏车为 REFRIGERATED。
            用户明确要求货物入库时选择 CARGO_INBOUND，必须提取 cargoNo、cargoName、weight、volume，以及货物种类 cargoTypeId/cargoTypeName 中至少一项、入库仓库 warehouseId/warehouseName/warehouseNo 中至少一项；description 可选。重量统一为公斤，体积统一为立方米。
            用户明确要求货物出库并安排运输时选择 CARGO_OUTBOUND。用户明确要求创建订单、运输订单或运输任务时选择 CREATE_TRANSPORT_TASK。
            CARGO_OUTBOUND 和 CREATE_TRANSPORT_TASK 都必须提取或从上下文获得 cargoNo、ownerId/ownerName、vehicleId/plateNumber/simCode、startLocation、endLocation、planStartTime、planEndTime。用户没有提供经纬度时不要猜测，保持 startLongitude/startLatitude/endLongitude/endLatitude 为 null，由高德地址解析工具自动补齐。地址中能识别城市时同时提取 startCity、endCity；地址过于模糊时 needsClarification=true，要求补充城市、区县或街道。
            planStartTime 和 planEndTime 必须转换为带时区的 ISO 8601 时间，例如 2026-08-30T12:00:00+08:00。
            写操作只允许仓库管理员执行，权限由业务后端根据用户 JWT 最终校验，不得伪造角色。
            缺少执行必需参数时 needsClarification=true，并给出简短澄清问题。
            除 VEHICLE_CREATE、ASSIGN_VEHICLE_DRIVER、CARGO_INBOUND、CARGO_OUTBOUND、CREATE_TRANSPORT_TASK 外，不得选择其他新增、修改、删除操作。
            """;

    private final ModelClient model;
    QwenToolRouter(ModelClient model) { this.model = model; }

    ToolSelection select(String question, String context) throws IOException {
        if (!model.enabled()) return null;
        String input = "最近上下文：\n" + (context == null ? "（无）" : context)
                + "\n\n用户问题：\n" + question;
        String raw = model.answer(INSTRUCTIONS, input).trim();
        int begin = raw.indexOf('{'), end = raw.lastIndexOf('}');
        if (begin < 0 || end < begin) throw new IOException("工具路由模型未返回 JSON");
        Map<String,Object> json = Json.object(raw.substring(begin, end + 1));
        String intent = text(json.get("intent")).toUpperCase(java.util.Locale.ROOT);
        if (!ALLOWED.contains(intent)) throw new IOException("工具路由模型返回未知意图");
        double confidence = number(json.get("confidence"), 0);
        @SuppressWarnings("unchecked")
        Map<String,Object> parameters = json.get("parameters") instanceof Map
                ? (Map<String,Object>) json.get("parameters") : Map.of();
        boolean clarification = Boolean.TRUE.equals(json.get("needsClarification"));
        return new ToolSelection(intent, confidence, parameters, clarification,
                text(json.get("clarificationQuestion")));
    }

    private static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private static double number(Object value, double fallback) {
        if (value instanceof Number) return ((Number)value).doubleValue();
        try { return Double.parseDouble(String.valueOf(value)); } catch (Exception e) { return fallback; }
    }
}
