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

    /** UserRole，该会话当前活跃身份的角色（/ws/alarms数据范围过滤用） */
    public static final String USER_ROLE = "ws.userRole";

    /** Long，货主身份id（OWNER推送过滤用，可能不存在） */
    public static final String OWNER_ID = "ws.ownerId";

    /** Long，司机身份id（DRIVER推送过滤用，可能不存在） */
    public static final String DRIVER_ID = "ws.driverId";

    private WsSessionAttributes() {
    }
}
