# 初始多路线规划前端联调说明

## 1. 最终业务流程

本说明替代旧的“先创建任务，再由调度员生成 READY 候选”初始规划流程。

```text
仓库管理员填写出库信息和终点
→ POST /initial-route-decisions 生成创建前决策
→ 后端从 originWarehouseId 读取权威起点
→ 后端生成 2～3 条真实候选并聚合路况、天气
→ 后端按 40/20/30/10 固定规则评分、排序
→ 智能体或规则模板生成可读解释
→ 前端展示路线并默认选中 recommendedRouteId
→ 仓库管理员可改选并人工确认
→ POST /transport-tasks/from-warehouse
→ 同一事务创建 Task 和唯一 v1 ACTIVE
```

调度员不参与初始路线确认。调度员只在运输中发生偏航等情况时调用
`replan-from-latest-location` 执行 `FAST_RECOVERY`。

## 2. 公共约定

- 公网 Base URL：`http://111.170.148.177:58080/api/v1`
- 角色：仅 `WAREHOUSE_MANAGER`
- 鉴权：`Authorization: Bearer <JWT>`
- 坐标系：统一 `GCJ02`
- 决策默认有效期：5 分钟
- `POST` 预规划和确认创建都必须各自使用一个稳定且不同的
  `Idempotency-Key`
- 前端只访问 58080，不携带高德 Key 或智能体凭证

## 3. 第一步：生成创建前路线决策

```http
POST /api/v1/initial-route-decisions
Authorization: Bearer <warehouse-manager-token>
Idempotency-Key: initial-plan-<uuid>
Content-Type: application/json
```

请求示例：

```json
{
  "originWarehouseId": 1,
  "endLocation": "湖南省长沙市雨花区长沙南站",
  "endLongitude": 113.06482,
  "endLatitude": 28.14672,
  "coordinateSystem": "GCJ02",
  "candidateCount": 3,
  "planningMode": "INITIAL_MULTI_OBJECTIVE"
}
```

注意：前端不再提交 `startLocation/startLongitude/startLatitude`。后端以
`originWarehouseId` 对应仓库的地址和坐标为权威起点，防止起点被篡改。

