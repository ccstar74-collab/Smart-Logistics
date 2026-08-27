package com.smart_logistics.backend.security;

/**
 * WebSocket会话属性键常量。
 * 握手拦截器在连接建立时一次性写入权限范围，
 * 推送循环只读这些内存属性，不在实时推送路径中查询数据库。
 */
public final class WsSessionAttributes {

    /** Boolean.TRUE表示管理员，可见全部已登记业务车辆的推送 */
    public static final String ALLOW_ALL_VEHICLES = "ws.allowAllVehicles";

    /** Set&lt;String&gt;，该会话可见的车辆simCode集合 */
    public static final String ALLOWED_VEHICLE_SIM_CODES = "ws.allowedVehicleSimCodes";

    private WsSessionAttributes() {
    }
}
