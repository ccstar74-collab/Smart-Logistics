# 多目标候选路线测试说明

> 状态说明（2026-08-31）：本文记录的是旧的 task-bound 候选路线测试流程，
> 不再用于初始路线规划验收。新流程请按
> [`api/frontend-initial-route-decision-integration.md`](api/frontend-initial-route-decision-integration.md)
> 和
> [`deployment/initial-route-decision-deployment.md`](deployment/initial-route-decision-deployment.md)
> 执行。旧文的已存在任务路线切换状态机测试仍可作为回归参考。

## 一、测试前提

- 使用 `integration/warehouse-route-ws`，或基于该集成分支创建的功能分支。
- 目标 MySQL 数据库已经执行路线相关迁移：`009`、`013` 和 `015`。
- 通过环境变量配置 `AMAP_WEB_SERVICE_KEY`，不要把 Key 写入代码或提交到 Git。
- 准备一个状态为 `WAITING` 或 `TRANSPORTING` 的运输任务。
- 任务必须包含完整的起点、终点经纬度，并且已经存在一条 `ACTIVE` 路线。
- 任务已经分配司机。
- 准备一个 `DISPATCHER` 调度员账号和该任务对应的 `DRIVER` 司机账号。

## 二、自动化回归测试

进入 `backend` 目录后执行多目标路线相关测试：

```powershell
.\mvnw.cmd '-Dtest=AmapEtaRouteProviderTest,MultiObjectiveRoutePlanningServiceTest,TransportTaskRouteServiceTest,DispatchCommandServiceTest,TransportTaskControllerTest' test
```

然后执行全量回归：

```powershell
.\mvnw.cmd test
```

成功标准：测试结果中 `Failures: 0`、`Errors: 0`。

## 三、准备接口测试变量

设置后端地址和任务 ID，并在本机填写测试账号。不要提交真实密码。

```powershell
$baseUrl = 'http://127.0.0.1:18080'
$taskId = 1
```

登录调度员账号：

```powershell
$dispatcherLogin = Invoke-RestMethod -Method Post `
  -Uri "$baseUrl/api/v1/auth/login" `
  -ContentType 'application/json' `
  -Body '{"username":"<调度员账号>","password":"<密码>"}'

$dispatcherHeaders = @{
  Authorization = "Bearer $($dispatcherLogin.data.accessToken)"
}
```

登录任务对应的司机账号：

```powershell
$driverLogin = Invoke-RestMethod -Method Post `
  -Uri "$baseUrl/api/v1/auth/login" `
  -ContentType 'application/json' `
  -Body '{"username":"<司机账号>","password":"<密码>"}'

$driverHeaders = @{
  Authorization = "Bearer $($driverLogin.data.accessToken)"
}
```

## 四、生成多目标候选路线

调度员调用候选路线生成接口：

```powershell
$candidates = Invoke-RestMethod -Method Post `
  -Uri "$baseUrl/api/v1/transport-tasks/$taskId/routes/candidates" `
  -Headers $dispatcherHeaders
```

查询任务的全部路线，并选择一条候选路线作为切换目标：

```powershell
$routesBefore = Invoke-RestMethod -Method Get `
  -Uri "$baseUrl/api/v1/transport-tasks/$taskId/routes" `
  -Headers $dispatcherHeaders

$targetRouteId = $candidates.data[0].routeId

$routesBefore.data | Select-Object `
  routeId, routeVersion, routeStatus, distanceMeters, referenceDurationSeconds
```

成功标准：

- 原来的 v1 路线仍然是 `ACTIVE`。
- 至少生成两条新的 `READY` 候选路线。
- 新路线版本连续递增，例如 v2、v3。
- 候选路线不会自动替换当前 `ACTIVE` 路线。
- 每条新路线包含不同的轨迹、距离或预计时长。

## 五、创建普通路线切换指令

调度员选择目标 `READY` 路线并创建 `ROUTE_CHANGE`：

```powershell
$commandBody = @{
  taskId = $taskId
  commandType = 'ROUTE_CHANGE'
  content = '切换到选定的多目标候选路线'
  routeId = $targetRouteId
} | ConvertTo-Json

$command = Invoke-RestMethod -Method Post `
  -Uri "$baseUrl/api/v1/dispatch-commands" `
  -Headers $dispatcherHeaders `
  -ContentType 'application/json' `
  -Body $commandBody

