# MQTT 接口契约

当前契约版本为 `1.0`。后端、前端和基地阶段鸿蒙硬件均以本目录 JSON Schema 为准。

| 主题 | Schema | QoS | retain | 说明 |
|---|---|---:|---:|---|
| `iot/carla/vehicle/{vehicle_id}/gps` | `gps.schema.json` | 1 | 否 | 车辆实时位置，默认 1 Hz |
| `iot/carla/vehicle/{vehicle_id}/status` | `status.schema.json` | 1 | 是 | 在线/离线及运输状态 |
| `iot/carla/alert` | `alert.schema.json` | 1 | 否 | 模拟器、后端或真设备产生的告警 |
| `iot/carla/alert/recovery` | `alert_recovery.schema.json` | 1 | 否 | 设备或设备模拟器上报异常条件已恢复；后端更新原告警，不新建“恢复告警” |
| `iot/carla/vehicle/{vehicle_id}/command` | `command.schema.json` | 1 | 否 | 后端以 `TASK_ROUTE_READY` 通知新路线已激活，只携带路线引用 |
| `iot/carla/vehicle/{vehicle_id}/command/ack` | `command_ack.schema.json` | 1 | 否 | 模拟车辆读取 route API 后的执行结果 |

坐标约定：MQTT 中统一使用 WGS84，`lat` 在前、`lon` 在后。若前端使用高德地图，由前端或后端统一转换为 GCJ-02，不在模拟器中混用坐标系。

兼容规则：新增可选字段属于向后兼容；删除字段、改名、改变类型或单位时必须提升主版本并通知接口消费方。

路线约定：后端 ETA 模块负责调用高德并持久化 GCJ02 路线；模拟器不得直接调用高德。偏航恢复只能由模拟器终端按 `R` 触发：模拟器立即停车并先发布速度为 0 的 WGS84 锚点 GPS，等待入库缓冲后请求业务后端重规划；后端必须以不早于 `positionAt` 的最新位置作为起点，并将新路线设为 ACTIVE。模拟器通过重规划响应或 `GET /api/v1/transport-tasks/{taskId}/planned-route` 读取 `points`，转换成 WGS84 后继续发布 GPS。MQTT `TASK_ROUTE_READY` 仅用于刷新已激活路线，不控制车辆停车。

幂等约定：`command_id` 是消息幂等键；同一 `generatedAt` 表示同一份ETA缓存路线。后端重发相同指令时模拟器只重发首次ACK，不重复切换。命令和ACK均为QoS 1、`retain=false`。

告警恢复约定：`triggered_at` 必须逐字符等于原 `iot/carla/alert` 的 `timestamp`，后端据此关联同一车辆、同一类型的原始告警；`alert_type` 必须前后一致，`recovered_at` 才是本次恢复时间。一次ACTIVE异常只允许一条trigger和一条recovery。恢复消息只把 `condition_status` 标记为 `RECOVERED`，最终是否 `RESOLVED` 仍由业务后端结合关联调度指令是否 `COMPLETED` 统一判断。
