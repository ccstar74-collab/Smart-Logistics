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
