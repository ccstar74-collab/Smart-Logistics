# 模拟数据样例

每行都是一个可回放事件：`delay_ms` 表示相对上一条消息的等待时间，`topic` 是 MQTT 主题，`payload` 是实际 JSON 消息。

- `gps_normal.jsonl`：5 辆车、3 秒、每车 1 Hz 的正常位置流。
- `abnormal_stop.jsonl`：单车停车及异常停留告警。
- `abnormal_drift.jsonl`：单车偏离路线及偏航告警。
- `abnormal_open.jsonl`：运输途中异常开箱告警。

使用 `iot/simulator/mqtt_replay.py` 回放。工具默认把消息时间戳改为当前 UTC 时间，避免后端因样例时间过期而拒收。

需要更多随机数据时，不要复制现有5车样例，使用批量发生器：

```powershell
# 50辆车，持续5分钟，1 Hz，直接发给 MQTT，并保存同一份数据
python iot\simulator\mqtt_data_generator.py --vehicles 50 --duration 300 --interval 1 --output iot\samples\generated_50v_5m.jsonl

# 加入停车/偏航，每车每秒约0.5%概率触发异常
python iot\simulator\mqtt_data_generator.py --vehicles 50 --duration 300 --anomaly-rate 0.005 --output iot\samples\generated_50v_anomaly.jsonl
```

答辩或验收时可确定性注入单次异常，不必等待随机概率：

```powershell
python iot\simulator\mqtt_data_generator.py --vehicles 1 --duration 10 --demo-anomaly stop
python iot\simulator\mqtt_data_generator.py --vehicles 1 --duration 10 --demo-anomaly drift
python iot\simulator\mqtt_data_generator.py --vehicles 1 --duration 10 --demo-anomaly open
```

同一个 `--seed` 会生成可复现的车流；换一个种子可得到另一组数据，例如 `--seed 2026`。

## 果园港真实道路批量数据

- `guoyuan_normal_10v_2m.jsonl`：10 辆车、2 分钟、1200 条 GPS，无告警。
- `guoyuan_anomaly_20v_5m.jsonl`：20 辆车、5 分钟、6000 条 GPS，含停车/偏航告警。
- `guoyuan_stress_50v_2m.jsonl`：50 辆车、2 分钟、6000 条 GPS，用于吞吐压力测试。

快速回放给队友的 Broker（把 IP 换成实际地址）：

```powershell
python .\iot\simulator\mqtt_replay.py .\iot\samples\guoyuan_normal_10v_2m.jsonl --host <BROKER_HOST> --speed 10
python .\iot\simulator\mqtt_replay.py .\iot\samples\guoyuan_anomaly_20v_5m.jsonl --host <BROKER_HOST> --speed 10
```

`--speed 10` 表示十倍速回放；去掉它就是按原始 1 Hz 节奏发送。

## 路线规划智能体数据

`planning_guoyuan_20v_30tasks/` 包含与 `sim_000`～`sim_019` 对齐的车辆、待分配任务、真实道路节点、距离时间矩阵和完整规划请求。智能体成员可先读取其中的 `planning_request.json` 离线开发，正式联调时再切换为后端 API。

## 业务路线加载闭环

先启动发生器并配置业务route API：

```powershell
python .\iot\simulator\mqtt_data_generator.py `
  --host localhost `
  --business-api-base "http://localhost:8080" `
  --vehicles 20 `
  --duration 0 `
  --interval 1 `
  --anomaly-rate 0.005
```

另开终端，模拟后端给 `sim_000` 下发新路线并等待 ACK：

```powershell
python .\iot\simulator\mqtt_route_command.py `
  --host localhost `
  --vehicle sim_000 `
  --task-id 1001 `
  --route-id ROUTE_1001 `
  --route-version 1
```

发生器订阅 `iot/carla/vehicle/+/command`，收到路线引用后通过业务API读取完整polyline，然后发布 `iot/carla/vehicle/{vehicle_id}/command/ack`。`iot/samples/task_route_ready_command.json` 可用于MQTTX手工发布；Topic填写 `iot/carla/vehicle/sim_000/command`，Retain必须关闭。成功回执见 `iot/samples/task_route_ready_ack.json`，route API样例见 `iot/samples/task_route_api_ready.json`。
