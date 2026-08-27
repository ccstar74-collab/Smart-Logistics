# GPS 实时异常判定服务

## 作用

正式偏航和异常停留由云端实时后端判定，模拟器只负责产生原始 GPS。
硬件与模拟车辆使用同一条链路：

```text
GPS -> MQTT -> InfluxDB -> 运输任务规划路线 -> 异常状态机 -> alarm 表
```

异常开箱没有可靠的 GPS 特征，仍由 E53_ST1 按键/箱门传感器通过
`iot/carla/alert` 上报。

## 生效条件

- 运输任务状态为 `TRANSPORTING`。
- 任务已填写完整起终点坐标，可以取得规划路线。
- 任务绑定车辆，且 `vehicle.sim_code` 与 GPS `vehicle_id` 一致。
- InfluxDB 中存在未过期 GPS。
- `ETA_ENABLED=true`，当前异常状态机复用 ETA 刷新周期取得的 GPS 和路线。
- `REALTIME_ANOMALY_ENABLED=true`。

## 默认规则

### 偏航

- 当前 GPS 到规划路线距离大于等于 `100m`。
- 连续保持 `30s` 后产生一条 `ROUTE_DEVIATION` 告警。
- 距离回落至 `60m` 内视为恢复。
- 同一异常持续期间不重复入库，并保存当前活动 `alarmId`。
- 距离恢复后，使用同一个 `alarmId` 调用物理条件恢复适配层；恢复成功后再次偏航可产生下一条告警。

### 异常停留

- 当前速度小于等于 `1km/h`。
- GPS 始终位于 `20m` 半径内。
- 连续保持 `2min` 后产生一条 `ABNORMAL_STOP` 告警。
- 距离终点不足 `100m` 时停车视为正常到达，不产生告警。
- 车辆重新移动后，使用原 `alarmId` 调用物理条件恢复适配层。
- 恢复调用成功后解除内部锁定，下一次异常停留可再次告警。

## 恢复与最终消警边界

GPS 状态机只判断物理异常是否恢复，不读取 `dispatch_command`，也不直接将
`alarm.status` 改为 `RESOLVED`。

当前预留接口为：

```java
AlarmConditionRecoveryPort.markConditionRecovered(Long alarmId, Instant recoveredAt)
```

告警模块第四轮完成后，用一个薄适配器把该接口转发给实际
`AlarmResolutionService.markConditionRecovered(...)`。实际服务负责写
`conditionStatus=RECOVERED`、`recoveredAt`，并结合至少一条关联调度指令
`COMPLETED` 的条件决定是否最终 `RESOLVED`。

在第四轮适配器尚未提供时，恢复调用返回“尚未接入”，状态机保留活动
`alarmId` 并对同一个 ID 重试，不创建“恢复告警”。

异常开箱不能由 GPS 判断恢复。E53_ST1 的 F2/真实箱门关闭事件后续需要通过
MQTT 进入同一个恢复适配层；本模块不根据速度或路线猜测关箱状态。

## 环境变量

| 变量 | 默认值 | 含义 |
| --- | --- | --- |
| `REALTIME_ANOMALY_ENABLED` | `true` | 是否启用 GPS 异常判断 |
| `ANOMALY_DEVIATION_THRESHOLD_METERS` | `100` | 偏航触发距离 |
| `ANOMALY_DEVIATION_RECOVERY_METERS` | `60` | 偏航恢复距离 |
| `ANOMALY_DEVIATION_DURATION` | `PT30S` | 偏航连续时间 |
| `ANOMALY_STOP_SPEED_THRESHOLD_KMH` | `1` | 停留速度阈值 |
| `ANOMALY_STOP_RADIUS_METERS` | `20` | 停留位置半径 |
| `ANOMALY_STOP_DURATION` | `PT2M` | 停留连续时间 |
| `ANOMALY_DESTINATION_GUARD_METERS` | `100` | 终点豁免距离 |

时长采用 ISO-8601 Duration，例如 `PT10S`、`PT2M`。

## 模拟器验收方式

正式判定验收必须使用：

```text
--alert-mode raw
```

`raw` 模式只发布 GPS，不提前发布 `iot/carla/alert`。如果使用
`--alert-mode precomputed`，看到的告警来自模拟器，不能证明云端判定服务生效。

## 告警结果

判定成功后直接复用现有幂等入库服务写入 `alarm`：

- `task_id`：当前运输任务 ID。
- `device_code`：车辆 `sim_code`。
- `alarm_type`：`ROUTE_DEVIATION` 或 `ABNORMAL_STOP`。
- `source`：`backend`。
- `status`：`UNHANDLED`。
- `event_key`：沿用现有 SHA-256 幂等键。

触发适配层返回 `AlarmIngestionResult(alarmId, created)`。首次写入返回新 ID，
MQTT 幂等命中时返回已有告警 ID，状态机始终持有原告警身份。

前端继续使用现有告警 REST API 查询和处理，不需要新增接口。
