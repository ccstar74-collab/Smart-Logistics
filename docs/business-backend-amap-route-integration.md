# 业务后端与高德真实路线模拟联调说明

## 1. 目标和职责边界

目标流程：

```text
业务后端运输任务（起点、终点、车辆设备编号）
        ↓ GET任务详情API
MQTT路线指令工具（标准化为WGS84）
        ↓ ROUTE_CHANGE
GPS发生器调用高德驾车路径规划
        ↓ 沿高德polyline逐秒行驶
MQTT GPS → 实时后端/InfluxDB/地图
```

业务后端负责保存任务、起终点和车辆绑定关系；GPS模拟端负责调用高德、生成道路轨迹和发布GPS。业务后端不要生成随机轨迹，也不要把高德返回的几百个polyline点写进MySQL。

告警、任务、货物和车辆状态的业务联动仍由各自模块负责。GPS发生器收到路线后只改变模拟车辆行驶路线，不直接修改 `TransportTask`、`Cargo` 或 `Vehicle` 状态。

## 2. 当前代码现状

`feature/transport-task` 当前的任务详情已经返回：

- `id`
- `vehicleId`
- `startLocation`
- `endLocation`

但自动规划还缺少：

- 起点经纬度；
- 终点经纬度；
- 坐标系；
- MQTT使用的车辆设备编号，例如 `sim_000`。

仅有 `startLocation="仓库A"` 不够稳定：名称可能重名，地理编码结果也可能变化。因此正式联调应优先传经纬度，地址只作展示和兼容兜底。

## 3. 业务后端建议返回格式

可以直接扩展现有接口：

```http
GET /api/v1/transport-tasks/{id}
Authorization: Bearer <token>
```

推荐响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1001,
    "taskNo": "T202608260001",
    "vehicleId": 1,
    "vehicleDeviceCode": "sim_000",
    "startLocation": "重庆果园港装卸点A",
    "startLongitude": 106.730553,
    "startLatitude": 29.613528,
    "endLocation": "重庆鱼嘴工业园配送点A",
    "endLongitude": 106.754928,
    "endLatitude": 29.622890,
    "coordinateSystem": "WGS84",
    "status": "TRANSPORTING"
  }
}
```

模拟端同时兼容下面这种嵌套格式：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "taskId": 1001,
    "vehicleId": 1,
    "vehicleDeviceCode": "sim_000",
    "coordinateSystem": "WGS84",
    "origin": {
      "name": "重庆果园港装卸点A",
      "longitude": 106.730553,
      "latitude": 29.613528
    },
    "destination": {
      "name": "重庆鱼嘴工业园配送点A",
      "longitude": 106.754928,
      "latitude": 29.622890
    }
  }
}
```

字段约定：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `id` 或 `taskId` | 是 | 正整数任务ID |
| `vehicleId` | 是 | MySQL车辆主键，用于业务校验 |
| `vehicleDeviceCode` | 自动联调时必填 | MQTT车辆编号，如 `sim_000`、`real_001` |
| `startLocation` / `endLocation` | 是 | 展示名称，建议包含城市、区县和详细地点 |
| 四个经纬度字段 | 强烈建议必填 | 数字，经度在前、纬度在后 |
| `coordinateSystem` | 是 | 仅允许 `WGS84` 或 `GCJ02` |

如果地点由高德地图选点获得，就应如实返回 `GCJ02`；模拟端会先转成MQTT契约要求的WGS84，再在调用高德时转换为GCJ-02，不能把GCJ-02坐标错误标记成WGS84。

## 4. 业务后端需要修改的文件

以下文件名以现有 `feature/transport-task` 结构为准，具体由业务后端负责人修改：

1. `TransportTask.java`
   - 增加 `startLongitude`、`startLatitude`、`endLongitude`、`endLatitude`、`coordinateSystem`。
2. `TransportTaskCreateRequest.java`
   - 接收上述字段；经度校验 `[-180, 180]`，纬度校验 `[-90, 90]`；坐标系只允许 `WGS84/GCJ02`。
3. `TransportTaskResponse.java`
   - 返回上述字段以及 `vehicleDeviceCode`。
4. `TransportTaskService.java`
   - 创建任务时保存坐标；详情转换时根据 `vehicleId` 取得设备编号；不得在GET接口中修改任何状态。
5. `Vehicle.java`、车辆DTO和Service
   - 如果当前没有设备编号，新增唯一字段 `deviceCode`；演示数据建立 `1 → sim_000`、`2 → sim_001` 等映射。
6. `TransportTaskServiceTest.java`、`TransportTaskControllerTest.java`
   - 补充坐标边界、坐标系枚举、详情字段和不存在车辆的测试。

不要直接修改已经多人共用的 `001_core_schema.sql`。在TransportTask合并后单独增加迁移，例如 `docs/sql/002_task_route_coordinates.sql`：

```sql
ALTER TABLE transport_task
    ADD COLUMN start_longitude DECIMAL(10, 6) NULL,
    ADD COLUMN start_latitude DECIMAL(9, 6) NULL,
    ADD COLUMN end_longitude DECIMAL(10, 6) NULL,
    ADD COLUMN end_latitude DECIMAL(9, 6) NULL,
    ADD COLUMN coordinate_system VARCHAR(10) NOT NULL DEFAULT 'WGS84';

ALTER TABLE vehicle
    ADD COLUMN device_code VARCHAR(64) NULL,
    ADD UNIQUE KEY uk_vehicle_device_code (device_code);
```

