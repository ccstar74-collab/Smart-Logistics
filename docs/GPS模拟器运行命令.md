# 智慧物流 GPS 模拟器运行命令

本文档用于在 Windows PowerShell 中启动智慧物流 GPS/MQTT 数据模拟器。

## 一、当前功能状态

| 模式 | 当前状态 | 说明 |
|---|---|---|
| 普通 GPS 模拟 | 可直接使用 | 生成 `sim_000`、`sim_001` 等车辆并持续发布 MQTT GPS |
| 指定演示异常 | 可直接使用 | 支持异常停车、路线偏离和异常开门 |
| 随机异常 | 可直接使用 | 可通过概率参数随机产生异常 |
| 本地 JSONL 记录 | 可直接使用 | MQTT 发布的同时保存本地记录 |
| 按任务高德路线行驶 | 可直接使用 | 读取 ETA 的 `/planned-route` 接口并沿道路路线运行 |

普通模式用于独立测试 MQTT、InfluxDB、WebSocket 和 Alarm；需要联调任务路线与 ETA 时使用 `--task-id`。

## 二、进入项目目录

打开 PowerShell，执行：

```powershell
Set-Location -LiteralPath 'D:\软综实训\5个课题选题\重庆交通大学\智慧物流'
```

后续命令都在该目录中运行。

## 三、普通 GPS 模拟

### 1. 20 辆车持续运行

```powershell
python .\tools\mqtt_data_generator.py `
  --credentials "$env:USERPROFILE\.smart-logistics\mqtt_cloud.env" `
  --vehicles 20 `
  --duration 0 `
  --interval 1 `
  --anomaly-rate 0
```

运行效果：

- 创建 `sim_000` 至 `sim_019` 共 20 辆模拟车；
- 每辆车每秒发布一次 GPS；
- 持续运行，直到按下 `Ctrl+C`；
- 不随机制造异常。

### 2. 三辆车运行 60 秒

适合快速检查 MQTT 是否连通：

```powershell
python .\tools\mqtt_data_generator.py `
  --credentials "$env:USERPROFILE\.smart-logistics\mqtt_cloud.env" `
  --vehicles 3 `
  --duration 60 `
  --interval 1 `
  --anomaly-rate 0
```

对应车辆编号：

```text
sim_000
sim_001
sim_002
```

### 3. 指定模拟中心点

`--origin` 格式固定为 `经度,纬度`：

```powershell
python .\tools\mqtt_data_generator.py `
  --credentials "$env:USERPROFILE\.smart-logistics\mqtt_cloud.env" `
  --vehicles 20 `
  --duration 0 `
  --origin '106.550000,29.560000' `
  --anomaly-rate 0
```

MQTT 中的 GPS 坐标统一使用 WGS84。高德地图展示时需要转换为 GCJ-02。

## 四、保存本地 GPS 记录

下面的命令会在发布 MQTT 的同时，把消息保存为 JSONL 文件：

```powershell
python .\tools\mqtt_data_generator.py `
  --credentials "$env:USERPROFILE\.smart-logistics\mqtt_cloud.env" `
  --vehicles 20 `
  --duration 0 `
  --interval 1 `
  --anomaly-rate 0 `
  --output '.\mqtt-gps-output.jsonl'
```

输出文件位置：

```text
D:\软综实训\5个课题选题\重庆交通大学\智慧物流\mqtt-gps-output.jsonl
```

## 五、演示指定异常

指定异常默认注入到 `sim_000`。

### 1. 异常停车

```powershell
python .\tools\mqtt_data_generator.py `
  --credentials "$env:USERPROFILE\.smart-logistics\mqtt_cloud.env" `
  --vehicles 20 `
  --duration 0 `
  --demo-anomaly stop
```

### 2. 路线偏离

```powershell
python .\tools\mqtt_data_generator.py `
  --credentials "$env:USERPROFILE\.smart-logistics\mqtt_cloud.env" `
  --vehicles 20 `
  --duration 0 `
  --demo-anomaly drift
```

### 3. 异常开门

```powershell
python .\tools\mqtt_data_generator.py `
  --credentials "$env:USERPROFILE\.smart-logistics\mqtt_cloud.env" `
  --vehicles 20 `
  --duration 0 `
  --demo-anomaly open
