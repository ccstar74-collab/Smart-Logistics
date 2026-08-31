# 路线天气与路况前端联调说明

本文档提供给前端开发人员，用于在任务详情页和多目标候选路线页面接入目的地实时天气与路线生成时的路况快照。

多目标候选路线的生成、选择和 `ROUTE_CHANGE` 状态机，请同时阅读：

- [多目标候选路线前端联调说明](./frontend-multi-objective-route-integration.md)

## 1. 联调环境

| 项目 | 地址或说明 |
|---|---|
| 公网后端地址 | `http://111.170.148.177:58080` |
| REST Base URL | `http://111.170.148.177:58080/api/v1` |
| REST 鉴权 | `Authorization: Bearer <accessToken>` |
| 数据格式 | `application/json; charset=UTF-8` |
| 路线坐标顺序 | `[longitude, latitude]`，即 `[经度, 纬度]` |

联调直接访问公网 `58080`，不需要 SSH。不要使用服务器内部的 Java 监听端口。

当前地址使用明文 HTTP，仅适合课程开发与联调。正式环境应切换为 HTTPS，前端源码、日志和 Git 中禁止出现账号密码、JWT 或高德 Key。

所有接口都使用统一响应信封：

```ts
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}
```

成功响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

前端必须同时判断 HTTP 状态码和响应体 `code`，不能只判断请求是否完成。

## 2. 前端推荐调用流程

进入任务详情页后，可以并行加载：

```text
GET /transport-tasks/{taskId}
GET /transport-tasks/{taskId}/routes
GET /transport-tasks/{taskId}/planned-route
GET /transport-tasks/{taskId}/route-data/weather
```

其中：

- 天气请求失败时，只降级天气卡片，不应阻塞任务和路线页面。
- 路况不是单独查询的接口，而是路线对象中的 `traffic` 字段。
- `planned-route` 当前不包含 `traffic`。如需显示当前活动路线的路况，应从 `/routes` 返回结果中按 `routeId` 或 `routeStatus === 'ACTIVE'` 查找。
- 必须按 `routeStatus` 识别活动路线，不能把最大 `routeVersion` 当成活动路线。

## 3. TypeScript 数据结构

### 3.1 天气

```ts
export interface WeatherSnapshot {
  source: 'AMAP_WEATHER_V3' | string
  adcode: string
  province: string
  city: string
  weather: string
  temperature: number
  humidity: number
  windDirection: string
  windPower: string
  reportTime: string
}
```

### 3.2 路况

```ts
export interface TrafficSnapshot {
  source: 'AMAP_DRIVING_V3' | string
  strategy: string
  restriction: boolean
  trafficLights: number
  unknownDistanceMeters: number
  smoothDistanceMeters: number
  slowDistanceMeters: number
  congestedDistanceMeters: number
  severeCongestedDistanceMeters: number
}
```

### 3.3 路线

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
  traffic: TrafficSnapshot | null
  generatedAt: string
  activatedAt: string | null
  deactivatedAt: string | null
  points: [number, number][]
}
```

`traffic` 必须声明为可空：

- 历史路线是在路况功能上线前生成的，可能为 `null`。
- 新生成的高德路线通常包含 `AMAP_DRIVING_V3` 路况快照。
- `null` 表示“没有快照”，不能显示成“道路全部畅通”。

## 4. 通用请求封装示例

```ts
const API_BASE = 'http://111.170.148.177:58080/api/v1'

export class ApiError extends Error {
  constructor(
    public readonly httpStatus: number,
    public readonly code: number,
    message: string,
  ) {
    super(message)
  }
}

export async function apiRequest<T>(
  path: string,
  accessToken: string,
  init: RequestInit = {},
): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: {
      Accept: 'application/json',
      Authorization: `Bearer ${accessToken}`,
      ...init.headers,
    },
  })

  const body = (await response.json()) as ApiResponse<T>
  if (!response.ok || body.code !== 200) {
    throw new ApiError(response.status, body.code, body.message)
  }
  return body.data
}
```

不要在代码中写死测试账号和密码。`accessToken` 应来自现有登录状态管理。

## 5. 目的地实时天气接口

### 5.1 请求

```http
GET /api/v1/transport-tasks/{taskId}/route-data/weather
Authorization: Bearer <accessToken>
```

支持角色：

- `OWNER`
- `DRIVER`
- `WAREHOUSE_MANAGER`
- `DISPATCHER`
- `ADMIN`

除了角色校验，用户仍须拥有该任务的数据访问权限。

后端使用任务的终点经纬度查询天气，不是车辆当前位置，也不是起点仓库位置。

### 5.2 成功响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "source": "AMAP_WEATHER_V3",
    "adcode": "430111",
    "province": "湖南",
    "city": "雨花区",
    "weather": "阴",
    "temperature": 23,
    "humidity": 90,
    "windDirection": "西南",
    "windPower": "5",
    "reportTime": "2026-08-31T10:34:12+08:00"
  }
}
```

