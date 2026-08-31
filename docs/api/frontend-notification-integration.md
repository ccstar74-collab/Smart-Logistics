# 消息中心接口使用说明

依据：`docs/notification.md`。P0 阶段只接入**告警创建/消除**与**调度指令创建**三类通知；
多仓库（WAREHOUSE）与任务类通知（TASK_ASSIGNED / TASK_COMPLETED）后续扩展。

本次对外新增的接口一共 **5 个**：4 个 REST + 1 个 WebSocket 端点，无其他新增。

---

## 1. 总览

| 项目 | 说明 |
|---|---|
| 服务地址 | `http://111.170.148.177:58084`（REST）/ `ws://111.170.148.177:58084`（WS） |
| 端口 | 与现有 REST / `/ws/alarms` 完全相同（容器内 18080，外部映射 58084） |
| 鉴权 | REST：请求头 `Authorization: Bearer <accessToken>`；WS：URL query `?token=<accessToken>` |
| 心跳（仅 WS） | 每 25 秒发送纯文本 `ping`，服务端回 `pong`，与 `/ws/alarms` 一致 |
| 数据归属 | 所有接口只操作 **JWT 当前用户本人**的通知；前端不传、也不能传 userId |
| 可用角色 | 五种角色均可访问自己的通知：货主 / 司机 / 调度员 / 管理员 / 仓库管理员 |
| 数据关系 | `/ws/notifications` 只做实时提醒；权威数据源是 `GET /api/v1/notifications`，断线后走 REST 补偿 |

所有 REST 响应都包在统一信封中：

```json
{ "code": 0, "message": "success", "data": { ... } }
```

下文示例中的"返回体"均指 `data` 内的内容。

---

## 2. 通知触发点与接收人（服务端已算好，前端无需过滤）

| 事件 | 通知类型 | 接收人 |
|---|---|---|
| 新告警入库（MQTT 上报 / 后端异常检测） | `ALARM_CREATED` | 全部调度员；HIGH 级另加管理员；告警有归属任务时加该任务货主 |
| 告警闭环消除（自动消警 / 调度员人工关闭） | `ALARM_RESOLVED` | 同上 |
| 调度指令创建 | `DISPATCH_COMMAND_CREATED` | **仅目标司机对应的用户账号** |

> 安全红线已在服务端实现：某司机的指令不会广播给所有 DRIVER，
> 某货主的告警不会广播给所有 OWNER。前端收到即展示，不需要再按角色过滤。

> 注意：通知只在事件发生的时刻生成，部署之前已存在的历史告警不会追溯补通知。

---

## 3. REST 接口

### 3.1 通知分页列表

```
GET /api/v1/notifications?page=1&pageSize=20&read=false&type=ALARM_CREATED
```

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `page` | int | 否 | 默认 1，最小 1 |
| `pageSize` | int | 否 | 默认 20，范围 1~100 |
| `read` | boolean | 否 | `true` 仅已读 / `false` 仅未读 / 不传返回全部 |
| `type` | enum | 否 | `ALARM_CREATED` / `ALARM_RESOLVED` / `DISPATCH_COMMAND_CREATED` |

返回体（按 `createdAt` 倒序）：

```json
{
  "records": [
    {
      "id": 1001,
      "type": "DISPATCH_COMMAND_CREATED",
      "title": "收到新的调度指令",
      "content": "车辆发生偏航，请查看新的调度指令",
      "level": "WARNING",
      "read": false,
      "createdAt": "2026-08-30T14:30:00+08:00",
      "businessType": "DISPATCH_COMMAND",
      "businessId": "82",
      "taskId": 30,
      "targetPath": "/dispatch?commandId=82"
    }
  ],
  "total": 1,
  "page": 1,
  "pageSize": 20
}
```

字段说明：

- `level`：`INFO` / `SUCCESS` / `WARNING` / `ERROR`，仅用于弹窗/角标样式
- `businessType` / `businessId`：关联业务对象（`ALARM` / `DISPATCH_COMMAND` + 其 id）
- `taskId`：关联运输任务（设备级告警可能为 `null`）
- `targetPath`：点击通知后的前端路由。点击统一执行 `router.push(item.targetPath)`，
  前端**无需**维护 type→页面映射

### 3.2 未读数量（侧栏角标）

```
GET /api/v1/notifications/unread-count
```

返回体：`{ "count": 5 }`（完整响应为 `{ "code": 0, "data": { "count": 5 } }`）