```

## 六、随机异常测试

```powershell
python .\tools\mqtt_data_generator.py `
  --credentials "$env:USERPROFILE\.smart-logistics\mqtt_cloud.env" `
  --vehicles 20 `
  --duration 0 `
  --anomaly-rate 0.005
```

`0.005` 表示每辆车在每次更新时约有 `0.5%` 的概率产生异常。正式测试无异常 GPS 时应使用：

```text
--anomaly-rate 0
```

## 七、按 ETA 高德规划路线运行

模拟器已经适配 ETA 模块的 `/planned-route` 返回格式。使用前需要部署包含 `vehicleDeviceCode` 字段的最新 ETA 后端。

后端需要提供：

```http
GET /api/v1/transport-tasks/{taskId}/planned-route
```

接口至少应返回：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "taskId": 12,
    "vehicleDeviceCode": "sim_000",
    "provider": "AMAP",
    "coordinateSystem": "GCJ02",
    "distanceMeters": 5500,
    "referenceDurationSeconds": 720,
    "generatedAt": "2026-08-26T16:00:00+08:00",
    "points": [
      {"longitude": 106.570100, "latitude": 29.490100},
      {"longitude": 106.580100, "latitude": 29.500100},
      {"longitude": 106.610100, "latitude": 29.520100}
    ]
  }
}
```

启动命令：

```powershell
$env:SMART_LOGISTICS_API_TOKEN = '后端登录获得的Token'

python .\tools\mqtt_data_generator.py `
  --credentials "$env:USERPROFILE\.smart-logistics\mqtt_cloud.env" `
  --business-api-base 'http://云服务器公网地址:8080' `
  --task-id 12 `
  --vehicles 20 `
  --duration 0 `
  --interval 1 `
  --anomaly-rate 0
```

如果后端接口不鉴权，可以不设置 `SMART_LOGISTICS_API_TOKEN`。

同时加载多个任务时，可以重复指定 `--task-id`：

```powershell
python .\tools\mqtt_data_generator.py `
  --credentials "$env:USERPROFILE\.smart-logistics\mqtt_cloud.env" `
  --business-api-base 'http://云服务器公网地址:8080' `
  --task-id 12 `
  --task-id 13 `
  --task-id 14 `
  --vehicles 20 `
  --duration 0 `
  --anomaly-rate 0
```

任务绑定车辆必须包含在本次模拟车队中：

- `sim_000` 至 `sim_019`：使用 `--vehicles 20`；
- `sim_025`：至少使用 `--vehicles 26`。

模拟器拿到路线后会执行：

```text
读取 taskId 对应的 planned-route
→ 根据 vehicleDeviceCode 找到模拟车辆
→ 将高德 GCJ-02 路线转换为 WGS84
→ 将车辆放到路线起点
→ 沿 points 顺序运行并上报 MQTT GPS
→ 到达终点后速度归零
```

## 八、常用参数

| 参数 | 示例 | 说明 |
|---|---|---|
| `--vehicles` | `20` | 模拟车辆数量 |
| `--duration` | `0` | 运行秒数，`0` 表示持续运行 |
| `--interval` | `1` | GPS 上报间隔，单位为秒 |
| `--origin` | `106.55,29.56` | 普通模式的模拟中心点，经度在前 |
| `--seed` | `2026` | 固定随机种子，便于复现相同数据 |
| `--anomaly-rate` | `0.005` | 每辆车每次更新产生异常的概率 |
| `--demo-anomaly` | `stop` | 给 `sim_000` 注入指定演示异常 |
| `--output` | `gps.jsonl` | 同时保存本地 JSONL 文件 |
| `--task-id` | `12` | 加载指定运输任务路线，可重复使用 |
| `--business-api-base` | `http://server:8080` | Spring Boot 后端地址 |
| `--credentials` | `mqtt_cloud.env` | MQTT 连接配置文件 |

## 九、停止模拟器

持续运行模式下，在模拟器 PowerShell 窗口按：

```text
Ctrl+C
```

程序会停止发布，并正常断开 MQTT 连接。

## 十、常见错误

### MQTT 返回 `rc=4`

表示 MQTT 用户名或密码错误。检查：

```text
%USERPROFILE%\.smart-logistics\mqtt_cloud.env
```

### `Connection refused`

检查 MQTT Broker：

- 服务是否运行；
- 地址和端口是否正确；
- 云服务器防火墙和 NAT 映射是否放行；
- 本机网络是否可以连接服务器。

### 使用 `--task-id` 时提示缺少后端地址

