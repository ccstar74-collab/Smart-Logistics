# 多目标候选路线前端联调说明

> 状态说明（2026-08-31）：本文原有“创建任务后由调度员生成 READY 候选”的内容
> 不再作为初始路线规划合同。新的初始规划请使用
> [`frontend-initial-route-decision-integration.md`](frontend-initial-route-decision-integration.md)。
> 本文的 `ROUTE_CHANGE → ACKNOWLEDGED → EXECUTING` 仍可作为已有任务普通路线
> 切换的参考；调度员偏航恢复仍走 `FAST_RECOVERY`。

本文档面向调度端、司机端和地图页面开发人员，说明多目标候选路线的页面流程、
REST 接口、状态机、地图刷新规则及联调验收标准。

## 1. 当前联调环境与适用范围

| 项目 | 当前值 |
|---|---|
| 适用基线 | `integration/warehouse-route-ws @ 211689f` |
| 公网 REST Base URL | `http://111.170.148.177:58080` |
| REST 统一前缀 | `/api/v1` |
| 通知 WebSocket | `ws://111.170.148.177:58080/ws/notifications?token=<accessToken>` |
| REST 鉴权 | `Authorization: Bearer <accessToken>` |
| 路线坐标顺序 | `[longitude, latitude]`，即 `[经度, 纬度]` |

当前公网 `58080` 已完成真实 E2E 验证。本合同只覆盖已经合并的多目标候选路线，
不包含尚未合并的天气、路况快照或 `015_route_traffic_snapshot.sql` 字段。

当前公网使用明文 HTTP/WS，仅适合课程联调环境；正式部署应切换 HTTPS/WSS。
前端禁止把账号、密码、JWT 或高德 Key 写入源码、日志或 Git。

所有 REST 响应使用统一信封：

```ts
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}
```

成功示例：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

业务失败时同时返回对应的 HTTP 状态码和业务 `code`，前端不能只判断 HTTP 200。

## 2. 最终业务流程

普通多目标路线必须遵循：

```text
调度员打开任务
  → 查询当前 ACTIVE 和历史路线
  → 生成至少两条 READY 候选路线
  → 调度员选择一条 READY
  → 创建 ROUTE_CHANGE 指令（SENT）
  → 目标司机 ACKNOWLEDGED（只确认收到，不切路线）
  → 目标司机 EXECUTING
  → 原 ACTIVE 变为 INACTIVE
  → 目标 READY 变为唯一 ACTIVE
  → planned-route 返回新 ACTIVE
```

核心规则：

- 生成候选路线后，原路线仍然是 `ACTIVE`，候选路线是 `READY`。
- `ACKNOWLEDGED` 绝对不能切换路线。
- 只有 `EXECUTING` 才会在后端事务中切换路线。
- 未选中的候选路线继续保持 `READY`。
- 前端不得调用或自行恢复旧的直接激活入口：
  `PUT /transport-tasks/{taskId}/routes/{routeId}/activate`。
- 偏航 `FAST_RECOVERY` 是另一条内部重规划链路，不要与普通人工选路 UI 合并。

## 3. 接口与权限速查

| 接口 | 角色 | 用途 |
|---|---|---|
| `GET /transport-tasks/{taskId}` | 有任务数据权限的角色 | 查询任务、司机和当前路线摘要 |
| `GET /transport-tasks/{taskId}/routes` | 有任务数据权限的角色 | 查询该任务全部路线版本 |
| `POST /transport-tasks/{taskId}/routes/candidates` | 仅 `DISPATCHER` | 生成并持久化多条 `READY` 候选路线 |
| `GET /transport-tasks/{taskId}/planned-route` | 有任务数据权限的角色 | 查询当前唯一 `ACTIVE` 路线 |
| `POST /dispatch-commands` | 仅 `DISPATCHER` | 创建 `ROUTE_CHANGE` 指令 |
| `GET /dispatch-commands?taskId={taskId}` | `DISPATCHER` / `ADMIN` | 调度端查询任务指令历史 |
| `GET /dispatch-commands/{commandId}` | 目标 `DRIVER` / `DISPATCHER` / `ADMIN` | 查询指令详情 |
| `GET /drivers/me/dispatch-commands` | 仅 `DRIVER` | 司机查询自己的指令 |
| `PATCH /dispatch-commands/{commandId}/status` | 仅目标 `DRIVER` | ACK、开始执行、完成或拒绝 |

`ADMIN` 可以查看指令，但不能生成候选路线、创建指令或替司机更新状态。

## 4. 路线数据结构

`GET /transport-tasks/{taskId}/routes` 和候选生成接口中的路线结构一致：