### 5.3 前端调用

```ts
export function getDestinationWeather(taskId: number, token: string) {
  return apiRequest<WeatherSnapshot>(
    `/transport-tasks/${taskId}/route-data/weather`,
    token,
  )
}
```

### 5.4 页面展示建议

天气卡片建议显示：

- 地区：`province + city`
- 天气现象：`weather`
- 温度：`${temperature} ℃`
- 湿度：`${humidity}%`
- 风向和风力：`${windDirection}风 ${windPower}级`
- 数据时间：格式化 `reportTime`

该接口每次调用都可能访问第三方实时服务。推荐在进入任务详情页时加载一次，并提供手动刷新按钮；不要每秒轮询。

浏览器 `fetch` 会按 UTF-8 解析 JSON。Windows PowerShell 5.1 联调时可能显示中文乱码，这不代表浏览器页面也会乱码。

## 6. 路线及路况快照

### 6.1 查询任务全部路线

```http
GET /api/v1/transport-tasks/{taskId}/routes
Authorization: Bearer <accessToken>
```

```ts
export function getTaskRoutes(taskId: number, token: string) {
  return apiRequest<TransportTaskRoute[]>(
    `/transport-tasks/${taskId}/routes`,
    token,
  )
}
```

响应中的每条路线都可能包含 `traffic`：

```json
{
  "routeId": "route_example_v2",
  "taskId": 1,
  "routeVersion": 2,
  "routeStatus": "READY",
  "provider": "AMAP",
  "coordinateSystem": "GCJ02",
  "distanceMeters": 837561,
  "referenceDurationSeconds": 34030,
  "traffic": {
    "source": "AMAP_DRIVING_V3",
    "strategy": "躲避拥堵",
    "restriction": false,
    "trafficLights": 12,
    "unknownDistanceMeters": 170,
    "smoothDistanceMeters": 836882,
    "slowDistanceMeters": 175,
    "congestedDistanceMeters": 288,
    "severeCongestedDistanceMeters": 46
  },
  "generatedAt": "2026-08-31T10:40:00+08:00",
  "activatedAt": null,
  "deactivatedAt": null,
  "points": [
    [106.551787, 29.56268],
    [106.552001, 29.56291]
  ]
}
```

路况是路线生成时保存的不可变快照，不会因为再次调用 `GET /routes` 而自动刷新。可使用路线的 `generatedAt` 作为快照生成时间。

### 6.2 生成多目标候选路线

```http
POST /api/v1/transport-tasks/{taskId}/routes/candidates
Authorization: Bearer <dispatcherToken>
```

该接口仅允许 `DISPATCHER` 调用，无请求体。任务必须处于 `WAITING` 或 `TRANSPORTING`，并具有完整的起终点坐标。

```ts
export function createRouteCandidates(taskId: number, token: string) {
  return apiRequest<TransportTaskRoute[]>(
    `/transport-tasks/${taskId}/routes/candidates`,
    token,
    { method: 'POST' },
  )
}
```

按钮处理要求：

1. 点击后立即进入 loading 并禁用按钮，防止重复生成路线版本。
2. 成功后重新调用 `GET /routes`，以服务端完整列表覆盖本地状态。
3. 请求超时或网络结果不明确时，也先调用 `GET /routes` 检查是否已经写入，不能立即重试。
4. 候选路线生成失败不应删除或替换原来的 `ACTIVE` 路线。

## 7. 路况展示和计算建议

```ts
export function summarizeTraffic(traffic: TrafficSnapshot | null) {
  if (!traffic) return null

  const observedMeters =
    traffic.unknownDistanceMeters +
    traffic.smoothDistanceMeters +
    traffic.slowDistanceMeters +
    traffic.congestedDistanceMeters +
    traffic.severeCongestedDistanceMeters

  const affectedMeters =
    traffic.slowDistanceMeters +
    traffic.congestedDistanceMeters +
    traffic.severeCongestedDistanceMeters

  return {
    observedMeters,
    affectedMeters,
    affectedRatio: observedMeters === 0 ? 0 : affectedMeters / observedMeters,
  }
}
```

