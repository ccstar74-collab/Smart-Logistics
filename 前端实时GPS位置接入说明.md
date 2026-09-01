# 智慧物流前端实时 GPS 位置接入说明

## 1. 文档目的

本文说明当前前端如何取得车辆实时 GPS、如何将硬件设备与业务车辆关联、如何在高德地图上显示位置，以及完成实时定位页面所涉及的接口。

本文以当前项目代码为准，不新增或假设云端不存在的接口。前端相关实现主要位于：

- `src/views/TrackingV2.vue`：实时车辆监控页面及数据编排。
- `src/composables/useVehicleWebSocketV2.js`：GPS WebSocket 连接、解析、过滤和重连。
- `src/components/AMapView.vue`：高德地图加载、坐标转换和车辆标记更新。
- `src/api/http.js`：车辆、任务、实时位置和历史轨迹 REST 接口。
- `src/utils/realtimeScope.js`：按任务、车辆编号和 SIM 编号限制可见车辆。

## 2. 整体数据链路

```text
GPS 硬件
   ↓ 上报位置（上报接口属于后端/硬件协议，前端代码中未定义）
云端实时定位服务
   ├─ 保存车辆最新位置和历史位置
   ├─ 通过 REST 提供最新位置快照
   └─ 通过 WebSocket 推送实时位置
          ↓
前端 useVehicleWebSocketV2
   ├─ 使用 JWT 建立连接
   ├─ 解析经纬度和设备编号
   ├─ 关联业务车辆
   ├─ 拒绝异常、过期和跳变坐标
   └─ 更新车辆位置数组
          ↓
TrackingV2 按当前用户和运输任务过滤车辆
          ↓
AMapView 将坐标转换为高德坐标并更新车辆标记
```

需要注意：前端不是 GPS 硬件数据的接收服务器。硬件如何把坐标写入云端，由硬件协议和后端服务决定；当前前端只消费云端提供的 REST 与 WebSocket 数据。

## 3. 当前运行地址

本地开发配置位于 `.env.local`：

```text
业务 REST API：/api/v1
Vite 代理目标：http://111.170.148.177:58080
GPS WebSocket：ws://111.170.148.177:58080/ws/vehicle-locations
```

浏览器请求 `/api/v1/...` 时，开发服务器会把请求转发到 `111.170.148.177:58080`。

WebSocket 使用完整云端地址，并自动附加登录 JWT：

```text
ws://111.170.148.177:58080/ws/vehicle-locations?token=<登录JWT>
```

生产环境如果部署在 HTTPS 下，应使用 `https://` 和 `wss://`，否则浏览器可能因为混合内容安全策略阻止连接。

## 4. 获取实时位置涉及的接口

### 4.1 必需接口

#### 4.1.1 登录接口

```http
POST /api/v1/auth/login
```

作用：获取 JWT。前端将令牌保存为 `localStorage.accessToken`，REST 和 WebSocket 都使用这个令牌鉴权。

没有有效令牌时，GPS WebSocket 不会连接，并显示“缺少登录 Token，无法订阅车辆实时位置”。

#### 4.1.2 业务车辆列表

```http
GET /api/v1/vehicles?page=1&pageSize=100
```

作用：取得业务车辆主数据，主要使用以下字段：

```json
{
  "id": 3,
  "plateNumber": "渝A33333",
  "simCode": "sim_999",
  "warehouseId": 1
}
```

其中：

- `id` 是业务车辆数据库编号。
- `plateNumber` 用于地图标记和详情展示。
- `simCode` 是 GPS 设备与业务车辆之间的重要关联键。
- `warehouseId` 用于仓库管理员只查看本仓车辆。

#### 4.1.3 实时车辆字典

```http
GET /api/vehicle/list
```

注意：该接口当前不带 `/v1`。

作用：补充实时定位系统中的设备编号与业务车辆编号映射。前端会同时使用业务车辆列表和此接口构建设备字典。

如果硬件消息只包含 `sim_code`，该字典必须能够把它映射到业务车辆，否则位置可能无法归属到正确车牌。

#### 4.1.4 最新位置批量快照

```http
GET /api/v1/vehicles/locations/latest
```

作用：页面首次加载以及每 5 秒 REST 刷新时取得车辆最新位置。它解决了以下问题：

- 页面打开之前已经产生的位置不会因为错过 WebSocket 消息而丢失。
- WebSocket 临时断开时仍可恢复最新位置。
- WebSocket 重新连接后可以用快照重新初始化地图。

如果批量接口失败，前端会按车辆逐个降级请求：

```http
GET /api/v1/vehicles/{vehicleId}/location/latest
```

#### 4.1.5 GPS 实时推送 WebSocket

```text
GET ws://111.170.148.177:58080/ws/vehicle-locations?token=<JWT>
```

作用：持续接收车辆最新 GPS 数据，并实时更新地图。

前端连接行为：

