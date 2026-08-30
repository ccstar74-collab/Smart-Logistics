# 运输任务到达电子围栏 V1

## 目标

司机只有在车辆携带的新鲜 GPS 位于任务终点 200 米范围内时，才能将运输任务从
`TRANSPORTING` 更新为 `COMPLETED`。

V1 只实现“司机手动完成的后端强校验”，不包含停留两分钟后自动完成，也不修改
`transport_task` 表。

## 坐标与距离

- 任务终点坐标：GCJ02；
- MQTT/InfluxDB GPS：WGS84；
- 后端计算前先执行 WGS84 到 GCJ02 转换；
- 距离使用 Haversine 公式；
- 默认围栏半径：200 米；
- 可通过环境变量 `ARRIVAL_GEOFENCE_RADIUS_METERS` 调整。

## 查询司机是否可以完成任务

```http
GET /api/v1/transport-tasks/{taskId}/arrival-eligibility
Authorization: Bearer <driver-jwt>
```

围栏外示例：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "taskId": 9,
    "eligible": false,
    "distanceMeters": 428.6,
    "radiusMeters": 200.0,
    "latestLocationAt": "2026-08-30T16:00:00+08:00",
    "locationOnline": true,
    "reason": "OUTSIDE_GEOFENCE"
  }
}
```

`reason` 取值：

- `ARRIVAL_ALLOWED`：车辆位于围栏内；
- `TASK_NOT_TRANSPORTING`：任务不是运输中；
- `DESTINATION_COORDINATES_MISSING`：历史任务缺少终点坐标；
- `LOCATION_NOT_FOUND`：lookback 范围内没有 GPS；
- `LOCATION_OFFLINE`：存在旧 GPS，但已经离线；
- `OUTSIDE_GEOFENCE`：GPS 在线，但车辆位于围栏外。

## 司机完成任务

沿用原接口：

```http
PUT /api/v1/transport-tasks/{taskId}/status
Authorization: Bearer <driver-jwt>
Content-Type: application/json

{
  "status": "COMPLETED"
}
```

后端会重新读取最新 GPS 并再次判断围栏。即使绕过前端直接请求接口，围栏外也会
返回 `40902 STATE_CONFLICT`，且任务、货物和车辆状态均不会改变。

## 前端接入

1. 获取当前运输任务；
2. 对 `TRANSPORTING` 任务请求 `arrival-eligibility`；
3. 使用 `endLongitude/endLatitude` 和 `radiusMeters` 绘制 `AMap.Circle`；
4. `eligible=false` 时禁用“确认到达”按钮，并根据 `reason` 显示原因；
5. GPS WebSocket 更新后重新查询到达资格；无 WebSocket 时每 3～5 秒轮询；
6. `eligible=true` 时允许点击，但点击后仍以状态更新接口的最终结果为准；
7. 若状态更新返回 409，刷新到达资格并保持任务为 `TRANSPORTING`。

前端限制只用于改善交互，业务安全边界始终在后端。

## 后续版本

若要实现“连续多次进入确认、停留两分钟自动完成、每个任务独立配置半径”，再新增
围栏状态表或任务围栏快照，并由 GPS 消费链路持久化进入/离开事件。不要使用进程内
`Map<taskId, count>` 作为正式状态，避免服务重启或多实例部署后丢失计数。
