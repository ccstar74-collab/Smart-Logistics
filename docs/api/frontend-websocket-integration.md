# 前端 WebSocket 实时推送对接说明

适用端点：`/ws/vehicle-locations`（车辆实时位置）、`/ws/alarms`（告警事件推送）
后端版本：feature/realtime-backend（合并 integration/eta-alarm-recovery 之后）

---

## 1. 总览

| 项目 | 说明                                                                                                                                                                                                             |
|---|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 协议 | 标准 WebSocket，纯文本帧（JSON 字符串）                                                                                                                                                                          |
| 端口 | 与 HTTP REST API 共用 `18080`，无独立端口                                                                                                                                                                        |
| 连接地址 | 当前服务器部署地址：`ws://111.170.148.177:58084/ws/vehicle-locations`、`ws://111.170.148.177:58084/ws/alarms`（REST 接口同端口）；新接入统一用这两个路径，旧路径 `/ws/logistics` 与 `/ws/vehicle-locations` 等价 |
| 端口说明 | 服务器上同时存在旧实例 `58083` 与新实例 `58084`，**必须连 58084**：MQTT 消费与实时推送仅在新实例上，连 58083 收不到任何推送                                                                                      |
| 鉴权方式 | JWT，通过 **query 参数 `token`** 传递（浏览器 WebSocket 无法自定义请求头）                                                                                                                                       |
| 通信方向 | 服务端单向推送 + 客户端心跳，前端**不要**发送业务指令                                                                                                                                                            |
| 心跳 | 客户端每 **25 秒**发送文本帧 `ping`，服务端回写文本帧 `pong`                                                                                                                                                     |
| 数据关系 | WS 只做**事件通知**；数据真实来源是 REST 接口，推送丢失时用 REST 刷新兜底                                                                                                                                        |

---

## 2. 鉴权与建连

### 2.1 先登录拿 token

```
POST /api/v1/auth/login
Content-Type: application/json

{ "username": "xxx", "password": "xxx" }
```

响应体（取 `data.accessToken`）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "accessToken": "eyJhbGciOi...",
    "tokenType": "Bearer",
    "expiresIn": 28800,
    "user": { "id": 17, "role": "DISPATCHER" }
  }
}
```

### 2.2 建立 WebSocket 连接

```
ws://111.170.148.177:58084/ws/vehicle-locations?token=<accessToken>
ws://111.170.148.177:58084/ws/alarms?token=<accessToken>
```

握手规则：

- `token` 缺失或解析失败 → 握手返回 **401**，连接建立失败
- 用户已被禁用/注销 → 同样 401
- 权限范围在**握手时一次性计算**并绑定到会话，连接存续期间不会变化；
  用户权限变更后需要**断开重连**才生效

### 2.3 端口映射说明（部署侧）

容器内只有一个监听端口 `18080`（`application.yml` 的 `server.port: 18080`），REST 与 WebSocket 共用：

```
浏览器 → 宿主机 111.170.148.177:58084 → (Docker -p 映射) → 容器内 18080 (Tomcat)
```

- 外部的 `58084` 只是启动参数 `-p 58084:18080` 的宿主机端口映射；旧实例 `58083` 同理（`-p 58083:18080`），内部端口相同。
- 改外部端口只需改启动命令的 `-p` 参数，后端代码无需改动。
- REST 与 WS 是同一地址同一端口，仅协议不同（`http://` 对 `ws://`）。

### 2.4 CORS（仅影响 REST，不影响 WS）

- 浏览器只对 **REST 请求**（`/api/v1/**`）做 CORS 检查；WebSocket 握手后端配置为放行任意 Origin，不受影响。
- 白名单来自启动参数 `CORS_ALLOWED_ORIGINS`（逗号分隔）；**不传时默认仅放行 `localhost:5173` 等本机开发地址**。
- 前端部署在真实域名/其他机器时，启动容器必须显式传入该环境变量，否则 REST 会被浏览器拦截。

---

## 3. 心跳协议（防 1006 断连，必须实现）

后端/反向代理对长时间无流量的连接会按空闲超时掐断（表现为 `code: 1006` 异常关闭）。前端必须实现心跳：

