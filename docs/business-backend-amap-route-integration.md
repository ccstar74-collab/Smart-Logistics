# 业务路线API与GPS模拟器最终联调合同

## 1. 最终数据流

```text
前端选择真实起点/终点
        ↓
业务后端创建TransportTask（007中的任务坐标为WGS84）
        ↓
业务后端异步调用高德并保存GCJ02 polyline、距离、静态时长
        ↓
路线READY后发布TASK_ROUTE_READY（只带路线引用）
        ↓
模拟器按taskId请求业务route API
        ↓
GCJ02 polyline转换成WGS84，沿单程路线生成GPS和速度
        ↓
MQTT / InfluxDB（WGS84）
        ↓
实时后端按保存路线计算剩余距离和动态ETA，WebSocket推送前端
```

职责冻结：高德Key只属于业务后端；模拟器不调用高德、不规划道路、不保存业务路线，也不计算正式动态ETA。

## 2. migration边界

- `004_vehicle_sim_code.sql`：`vehicle.sim_code`；
- `005_transport_task_status_record.sql`：任务状态记录；
- `006`：Cargo；
- `007_transport_task_eta.sql`：`start_longitude / start_latitude / end_longitude / end_latitude / eta_calculated_at`，这些任务坐标是WGS84；
- `008`：由业务后端负责路线表，IoT分支不创建或修改该migration。

路线表中的高德polyline使用GCJ02，不改变007任务坐标的WGS84约定。

## 3. route API

```http
GET /api/v1/transport-tasks/{taskId}/route
Authorization: Bearer <token>
```

READY示例：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "taskId": 1001,
    "routeId": "ROUTE_1001",
    "routeVersion": 1,
    "routeStatus": "READY",
    "vehicleId": 1,
    "vehicleDeviceCode": "sim_000",
    "coordinateSystem": "GCJ02",
    "startLocation": "重庆果园港装卸点A",
    "startLongitude": 106.735012,
    "startLatitude": 29.610634,
    "endLocation": "重庆鱼嘴工业园配送点A",
    "endLongitude": 106.759396,
    "endLatitude": 29.620115,
    "totalDistanceMeters": 2943,
    "estimatedDurationSeconds": 420,
    "polyline": "106.735012,29.610634;106.741312,29.615804;106.759396,29.620115"
  }
}
```

合同要求：

- `routeStatus` 仅允许 `PLANNING / READY / FAILED`；
- 只有READY时，polyline、距离和静态时长必须非空；
- polyline格式固定为 `lon,lat;lon,lat;...`；
- `coordinateSystem=GCJ02` 表示polyline和本响应中的路线起终点为GCJ02；
- `vehicleDeviceCode` 必须来自 `vehicle.sim_code`；
- `routeVersion` 从1开始，改线后严格递增；
- `routeId` 由业务后端生成，不依赖高德返回永久业务ID。

PLANNING/FAILED可以继续返回200及状态，polyline为null。业务后端只在READY或READY版本更新后发布MQTT通知。

## 4. MQTT路线引用通知

Topic：

```text
iot/carla/vehicle/{vehicleDeviceCode}/command
```

Payload：

```json
{
  "schema_version": "1.0",
  "command_id": "CMD_ROUTE_1001_V1",
  "vehicle_id": "sim_000",
  "task_id": 1001,
  "route_id": "ROUTE_1001",
  "route_version": 1,
  "command_type": "TASK_ROUTE_READY",
  "timestamp": "2026-08-26T08:00:00.000Z"
}
```

QoS 1、`retain=false`。MQTT消息不得包含完整polyline。

模拟器成功读取并安装路线后向以下Topic返回ACK：

```text
iot/carla/vehicle/{vehicleDeviceCode}/command/ack
```

`command_id`用于消息幂等，`routeId + routeVersion`用于路线幂等。相同或更旧版本不会重复切换或回退；更高版本才重新请求route API。

## 5. 模拟器配置与启动

模拟器需要：

- 业务API基础地址；
- 可选Bearer Token；
- MQTT凭据；
- taskId。

不再需要高德Key。

启动并立即加载一个任务：

```powershell
$env:SMART_LOGISTICS_API_TOKEN = '<如果接口不鉴权可不设置>'

python .\iot\simulator\mqtt_data_generator.py `
  --credentials "$env:USERPROFILE\.smart-logistics\mqtt_cloud.env" `
  --business-api-base "http://服务器地址:8080" `
  --task-id 1001 `
  --vehicles 20 `
  --duration 0
```

也可以不传taskId，先启动发生器，再由后端发布 `TASK_ROUTE_READY`。这种方式仍必须通过参数或 `SMART_LOGISTICS_API_BASE_URL` 配置业务API地址。

手工验证通知：

```powershell
python .\iot\simulator\mqtt_route_command.py `
  --credentials "$env:USERPROFILE\.smart-logistics\mqtt_cloud.env" `
  --vehicle sim_000 `
  --task-id 1001 `
  --route-id ROUTE_1001 `
  --route-version 1
```

## 6. 模拟器行为

- READY路线从GCJ02转换为WGS84；
- 第一次加载任务时车辆位于路线第一个点；
- GPS沿保存的polyline逐段前进，不随机改变方向；
- 到达终点后速度变为0、位置保持在终点，不折返、不瞬移；
- 同一任务的新版本从新路线距离当前位置最近的节点继续；
- MQTT断线期间位置暂停，重连后从原位置继续；
- route API字段、车辆编号、taskId、routeId或版本与通知不一致时返回FAILED ACK。

## 7. ETA字段边界

- `estimatedDurationSeconds`：业务后端规划路线时保存的静态预计耗时；
- `estimatedArrivalTime`：实时后端根据最新GPS和剩余路线算出的动态到达时间；
- `etaCalculatedAt`：本次动态ETA的计算时间。

实时后端应将WGS84 GPS投影到已保存路线，计算剩余路线距离；不得让模拟器计算第二套ETA，也不应每次GPS更新都再次调用高德规划。

## 8. 验收条件

1. route API READY样例通过 `route_api.schema.json`；
2. 模拟器日志出现 `[ROUTE][LOADED]` 或 `[ROUTE][APPLIED]`；
3. ACK为 `TASK_ROUTE_READY / EXECUTED`；
4. MQTT和Influx GPS为WGS84；
5. 地图轨迹沿业务后端保存的道路polyline移动；
6. 到达终点后位置不再变化，速度为0；
7. 相同/低版本不回退，高版本会重新获取；
8. 模拟端运行过程中没有任何高德HTTP请求。