1. 登录后读取 JWT。
2. 建立 WebSocket。
3. 连接成功后发送文本 `ping`。
4. 每 25 秒发送一次 `ping`。
5. 收到文本 `pong` 后重置心跳计数。
6. 连续 3 个心跳周期没有收到 `pong` 时主动断开并重连。
7. 重连延迟按 1、2、4、8 秒递增，最大 30 秒。

### 4.2 实时监控页面需要的业务辅助接口

#### 4.2.1 运输任务列表

```http
GET /api/v1/transport-tasks?page=1&pageSize=100
```

作用：确定当前账号可以查看哪些任务，以及任务绑定的 `vehicleId`。前端不会把 WebSocket 中的所有设备无条件显示出来，而是结合当前角色和任务进行过滤。

#### 4.2.2 货物列表

```http
GET /api/v1/cargos?page=1&pageSize=100
```

作用：显示任务对应的货物名称和货物编号。它不负责产生 GPS 位置。

#### 4.2.3 单个任务详情

```http
GET /api/v1/transport-tasks/{taskId}
```

作用：每次刷新实时位置时同步任务详情，例如实时 ETA、剩余距离和任务状态。

### 4.3 路线和历史轨迹接口

以下接口不是取得“当前坐标”的必要条件，但用于地图路线和轨迹展示。

#### 当前规划路线

```http
GET /api/v1/transport-tasks/{taskId}/planned-route
```

#### 路线版本

```http
GET /api/v1/transport-tasks/{taskId}/routes
```

#### 任务历史轨迹

```http
GET /api/v1/transport-tasks/{taskId}/track-points
```

如果任务轨迹接口返回业务 404，前端会降级到车辆历史位置：

```http
GET /api/v1/vehicles/{vehicleId}/location-history?startTime=...&endTime=...
```

代码中还声明了按数据库编号或 SIM 编号查询历史位置的接口：

```http
GET /api/v1/vehicles/db/{dbId}/location-history
GET /api/v1/vehicles/by-sim-code/{simCode}/location-history
```

当前实时监控主流程优先使用任务轨迹接口和车辆编号历史接口。

### 4.4 不属于 GPS 位置的接口

```text
ws://111.170.148.177:58080/ws/logistics?token=<JWT>
```

这是物流 ETA 推送连接，负责更新时间预估等任务字段，不负责车辆经纬度。排查 GPS 时应将它与 `/ws/vehicle-locations` 区分。

告警 WebSocket、通知 WebSocket同样不是 GPS 坐标来源。

## 5. WebSocket 消息字段要求

当前前端兼容以下字段别名。

### 5.1 坐标字段

```text
经度：longitude / lon / lng
纬度：latitude / lat
```

建议后端统一发送：

```json
{
  "vehicleId": 3,
  "simCode": "sim_999",
  "longitude": 106.504805,
  "latitude": 29.618176,
  "speed": 42.5,
  "direction": 135,
  "timestamp": "2026-09-01T10:30:00+08:00",
  "coordinateSystem": "WGS84"
}
```

消息也可以包在 `gps`、`data.gps` 或 `data` 中；数组消息会逐条处理。

### 5.2 设备与车辆关联字段

前端按以下顺序寻找设备键：

```text
vehicleId / vehicle_id / simCode / sim_code / deviceId / device_id
```

然后使用车辆字典将设备键转换为业务 `vehicleId`。硬件端、实时服务、业务车辆表三者必须对同一设备编号达成一致。

### 5.3 时间和序号字段

```text
序号：sequence / seq
时间：timestamp / collectedAt / collectTime
```

序号用于拒绝重复或倒序消息；时间用于拒绝晚到消息和不合理位置跳变。

## 6. 前端 GPS 数据校验

WebSocket 数据进入地图前会经过以下过滤：

1. 经度和纬度必须能转换为有限数字。
2. 经度必须在 `-180～180`，纬度必须在 `-90～90`。
3. `(0, 0)` 被视为无效坐标。
4. 消息必须能取得车辆编号或设备编号。
5. 相同车辆的新序号必须大于旧序号。
6. 新消息时间必须晚于旧消息。
7. 两分钟以内移动超过 300 米时，会计算隐含速度；超过 220 公里/小时的跳点被拒绝。

这些规则用于避免硬件初始化坐标、历史消息倒灌或异常漂移把车辆瞬间移动到其他城市。

## 7. 用户和车辆可见范围

实时监控不会直接展示 WebSocket 的全部车辆：

- 货主和司机：只显示其可见任务绑定的车辆。
- 仓库管理员：只显示已登记且属于当前仓库的车辆。
- 调度员和管理员：可查看任务范围内的车队位置。

过滤同时兼容业务 `vehicleId` 和设备 `simCode`。因此即使 WebSocket 只推送 SIM 编号，只要字典映射正确，仍可显示对应车辆。

## 8. 高德地图显示过程