```ts
export type RouteStatus = 'READY' | 'ACTIVE' | 'INACTIVE'

export interface TransportTaskRoute {
  routeId: string
  taskId: number
  routeVersion: number
  routeStatus: RouteStatus
  provider: string
  coordinateSystem: string
  distanceMeters: number
  referenceDurationSeconds: number
  generatedAt: string
  activatedAt: string | null
  deactivatedAt: string | null
  points: [number, number][]
}
```

示例：

```json
{
  "routeId": "route_8855e795-937b-4691-8621-1631b08015de",
  "taskId": 12,
  "routeVersion": 2,
  "routeStatus": "READY",
  "provider": "AMAP",
  "coordinateSystem": "GCJ02",
  "distanceMeters": 13376,
  "referenceDurationSeconds": 2306,
  "generatedAt": "2026-08-31T09:39:00+08:00",
  "activatedAt": null,
  "deactivatedAt": null,
  "points": [
    [106.4618, 29.552],
    [106.4621, 29.5523]
  ]
}
```

前端注意事项：

- 必须根据 `routeStatus` 判断当前路线，不能把最高版本默认当作 `ACTIVE`。
- `points` 已按行驶顺序排列，单点顺序为 `[经度, 纬度]`。
- 地图组件应消费 `coordinateSystem`；当前高德路线通常为 `GCJ02`。
- 距离显示可使用 `(distanceMeters / 1000).toFixed(1) + ' km'`。
- 时间显示可使用 `Math.ceil(referenceDurationSeconds / 60) + ' 分钟'`。
- 后端没有返回“最快、最短”等固定标签。前端如需标签，应根据本批数据比较
  `distanceMeters` 和 `referenceDurationSeconds` 后生成，不能按数组下标硬编码。

## 5. 调度端接入

### 5.1 加载任务和全部路线

进入任务详情页时并行请求：

```http
GET /api/v1/transport-tasks/{taskId}
GET /api/v1/transport-tasks/{taskId}/routes
GET /api/v1/transport-tasks/{taskId}/planned-route
```

推荐地图样式：

| 状态 | 样式 | 操作 |
|---|---|---|
| `ACTIVE` | 蓝色实线、默认选中 | 仅查看 |
| `READY` | 不同颜色虚线 | 可选择并创建切换指令 |
| `INACTIVE` | 灰色细线，默认隐藏 | 可查看历史 |

### 5.2 生成候选路线

```http
POST /api/v1/transport-tasks/{taskId}/routes/candidates
Authorization: Bearer <dispatcherToken>
```

无请求体。任务必须满足：

- 状态为 `WAITING` 或 `TRANSPORTING`；
- 起点和终点经纬度完整；
- 调用者为 `DISPATCHER`。

成功返回 `TransportTaskRoute[]`，通常至少两条且状态均为 `READY`。

前端调用要求：

1. 点击后立即进入 loading，并禁用重复点击。
2. 请求成功后重新调用 `GET /routes`，以服务端完整列表覆盖本地状态。
3. 请求超时或网络结果不明确时，也先调用 `GET /routes` 检查是否已写入，不能立即重试。
4. 相同任务重复生成时，后端会排除已经存在的轨迹，可能因新增候选不足两条返回 503。

### 5.3 选择路线并创建指令

请求：

```http
POST /api/v1/dispatch-commands
Content-Type: application/json
Authorization: Bearer <dispatcherToken>
```

```json
{
  "taskId": 12,
  "commandType": "ROUTE_CHANGE",
  "content": "切换到选定的多目标候选路线",
  "routeId": "route_8855e795-937b-4691-8621-1631b08015de"
}
```

