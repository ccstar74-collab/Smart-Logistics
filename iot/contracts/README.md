# MQTT 接口契约

当前契约版本为 `1.0`。后端、前端和基地阶段鸿蒙硬件均以本目录 JSON Schema 为准。

| 主题 | Schema | QoS | retain | 说明 |
|---|---|---:|---:|---|
| `iot/carla/vehicle/{vehicle_id}/gps` | `gps.schema.json` | 1 | 否 | 车辆实时位置，默认 1 Hz |
| `iot/carla/vehicle/{vehicle_id}/status` | `status.schema.json` | 1 | 是 | 在线/离线及运输状态 |
| `iot/carla/alert` | `alert.schema.json` | 1 | 否 | 模拟器、后端或真设备产生的告警 |
| `iot/carla/alert/recovery` | `alert_recovery.schema.json` | 1 | 否 | 真设备上报物理异常已恢复；后端更新原告警，不新建“恢复告警” |
| `iot/carla/vehicle/{vehicle_id}/command` | `command.schema.json` | 1 | 否 | 后端通知指定车辆某个任务路线已READY，只携带路线引用 |
| `iot/carla/vehicle/{vehicle_id}/command/ack` | `command_ack.schema.json` | 1 | 否 | 模拟车辆读取route API后的执行结果 |

坐标约定：MQTT 中统一使用 WGS84，`lat` 在前、`lon` 在后。若前端使用高德地图，由前端或后端统一转换为 GCJ-02，不在模拟器中混用坐标系。

兼容规则：新增可选字段属于向后兼容；删除字段、改名、改变类型或单位时必须提升主版本并通知接口消费方。

路线约定：后端 ETA 模块负责调用高德并缓存GCJ02路线；模拟器不得调用高德。模拟器通过 `GET /api/v1/transport-tasks/{taskId}/planned-route` 读取 `points`，转换成WGS84后发布GPS。可选的 `TASK_ROUTE_READY` 刷新通知只包含 `task_id / vehicle_id`，不得发送完整路线。

幂等约定：`command_id` 是消息幂等键；同一 `generatedAt` 表示同一份ETA缓存路线。后端重发相同指令时模拟器只重发首次ACK，不重复切换。命令和ACK均为QoS 1、`retain=false`。

告警恢复约定：`triggered_at` 必须等于原 `iot/carla/alert` 的 `timestamp`，后端据此关联同一车辆、同一类型的原始告警；`recovered_at` 是物理异常恢复时间。恢复消息只把 `condition_status` 标记为 `RECOVERED`，最终是否 `RESOLVED` 仍由业务后端结合关联调度指令是否 `COMPLETED` 统一判断。
