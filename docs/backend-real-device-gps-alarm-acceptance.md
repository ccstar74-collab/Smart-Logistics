# 真实板运输任务 GPS 与告警现场验收

适用设备：E53_ST1 + BearPi-HM Nano，当前设备编号 `sim_999`。目标是让队友发布并开始运输任务后，拿板子到室外行走，确认 GPS、MQTT、云端入库、任务关联和异常告警全部正常。

## 一、数据如何绑定任务

真板不会保存业务数据库中的 `taskId`，只持续上报 `vehicle_id=sim_999`：

```text
真板 GPS / 告警
  → MQTT vehicle_id=sim_999
  → 后端查 Vehicle.simCode=sim_999
  → 找到该车辆绑定的 TRANSPORTING 任务
  → GPS、轨迹、ETA、告警归入该车辆和任务
```

因此队友必须把任务绑定到 `simCode=sim_999` 的车辆，并将任务切换为 `TRANSPORTING`。仅创建任务但不绑定车辆，GPS 虽能到 MQTT，却不能正确归入任务。

## 二、出发前检查

### 1. 后端/前端队友确认

- 车辆表存在唯一的 `simCode=sim_999`；
- 新任务已经绑定这辆车；
- 任务含有效起点、终点和经纬度；
- 任务状态已进入 `TRANSPORTING`；
- 前端能打开任务详情、车辆监控或告警页面。

### 2. 停止同编号模拟器

真实板测试期间不要运行手工 `sim_999` 模拟器。若自动兜底正在运行，先在窗口按 `Ctrl+C`。否则真板和模拟器可能同时发布同一 Topic，轨迹会跳动。

### 3. 网络和供电

- 开启固件对应的 2.4 GHz 手机热点，并保持手机移动数据可用；
- 手机必须与板子一起带到室外；
- 想看串口：用笔记本 Type-C 给板子供电并打开 PuTTY；
- 只想轻便采集：用充电宝供电，此时 PuTTY 会断开，需要从 MQTT、云端或前端观察。

## 三、四层观察方法

### 第 1 层：PuTTY 确认板子定位并发布

板子连接电脑时，PuTTY 选择当前 COM 口，参数为 `115200 / 8N1 / None flow control`。

联网成功：

```text
[MQTT] connected as sim_999
[MQTT] status published (retain).
```

尚未定位：

```text
[GPS] waiting for valid RMC fix ...
```

这不算 GPS 成功。到室外开阔处保持天线朝上，直到连续出现：

```text
[GPS] FIX lon=... lat=... speed=... heading=... UTC=...
[MQTT] GPS published topic=iot/carla/vehicle/sim_999/gps
```

连续出现 `FIX` 和 `GPS published`，才表示板子取得卫星定位并已向 Broker 发布。

### 第 2 层：电脑实时观察云端 MQTT

在联网电脑上启动录制：

```powershell
Set-Location -LiteralPath 'D:\软综实训\5个课题选题\重庆交通大学\智慧物流'

python .\tools\mqtt_capture.py `
  .\tools\output\captures\sim_999_field_test.jsonl `
  --credentials "$env:USERPROFILE\.smart-logistics\mqtt_cloud.env" `
  --topic 'iot/carla/#' `
  --duration 0
```

持续出现以下 Topic，说明云端 Broker 正在收到真板数据：

```text
iot/carla/vehicle/sim_999/status
iot/carla/vehicle/sim_999/gps
```

按 `Ctrl+C` 停止。查看最后 20 条完整 JSON：

```powershell
Get-Content -LiteralPath `
  '.\tools\output\captures\sim_999_field_test.jsonl' `
  -Tail 20
```

想边录边看 JSON，可另开 PowerShell：

```powershell
Get-Content -LiteralPath `
  '.\tools\output\captures\sim_999_field_test.jsonl' `
  -Wait -Tail 5
```

### 第 3 层：云服务器确认已经入库

SSH 登录云服务器后执行：

```bash
smart-gps status
smart-gps latest sim_999
smart-gps logs
```

- `status`：确认 MQTT、Recorder、InfluxDB 服务正常；
- `latest sim_999`：显示最新经纬度、速度、航向和时间；
- `logs`：持续看记录服务，按 `Ctrl+C` 退出。

成功入库时日志会出现：

```text
stored GPS vehicle=sim_999 timestamp=...
stored status vehicle=sim_999 timestamp=...
```

`GPS published` 只证明发布到了 Broker；出现 `stored GPS` 或 `smart-gps latest sim_999` 更新，才证明云端记录服务已经写入数据。