`routeId` 必须属于当前任务且当前状态为 `READY`。成功后的指令状态为 `SENT`：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 18,
    "taskId": 12,
    "targetDriverId": 1,
    "routeId": "route_8855e795-937b-4691-8621-1631b08015de",
    "routeVersion": 2,
    "routeStatus": "READY",
    "commandType": "ROUTE_CHANGE",
    "status": "SENT",
    "sentAt": "2026-08-31T09:41:43+08:00"
  }
}
```

创建指令后路线还没有切换。前端应：

- 将目标候选卡片标记为“等待司机确认”；
- 保留原 `ACTIVE` 地图主路线；
- 禁用本次提交按钮，避免重复创建相同指令；
- 通过指令查询接口刷新 `SENT / ACKNOWLEDGED / EXECUTING` 状态。

建议 MVP 在存在未结束的 `ROUTE_CHANGE`（`SENT`、`ACKNOWLEDGED` 或
`EXECUTING`）时禁止再次创建同任务的路线切换指令。

### 5.4 调度端观察指令状态

```http
GET /api/v1/dispatch-commands?page=1&pageSize=20&taskId=12&commandType=ROUTE_CHANGE
```

当前 WebSocket 不会把 ACK、EXECUTING 或路线切换事件推给调度员。调度端可在指令
未结束期间每 3~5 秒轮询一次，页面失焦时暂停；也可提供“刷新状态”按钮。

看到 `EXECUTING` 后立即并行刷新：

```http
GET /api/v1/transport-tasks/{taskId}/routes
GET /api/v1/transport-tasks/{taskId}/planned-route
```

然后用新的 `ACTIVE` 轨迹替换地图主路线。

## 6. 司机端接入

### 6.1 接收和查询本人指令

创建指令后，目标司机会收到消息中心通知：

```text
type = DISPATCH_COMMAND_CREATED
businessType = DISPATCH_COMMAND
businessId = commandId
```

WebSocket：

```text
ws://111.170.148.177:58080/ws/notifications?token=<driverToken>
```

收到 `NOTIFICATION_CREATED` 后，按 `notification.id` 去重，并根据
`businessId` 查询指令详情；断线重连后使用 REST 补偿。完整通知协议见
`docs/api/frontend-notification-integration.md`，但联调时应把其中旧端口替换为
当前公网 `58080`。

司机也可以分页查询本人指令：

```http
GET /api/v1/drivers/me/dispatch-commands?page=1&pageSize=20
```

### 6.2 状态机与按钮

合法状态变化：

```text
SENT → ACKNOWLEDGED → EXECUTING → COMPLETED
  └──────────→ REJECTED
               ↑
ACKNOWLEDGED ───┘
```

| 当前状态 | 司机端按钮 | 提交状态 | 是否切换路线 |
|---|---|---|---|
| `SENT` | 确认收到 | `ACKNOWLEDGED` | 否 |
| `SENT` | 拒绝 | `REJECTED` | 否 |
| `ACKNOWLEDGED` | 开始执行 | `EXECUTING` | 是 |
| `ACKNOWLEDGED` | 拒绝 | `REJECTED` | 否 |
| `EXECUTING` | 完成指令 | `COMPLETED` | 路线已在此前切换 |

更新接口：

```http
PATCH /api/v1/dispatch-commands/{commandId}/status
Content-Type: application/json
Authorization: Bearer <driverToken>
```

确认收到：

```json
{
  "status": "ACKNOWLEDGED"
}
```

开始执行：

```json
{
  "status": "EXECUTING"
}
```

拒绝时可附加反馈：

```json
{
  "status": "REJECTED",
  "feedback": "当前道路条件不允许切换"
}
```

### 6.3 ACK 后的前端行为

PATCH 成功后：

- 指令显示“已确认”；
- 原路线仍应显示为 `ACTIVE`；
- 目标路线仍应显示为 `READY`；
- `planned-route` 仍应返回原路线；
- 显示“开始执行”按钮。

ACK 后不能提前把地图切到候选路线，即使前端已经知道目标 `routeId`。

### 6.4 EXECUTING 后的前端行为

PATCH 成功响应中的目标 `routeStatus` 应变为 `ACTIVE`。随后立即并行刷新：

```http
GET /api/v1/transport-tasks/{taskId}/routes
GET /api/v1/transport-tasks/{taskId}/planned-route
```

预期：

- 原路线：`INACTIVE`，`deactivatedAt` 非空；
- 目标路线：唯一 `ACTIVE`，`activatedAt` 非空；
- 其他候选：继续 `READY`；
- `planned-route.routeId` 等于指令目标 `routeId`。

只有拿到刷新后的新 `ACTIVE` 数据，前端才替换地图主路线。

## 7. TypeScript / Axios 接口示例

以下示例假设项目已有统一 Axios 实例，并已通过拦截器注入 JWT：

```ts
import api from '@/api'

export type DispatchStatus =
  | 'SENT'
  | 'ACKNOWLEDGED'
  | 'EXECUTING'
  | 'COMPLETED'
  | 'REJECTED'

export async function listTaskRoutes(taskId: number) {
  const response = await api.get<ApiResponse<TransportTaskRoute[]>>(
    `/api/v1/transport-tasks/${taskId}/routes`,
  )
  return response.data.data
}

export async function generateRouteCandidates(taskId: number) {
  const response = await api.post<ApiResponse<TransportTaskRoute[]>>(
    `/api/v1/transport-tasks/${taskId}/routes/candidates`,
  )
  return response.data.data
}

export async function createRouteChangeCommand(
  taskId: number,
  routeId: string,
) {
  const response = await api.post<ApiResponse<DispatchCommand>>(
    '/api/v1/dispatch-commands',
    {
      taskId,
      commandType: 'ROUTE_CHANGE',
      content: '切换到选定的多目标候选路线',
      routeId,
    },
  )
  return response.data.data
}

