# 2号物联网数据模块

本目录是2号成员“物联网数据开发”的正式交付，负责生成和上报数据，不负责后端告警判定、轨迹入库、WebSocket或前端地图。

## 交付内容

```text
iot/
├── simulator/   # Python/CARLA GPS模拟器、MQTT回放与验收工具
├── contracts/   # GPS、状态、告警、指令和ACK的JSON Schema
├── samples/     # 正常、停车、偏航、开箱及批量数据集
├── firmware/    # E53_ST1真实GPS采集与MQTT上云源码
├── evidence/    # 已完成联调的脱敏消息记录
├── tests/       # 离线契约和样本自动化测试
└── requirements.txt
```

## 安装

推荐使用Python 3.8或更高版本：

```powershell
python -m pip install -r .\iot\requirements.txt
```

本地MQTT Broker默认使用 `localhost:1883`。云端或其他启用认证的Broker使用本机凭据文件，禁止将凭据提交到Git：

```text
MQTT_HOST=<broker-host>
MQTT_EXTERNAL_PORT=<broker-port>
MQTT_USERNAME=<username>
MQTT_PASSWORD=<password>
```

建议保存为：

```text
%USERPROFILE%\.smart-logistics\mqtt_cloud.env
```

## 正常GPS模拟

本地发布5辆车、持续30秒、每车1 Hz：

```powershell
python .\iot\simulator\mqtt_data_generator.py --vehicles 5 --duration 30 --interval 1
```

发布到云端并同时保存JSONL：

```powershell
python .\iot\simulator\mqtt_data_generator.py `
  --credentials "$env:USERPROFILE\.smart-logistics\mqtt_cloud.env" `
  --vehicles 20 --duration 300 --interval 1 `
  --output .\iot\samples\generated_20v_5m.jsonl
```

## 三类异常的确定性演示

以下命令都会在 `sim_000` 上只注入一次指定异常，适合联调和答辩：

```powershell
python .\iot\simulator\mqtt_data_generator.py --vehicles 1 --duration 10 --demo-anomaly stop
python .\iot\simulator\mqtt_data_generator.py --vehicles 1 --duration 10 --demo-anomaly drift
python .\iot\simulator\mqtt_data_generator.py --vehicles 1 --duration 10 --demo-anomaly open
```

云端运行时在每条命令后增加：

```powershell
--credentials "$env:USERPROFILE\.smart-logistics\mqtt_cloud.env"
```

`--alert-mode raw` 只制造可由GPS推断的停车/偏航，不直接发告警；异常开箱没有GPS原始特征，必须使用默认的 `precomputed` 模式发布告警消息。

## 业务任务路线

业务后端负责调用高德并保存路线；模拟器不调用高德，只根据taskId读取route API。启动并加载任务：

```powershell
$env:SMART_LOGISTICS_API_TOKEN = '<接口不鉴权时可不设置>'

python .\iot\simulator\mqtt_data_generator.py `
  --credentials "$env:USERPROFILE\.smart-logistics\mqtt_cloud.env" `
  --business-api-base "http://服务器地址:8080" `
  --task-id 1001 `
  --vehicles 20 --duration 0 --interval 1
```

路线由后端 ETA 模块调用高德生成并缓存。模拟器读取 `/api/v1/transport-tasks/{taskId}/planned-route`，将返回的GCJ02 `points` 转成WGS84后沿单程路线运行。也可通过只携带 `task_id` 和 `vehicle_id` 的 `TASK_ROUTE_READY` 通知触发重新读取。完整字段和联调步骤见 [ETA规划路线API与GPS模拟器联调合同](../docs/business-backend-amap-route-integration.md)。

## 长时间轨迹与断线恢复

加载业务任务路线后连续运行1小时，并保存同一份JSONL：

```powershell
python .\iot\simulator\mqtt_data_generator.py `
  --credentials "$env:USERPROFILE\.smart-logistics\mqtt_cloud.env" `
  --business-api-base "http://服务器地址:8080" `
  --task-id 1001 `
  --vehicles 20 --duration 3600 --interval 1 `
  --output .\iot\samples\long_track_20v_1h.jsonl `
  --seed 2026
```

使用 `--duration 0` 可一直运行到按下 `Ctrl+C`。网络中断后，发生器会：

1. 停止生成新批次，保持当前车辆位置；
2. 按1～30秒退避自动重连MQTT；
3. 重试中断时尚未成功提交的消息；
4. 重新发布全部车辆在线状态；
5. 从原位置继续生成GPS，不会因断线时间产生轨迹跳跃。

日志中的 `[DISCONNECTED]`、`[RECONNECTING]`、`[RECONNECTED]`、`[RESUMED]` 和 `[ONLINE_RESTORED]` 可用于判断恢复进度。

## 样本回放与验收

回放异常开箱样本：

```powershell
python .\iot\simulator\mqtt_replay.py .\iot\samples\abnormal_open.jsonl `
  --credentials "$env:USERPROFILE\.smart-logistics\mqtt_cloud.env"
```

订阅并生成验收报告：

```powershell
python .\iot\simulator\verify_mqtt_stream.py `
  --credentials "$env:USERPROFILE\.smart-logistics\mqtt_cloud.env" `
  --duration 20 --expected-vehicles 1 --require-alerts 1 `
  --report .\iot\evidence\mqtt_acceptance.json
```

离线检查所有Schema和样本：

```powershell
python -m unittest discover -s .\iot\tests -v
```

## Topic与坐标约定

- GPS：`iot/carla/vehicle/{vehicle_id}/gps`
- 状态：`iot/carla/vehicle/{vehicle_id}/status`
- 告警：`iot/carla/alert`
- 路线READY引用通知：`iot/carla/vehicle/{vehicle_id}/command`
- 指令回执：`iot/carla/vehicle/{vehicle_id}/command/ack`
- MQTT内统一使用WGS84；高德地图需要在展示层转换为GCJ-02。
- `sim_000...` 表示模拟车辆，`real_001...` 表示真实设备，二者使用相同Topic和JSON格式。

## 安全要求

- 不提交MQTT账号、密码、Wi-Fi密码或业务API Token；模拟端不需要地图Key。
- 云端固件会把配置编译进二进制，因此带真实凭据的 `.bin` 不进入公共仓库。
- `status` 使用retain，GPS和告警不使用retain；发布QoS默认是1。
