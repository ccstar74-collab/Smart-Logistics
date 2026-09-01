# 初始多路线决策云端 E2E 与联调报告

## 1. 验收结论

2026-08-31 已在云端集成环境完成真实 REST、MySQL 持久化和高德路线数据验收。

本次验证基线：

```text
integration/warehouse-route-ws @ 29f057e
```

最终业务链已走通：

```text
仓库管理员创建任务前预规划
→ 后端生成并持久化 3 条候选路线
→ 后端聚合实时路况并执行固定规则评分
→ 仓库管理员确认推荐路线
→ 同一事务创建运输任务和唯一 v1 ACTIVE
→ 相同确认 Idempotency-Key 重试返回原任务
```

本次未接入智能体 HTTP 服务，因此实测返回
`recommendationSource=RULE_FALLBACK`。正式评分、排序、任务创建和路线持久化均不受影响。

## 2. 环境与权限

- 公网 API：`http://111.170.148.177:58080/api/v1`
- 服务端内部端口：`8080`
- 预规划和确认角色：仅 `WAREHOUSE_MANAGER`
- migration：`016_initial_route_decision.sql`
- 决策有效期：5 分钟，以响应中的 `expiresAt` 为准
- 坐标系：`GCJ02`

所有请求都必须携带仓库管理员 JWT。两个写请求必须使用不同且稳定的
`Idempotency-Key`：

```text
POST /initial-route-decisions
POST /transport-tasks/from-warehouse
```

## 3. 云端实测证据

### 3.1 预规划

实测使用成都中心仓作为权威起点、重庆市渝中区作为终点，返回 3 条不同候选路线：

```text
status                 = PENDING
planningResult         = MULTI_ROUTE
recommendationSource   = RULE_FALLBACK
candidate count        = 3
trafficDataSource      = AMAP_DRIVING_V3
recommended rank       = 1
recommended totalScore = 100
```

候选路线均包含可绘制的 `points`、距离、参考用时、路况快照、分项评分和推荐理由。

### 3.2 人工确认与任务创建

实测决策与任务：

```text
decisionId       = ird_c91cd99701344affb1a8b83e93194968
decision status  = CONFIRMED
taskId           = 2
task status      = WAITING
routeVersion     = 1
routeStatus      = ACTIVE
```

正式路线与所选预览快照一致：

```text
distanceMeters            = 308484
referenceDurationSeconds  = 14533
```

任务只创建了一条正式路线，状态为 `v1 ACTIVE`；其他未选候选只保留在决策快照表中。

### 3.3 幂等

使用相同确认 `Idempotency-Key` 和相同请求体重试后，服务仍返回：

```text
taskId       = 2
routeVersion = 1
routeStatus  = ACTIVE
```

没有重复创建任务或路线。

## 4. 前端联调合同

详细字段与错误处理见：

```text
docs/api/frontend-initial-route-decision-integration.md
```

前端核心流程：

1. 调用 `POST /api/v1/initial-route-decisions` 获取并展示候选路线。
2. 按 `rank` 排序，默认选中 `recommendedRouteId`，但允许仓库管理员改选。
3. 倒计时必须使用服务端 `expiresAt`。
4. 确认时只提交 `routeDecisionId`、`selectedRouteId` 和出库业务字段。
5. 不得回传或覆盖路线 points、距离、用时、分数和排序。
6. 调用 `POST /api/v1/transport-tasks/from-warehouse` 创建任务。
7. POST 超时应使用原 `Idempotency-Key` 重试，不能生成新 Key。
8. 成功后以返回的 `taskId` 进入任务详情，正式路线应为唯一 `v1 ACTIVE`。

旧的 task-bound 初始规划入口已经退出：

```text
POST /api/v1/transport-tasks
POST /api/v1/transport-tasks/{taskId}/routes
POST /api/v1/transport-tasks/{taskId}/routes/candidates
```

调度员只保留运输中偏航恢复入口：

```text
POST /api/v1/transport-tasks/{taskId}/routes/replan-from-latest-location
```

## 5. 智能体联调边界

详细输入输出合同见：

```text
docs/api/agent-initial-route-decision-integration.md
```

业务后端拥有正式评分和排序权，当前固定权重为：

```text
时间 40%
距离 20%
路况 30%
天气 10%
```

智能体只负责基于后端候选快照生成可读解释，不得修改：

- `routeId`
- `rank`
- `totalScore`
- `scoreDetails`
- 距离、用时、points、路况或天气快照
- 最终任务和路线状态

智能体不可用、超时或返回非法内容时，业务后端必须继续使用
`RULE_FALLBACK`；前端允许仓库管理员继续确认创建任务。

## 6. 已修复问题

云端首次确认暴露 MyBatis 将 `rank_no` 生成为 `rank_no AS rank`，与 MySQL 8
保留字冲突。PR #7 已在 `29f057e` 修复，并增加映射回归测试。

修复后验证：

```text
673 tests
0 failures
0 errors
Maven package passed
cloud E2E passed
```

该修复不改变 `016` schema，不需要重复执行 migration。

## 7. 后续联调重点

前端接入时继续验证：

- 页面刷新后使用 GET 恢复同一决策；
- 决策过期返回 HTTP 410 / code 41001；
- 非仓库管理员调用预规划和确认接口返回 403；
- 重复点击确认不会创建重复任务；
- 地图绘制使用返回的 GCJ02 points；
- `UNKNOWN` 路况或天气显示“实时数据暂不可用”。

智能体接入后只需补充验证：

- 成功时 `recommendationSource=AGENT_EXPLANATION`；
- 解释与候选事实一致；
- 超时、5xx 或非法输出能够自动回退到 `RULE_FALLBACK`；
- 智能体接入不改变固定评分、排序和任务事务。
