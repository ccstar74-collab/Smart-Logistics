# MQTT 接口契约

当前契约版本为 `1.0`。后端、前端和基地阶段鸿蒙硬件均以本目录 JSON Schema 为准。

| 主题 | Schema | QoS | retain | 说明 |
|---|---|---:|---:|---|
| `iot/carla/vehicle/{vehicle_id}/gps` | `gps.schema.json` | 1 | 否 | 车辆实时位置，默认 1 Hz |
| `iot/carla/vehicle/{vehicle_id}/status` | `status.schema.json` | 1 | 是 | 在线/离线及运输状态 |
| `iot/carla/alert` | `alert.schema.json` | 1 | 否 | 模拟器、后端或真设备产生的告警 |
| `iot/carla/vehicle/{vehicle_id}/command` | `command.schema.json` | 1 | 否 | 后端向指定车辆下发路线调整指令 |
| `iot/carla/vehicle/{vehicle_id}/command/ack` | `command_ack.schema.json` | 1 | 否 | 模拟车辆返回指令执行结果 |

坐标约定：MQTT 中统一使用 WGS84，`lat` 在前、`lon` 在后。若前端使用高德地图，由前端或后端统一转换为 GCJ-02，不在模拟器中混用坐标系。

兼容规则：新增可选字段属于向后兼容；删除字段、改名、改变类型或单位时必须提升主版本并通知接口消费方。

调度约定：`command_id` 是全链路幂等键，后端重发相同指令时模拟器不得重复切换路线，只需重发首次 ACK。命令和 ACK 均不得使用 retain；后端收到 `EXECUTED` 后更新 `dispatch_command`，再结合车辆恢复正常的 GPS 将关联告警更新为 `RESOLVED`，不得删除历史告警。