推荐颜色：

| 路况 | 字段 | 推荐颜色 |
|---|---|---|
| 未知 | `unknownDistanceMeters` | 灰色 |
| 畅通 | `smoothDistanceMeters` | 绿色 |
| 缓行 | `slowDistanceMeters` | 黄色 |
| 拥堵 | `congestedDistanceMeters` | 橙色 |
| 严重拥堵 | `severeCongestedDistanceMeters` | 红色 |

候选路线卡片至少展示：

- 总里程和参考时长
- 缓行、拥堵和严重拥堵里程
- 红绿灯数量
- 是否存在限行信息
- 路况快照生成时间

不要假设各路况分段之和一定与 `distanceMeters` 完全相等。第三方分段覆盖和取整可能产生少量差值。

## 8. 与路线切换流程的关系

天气和路况不会改变普通路线切换状态机：

```text
ACTIVE + READY 候选
  → 调度员选择 READY
  → 创建 ROUTE_CHANGE（SENT）
  → 司机 ACKNOWLEDGED（不切换）
  → 司机 EXECUTING
  → 原 ACTIVE 变 INACTIVE
  → 目标 READY 变 ACTIVE
```

前端禁止调用或恢复旧的直接激活入口：

```text
PUT /transport-tasks/{taskId}/routes/{routeId}/activate
```

完整指令结构和司机端状态更新方式以多目标候选路线前端联调说明为准。

## 9. 错误处理

| HTTP | 业务 code | 含义 | 前端建议 |
|---:|---:|---|---|
| 400 | `40001` | 参数格式错误 | 检查 `taskId` 和请求参数 |
| 401 | `40101` | 未登录或 Token 失效 | 清理登录态并重新登录 |
| 403 | `40301` | 角色或任务数据权限不足 | 隐藏无权限按钮并提示用户 |
| 404 | `40401` | 任务或接口不存在 | 刷新任务列表；同时确认部署版本 |
| 409 | `40902` | 任务状态或起终点坐标冲突 | 展示后端 `message`，不要自动重试 |
| 503 | `50301` | 高德天气/路线不可用，或有效候选不足 | 保留现有数据，允许稍后手动重试 |
| 500 | `50001` | 后端内部错误 | 提示稍后重试并保留错误信息供排查 |

天气请求失败只影响天气卡片。路线候选请求失败时必须保留当前 `ACTIVE` 和已有 `READY` 路线。

## 10. CORS 联调说明

后端默认允许以下本地前端来源：

```text
http://localhost:5173
http://127.0.0.1:5173
http://172.28.48.1:5173
```

如果前端使用其他端口、局域网地址或正式域名，浏览器可能因为 CORS 拒绝请求。此时应由部署人员把完整前端 Origin 加入服务器环境变量 `CORS_ALLOWED_ORIGINS`，而不是在前端关闭浏览器安全策略。

## 11. 联调验收清单

- [ ] 使用有效 JWT 请求天气接口，返回 `code = 200`。
- [ ] 天气卡片中文显示正常，并展示 `reportTime`。
- [ ] 天气接口失败时，任务详情和路线地图仍可使用。
- [ ] 历史路线 `traffic = null` 时显示“暂无路况快照”，不显示“全部畅通”。
- [ ] 新候选路线为 `READY`，且 `traffic.source = AMAP_DRIVING_V3`。
- [ ] 候选路线可以比较总里程、时长和拥堵程度。
- [ ] 候选生成按钮在请求期间禁用，避免重复产生新版本。
- [ ] `ACKNOWLEDGED` 后路线不切换。
- [ ] `EXECUTING` 后目标路线成为唯一 `ACTIVE`。
- [ ] 前端没有调用旧的直接激活接口。
- [ ] 浏览器 Network 中没有账号密码、高德 Key 等敏感信息。

## 12. 当前后端实测结果

当前公网联调环境已验证：

- 天气接口返回 `200`，数据来源为 `AMAP_WEATHER_V3`。
- 历史 v1 路线保持 `ACTIVE`，其 `traffic` 为 `null`。
- 新生成的 v2、v3 候选路线均为 `READY`。
- v2、v3 均返回 `AMAP_DRIVING_V3` 路况，包括畅通、缓行、拥堵、严重拥堵和红绿灯数据。
- v2、v3 的路况 JSON 已持久化到 MySQL，且 `JSON_VALID(traffic_snapshot) = 1`。