### 第 4 层：前端或业务 API 看任务结果

最直观的是让队友打开车辆监控/当前任务详情页：

- 地图上的 `sim_999` 位置应随行走更新；
- 当前任务应显示该车辆的位置和轨迹；
- ETA/剩余距离是否更新由实时后端和 ETA 模块决定。

对应 REST API：

```http
GET /api/v1/vehicles/locations/latest
GET /api/v1/vehicles/{vehicleDatabaseId}/location/latest
GET /api/v1/vehicles/{vehicleDatabaseId}/location-history?startTime=...&endTime=...
GET /api/v1/transport-tasks/current
```

`{vehicleDatabaseId}` 是 MySQL 车辆主键，不是字符串 `sim_999`；请求需要登录后的 Bearer Token。

## 四、现场行走测试顺序

1. 队友创建任务、绑定 `sim_999` 对应车辆并切换为 `TRANSPORTING`。
2. 停止 `sim_999` 的所有模拟器/兜底发布器。
3. 开启手机热点，给板子供电。
4. 确认串口显示 `connected as sim_999`，或让 MQTT 录制窗口开始等待。
5. 带手机和板子到室外，天线朝上，等待有效 `RMC FIX`。
6. 出现连续 GPS 后步行 3～5 分钟，不要只站在原地。
7. 让队友观察前端位置，同时用 MQTT 或 `smart-gps latest sim_999` 交叉验证。
8. 行走过程中按一次 F1 测试异常开箱；确认后按一次 F2 测试恢复。
9. 测试结束后让队友完成任务，再关闭热点和板子电源。

## 五、F1 告警与 F2 恢复

### 1. 按 F1

PuTTY 应显示：

```text
[BOX] F1 pressed: abnormal open detected.
[MQTT] abnormal-open alert published topic=iot/carla/alert
```

MQTT 录制文件应出现 `iot/carla/alert`。云服务器执行：

```bash
smart-gps alerts
```

应看到 `vehicle=sim_999`、`type=异常开箱`。业务后端/前端告警列表应出现对应 Alarm：

```http
GET /api/v1/alarms?page=1&pageSize=20
GET /api/v1/alarms/{alarmId}
```

重复按 F1 应显示 `box is already marked open`，不能为同一次开箱新建重复告警。

### 2. 按 F2

PuTTY 应显示：

```text
[BOX] F2 pressed: box marked closed; recovery queued.
[MQTT] abnormal-open recovery published topic=iot/carla/alert/recovery
```

MQTT 录制文件应出现 `iot/carla/alert/recovery`。F2 只表示物理条件恢复；Alarm 是否最终变成 `RESOLVED`，还要满足业务后端规定的调度指令完成条件。重复按 F2 不应重复发布恢复。

## 六、故障定位

| 现象 | 结论与处理 |
| --- | --- |
| 一直 `waiting for valid RMC fix` | 卫星未定位；继续在开阔处等待并保持天线朝上 |
| 有 `FIX`，没有 `GPS published` | MQTT 连接或发布失败；检查热点移动数据和 Broker |
| 有 `GPS published`，MQTT 没消息 | 检查 Broker 地址、端口、账号和 Topic |
| MQTT 有 GPS，`smart-gps latest` 不更新 | Recorder/InfluxDB 异常，查 `status` 和 `logs` |
| InfluxDB 有 GPS，前端没有位置 | 检查位置 REST API、WebSocket 和前端请求 |
| 有车辆位置，但没归到任务 | 检查任务绑定车辆和 `TRANSPORTING` 状态 |
| MQTT 有告警，MySQL/前端没有 Alarm | 检查正式后端的 Alert 订阅、校验和入库日志 |
| 轨迹突然跳跃 | 停止所有同编号 `sim_999` 模拟发布器 |

## 七、验收通过标准

- [ ] 任务绑定车辆的 `simCode` 为 `sim_999`，任务状态为 `TRANSPORTING`。
- [ ] 串口出现有效 `GPS FIX` 和 `GPS published`。
- [ ] MQTT 收到 `iot/carla/vehicle/sim_999/gps`。
- [ ] `smart-gps latest sim_999` 的坐标和时间持续更新。
- [ ] 前端地图或位置 API 显示车辆移动。
- [ ] F1 在 MQTT、InfluxDB 和业务告警列表中各出现一次。
- [ ] 重复 F1 不产生重复告警。
- [ ] F2 发布一条 recovery，重复 F2 幂等。
- [ ] 全程没有其他 `sim_999` 模拟发布器混入轨迹。