### 8.1 地图初始化

前端使用高德 JS API 2.0，密钥来自：

```text
VITE_AMAP_KEY
VITE_AMAP_SECURITY_CODE
```

加载插件：

- `AMap.Scale`
- `AMap.ToolBar`

### 8.2 坐标系

GPS 硬件通常产生 WGS84，高德地图使用 GCJ-02。`AMapView.vue` 会把默认 GPS 坐标从 WGS84 转换为 GCJ-02 后再设置车辆标记。

当前实现中，WebSocket 解析结果没有保留硬件消息的坐标系字段，因此地图默认按 WGS84 处理。若云端推送的实际数据已经是 GCJ-02，会发生二次转换和位置偏移。后续修复时应让后端明确返回坐标系，并由前端完整保留该字段。

### 8.3 标记更新

- 第一次收到车辆坐标：创建 `AMap.Marker`。
- 后续收到同一车辆坐标：调用 `marker.setPosition()` 更新位置。
- 车辆不再属于当前可见范围：从地图移除标记。
- 选择具体任务或车辆时：地图聚焦对应车辆和路线。

## 9. 页面加载时序

实时车辆监控页面当前按以下顺序工作：

1. 请求货物、任务、业务车辆和告警数据。
2. 使用业务车辆列表创建车辆字典。
3. 异步请求 `/api/vehicle/list` 补充实时设备映射。
4. 请求最新位置快照并写入实时位置状态。
5. 建立 GPS WebSocket。
6. 建立 ETA WebSocket。
7. 加载所选任务的规划路线和历史轨迹。
8. 每 5 秒使用 REST 刷新最新位置和任务详情。
9. WebSocket 有新 GPS 时即时更新车辆状态和地图标记。

该设计属于“REST 快照兜底 + WebSocket 实时推送”，不是只依赖单一连接。

## 10. 硬件联调检查清单

### 后端和设备

- 硬件是否持续向正确的云端环境上报。
- 上报记录是否包含经度、纬度、设备编号和采集时间。
- 硬件设备编号是否与车辆 `simCode` 一致。
- `/api/vehicle/list` 是否能查到该设备映射。
- `/vehicles/locations/latest` 是否已经返回该车辆的位置。
- `/ws/vehicle-locations` 是否向当前 JWT 推送该车辆消息。
- WebSocket 是否正确回复文本 `pong`。

### 浏览器

- `localStorage.accessToken` 是否存在且未过期。
- Network → WS 中连接状态是否为 `101 Switching Protocols`。
- WS Messages 中是否持续收到合法 JSON。
- Console 是否有高德 Key、安全密钥、坐标或 Marker 异常。
- 页面显示的 GPS 设备编号是否等于硬件编号。

### 坐标数据

- 经度和纬度是否没有颠倒。
- 是否为十进制度，而不是未经转换的 NMEA 度分格式。
- 是否错误放大了 `10^6` 或 `10^7` 倍。
- 时间戳单位是秒、毫秒还是纳秒。
- 坐标系是 WGS84 还是 GCJ-02。
- 上报频率是否过高；当前地图更新尚未做专门节流，建议联调时先使用每秒 1 次。

## 11. 常见问题判断

### REST 有位置、WebSocket 没消息

说明硬件上报和位置存储可能正常，但实时推送、JWT 权限或 WebSocket 订阅存在问题。

### WebSocket 有消息、地图没有车辆

重点检查：

- 消息设备编号能否映射到业务车辆。
- 当前账号是否能看到该车辆绑定的任务。
- 经纬度字段名称和数值是否符合要求。
- 消息是否被跳点或时间倒序规则拒绝。

### 地图位置整体偏移

重点确认 WGS84/GCJ-02，避免已经是 GCJ-02 的数据再次转换。

### 接入硬件后地图卡死

重点检查硬件上报频率和单条消息中包含的数据量。当前每次有效 WebSocket 消息都会触发车辆数组和地图标记更新；建议先将硬件频率控制为每秒 1 次，并记录浏览器 Console 的第一条异常。

## 12. 最小可用接口集合

如果只要求“登录后在地图看到一辆车的当前实时位置”，最少需要：

1. `POST /api/v1/auth/login`
2. `GET /api/v1/vehicles`
3. `GET /api/vehicle/list`（当设备编号与车辆编号需要映射时）
4. `GET /api/v1/vehicles/locations/latest`
5. `WS /ws/vehicle-locations?token=<JWT>`

如果还要按运输业务控制可见范围和显示货物信息，则增加：

6. `GET /api/v1/transport-tasks`
7. `GET /api/v1/cargos`

如果还要显示路线和历史轨迹，则增加：

8. `GET /api/v1/transport-tasks/{taskId}/planned-route`
9. `GET /api/v1/transport-tasks/{taskId}/routes`
10. `GET /api/v1/transport-tasks/{taskId}/track-points`