1. 连接建立后启动定时器，每 **25 秒** `ws.send("ping")`（纯文本，不是 JSON）
2. 服务端收到后立即回写文本帧 `"pong"`
3. 前端收到 `"pong"` 视为链路健康，重置失败计数
4. **连续 3 个周期（约 75 秒）未收到 `pong`** → 判定链路已死，主动 `close()` 并重连
5. 收到 `"pong"` 时不要走业务消息解析逻辑（它不是 JSON）

服务端对非 `ping` 的文本帧一律忽略，不会回复。

---

## 4. 端点一：`/ws/vehicle-locations` 车辆实时位置

### 4.1 推送时机

每当后端从 MQTT 收到一条车辆 GPS 报文并完成入库，立即向有权限的会话推送一条消息。频率取决于设备上报频率（当前模拟器约每秒一条/每车）。

### 4.2 消息格式

单条消息是一个 JSON 对象，**无外层信封**：

```json
{
  "vehicleId": "12",
  "simCode": "sim_001",
  "latitude": 29.50,
  "longitude": 106.58,
  "speed": 30.0,
  "direction": 90.0,
  "collectedAt": "2026-08-28T12:00:00+08:00"
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `vehicleId` | string | 数据库车辆主键（字符串形式）。**未注册设备**为 `null` |
| `simCode` | string | 设备外部编号（如 `sim_001`），**建议以此字段作为地图标记的稳定 key**。未注册设备为 `null` |
| `latitude` | number | 纬度，WGS84 |
| `longitude` | number | 经度，WGS84 |
| `speed` | number | 速度，km/h |
| `direction` | number | 航向角，0-360 度 |
| `collectedAt` | string | 采集时间，**ISO-8601 字符串**（不是时间戳数字），可直接 `new Date(...)` |

### 4.3 可见性规则

服务端按会话过滤，前端只会收到**有权查看的车辆**：

- `ADMIN`：全部已登记业务车辆
- 其他角色：仅与自己关联的车辆（名下车辆/所驾驶车辆等）
- 收到某辆车的点位就代表有权限，**前端无需再做过滤**

### 4.4 坐标系注意

推送坐标为 **WGS84** 原始坐标。若使用高德地图（GCJ-02）展示，前端需自行做坐标纠偏转换，否则会有偏移。

---

## 5. 端点二：`/ws/alarms` 告警事件推送

### 5.1 推送时机与事件类型

告警状态在数据库中变化、且**事务提交成功后**推送（保证推出去的告警一定已持久化）：

| `event` | 触发场景 |
|---|---|
| `ALARM_CREATED` | 新告警入库（设备上报或后端异常检测生成） |
| `ALARM_UPDATED` | 告警处理状态变化（如调度指令下发后 `UNHANDLED → PROCESSING`；或物理状态恢复） |
| `ALARM_RESOLVED` | 告警关闭（司机完成指令自动消警 / 调度员人工兜底消警） |

### 5.2 消息格式

每条消息是一个信封，`alarm` 字段与 `GET /api/v1/alarms` 列表项**完全同构**，前端不需要维护第二套告警模型：

```json
{
  "event": "ALARM_CREATED",
  "alarmId": 42,
  "alarm": {
    "id": 42,
    "vehicleId": 23,
    "plateNumber": "渝A33333",
    "taskId": 30,
    "taskNo": "T20260828001",
    "deviceCode": "sim_001",
    "type": "ABNORMAL_OPEN",
    "alarmType": "ABNORMAL_OPEN",
    "level": "HIGH",
    "description": "sim_001车辆货箱异常开启",
    "message": "sim_001车辆货箱异常开启",
    "status": "UNHANDLED",
    "conditionStatus": "ACTIVE",
    "source": "simulator",
    "occurredAt": "2026-08-28T20:23:10Z",
    "recoveredAt": null,
    "handledBy": null,
    "handledAt": null,
    "createdAt": "2026-08-28T20:23:11+08:00",
    "resolvedAt": null,
    "resolutionRemark": null,
    "longitude": 106.58,
    "latitude": 29.50,
    "coordSystem": "WGS84"
  }
}
```

### 5.3 字段枚举值

| 字段 | 取值 | 说明 |
|---|---|---|
| `type` / `alarmType` | `ROUTE_DEVIATION` / `ABNORMAL_STOP` / `ABNORMAL_OPEN` / `OTHER` | 偏航 / 异常停留 / 异常开箱 / 其他（两个字段值相同，任选其一） |
| `level` | `LOW` / `MEDIUM` / `HIGH` | 告警级别 |
| `status` | `UNHANDLED` / `PROCESSING` / `RESOLVED` | **业务状态**：未处理 / 处理中 / 已解决 |
| `conditionStatus` | `ACTIVE` / `RECOVERED` | **物理状态**：异常仍在发生 / 物理现象已恢复 |
| `source` | `simulator` / `backend` / `device` | 设备模拟器上报 / 后端异常检测生成 / 真实设备 |
| `description` / `message` | string | 描述文案（两个字段值相同，任选其一） |

> 告警是**双状态模型**：`status`（业务）和 `conditionStatus`（物理）独立变化。
> 例如物理已恢复但尚未走处理流程：`status=UNHANDLED, conditionStatus=RECOVERED`。
> 前端展示建议同时考虑两个维度。

### 5.4 告警生命周期（前端典型处理）

```
ALARM_CREATED   → 在告警列表/地图插入新告警，弹提醒
ALARM_UPDATED   → 用 alarmId 找到现有告警，整条替换为消息中的 alarm
ALARM_RESOLVED  → 从"未处理"视图中移除（或标记已解决）
```

- 以 `alarmId`（或 `alarm.id`）作为更新定位键
- 幂等：同一条告警的重复事件（如重连后）按"整条替换"处理即可，天然幂等
- 消息乱序容忍：如果先收到 `RESOLVED` 后收到 `UPDATED`，以 `status` 为准——`RESOLVED` 是终态，不要回退

### 5.5 可见性规则（按角色）

| 角色 | 收到的告警 |
|---|---|
| `ADMIN` / `DISPATCHER` | 全部告警（含 `taskId` 为空的设备级告警） |
| `OWNER`（货主） | 仅本人任务关联的告警；设备级告警（`taskId` 为空）**不推送** |
| `DRIVER` 及其他 | 当前不推送任何告警 |

前端无需做过滤，收到即可展示。

---

## 6. 断线与重连策略

### 6.1 关闭码含义

| code | 含义 | 前端动作 |
|---|---|---|
| `1006` | 异常关闭（多为空闲超时/网络中断，无 Close 帧） | 立即重连 |
| `1001` / `1002` / 其他 | 常规关闭或协议错误 | 按重连策略处理 |
| 握手 401 | token 无效或过期 | **不要无限重连**：先重新登录取新 token，再重连 |

### 6.2 重连建议

- 指数退避：1s → 2s → 4s → …，上限 30s
- **重连成功后**：
  - `/ws/alarms`：调用 `GET /api/v1/alarms?status=UNHANDLED` 刷新未处理告警（断线期间的事件无法补推）
  - `/ws/vehicle-locations`：调用车辆最新位置 REST 接口全量刷新一次，再回到增量推送模式
- token 有效期 8 小时（`expiresIn: 28800`），过期后所有连接都会握手失败，注意在登录态失效时统一处理

---

## 7. 前端参考实现

```javascript
class RealtimeChannel {
  constructor(path, token, onMessage, { host = '111.170.148.177:58084', heartbeatMs = 25000, missLimit = 3 } = {}) {
    this.url = `ws://${host}${path}?token=${encodeURIComponent(token)}`;
    this.onMessage = onMessage;
    this.heartbeatMs = heartbeatMs;
    this.missLimit = missLimit;
    this.missed = 0;
    this.retry = 0;
    this.connect();
  }

