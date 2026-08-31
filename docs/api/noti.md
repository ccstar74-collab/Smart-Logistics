# 消息中心前端使用说明（noti.md）

> 本文档面向前端开发，覆盖消息中心全部 **5 个接口**：4 个 REST + 1 个 WebSocket。
> 当前联调环境（队友部署）：`http://111.170.148.177:58080`，WS 与 HTTP 共用同一端口。
> 文档中的推送报文均为 2026-08-31 实测抓取的样例，可直接对照。

---

## 1. 总览

### 1.1 接口清单

| # | 方法 | 路径 | 用途 |
|---|---|---|---|
| 1 | GET | `/api/v1/notifications` | 通知分页列表（历史消息、断线补偿） |
| 2 | GET | `/api/v1/notifications/unread-count` | 未读数（铃铛红点角标） |
| 3 | PUT | `/api/v1/notifications/{id}/read` | 单条标记已读 |
| 4 | PUT | `/api/v1/notifications/read-all` | 全部标记已读 |
| 5 | WS | `/ws/notifications?token=<accessToken>` | 实时推送新通知 |

### 1.2 职责划分（重要）

- **REST 是权威数据源**：列表、已读状态、未读数一律以 REST 为准；
- **WebSocket 只做实时提醒**：新通知产生瞬间推一条，前端弹窗 + 角标 +1；
- **数据不会丢**：所有通知都已落库，断线期间漏掉的推送，重连后调 REST 列表即可找回。

### 1.3 鉴权

- REST：请求头 `Authorization: Bearer <accessToken>`；
- WS：token 放 URL 查询参数 `?token=<accessToken>`；
- token 过期：REST 返回 401，WS 握手直接失败——前端应重新登录/刷新 token 后再重连，**禁止无限重试**。

### 1.4 统一响应信封

所有 REST 接口返回：

```json
{ "code": 0, "message": "success", "data": { ... } }
```

业务数据永远在 `data` 里；`code != 0` 时为业务错误。

### 1.5 权限隔离（前端不用传、也不能传 userId）

后端从 JWT 识别当前用户，**所有接口只操作本人的通知**：
- 列表/未读数只返回自己的；
- 标记他人通知已读 → 返回 404（不泄露该通知是否存在）。

---

## 2. 通知类型与接收人（后端自动生成，前端了解即可）

| type | 触发场景 | 谁会收到 |
|---|---|---|
| `ALARM_CREATED` | 车辆产生新告警（偏航/异常停留/异常开箱） | 全体调度员；HIGH 级告警额外加管理员；告警关联了任务时再加该任务的货主 |
| `ALARM_RESOLVED` | 告警被消除 | 同上 |
| `DISPATCH_COMMAND_CREATED` | 调度员下发调度指令 | **仅目标司机**的账号 |

前端收到什么就显示什么，**不需要按角色二次过滤**——服务端已经算好了接收人。

---

## 3. REST 接口详解

### 3.1 通知分页列表

```
GET /api/v1/notifications
```

| 参数 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `page` | int | 否 | 1 | 页码，≥1 |
| `pageSize` | int | 否 | 20 | 每页条数，1~100 |
| `read` | boolean | 否 | 不过滤 | `false`=只看未读，`true`=只看已读 |
| `type` | string | 否 | 不过滤 | `ALARM_CREATED` / `ALARM_RESOLVED` / `DISPATCH_COMMAND_CREATED` |