必须同时提供：

```text
--business-api-base 'http://服务器地址:8080'
```

### `任务路线加载失败`

依次检查：

1. Spring Boot 后端是否已经部署最新 ETA 代码；
2. `taskId` 是否真实存在；
3. 任务起点和终点坐标是否完整；
4. 任务绑定车辆是否配置 `vehicle.sim_code`；
5. `/planned-route` 是否返回 `vehicleDeviceCode` 和至少两个 `points`；
6. Bearer Token 是否有效；
7. 服务器是否能够访问高德 Web 服务 API。

## 十一、推荐测试顺序

1. 用三辆车运行 60 秒，确认 MQTT 连接成功；
2. 用 20 辆车持续运行，确认 InfluxDB 可以查询 GPS；
3. 检查 WebSocket 和前端车辆位置更新；
4. 使用 `--demo-anomaly` 测试 Alarm；
5. 使用 `--task-id` 测试道路规划路线和动态 ETA。

## 十二、使用 eta_service 账号联调任务路线

本节是从登录业务后端到启动任务路线模拟器的完整操作流程。所有命令必须在同一个 PowerShell 窗口依次执行。

当前联调信息：

```text
业务后端：http://111.170.148.177:58080
用户名：eta_service
密码：shenyuxueshenyuxue
角色：DISPATCHER
```

### 1. 登录并保存 JWT

```powershell
$apiBase = 'http://111.170.148.177:58080'

$loginBody = @{
    username = 'eta_service'
    password = 'shenyuxueshenyuxue'
} | ConvertTo-Json

$response = Invoke-RestMethod `
    -Method Post `
    -Uri "$apiBase/api/v1/auth/login" `
    -ContentType 'application/json' `
    -Body $loginBody

$env:SMART_LOGISTICS_API_TOKEN = $response.data.accessToken

if ([string]::IsNullOrWhiteSpace($env:SMART_LOGISTICS_API_TOKEN)) {
    throw '登录失败：响应中没有 data.accessToken'
}

$headers = @{
    Authorization = "Bearer $env:SMART_LOGISTICS_API_TOKEN"
}

Write-Host '登录成功，Token已经保存到当前PowerShell窗口' `
    -ForegroundColor Green
```

该 Token 只保存在当前 PowerShell 窗口。关闭窗口、Token过期或重新打开终端后，需要从本节第一步重新登录。

### 2. 验证账号和角色

```powershell
$me = Invoke-RestMethod `
    -Method Get `
    -Uri "$apiBase/api/v1/users/me" `
    -Headers $headers

$me.data | Format-List
```

正常应该包含：

```text
username : eta_service
role     : DISPATCHER
```

### 3. 查询云端运输任务

```powershell
$tasks = Invoke-RestMethod `
    -Method Get `
    -Uri "$apiBase/api/v1/transport-tasks?page=1&pageSize=100" `
    -Headers $headers

$records = @($tasks.data.records)

Write-Host "任务总数：$($records.Count)" -ForegroundColor Cyan

$records |
    Select-Object id, taskNo, status, vehicleId,
        startLocation, startLongitude, startLatitude,
        endLocation, endLongitude, endLatitude,
        @{
            Name = 'coordinatesComplete'
            Expression = {
                $null -ne $_.startLongitude -and
                $null -ne $_.startLatitude -and
                $null -ne $_.endLongitude -and
                $null -ne $_.endLatitude
            }
        } |
    Format-Table -AutoSize
```

查看各任务状态数量：

```powershell
$records |
    Group-Object status |
    Select-Object Name, Count |
    Format-Table -AutoSize
```

### 4. 自动选择可以模拟的任务

```powershell
$task = $records |
    Where-Object {
        $_.status -in @('WAITING', 'TRANSPORTING') -and
        $null -ne $_.startLongitude -and
        $null -ne $_.startLatitude -and
        $null -ne $_.endLongitude -and
        $null -ne $_.endLatitude
    } |
    Select-Object -First 1

