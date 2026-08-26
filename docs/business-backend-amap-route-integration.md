# ETA规划路线API与GPS模拟器联调合同

## 一、最终职责

```text
TransportTask业务模块
提供任务起终点、状态和绑定车辆
        ↓
ETA模块（运行在Spring Boot后端）
调用高德生成并缓存GCJ02规划路线
提供planned-route接口
        ↓
GPS模拟器
按taskId读取路线
将GCJ02 points转换为WGS84
沿单程路线发布GPS和速度
        ↓
MQTT / InfluxDB / ETA / WebSocket
```

职责冻结：

- 高德 Web 服务 Key 只配置在 ETA 后端；
- TransportTask业务模块不重复调用高德；
- GPS模拟器不调用高德、不计算正式ETA；
- MQTT和InfluxDB中的GPS统一使用WGS84；
- ETA返回的高德路线使用GCJ02。

## 二、正式接口

```http
GET /api/v1/transport-tasks/{taskId}/planned-route
Authorization: Bearer {TOKEN}
```

接口路径使用 `planned-route` 连字符，不使用 `planned_route`。

成功响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "taskId": 1001,
    "vehicleDeviceCode": "sim_000",
    "provider": "AMAP",
    "coordinateSystem": "GCJ02",
    "distanceMeters": 2943,
    "referenceDurationSeconds": 420,
    "generatedAt": "2026-08-26T16:00:00+08:00",
    "points": [
      {"longitude": 106.735012, "latitude": 29.610634},
      {"longitude": 106.741312, "latitude": 29.615804},
      {"longitude": 106.751240, "latitude": 29.618910},
      {"longitude": 106.759396, "latitude": 29.620115}
    ]
  }
}
```

## 三、字段要求

| 字段 | 要求 |
|---|---|
| `taskId` | 必须与路径中的任务ID一致 |
| `vehicleDeviceCode` | 来自任务绑定车辆的 `vehicle.sim_code` |
| `provider` | 当前固定为 `AMAP` |
| `coordinateSystem` | 当前固定为 `GCJ02` |
| `distanceMeters` | 正整数，完整路线总距离，单位米 |
| `referenceDurationSeconds` | 正整数，高德静态参考耗时，单位秒 |
| `generatedAt` | 带时区的ISO 8601时间；起终点变化并重新规划时更新 |
| `points` | 至少两个点，按起点到终点顺序排列 |
| `points[].longitude` | 经度，范围 `-180..180` |
| `points[].latitude` | 纬度，范围 `-90..90` |

后端错误约定：

- `401`：Token缺失或过期；
- `403`：当前账号无权查看任务；
- `404`：任务或绑定车辆不存在；
- `409`：起终点坐标不完整，或车辆没有配置 `sim_code`；
- `500/503`：高德或ETA路线服务暂时不可用。

路线查询应允许 `WAITING` 和 `TRANSPORTING` 任务。模拟器需要在车辆开始移动前取得路线。

## 四、直接启动任务路线模拟

```powershell
$env:SMART_LOGISTICS_API_TOKEN = '后端登录获得的Token'

python .\tools\mqtt_data_generator.py `
  --credentials "$env:USERPROFILE\.smart-logistics\mqtt_cloud.env" `
  --business-api-base 'http://服务器地址:8080' `
  --task-id 1001 `
  --vehicles 20 `
  --duration 0 `
  --interval 1 `
  --anomaly-rate 0
```

如果接口不鉴权，可以不设置 `SMART_LOGISTICS_API_TOKEN`。

可以重复指定任务：

```powershell
python .\tools\mqtt_data_generator.py `
  --credentials "$env:USERPROFILE\.smart-logistics\mqtt_cloud.env" `
  --business-api-base 'http://服务器地址:8080' `
  --task-id 1001 `
  --task-id 1002 `
  --vehicles 20 `
  --duration 0 `
  --anomaly-rate 0
```

任务返回的 `vehicleDeviceCode` 必须存在于本次车队。例如：

- `--vehicles 20` 包含 `sim_000` 至 `sim_019`；
- 任务绑定 `sim_025` 时至少需要 `--vehicles 26`。

## 五、可选的MQTT刷新通知

模拟器也支持先启动，再接收任务路线刷新通知：

Topic：

```text
iot/carla/vehicle/{vehicleDeviceCode}/command
```

Payload：

```json
{
  "schema_version": "1.0",
  "command_id": "CMD_ROUTE_1001_REFRESH",
  "vehicle_id": "sim_000",
  "task_id": 1001,
  "command_type": "TASK_ROUTE_READY",
  "timestamp": "2026-08-26T08:00:00.000Z"
}
```

通知不携带完整路线，也不再携带 `routeId/routeVersion`。模拟器收到通知后重新请求 `planned-route`。`command_id` 用于QoS 1消息幂等；同一 `generatedAt` 的路线不会重复安装。

手工发送并等待ACK：

```powershell
python .\tools\mqtt_route_command.py `
  --credentials "$env:USERPROFILE\.smart-logistics\mqtt_cloud.env" `
  --vehicle sim_000 `
  --task-id 1001
```

## 六、模拟器行为

1. 请求 `planned-route` 并验证统一 `ApiResponse`；
2. 校验任务ID、车辆设备编号、坐标系、距离、耗时和路线点；
3. 将GCJ02路线点转换为WGS84；
4. 把任务绑定车辆放到第一个路线点；
5. 按道路点顺序逐段前进并发布GPS、速度和方向；
6. 到达最后一个点后速度变为0，状态变为“已送达”，不自动折返；
7. MQTT断线时暂停发布，自动重连后恢复车辆在线状态和GPS发布。

## 七、ETA时间字段

- `referenceDurationSeconds`：高德规划路线时返回的静态参考耗时；
- `estimatedArrivalTime`：ETA根据实时GPS、剩余路线和有效速度计算出的动态预计到达时间；
- `etaCalculatedAt`：动态ETA最近一次计算时间。

模拟器只使用路线，不生成第二套正式ETA。

## 八、联调验收

1. 任务存在完整WGS84起终点；
2. 任务绑定车辆已配置 `vehicle.sim_code`；
3. `planned-route` 返回GCJ02 points和正确的 `vehicleDeviceCode`；
4. 模拟器打印 `[ROUTE][LOADED]`；
5. MQTT/InfluxDB中出现对应车辆的WGS84 GPS；
6. 地图上的车辆沿规划道路移动；
7. ETA更新 `estimatedArrivalTime/etaCalculatedAt`；
8. WebSocket收到对应 `taskId` 的 `ETA_UPDATED`；
9. 模拟器运行过程中没有高德HTTP请求。
