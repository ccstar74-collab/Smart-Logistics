# ETA 与规划路线前端接入说明

## 1. 接入目标

前端需要完成两件事：

1. 请求运输任务的规划路线，并在高德地图上绘制 polyline。
2. 展示任务 ETA，并通过 WebSocket 接收 ETA 实时更新。

路线规划、当前位置投影、剩余道路距离和正式 ETA 均由实时后端负责。
前端不计算 ETA，也不要用起点到终点或当前位置到终点的直线距离代替道路距离。

## 2. 接入前需要的配置

```text
API_BASE_URL = 后端实际 HTTP 地址，例如 http://服务器地址:公网端口
WS_BASE_URL  = 后端实际 WebSocket 地址，例如 ws://服务器地址:公网端口
TOKEN        = 登录接口返回的 Bearer Token
```

公网端口由部署同学最终确认。不要在代码中写死当前临时 NAT 端口，建议放入前端环境变量。

示例：

```env
VITE_API_BASE_URL=http://服务器地址:公网端口
VITE_WS_BASE_URL=ws://服务器地址:公网端口
```

## 3. 获取任务规划路线

### 3.1 请求

```http
GET {API_BASE_URL}/api/v1/transport-tasks/{taskId}/planned-route
Authorization: Bearer {TOKEN}
```

示例：

```http
GET /api/v1/transport-tasks/12/planned-route
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

允许访问该任务的角色：

- `OWNER`
- `DRIVER`
- `WAREHOUSE_MANAGER`
- `DISPATCHER`
- `ADMIN`

后端会复用 ETA 计算使用的同一条规划路线，不会为前端单独生成另一条路线。

### 3.2 成功响应

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "taskId": 12,
    "provider": "AMAP",
    "coordinateSystem": "GCJ02",
    "distanceMeters": 5500,
    "referenceDurationSeconds": 720,
    "generatedAt": "2026-08-26T16:00:00+08:00",
    "points": [
      {"longitude": 106.5701, "latitude": 29.4901},
      {"longitude": 106.5802, "latitude": 29.5002},
      {"longitude": 106.6101, "latitude": 29.5201}
    ]
  }
}
```

字段说明：

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| `taskId` | Long | 运输任务 ID |
| `provider` | String | 路线提供方，当前固定为 `AMAP` |
| `coordinateSystem` | String | 路线坐标系，当前固定为 `GCJ02` |
| `distanceMeters` | Long | 完整规划路线总距离，单位米 |
| `referenceDurationSeconds` | Long | 高德返回的参考行驶时间，单位秒 |
| `generatedAt` | OffsetDateTime | 本次路线生成时间，带时区 |
| `points` | Array | 从起点到终点、按顺序排列的路线点 |
| `points[].longitude` | Double | GCJ-02 经度 |
| `points[].latitude` | Double | GCJ-02 纬度 |

`points` 已经是高德地图使用的 GCJ-02 坐标，前端不得再次做 WGS84 → GCJ-02 转换，
否则路线会产生二次偏移。

## 4. Vue/JavaScript 绘制示例

```javascript
import axios from 'axios'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL
})

async function loadPlannedRoute(taskId, map, token) {
  const response = await api.get(
    `/api/v1/transport-tasks/${taskId}/planned-route`,
    {
      headers: {
        Authorization: `Bearer ${token}`
      }
    }
  )

  const route = response.data.data
  const path = route.points.map(point => [
    point.longitude,
    point.latitude
  ])

  const polyline = new AMap.Polyline({
    path,
    strokeColor: '#1677ff',
    strokeWeight: 6,
    strokeOpacity: 0.9,
    lineJoin: 'round',
    lineCap: 'round',
    showDir: true
  })

  map.add(polyline)
  map.setFitView([polyline], false, [80, 80, 80, 80])

  return {
    route,
    polyline
  }
}
```

建议在进入任务详情页时请求一次。如果任务的起点或终点被重新编辑，需要重新请求该接口。

## 5. 获取当前 ETA

运输任务详情接口已经返回 ETA：

```http
GET {API_BASE_URL}/api/v1/transport-tasks/{taskId}
Authorization: Bearer {TOKEN}
```

重点字段：

```json
{
  "estimatedArrivalTime": "2026-08-26T16:35:20+08:00",
  "etaCalculatedAt": "2026-08-26T16:21:00+08:00"
}
```