### 3.3 单条标记已读

```
PUT /api/v1/notifications/{notificationId}/read
```

- 幂等：重复标记直接返回该通知（不报错）
- 归属校验：通知不存在**或属于其他用户**，一律返回 **404**（不泄露他人通知的存在）
- 成功后写入 `read_at`，返回体为更新后的完整通知对象

### 3.4 全部标记已读

```
PUT /api/v1/notifications/read-all
```

- 只更新**当前用户**的未读行，绝不全表更新
- 返回体为本次更新的条数，如 `3`

### curl 示例

```bash
TOKEN="<accessToken>"
BASE="http://111.170.148.177:58084"

# 未读数
curl -H "Authorization: Bearer $TOKEN" "$BASE/api/v1/notifications/unread-count"

# 未读列表
curl -H "Authorization: Bearer $TOKEN" "$BASE/api/v1/notifications?read=false"

# 单条已读
curl -X PUT -H "Authorization: Bearer $TOKEN" "$BASE/api/v1/notifications/1001/read"

# 全部已读
curl -X PUT -H "Authorization: Bearer $TOKEN" "$BASE/api/v1/notifications/read-all"
```

---

## 4. WebSocket 端点 `/ws/notifications`

| 项目 | 说明 |
|---|---|
| 连接地址 | `ws://111.170.148.177:58084/ws/notifications?token=<accessToken>` |
| 握手鉴权 | 与 `/ws/alarms` 完全一致：query `token` + 用户启用状态检查，失败返回 401 |
| 推送对象 | **按用户精确推送**（握手时绑定 userId），同一用户多设备都会收到；不按角色广播 |
| 心跳 | 每 25 秒发 `ping`，服务端回 `pong` |

推送消息格式（服务端 → 前端）：

```json
{
  "event": "NOTIFICATION_CREATED",
  "notification": {
    "id": 1001,
    "type": "DISPATCH_COMMAND_CREATED",
    "title": "收到新的调度指令",
    "content": "车辆发生偏航，请查看新的调度指令",
    "level": "WARNING",
    "read": false,
    "createdAt": "2026-08-30T14:30:00+08:00",
    "businessType": "DISPATCH_COMMAND",
    "businessId": "82",
    "taskId": 30,
    "targetPath": "/dispatch?commandId=82"
  }
}
```

`notification` 结构与 `GET /api/v1/notifications` 列表项完全同构。

前端实时处理流程：

```
收到 NOTIFICATION_CREATED
  → 按 notification.id 去重
  → 消息列表顶部插入
  → unreadCount + 1
  → Element Plus 弹窗
  → 用户点击 → PUT /{id}/read → router.push(targetPath)
```

断线补偿：重连成功后调用 `GET /api/v1/notifications` + `/unread-count` 全量刷新，
避免断线期间通知丢失（离线期间服务端不缓存推送，只保证落库）。

---

## 5. 权限速查表

| 角色 | 能看到的通知 | 能做的操作 |
|---|---|---|
| 调度员 | 全部告警的通知（自己作为接收人产生的行） | 列表/已读/全部已读 |
| 管理员 | 仅 HIGH 级告警的通知（只读定位） | 同上 |
| 货主 | 自己货物关联任务的告警通知 | 同上 |
| 司机 | 发给本人的调度指令通知 | 同上 |
| 仓库管理员 | 暂无（多仓库阶段接入） | 同上 |

所有"已读"类操作只影响本人通知行；访问他人通知一律 404。

---

## 6. 错误码约定

| 场景 | 状态码 | 前端行为 |
|---|---|---|
| JWT 缺失/无效（REST） | 401 | 进入登录/刷新 Token 流程 |
| 通知不存在 / 属于其他用户 | 404 | 刷新列表（不泄露他人通知） |
| 分页参数非法（page<1、pageSize>100） | 400 | 使用默认参数重试或提示 |
| `type` 参数取值非法 | 400 | 去掉该参数重试 |
| WS token 缺失/无效 | 握手 401 | 停止无限重连，先刷新 Token |

---

## 7. 部署后验证步骤

1. **接口连通**：用任一已登录账号的 token 调
   `GET /api/v1/notifications/unread-count`，应返回 `{"code":0,"data":{"count":0}}`。
2. **告警通知**：浏览器连上 `/ws/notifications`（调度员 token），触发一次偏航告警
   → 调度员立刻收到 `ALARM_CREATED` 推送，未读数 +1。