返回（按创建时间倒序，最新在前）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "records": [
      {
        "id": 13,
        "type": "ALARM_CREATED",
        "title": "新告警通知",
        "content": "车辆异常停留（批量模拟）",
        "level": "WARNING",
        "read": false,
        "createdAt": "2026-08-31T11:59:44+08:00",
        "businessType": "ALARM",
        "businessId": "5",
        "taskId": null,
        "targetPath": "/alarms?alarmId=5"
      }
    ],
    "total": 1,
    "page": 1,
    "pageSize": 20
  }
}
```

主要场景：消息中心页面加载；**WS 断线重连后的数据补偿**。

### 3.2 获取未读数量

```
GET /api/v1/notifications/unread-count
```

无参数。返回：

```json
{ "code": 0, "message": "success", "data": { "count": 3 } }
```

⚠️ 取值路径是 **`res.data.count`**，不是顶层 `count`。

主要场景：页面初始化渲染角标；收到推送后角标 +1；标记已读后刷新。

### 3.3 单条标记已读

```
PUT /api/v1/notifications/{id}/read
```

- 路径参数 `id`：通知 id（来自列表或推送）；
- **幂等**：重复调用不报错；
- 成功返回**更新后的完整通知对象**（`read: true`）；
- id 不存在或不属于当前用户 → **404**（这是刻意的隐私设计）。

主要场景：用户点击某条通知时调用，然后拿 `targetPath` 跳转。

### 3.4 全部标记已读

```
PUT /api/v1/notifications/read-all
```

无参数。只更新当前用户自己的未读行，返回本次更新的条数：

```json
{ "code": 0, "message": "success", "data": 3 }
```

主要场景：消息中心的"全部已读"按钮。

---

## 4. WebSocket 实时推送

### 4.1 连接

```
ws://111.170.148.177:58080/ws/notifications?token=<accessToken>
```

- 握手成功 → 正常连接（服务端日志出现"通知会话建立"）；
- token 无效/过期 → 握手失败（浏览器表现为连接失败 + code 1006），刷新 token 后重试。

### 4.2 心跳（⚠️ 易错点）

**必须发送纯文本 `ping`，不是 JSON！** 服务端只认字符串 `"ping"`，收到即回 `"pong"`：

```javascript
// 正确：
ws.send("ping");

// 错误（服务端会直接忽略，等于没发心跳）：
ws.send(JSON.stringify({ type: "ping" }));
```

建议每 25 秒发一次，防止被反向代理按空闲超时掐断。

### 4.3 推送消息格式（实测样例）

```json
{
  "event": "NOTIFICATION_CREATED",
  "notification": {
    "id": 13,
    "type": "ALARM_CREATED",
    "title": "新告警通知",
    "content": "车辆异常停留（批量模拟）",
    "level": "WARNING",
    "read": false,
    "createdAt": "2026-08-31T11:59:44+08:00",
    "businessType": "ALARM",
    "businessId": "5",
    "taskId": null,
    "targetPath": "/alarms?alarmId=5"
  }
}
```

`notification` 的字段结构与列表接口 `records` 里的元素**完全一致**。

### 4.4 通知字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | long | 通知 id，标记已读用；也用它**去重**（同一 id 只渲染一次） |
| `type` | string | 通知类型，见第 2 节 |
| `title` | string | 弹窗标题 |
| `content` | string | 弹窗正文 |
| `level` | string | 展示级别：`INFO` / `SUCCESS` / `WARNING` / `ERROR`，只影响弹窗/角标样式 |
| `read` | boolean | 是否已读，推送的新通知恒为 `false` |
| `createdAt` | string | ISO-8601 时间（带时区偏移） |
| `businessType` | string | 关联业务对象类型，如 `ALARM` / `DISPATCH_COMMAND` |
| `businessId` | string | 关联业务对象 id（统一为字符串） |
| `taskId` | long \| null | 关联的运输任务，设备级告警为 `null` |
| `targetPath` | string | 点击通知后的跳转路由，前端**直接 `router.push(targetPath)`**，不需要自己维护类型到页面的映射 |

### 4.5 断线重连与补偿

- 重连建议指数退避（1s → 2s → 4s…），避免雪崩；
- **重连成功后立刻调两个 REST 接口**：
  1. `GET /api/v1/notifications?read=false` 补拉断线期间的未读通知；
  2. `GET /api/v1/notifications/unread-count` 校正角标。
- 推送是"尽力而为"，漏推不影响数据完整性（通知都在库里）。

---

## 5. 完整接入示例代码（浏览器可直接粘贴测试）

```javascript
const BASE = "http://111.170.148.177:58080";
let accessToken = "<登录后拿到的token>";
let ws = null;
let heartBeatTimer = null;
let reconnectDelay = 1000;