  connect() {
    this.ws = new WebSocket(this.url);
    this.ws.onopen = () => {
      this.retry = 0;
      this.missed = 0;
      this.startHeartbeat();
    };
    this.ws.onmessage = (e) => {
      if (e.data === 'pong') {          // 心跳应答，重置失败计数
        this.missed = 0;
        return;
      }
      this.onMessage(JSON.parse(e.data));
    };
    this.ws.onclose = (e) => this.scheduleReconnect(e);
    this.ws.onerror = () => this.ws.close();
  }

  startHeartbeat() {
    clearInterval(this.hb);
    this.hb = setInterval(() => {
      if (this.ws.readyState !== WebSocket.OPEN) return;
      this.missed += 1;
      if (this.missed > this.missLimit) {   // 连续多个周期无 pong，判定链路已死
        this.ws.close();                     // 触发 onclose → 重连
        return;
      }
      this.ws.send('ping');
    }, this.heartbeatMs);
  }

  scheduleReconnect(e) {
    clearInterval(this.hb);
    if (e.code === 1006 || e.wasClean === false || e.code >= 1001) {
      const delay = Math.min(1000 * 2 ** this.retry, 30000);
      this.retry += 1;
      setTimeout(() => this.connect(), delay);
      // 重连成功后业务侧应通过 REST 全量刷新兜底
    }
    // 401 握手失败场景：由外层登录逻辑刷新 token 后重建本对象
  }
}