3. **指令通知**：调度员对某任务下发调度指令 → 只有**目标司机的账号**收到推送。
4. **已读闭环**：`PUT /{id}/read` 后未读数减少；用自己的 token 标记他人通知 → 404。


# 消息中心5个接口分别作用
> 4个REST接口 + 1个WebSocket接口，P0只处理告警创建/消除、调度指令创建通知

## REST接口（4个，HTTP请求，做查询、标记状态，权威数据源）
### 1. `GET /api/v1/notifications` 通知分页列表
**作用**：拉取当前登录用户的通知历史列表，支持分页、筛选已读/未读、筛选通知类型。
- 主要场景：打开消息中心页面加载通知列表；WebSocket断线重连后做**数据补偿**，补拿断线期间漏掉的通知。
- 返回分页数据：通知id、标题、内容、跳转路由`targetPath`、业务关联ID、是否已读等完整通知字段。
- 支持过滤：只看未读、只看某一类通知；按创建时间倒序展示最新消息在前。

### 2. `GET /api/v1/notifications/unread-count` 获取未读数量
**作用**：查询当前用户未读通知总条数，用于页面侧边栏、消息铃铛右上角**红色角标数字**。
- 场景：页面初始化、WebSocket收到新通知后刷新角标、标记全部已读后更新角标。
- 轻量接口，不拿完整消息体。注意实际响应包在统一信封里：`{"code":0,"data":{"count":n}}`，前端取 `res.data.count`。

### 3. `PUT /api/v1/notifications/{notificationId}/read` 单条标记已读
**作用**：把某一条通知标记为已读状态。
- 场景：用户点击单条通知弹窗 / 在消息列表点击某一条消息时调用。
- 幂等接口：重复调用不会报错（已读的通知再次标记直接返回该通知）；如果这条通知不属于当前用户或者不存在，直接返回404（不泄露他人通知的存在）。调用成功会写入`read_at`时间，并返回更新后的完整通知对象。

### 4. `PUT /api/v1/notifications/read-all` 全部标记已读
**作用**：将当前登录用户**所有未读通知批量置为已读**。
- 场景：消息中心提供“全部已读”按钮。
- 只会更新当前JWT用户自己的数据，不会改动其他账号通知；返回本次更新了多少条记录。

## WebSocket接口（1个） `/ws/notifications`
**作用**：**实时推送新通知**，服务端主动把刚产生的通知推送到前端，不需要前端轮询http接口。
- 场景：有新告警、新调度指令产生瞬间，立刻给对应用户浏览器推送弹窗提醒。
- ⚠️ 注意：WebSocket只做实时提醒，**不做数据持久存储**。如果网络断开，断开期间的消息不会在WS缓存；重连之后必须调用上面REST接口拉取全量数据做补偿。不过通知本身都会落库，断线期间漏掉的推送可以在重连后通过列表/未读数接口找回，数据不会丢。
- 推送是**按用户精确投递**的：服务端按登录用户找会话，同一用户多设备登录时每台都会收到；不是按角色广播，前端收到即可展示，无需二次过滤。
- 附带心跳机制：前端每25s发`ping`，服务端回复`pong`维持长连接；鉴权放在url的token参数，握手失败返回401（此时应先刷新Token再重连，不要无限重试）。
- 断线重连建议用指数退避策略，避免瞬时风暴。
- 收到推送事件`NOTIFICATION_CREATED`后：前端按`notification.id`去重、新增一条消息、未读角标+1、弹出提示弹窗。

---

# 整体协作逻辑
1. 平时页面加载：调用`unread‑count`渲染角标 + `notifications`拉取消息列表；
2. 建立WS长连接：等待服务端推送**刚发生的新通知**，做实时弹窗；
3. 用户点击通知：调用单条标记已读，然后拿`targetPath`跳转业务页面；
4. 点全部已读：调用`read‑all`批量更新；
5. 网络断线重连：WS重新连接，立刻调用两个REST接口刷新列表+未读数，弥补断网丢失的实时推送。
6. 所有接口只操作**当前JWT用户本人**的通知，前端不传也不能传userId；标记他人通知一律404。

# 快速记忆
1. 分页列表：拿历史消息，断线补偿
2. 未读数：角标小红点
3. 单条已读：点一条消一条
4. 全部已读：一键清零未读
5. WS长连接：实时收到刚发生的通知弹窗