成功响应中的核心结构：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "decisionId": "ird_xxx",
    "status": "PENDING",
    "planningMode": "INITIAL_MULTI_OBJECTIVE",
    "planningResult": "MULTI_ROUTE",
    "start": {
      "location": "重庆中心仓",
      "longitude": 106.55187,
      "latitude": 29.572965,
      "coordinateSystem": "GCJ02"
    },
    "destination": {
      "location": "湖南省长沙市雨花区长沙南站",
      "longitude": 113.06482,
      "latitude": 28.14672,
      "coordinateSystem": "GCJ02"
    },
    "recommendedRouteId": "preview_route_xxx",
    "selectedRouteId": null,
    "scoringRuleVersion": "initial-route-score-v1",
    "recommendationSource": "RULE_FALLBACK",
    "calculatedAt": "2026-08-31T15:20:00+08:00",
    "expiresAt": "2026-08-31T15:25:00+08:00",
    "confirmedAt": null,
    "taskId": null,
    "weatherSnapshot": {},
    "routes": [
      {
        "routeId": "preview_route_xxx",
        "displayName": "候选路线 A",
        "rank": 1,
        "totalScore": 88.60,
        "distanceMeters": 829700,
        "referenceDurationSeconds": 33720,
        "trafficLevel": "SLOW",
        "trafficDataSource": "AMAP_DRIVING_V3",
        "provider": "AMAP",
        "coordinateSystem": "GCJ02",
        "points": [[106.55187, 29.572965]],
        "traffic": {},
        "weather": {},
        "scoreDetails": {
          "time": 100.00,
          "distance": 92.40,
          "traffic": 75.00,
          "weather": 80.00
        },
        "reasons": ["预计用时最短", "整体拥堵程度较低"]
      }
    ],
    "explanation": "综合评分推荐候选路线 A。"
  }
}
```

`recommendationSource`：

- `AGENT_EXPLANATION`：智能体解释成功
- `RULE_FALLBACK`：智能体不可用，后端使用规则模板；正式分数和排名仍有效，
  前端应允许继续创建任务

## 4. 页面刷新或 POST 超时：查询决策

```http
GET /api/v1/initial-route-decisions/{decisionId}
Authorization: Bearer <warehouse-manager-token>
```

用途：

- POST 超时后确认服务端是否已经成功保存；
- 页面刷新恢复路线弹窗；
- 确认 `status` 是否仍为 `PENDING`；
- 获取最终 `taskId`。

相同 `Idempotency-Key` 重试 POST 会返回同一份决策，不会重复调用规划并创建新快照。

## 5. 路线展示和人工选择

- 地图使用每条路线的 `points`，坐标系是 `GCJ02`。
- 路线卡片按 `rank` 升序显示。
- 初次打开默认选中 `recommendedRouteId`，但必须允许管理员改选。
- 倒计时以服务端 `expiresAt` 为准；不要用“本地 5 分钟”替代。
- `trafficLevel=UNKNOWN` 或天气 `UNKNOWN` 时显示“实时数据暂不可用”，不要把它
  显示成畅通或晴天。
- 前端不得计算或覆盖 `totalScore`、`rank`、`points`、距离和用时。

## 6. 第二步：确认路线并创建运输任务

```http
POST /api/v1/transport-tasks/from-warehouse
Authorization: Bearer <warehouse-manager-token>
Idempotency-Key: create-task-<uuid>
Content-Type: application/json
```

请求示例：

```json
{
  "routeDecisionId": "ird_xxx",
  "selectedRouteId": "preview_route_xxx",
  "routeSelectionRemark": "道路更熟悉",
  "ownerId": 30,
  "cargoTypeId": 40,
  "originWarehouseId": 1,
  "cargoId": 10,
  "vehicleId": 20,
  "endLocation": "湖南省长沙市雨花区长沙南站",
  "endLongitude": 113.06482,
  "endLatitude": 28.14672,
  "plannedStartTime": "2026-08-31T16:00:00+08:00",
  "planEndTime": "2026-08-31T23:00:00+08:00"
}
```

禁止附带以下字段：

```text
routePoints
distanceMeters
durationSeconds
totalScore
scoreDetails
recommendedRouteId
```

后端会锁定决策，重新校验决策归属、有效期、仓库、货物、车辆和终点上下文，
然后直接从已持久化候选快照复制路线；确认阶段不会再次调用高德。

成功后：

- `data.routeVersion = 1`
- `data.routeStatus = ACTIVE`
- 决策变为 `CONFIRMED`
- 未选中的候选只留在决策表，不进入 `transport_task_route`

同一个确认 `Idempotency-Key` 和同一个 `selectedRouteId` 重试，会返回同一任务；
更换 Key 或路线重复确认同一决策会返回冲突。

## 7. 错误处理

| HTTP / code | 含义 | 前端处理 |
|---|---|---|
| 400 / 40001 | 参数、坐标系或幂等键不合法 | 保留表单并定位字段 |
| 401 / 40101 | JWT 无效 | 重新登录 |
| 403 / 40301 | 非仓库管理员或数据不属于当前用户 | 显示无权限 |
| 404 / 40401 | 决策、候选或业务资源不存在 | 刷新资源并重新预规划 |
| 409 / 40901、40902 | 已确认、上下文改变、资源占用或并发冲突 | 查询决策和资源最新状态 |
| 410 / 41001 | 决策超过 `expiresAt` | 保留出库表单，用新 Key 重新 POST |
| 503 / 50301 | 未获得至少两条真实候选，或路线服务不可用 | 提示稍后重试，不伪造重复路线 |

## 8. 已退出初始规划的旧接口

以下入口不再用于初始路线规划，并已取消公开映射：

```text
POST /api/v1/transport-tasks
POST /api/v1/transport-tasks/{taskId}/routes
POST /api/v1/transport-tasks/{taskId}/routes/candidates
```

仍保留的调度员偏航恢复入口：

```text
POST /api/v1/transport-tasks/{taskId}/routes/replan-from-latest-location
```

## 9. 前端验收清单

- 预规划前数据库中没有新任务；
- 返回至少两条不同路线，能在地图同时绘制；
- 默认选中推荐路线但可人工改选；
- 刷新后可通过 GET 恢复同一决策；
- 重复点击不会生成重复决策或任务；
- 过期后不能确认，重新规划使用新 Key；
- 创建成功后任务只有一条 `v1 ACTIVE`；
- 正式路线几何、距离、用时与选中预览一致；
- 非仓库管理员调用预规划或创建接口得到 403。