$commandId = $command.data.id
```

成功标准：

- 指令类型为 `ROUTE_CHANGE`。
- 指令初始状态为 `SENT`。
- `routeId` 指向本次任务的一条 `READY` 路线。
- 创建指令后还不能切换路线。

## 六、验证 ACKNOWLEDGED 不切换路线

司机确认收到指令：

```powershell
Invoke-RestMethod -Method Patch `
  -Uri "$baseUrl/api/v1/dispatch-commands/$commandId/status" `
  -Headers $driverHeaders `
  -ContentType 'application/json' `
  -Body '{"status":"ACKNOWLEDGED"}'
```

再次查询路线：

```powershell
$routesAfterAck = Invoke-RestMethod -Method Get `
  -Uri "$baseUrl/api/v1/transport-tasks/$taskId/routes" `
  -Headers $driverHeaders

$routesAfterAck.data | Select-Object routeId, routeVersion, routeStatus
```

成功标准：

- DispatchCommand 状态变为 `ACKNOWLEDGED`。
- 原路线仍然是 `ACTIVE`。
- 目标路线仍然是 `READY`。
- `planned-route` 仍返回原来的 `ACTIVE` 路线。

如果 ACK 后路线已经发生切换，说明状态机实现错误。

## 七、验证 EXECUTING 才执行路线切换

司机开始执行指令：

```powershell
Invoke-RestMethod -Method Patch `
  -Uri "$baseUrl/api/v1/dispatch-commands/$commandId/status" `
  -Headers $driverHeaders `
  -ContentType 'application/json' `
  -Body '{"status":"EXECUTING"}'
```

查询全部路线和当前规划路线：

```powershell
$routesAfterExecuting = Invoke-RestMethod -Method Get `
  -Uri "$baseUrl/api/v1/transport-tasks/$taskId/routes" `
  -Headers $driverHeaders

$plannedRoute = Invoke-RestMethod -Method Get `
  -Uri "$baseUrl/api/v1/transport-tasks/$taskId/planned-route" `
  -Headers $driverHeaders

$routesAfterExecuting.data | Select-Object `
  routeId, routeVersion, routeStatus, activatedAt, deactivatedAt

$plannedRoute.data | Select-Object routeId, routeVersion, routeStatus
```

成功标准：

- 原 `ACTIVE` 路线事务性变为 `INACTIVE`。
- 选中的目标路线从 `READY` 变为唯一的 `ACTIVE`。
- 未选中的其他候选路线继续保持 `READY`。
- 原路线的 `deactivatedAt` 已写入。
- 新路线的 `activatedAt` 已写入。
- `planned-route` 立即返回新的 `ACTIVE` 路线。
- 后续 ETA 可以基于新路线重新计算。

## 八、负向和异常测试

### 1. 公开直接激活入口必须不存在

调用下面的旧接口不应成功：

```text
PUT /api/v1/transport-tasks/{taskId}/routes/{routeId}/activate
```

普通路线不能绕过 `ROUTE_CHANGE` 和 DispatchCommand 生命周期直接激活。

### 2. 非调度员不能生成候选路线

使用 `OWNER`、`DRIVER`、`WAREHOUSE_MANAGER` 或 `ADMIN` 调用：

```text
POST /api/v1/transport-tasks/{taskId}/routes/candidates
```

预期返回 `403 Forbidden`。

### 3. 非法目标路线必须被拒绝

以下情况不能创建或执行普通路线切换：

- `routeId` 不属于当前任务。
- 目标路线不是 `READY`。
- 目标路线已经是 `ACTIVE` 或 `INACTIVE`。
- 任务已经 `COMPLETED` 或 `CANCELLED`。

### 4. 多 ACTIVE 数据必须快速失败

如果测试环境被故意写入同一任务两条 `ACTIVE` 路线，查询当前路线时必须返回
`DATA_CONFLICT`，不能静默选择 routeVersion 最大的一条。

### 5. FAST_RECOVERY 不能受到影响

偏航恢复仍然必须：

```text
最新 GPS → replan → 新路线直接 ACTIVE
```

它不生成 `READY` 候选、不等待 Agent，也不经过普通人工路线选择。

## 九、最终验收清单

- [ ] v1 `ACTIVE` 与 v2/v3 `READY` 可以同时存在。
- [ ] 候选路线至少两条且版本连续。
- [ ] ACKNOWLEDGED 后没有切换路线。
- [ ] EXECUTING 后才完成事务切换。
- [ ] 任意时刻最多只有一条 `ACTIVE` 路线。
- [ ] `planned-route` 返回新的 `ACTIVE` 路线。
- [ ] 激活与停用时间正确持久化。
- [ ] 非调度员不能生成候选路线。
- [ ] 公开直接 activate 旁路不存在。
- [ ] 多 ACTIVE 异常会 fail-fast。
- [ ] FAST_RECOVERY、ETA、Alarm、Dispatch、Playback 回归测试通过。