export async function updateDispatchStatus(
  commandId: number,
  status: DispatchStatus,
  feedback?: string,
) {
  const response = await api.patch<ApiResponse<DispatchCommand>>(
    `/api/v1/dispatch-commands/${commandId}/status`,
    { status, feedback },
  )
  return response.data.data
}

export async function getPlannedRoute(taskId: number) {
  const response = await api.get<ApiResponse<PlannedRoute>>(
    `/api/v1/transport-tasks/${taskId}/planned-route`,
  )
  return response.data.data
}
```

指令结构至少定义以下字段：

```ts
export interface DispatchCommand {
  id: number
  taskId: number
  taskNo: string
  targetDriverId: number
  targetDriverName: string
  vehicleId: number
  plateNumber: string
  routeId: string | null
  routeVersion: number | null
  routeStatus: RouteStatus | null
  commandType: 'TEXT' | 'ROUTE_CHANGE'
  content: string
  status: DispatchStatus
  feedback: string | null
  sentAt: string | null
  acknowledgedAt: string | null
  executingAt: string | null
  completedAt: string | null
  rejectedAt: string | null
}

export interface PlannedRoute {
  taskId: number
  routeId: string
  routeVersion: number
  routeStatus: 'ACTIVE'
  vehicleDeviceCode: string
  provider: string
  coordinateSystem: string
  distanceMeters: number
  referenceDurationSeconds: number
  generatedAt: string
  points: [number, number][]
}
```

## 8. 错误处理

| HTTP | 业务 code | 常见场景 | 前端处理 |
|---|---:|---|---|
| 400 | `40001` | 参数缺失、枚举或 `routeId` 格式错误 | 保留页面状态并提示校验信息 |
| 401 | `40101` | Token 缺失、无效或过期 | 清理登录态并重新登录 |
| 403 | `40301` | 非调度员生成候选、非目标司机更新指令 | 显示无权限，不要当成登录过期 |
| 404 | `40401` | 任务、路线或指令不存在 | 返回列表或刷新权威数据 |
| 409 | `40902` | 任务状态不允许、目标不再 READY、非法状态跳转 | 刷新任务、路线和指令后提示冲突 |
| 503 | `50301` | 高德不可用、超时、候选不足两条 | 保留 ACTIVE；允许稍后人工重试 |
| 500 | `50001` | 数据 invariant 或未知内部错误 | 停止自动重试并上报后端日志 |

前端遇到 `40902` 时禁止继续使用本地缓存的路线状态，必须重新请求 `/routes` 和
指令详情。遇到候选 POST 超时，先 GET 路线再决定是否允许重试。

如果 Postman/PowerShell 正常而浏览器报 CORS，需让后端把前端实际 Origin 加入
`CORS_ALLOWED_ORIGINS`，前端不要通过关闭浏览器安全策略解决。

## 9. 前后端联合验收清单

- [ ] 调度员可以看到一条 `ACTIVE` 和多条 `READY`，地图轨迹可切换预览。
- [ ] 非调度员看不到“生成候选路线”和“创建路线切换”操作。
- [ ] 生成按钮有 loading、防重复提交和失败恢复。
- [ ] 创建指令后显示 `SENT`，地图仍使用原 `ACTIVE`。
- [ ] 只有目标司机收到 `DISPATCH_COMMAND_CREATED` 通知。
- [ ] 司机 ACK 后显示 `ACKNOWLEDGED`，地图仍使用原 `ACTIVE`。
- [ ] 司机 EXECUTING 后原路线变 `INACTIVE`，目标路线变唯一 `ACTIVE`。
- [ ] 未选中的候选路线继续保持 `READY`。
- [ ] `planned-route` 与路线列表中的唯一 `ACTIVE` 一致。
- [ ] 页面刷新后状态仍然一致，证明前端没有只依赖内存状态。
- [ ] 403、409、503 均有明确提示，不会无限自动重试。
- [ ] 前端代码中不存在公开 `/activate` 调用。

## 10. 已完成的云端 E2E 证据

2026-08-31 在公网 `58080`、真实 MySQL 和高德服务上完成任务 12 验证：

```text
生成后：v1 ACTIVE + v2 READY + v3 READY
Command 18：SENT → ACKNOWLEDGED → EXECUTING
ACK 后：v1 ACTIVE，v2 READY，planned-route = v1
EXECUTING 后：v1 INACTIVE，v2 ACTIVE，v3 READY
planned-route = v2 ACTIVE
```

事务切换时间：

```text
v1.deactivatedAt = 2026-08-31T09:50:37.927+08:00
v2.activatedAt   = 2026-08-31T09:50:37.927+08:00
```

这证明候选生成、路线版本化持久化、ACK 不切换、EXECUTING 事务切换及
`planned-route` 更新已在当前联调环境通过。
