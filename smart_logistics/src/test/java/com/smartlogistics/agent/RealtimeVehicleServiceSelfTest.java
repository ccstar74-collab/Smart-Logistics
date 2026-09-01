package com.smartlogistics.agent;

import java.util.Map;

public final class RealtimeVehicleServiceSelfTest {
    public static void main(String[] args) {
        RealtimeVehicleService service=new RealtimeVehicleService("ws://127.0.0.1:1/ws/vehicle-locations",refresh->"test-token",false);
        ok(service.acceptForTest("pong")==false,"pong 不应作为业务消息");
        ok(service.acceptForTest("""
                {"vehicleId":"12","simCode":"sim_001","latitude":29.50,"longitude":106.58,
                 "speed":30.0,"direction":90.0,"collectedAt":"2026-08-28T12:00:00+08:00"}
                """),"新协议消息未接收");
        Map<String,Object> bySim=service.get("sim_001"),byVehicle=service.get("12");
        ok(bySim!=null&&byVehicle==bySim,"未同时按 simCode 和 vehicleId 缓存");
        ok("WGS84".equals(bySim.get("coordSystem")),"坐标系未标记为 WGS84");
        ok(((Number)bySim.get("longitude")).doubleValue()==106.58,"经度错误");
        ok(service.acceptForTest("""
                {"gps":{"vehicleId":"sim_legacy","latitude":29.6,"longitude":106.6,"speed":10}}
                """),"旧 gps 信封兼容失败");
        ok(service.get("sim_legacy")!=null,"旧 vehicleId=simCode 兼容失败");
        ok(service.acceptForTest("""
                {"data":{"gps":{"vehicle_id":3,"sim_code":"sim_003","lng":106.60,"lat":29.44,
                 "speed":43.1,"timestamp":"2026-09-01T11:50:05+08:00","coordinateSystem":"WGS84"}}}
                """),"Vue data.gps/snake_case/字段别名兼容失败");
        Map<String,Object> vue=service.get("sim_003");
        ok(vue!=null&&service.get("3")==vue&&"2026-09-01T11:50:05+08:00".equals(vue.get("collectedAt")),"Vue 消息缓存或时间字段错误");
        ok(service.acceptForTest("""
                [{"deviceId":"sim_004","lon":106.61,"lat":29.45,"collectTime":"2026-09-01T11:51:00+08:00"},
                 {"vehicle_id":5,"sim_code":"sim_005","longitude":106.62,"latitude":29.46}]
                """),"数组位置消息兼容失败");
        ok(service.get("sim_004")!=null&&service.get("sim_005")!=null,"数组位置未逐条缓存");
        ok(!service.acceptForTest("""
                {"vehicleId":"13","simCode":"sim_013"}
                """),"缺少坐标的消息不应缓存");
        System.out.println("实时位置 WebSocket/Vue 协议兼容自检通过（13/13）");
    }
    private static void ok(boolean value,String message){if(!value)throw new AssertionError(message);}
}
