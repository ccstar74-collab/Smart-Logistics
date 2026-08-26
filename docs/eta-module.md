# ETA 计算模块

## 范围

ETA 由实时后端统一计算。硬件和模拟器只负责向 InfluxDB 写入 WGS84 GPS，
不得计算或上报正式 ETA。前端通过运输任务接口读取 ETA，并通过 WebSocket 接收更新。

仅状态为 `TRANSPORTING`、已配置完整起终点坐标、已绑定 `vehicle.sim_code` 且两分钟内有
GPS 的任务参与计算。旧任务的坐标字段允许为空，字段为空时跳过 ETA，不影响其他接口。

## 数据流

1. 定时任务读取 `TRANSPORTING` 运输任务。
2. 通过 `vehicle.sim_code` 查询 InfluxDB `vehicle_gps` 最新 GPS 和十分钟速度历史。
3. 将 WGS84 起终点转换为 GCJ-02，调用高德生成任务驾车 polyline；同一任务缓存该路线，
   不用当前位置到终点的直线距离代替道路距离。
4. 将当前 GPS 转为 GCJ-02 后投影到任务 polyline，计算沿路线到终点的剩余距离；偏离路线时
   将当前位置到最近投影点的距离计入剩余距离。
5. 使用 `40% 当前速度 + 60% 历史平均速度` 得到有效速度；缺少有效速度时使用路线参考速度。
6. 由剩余路线距离和有效速度计算 ETA，更新
   `transport_task.estimated_arrival_time` 与 `eta_calculated_at`。
7. 写库成功后通过 `/ws/logistics` 推送 `ETA_UPDATED` 消息。

## 前端规划路线接口

任务详情页通过以下接口获取 ETA 服务生成并缓存的同一条任务路线：

```http
GET /api/v1/transport-tasks/{id}/planned-route
```

响应中的 polyline 是高德地图可直接显示的 `GCJ02` 坐标：

```json
{
  "taskId": 12,
  "provider": "AMAP",
  "coordinateSystem": "GCJ02",
  "distanceMeters": 5500,
  "referenceDurationSeconds": 720,
  "generatedAt": "2026-08-26T16:00:00+08:00",
  "points": [
    {"longitude": 106.5701, "latitude": 29.4901},
    {"longitude": 106.6101, "latitude": 29.5201}
  ]
}
```

前端进入页面时请求一次并绘制 `points`。ETA 定时计算复用该缓存路线，
`ETA_UPDATED` 只推送变化的 ETA 数据，不重复携带整条 polyline。

## 运输任务坐标字段

创建、编辑和查询运输任务增加以下可空字段：

```json
{
  "startLongitude": 106.5712345,
  "startLatitude": 29.4934567,
  "endLongitude": 106.4432100,
  "endLatitude": 29.5023100
}
```

接口坐标统一为 WGS84。经纬度必须成对出现；旧客户端不传坐标仍可正常创建任务，
但该任务不会计算 ETA。

## 服务器配置

高德 Key 必须是“Web 服务 API”类型，只允许通过服务器环境变量配置：

```bash
ETA_ENABLED=true
AMAP_WEB_SERVICE_KEY=替换为服务器密钥
ETA_REFRESH_DELAY_MS=30000
ETA_GPS_MAX_AGE=PT2M
ETA_SPEED_HISTORY_WINDOW=PT10M
ETA_MIN_CHANGE=PT30S
ETA_FORCE_PERSIST_INTERVAL=PT2M
```

不要把真实 Key 写入 `application.yml` 或提交到 Git。

## 更新节流

- 默认每 30 秒扫描一次，不按 1 Hz GPS 消息直接写 MySQL。
- ETA 变化达到 30 秒时更新。
- ETA 变化较小时每两分钟至少刷新一次 `etaCalculatedAt`。
- 高德不可用时保留最近一次有效 ETA，并在日志中记录失败原因。
- 任务不再处于 `TRANSPORTING` 后停止刷新。

## 部署步骤

1. 按 `005 → 006（Cargo）→ 007（ETA）` 的固定顺序执行迁移；ETA 使用
   `docs/sql/007_transport_task_eta.sql`，不得占用或修改 `006`。
2. 在服务器后端环境文件增加 `ETA_ENABLED` 和 `AMAP_WEB_SERVICE_KEY`。
3. 构建并部署后端。
4. 给运输中的测试任务补齐 WGS84 起点和终点坐标。
5. 保持模拟器或真实板上报 GPS，观察后端 ETA 更新日志。

日志示例：

```text
ETA updated taskId=12 vehicle=real_001 remainingMeters=3862 offRouteMeters=8 speedKmh=31.4 eta=2026-08-26T16:35:20
```

任务详情接口会直接返回：

```json
{
  "estimatedArrivalTime": "2026-08-26T16:35:20+08:00",
  "etaCalculatedAt": "2026-08-26T16:21:00+08:00"
}
```

WebSocket 更新消息：

```json
{
  "type": "ETA_UPDATED",
  "taskId": 12,
  "vehicleId": "real_001",
  "estimatedArrivalTime": "2026-08-26T16:35:20+08:00",
  "etaCalculatedAt": "2026-08-26T16:21:00+08:00",
  "remainingDistanceMeters": 3862,
  "effectiveSpeedKmh": 31.4
}
```