// 使用
const alarmChannel = new RealtimeChannel('/ws/alarms', accessToken, (msg) => {
  // msg = { event, alarmId, alarm }
  switch (msg.event) {
    case 'ALARM_CREATED':  alarmStore.insert(msg.alarm); break;
    case 'ALARM_UPDATED':  alarmStore.replace(msg.alarm); break;
    case 'ALARM_RESOLVED': alarmStore.resolve(msg.alarm); break;
  }
});

const gpsChannel = new RealtimeChannel('/ws/vehicle-locations', accessToken, (point) => {
  // point = { vehicleId, simCode, latitude, longitude, speed, direction, collectedAt }
  mapLayer.upsertMarker(point.simCode ?? point.vehicleId, point);
});
```

---

## 8. 测试配合

### 8.1 模拟一条告警（服务器上执行）

```bash
mosquitto_pub -h <broker> -p 1883 -t "iot/carla/alert" -u admin -P public \
  -m '{"schema_version":"1.0","vehicle_id":"sim_001","alert_type":"异常开箱","description":"联调测试","timestamp":"'"$(date -u +%Y-%m-%dT%H:%M:%SZ)"'","source":"simulator"}'
```

注意事项：

- `timestamp` 每次必须**不同**（幂等去重键 = 车辆 + 类型 + 时间戳，相同组合只入库一次，第二次会被静默丢弃且不推送）
- `alert_type` 仅支持：`偏航` / `异常停留` / `异常开箱`
- 入库成功后约 1 秒内前端应收到 `ALARM_CREATED`

### 8.2 REST 兜底接口

| 用途 | 接口 |
|---|---|
| 告警分页列表 | `GET /api/v1/alarms?page=1&pageSize=20&status=UNHANDLED` |
| 告警详情 | `GET /api/v1/alarms/{id}` |
| 人工关闭告警 | `PATCH /api/v1/alarms/{id}/status`（仅调度员/管理员） |
| 车辆最新位置 | 见车辆/轨迹相关 REST 接口 |

---

## 9. 常见问题

**Q：连接成功但收不到任何告警推送？**
先确认角色（DRIVER 等角色本就不推）；再确认告警是否真的入库（看告警列表接口）；
若后端存在多个实例，告警只会从**处理该消息的实例**推送，前端必须连接到消费 MQTT 的那个实例。

**Q：静置几分钟后 1006 断开？**
心跳没生效。检查是否发送的是纯文本 `ping`（不要发 `{"type":"ping"}` 之类的 JSON），
以及定时器是否被页面后台节流（浏览器切后台会降频 `setInterval`，必要时用 `visibilitychange` 恢复）。

**Q：时间字段怎么解析？**
所有时间字段都是 ISO-8601 字符串（如 `2026-08-28T20:23:10Z` 或 `+08:00` 带偏移），
`new Date(str)` 直接可用；**不要**按时间戳数字处理。

**Q：地图上点位偏移？**
推送为 WGS84，高德底图需转 GCJ-02，百度底图需转 BD-09。