if ($null -eq $task) {
    Write-Host '当前没有起终点坐标完整的WAITING或TRANSPORTING任务' `
        -ForegroundColor Yellow
} else {
    $taskId = $task.id
    Write-Host "已选择任务：taskId=$taskId，taskNo=$($task.taskNo)" `
        -ForegroundColor Green
}
```

如果显示：

```text
当前没有起终点坐标完整的WAITING或TRANSPORTING任务
```

表示登录和查询操作已经成功，只是云端暂时没有可用于路线模拟的任务。此时不需要继续操作，也不要运行带 `--task-id` 的模拟器，等待后端准备测试任务即可。

后端需要完成：

1. 部署最新 `feature/eta-calculation`；
2. 执行 `007_transport_task_eta.sql`；
3. 创建或更新一个完整起终点坐标的任务；
4. 任务状态为 `WAITING` 或 `TRANSPORTING`；
5. 给任务绑定车辆；
6. 给绑定车辆配置 `vehicle.sim_code`，例如 `sim_000`；
7. 部署包含 `vehicleDeviceCode` 的 `/planned-route` 接口。

可以直接发给后端同学：

> eta_service 登录和任务列表查询已经成功，但当前云端没有起终点坐标完整的 WAITING/TRANSPORTING 任务，所以模拟器无法加载路线。请部署包含 007 坐标字段、planned-route 和 vehicleDeviceCode 的最新 ETA 后端，并创建或更新一个起终点坐标完整、绑定 vehicle.sim_code 的 WAITING/TRANSPORTING 测试任务。完成后请把 taskId 发给我。

### 5. 后端给出 taskId 后验证规划路线

假设后端给出的任务ID是 `12`：

```powershell
$taskId = 12

$route = Invoke-RestMethod `
    -Method Get `
    -Uri "$apiBase/api/v1/transport-tasks/$taskId/planned-route" `
    -Headers $headers

$route.data |
    Select-Object taskId, vehicleDeviceCode, provider,
        coordinateSystem, distanceMeters,
        referenceDurationSeconds, generatedAt |
    Format-List

$route.data.points |
    Select-Object -First 10 |
    Format-Table longitude, latitude
```

成功响应必须满足：

```text
taskId与请求一致
vehicleDeviceCode不为空，例如sim_000
provider为AMAP
coordinateSystem为GCJ02
distanceMeters大于0
referenceDurationSeconds大于0
points至少包含两个路线点
```

如果返回 `404`，通常表示新版接口尚未部署或任务不存在。如果返回 `409`，通常表示任务坐标不完整或绑定车辆没有配置 `sim_code`。如果返回 `403`，需要让后端检查 `DISPATCHER` 对该任务的访问权限。

### 6. 自动计算车辆数量并启动路线模拟器

规划路线查询成功后执行：

```powershell
$vehicleDeviceCode = $route.data.vehicleDeviceCode

if ($vehicleDeviceCode -match '^sim_(\d+)$') {
    $vehicleCount = [int]$Matches[1] + 1
} else {
    throw "车辆设备编号不是sim_数字格式：$vehicleDeviceCode"
}

if ($vehicleCount -lt 1) {
    $vehicleCount = 1
}

Write-Host "任务绑定车辆：$vehicleDeviceCode" -ForegroundColor Cyan
Write-Host "模拟车辆数量：$vehicleCount" -ForegroundColor Cyan

Set-Location -LiteralPath `
    'D:\软综实训\5个课题选题\重庆交通大学\智慧物流'

python .\tools\mqtt_data_generator.py `
    --credentials "$env:USERPROFILE\.smart-logistics\mqtt_cloud.env" `
    --business-api-base $apiBase `
    --task-id $taskId `
    --vehicles $vehicleCount `
    --duration 0 `
    --interval 1 `
    --anomaly-rate 0
```

成功时会出现：

```text
[ROUTE][LOADED] task_id=...，vehicle=sim_...，generated_at=...，points=...
```

模拟器会读取ETA规划路线、把GCJ02转换为WGS84、沿路线发布MQTT GPS，并在到达终点后停止。按 `Ctrl+C` 结束运行。

### 7. 当前没有任务时仍可运行普通模拟

普通随机GPS模拟不依赖业务任务和 `/planned-route`，可以随时运行：

```powershell
Set-Location -LiteralPath `
    'D:\软综实训\5个课题选题\重庆交通大学\智慧物流'

python .\tools\mqtt_data_generator.py `
    --credentials "$env:USERPROFILE\.smart-logistics\mqtt_cloud.env" `
    --vehicles 20 `
    --duration 0 `
    --interval 1 `
    --anomaly-rate 0
```

普通模式可以继续测试 MQTT、InfluxDB、车辆位置、WebSocket 和 Alarm，但它不会关联具体 TransportTask，也不会验证任务规划路线。