// ---------- WebSocket ----------
function connectNotifications() {
    ws = new WebSocket(`ws://111.170.148.177:58080/ws/notifications?token=${accessToken}`);

    ws.onopen = () => {
        console.log("通知WS连接成功");
        reconnectDelay = 1000; // 重置退避
        // 心跳：必须是纯文本 "ping"，每25秒一次
        heartBeatTimer = setInterval(() => {
            if (ws.readyState === WebSocket.OPEN) ws.send("ping");
        }, 25000);
        // 断线重连补偿：补拉未读列表 + 校正角标
        refreshAfterReconnect();
    };

    ws.onmessage = (event) => {
        if (event.data === "pong") return; // 心跳应答，忽略
        const msg = JSON.parse(event.data);
        if (msg.event === "NOTIFICATION_CREATED") {
            const n = msg.notification;
            // 1. 按 id 去重  2. 弹窗提示  3. 角标+1
            showPopup(n.title, n.content, n.level);
            bumpBadge(1);
        }
    };

    ws.onclose = () => {
        clearInterval(heartBeatTimer);
        console.log("通知WS断开，", reconnectDelay, "ms后重连");
        setTimeout(connectNotifications, reconnectDelay);
        reconnectDelay = Math.min(reconnectDelay * 2, 30000);
    };
}

// ---------- REST ----------
const headers = () => ({ "Authorization": `Bearer ${accessToken}` });

async function refreshAfterReconnect() {
    const list = await fetch(`${BASE}/api/v1/notifications?read=false`, { headers: headers() }).then(r => r.json());
    const count = await fetch(`${BASE}/api/v1/notifications/unread-count`, { headers: headers() }).then(r => r.json());
    // list.data.records -> 渲染消息列表；count.data.count -> 渲染角标
}

async function markRead(id) {
    const res = await fetch(`${BASE}/api/v1/notifications/${id}/read`, { method: "PUT", headers: headers() }).then(r => r.json());
    // 成功后跳转：router.push(res.data.targetPath)
}

async function markAllRead() {
    const res = await fetch(`${BASE}/api/v1/notifications/read-all`, { method: "PUT", headers: headers() }).then(r => r.json());
    // res.data 为本次更新条数，之后角标清零
}

connectNotifications();
```

---

## 6. 前后端协作流程

1. 用户登录成功 → 拿到 `accessToken`；
2. 页面初始化：调 `unread-count` 渲染角标 + 调列表接口渲染消息中心；
3. 建立 `/ws/notifications` 长连接，等待实时推送；
4. 收到推送 → 弹窗 + 角标 +1（按 `id` 去重）；
5. 用户点击某条通知 → `PUT /{id}/read` → `router.push(targetPath)` 跳转；
6. 用户点"全部已读" → `PUT /read-all` → 角标清零；
7. WS 断线 → 指数退避重连 → 重连成功立刻用 REST 补偿。

---

## 7. 常见问题（联调中真实踩过的坑）

| 现象 | 原因 | 解决 |
|---|---|---|
| WS 连接失败，code 1006 | token 用了**别的后端实例**签发的，或已过期 | 必须用当前部署实例的登录接口拿 token |
| 连接成功但收不到推送 | 心跳发的是 JSON `{type:"ping"}` 等无效内容只是次要问题；主要是触发时机没到（没人产生告警/指令） | 心跳发纯文本 `"ping"`；让调度员下发一条指令或触发一次告警验证 |
| 收不到指令通知 | 指令通知只推给**目标司机**，调度员本人收不到 | 用司机账号登录连接验证 |
| 未读数取不到 | 取了顶层 `count` | 取 `res.data.count` |
| 页面切换/后退后连接断开 | 浏览器 Back-Forward Cache 会挂起页面 | 属浏览器行为，真实前端页面常驻不受影响；测试时保持页面在前台 |
| 标记已读返回 404 | 通知不属于当前用户 | 正常现象，前端静默处理即可 |

---

## 8. 附：链路原理（前端了解即可）

```
车辆告警(MQTT) / 调度员下发指令
        │
        ▼
后端业务处理并写入数据库（alarm / dispatch_command 表）
        │ 事务提交后触发
        ▼
服务端计算接收人，逐人插入 notification 表
        │
        ▼
/ws/notifications 按用户 id 精确推送 NOTIFICATION_CREATED
        │
        ▼
前端弹窗 + 角标 +1；历史与已读状态用 4 个 REST 接口管理
```

要点：**推送一定发生在数据落库之后**，所以收到的每条推送都能立刻在列表接口里查到。
