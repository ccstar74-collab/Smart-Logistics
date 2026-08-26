# 2号物联网数据模块验收报告

## 1. 验收范围

本报告验证2号成员负责的以下能力：

1. GPS位置、速度、航向、时间和运输状态的连续生成；
2. MQTT Topic与JSON格式符合接口契约；
3. 正常、异常停留、偏航、异常开箱四类场景可复现；
4. 模拟态与真实GPS设备使用相同接口；
5. 凭据和设备配置不进入Git仓库。

后端告警规则、InfluxDB存储、轨迹API、WebSocket和前端地图不属于本模块验收范围。

## 2. 环境

| 项目 | 值 |
| --- | --- |
| 日期 | 2026-08-24 |
| 操作系统 | Windows |
| Python | 3.8.3 |
| paho-mqtt | 2.1.0 |
| jsonschema | 3.2.0 |
| Broker | 本机Mosquitto `localhost:1883` |
| Git基线 | `5297b19`（CargoItem REST API V1） |

## 3. 离线契约验收

执行：

```powershell
python -m unittest discover -s .\iot\tests -v
```

结果：4项测试全部通过。

- 13,226条GPS消息通过Schema校验；
- 160条状态消息通过Schema校验；
- 43条告警消息通过Schema校验；
- 路线指令与ACK示例通过Schema校验；
- 停车、偏航、开箱三类告警样本全部存在；
- 未提交真实固件配置头文件。

## 4. MQTT端到端验收

验收方式：先运行 `verify_mqtt_stream.py` 订阅 `iot/carla/#`，再运行 `mqtt_data_generator.py` 发布4秒数据。GPS最低要求为0.5 Hz，实际约1 Hz。

| 场景 | GPS消息 | GPS频率 | 告警数 | Schema错误 | 结果 |
| --- | ---: | ---: | ---: | ---: | --- |
| 正常GPS | 4 | 0.990 Hz | 0 | 0 | 通过 |
| 异常停留 | 4 | 0.990 Hz | 1 | 0 | 通过 |
| 偏航 | 4 | 0.995 Hz | 1 | 0 | 通过 |
| 异常开箱 | 4 | 0.995 Hz | 1 | 0 | 通过 |

原始验收报告：

- `iot/evidence/acceptance_normal.json`
- `iot/evidence/acceptance_stop.json`
- `iot/evidence/acceptance_drift.json`
- `iot/evidence/acceptance_open.json`

## 5. 云端与真实设备证据

### 5.1 高德真实道路规划测试

2026-08-26 使用高德Web服务驾车路径规划2.0测试果园港附近两个WGS84点：

- 高德接口调用成功；
- 单程道路距离约2.94 km；
- 解析并转换后得到80个往返轨迹节点；
- 高德GCJ-02折线已在模拟端转换为WGS84；
- 单元测试覆盖坐标转换、高德polyline解析、业务任务DTO和地址兜底解析。
- 模拟任务API → MQTT路线指令 → 高德规划 → `EXECUTED` ACK端到端测试通过，动态路线得到126个往返节点。

### 5.2 MQTT断线恢复测试

使用独立Mosquitto测试端口运行12秒发生器，在第3秒主动停止Broker，等待3秒后重新启动。测试结果：

- 识别到 `[DISCONNECTED]`；
- 进入 `[RECONNECTING]`，断线期间不生成新位置；
- Broker恢复后出现 `[RECONNECTED]` 和 `[RESUMED]`；
- 全部车辆在线状态通过 `[ONLINE_RESTORED]` 重新发布；
- 发生器退出码为0，恢复后继续发布GPS，未出现二次Traceback。

### 5.3 已有云端证据

- `iot/evidence/cloud_alert_probe.jsonl`：云端MQTT正常/异常消息接收记录；
- `iot/evidence/real_001_outdoor_capture.jsonl`：E53_ST1真实GPS室外采集记录；
- 模拟车辆使用 `sim_000...`，真实设备使用 `real_001...`；
- 两类数据源均发布到 `iot/carla/vehicle/{vehicle_id}/gps`，并遵循同一GPS Schema。

## 6. 结论

2号物联网数据模块已形成完整交付：GPS模拟器、MQTT通信工具、JSON契约、正常与异常数据集、真实GPS设备源码及验收证据。异常开箱已补齐并支持 `--demo-anomaly open` 确定性演示。

云端再次验收时，只需通过本机凭据文件增加 `--credentials` 参数；凭据文件不得提交到Git。