旧数据可以先允许坐标为 `NULL`。模拟端发现坐标缺失时会使用 `startLocation/endLocation` 调用高德地理编码作为兼容兜底，但正式演示数据应补齐坐标。

## 5. 模拟端已经完成的能力

模拟端现在统一使用高德Web服务：

- 使用高德路径规划2.0驾车接口取得真实道路 `polyline`；
- 默认使用策略 `32`（高德推荐）；
- 支持起点、终点和最多16个有序途经点；
- 自动执行 WGS84 ↔ GCJ-02 转换；
- 对外发布的MQTT GPS仍为 `coordinate_system=WGS84`；
- 路线确定后只在道路折线上前进，速度变化不会造成随机方向漂移；
- 长时间演示到达终点后沿原路线返回，避免瞬移回起点；
- MQTT断线时暂停位置，自动重连后从原位置恢复。

涉及文件：

- `iot/simulator/route_planner.py`：高德地理编码、驾车规划和坐标转换；
- `iot/simulator/task_route.py`：解析业务任务API；
- `iot/simulator/mqtt_route_command.py`：读取任务API并下发 `ROUTE_CHANGE`；
- `iot/simulator/mqtt_data_generator.py`：接收指令并沿规划路线持续发布GPS。

## 6. 联调操作

### 6.1 环境变量

高德Key只配置在运行模拟器的电脑上，不写入Java代码、YAML、Markdown、Git提交或命令参数：

```powershell
$env:AMAP_WEB_SERVICE_KEY = '<高德Web服务Key>'
```

如果业务API要求登录，再设置短期Token：

```powershell
$env:SMART_LOGISTICS_API_TOKEN = '<Bearer Token，不带Bearer前缀>'
```

### 6.2 启动GPS发生器

```powershell
python .\iot\simulator\mqtt_data_generator.py `
  --credentials "$env:USERPROFILE\.smart-logistics\mqtt_cloud.env" `
  --vehicles 20 `
  --duration 0 `
  --interval 1 `
  --amap-strategy 32
```

发生器会订阅：

```text
iot/carla/vehicle/+/command
```

### 6.3 从业务API下发任务路线

另开一个PowerShell窗口：

```powershell
$env:AMAP_WEB_SERVICE_KEY = '<高德Web服务Key>'
$env:SMART_LOGISTICS_API_TOKEN = '<如果接口无需登录可不设置>'

python .\iot\simulator\mqtt_route_command.py `
  --task-url "http://服务器地址:8080/api/v1/transport-tasks/1001" `
  --credentials "$env:USERPROFILE\.smart-logistics\mqtt_cloud.env" `
  --city 重庆
```

如果业务接口还没返回 `vehicleDeviceCode`，联调期间可以临时指定：

```powershell
python .\iot\simulator\mqtt_route_command.py `
  --task-url "http://服务器地址:8080/api/v1/transport-tasks/1001" `
  --vehicle sim_000 `
  --credentials "$env:USERPROFILE\.smart-logistics\mqtt_cloud.env" `
  --city 重庆
```

成功日志应包含：

```text
[TASK] task_id=1001，起点=...，终点=...
[COMMAND_SENT] Topic=iot/carla/vehicle/sim_000/command
[ROUTE][AMAP] 已加载高德真实道路...
[ACK_RECEIVED] ... "status": "EXECUTED"
```

## 7. MQTT指令边界

工具从业务API得到任务后会发布：

```json
{
  "schema_version": "1.0",
  "command_id": "CMD_...",
  "vehicle_id": "sim_000",
  "task_id": 1001,
  "command_type": "ROUTE_CHANGE",
  "route": {
    "coordinate_system": "WGS84",
    "waypoints": [
      {"lon": 106.730553, "lat": 29.613528},
      {"lon": 106.754928, "lat": 29.622890}
    ]
  },
  "timestamp": "2026-08-26T08:00:00.000Z"
}
```

业务后端以后也可以直接发布完全相同的指令，从而省掉命令行桥接工具；但第一阶段建议先用现成工具把任务API和GPS闭环跑通，再把发布逻辑并入Dispatch模块。

## 8. 验收标准

1. 任务详情返回任务ID、设备编号、起终点坐标和坐标系。
2. 指令工具能收到接口数据，并获得 `EXECUTED` ACK。
3. 发生器日志出现 `[ROUTE][AMAP]`，实际运行链路不调用OSRM。
4. `sim_000` GPS轨迹沿公路移动，不穿楼、不画直线跨越道路。
5. 地图显示起点、终点和轨迹基本重合，允许真实GPS和坐标反算造成的米级误差。
6. 重复下发同一 `command_id` 不重复切换路线，只重发首次ACK。
7. MQTT断线恢复后，车辆从断线前位置继续，不产生大范围跳点。

## 9. 高德接口依据

- [高德路径规划2.0](https://lbs.amap.com/api/webservice/guide/api/newroute)：驾车接口为 `/v5/direction/driving`，`show_fields=polyline` 返回道路折线，最多支持16个途经点。
- [高德地理编码](https://lbs.amap.com/api/webservice/guide/api/georegeo/)：当旧任务只有地址字符串时，将结构化地址转换成经纬度。

高德Key属于服务凭据。已经通过聊天、截图或群消息发送过的Key建议在联调完成后重新生成，并限制调用配额和可用服务。