- `estimatedArrivalTime`：预计到达时间。
- `etaCalculatedAt`：该 ETA 的最后计算时间，可用于显示数据新鲜度。
- 两个字段均允许为 `null`。任务未开始、坐标不完整或暂时没有有效 GPS 时可能为空。

## 6. WebSocket 实时 ETA

### 6.1 建立连接

```text
{WS_BASE_URL}/ws/logistics
```

如果页面通过 HTTPS 访问，应使用 `wss://`；HTTP 页面使用 `ws://`。

```javascript
const wsBaseUrl = import.meta.env.VITE_WS_BASE_URL
const socket = new WebSocket(`${wsBaseUrl}/ws/logistics`)

socket.onmessage = event => {
  const message = JSON.parse(event.data)

  if (message.type === 'ETA_UPDATED') {
    handleEtaUpdated(message)
    return
  }

  // 现有 GPS 消息仍走原来的处理逻辑
  handleGpsMessage(message)
}
```

### 6.2 ETA 更新消息

```json
{
  "type": "ETA_UPDATED",
  "taskId": 12,
  "vehicleId": "real_001",
  "estimatedArrivalTime": "2026-08-26T16:35:20+08:00",
  "etaCalculatedAt": "2026-08-26T16:21:00+08:00",
  "remainingDistanceMeters": 3862,
  "effectiveSpeedKmh": 31.4
}
```

字段说明：

| 字段 | 含义 |
| --- | --- |
| `type` | 固定为 `ETA_UPDATED`，前端据此区分 GPS 消息 |
| `taskId` | ETA 所属运输任务 |
| `vehicleId` | 车辆 `vehicle.sim_code`，与 MQTT/Influx 编号一致 |
| `estimatedArrivalTime` | 最新预计到达时间 |
| `etaCalculatedAt` | 本次 ETA 计算时间 |
| `remainingDistanceMeters` | 投影到任务路线后计算的剩余道路距离 |
| `effectiveSpeedKmh` | 当前速度与历史平均速度合成后的 ETA 计算速度 |

同一个 WebSocket 中同时存在 GPS 和 ETA 消息。前端必须先判断 `type`，不要把
`ETA_UPDATED` 当作 GPS 消息解析。

## 7. 推荐的页面数据流

```text
进入运输任务详情页
        ↓
GET /transport-tasks/{id}
读取任务信息和当前 ETA
        ↓
GET /transport-tasks/{id}/planned-route
绘制规划路线
        ↓
连接 /ws/logistics
        ↓
GPS 消息：更新车辆 Marker
ETA_UPDATED：更新 ETA、剩余距离和有效速度
```

离开页面时应移除 Polyline，并根据前端连接管理方案关闭或复用 WebSocket。

## 8. 错误处理

| HTTP 状态 | 常见原因 | 前端处理建议 |
| --- | --- | --- |
| `400` | taskId 非法 | 不发送请求，检查路由参数 |
| `401` | Token 缺失或过期 | 跳转登录或刷新登录状态 |
| `403` | 当前账号无任务访问权限 | 显示“无权查看该任务” |
| `404` | 运输任务不存在 | 显示“任务不存在或已删除” |
| `409` | 任务起终点坐标不完整 | 暂不绘制路线，提示先补齐坐标 |
| `500` | 高德接口或后端暂时不可用 | 保留旧路线并提供重试按钮 |

不要在请求失败时自行用起点和终点画直线伪装成规划路线。

## 9. 前后端联调检查清单

- [ ] 已取得可用登录 Token。
- [ ] 运输任务存在且当前账号有访问权限。
- [ ] 任务已经保存完整的 WGS84 起点和终点坐标。
- [ ] 路线接口返回 `coordinateSystem=GCJ02` 和至少两个 `points`。
- [ ] 高德地图可以正确绘制路线且没有二次偏移。
- [ ] 任务状态为 `TRANSPORTING`。
- [ ] `vehicle.sim_code` 与 MQTT/Influx 的 `vehicle_id` 一致。
- [ ] 最近两分钟内存在有效 GPS。
- [ ] 任务详情能够读取 `estimatedArrivalTime`。
- [ ] WebSocket 能收到 `ETA_UPDATED` 并只更新对应的 `taskId`。

## 10. 前端不需要实现的内容

- 不调用高德路线接口自行计算正式 ETA。
- 不计算当前位置到终点的直线距离。
- 不把模拟器生成的 ETA 当作业务 ETA。
- 不对路线接口返回的 GCJ-02 点再次转换坐标。
- 不把完整 polyline 放进 WebSocket 消息中反复传输。
