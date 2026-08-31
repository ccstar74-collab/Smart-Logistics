# 消息中心功能交接与部署说明（迁移到队友环境）

> 适用场景：把 `feature/websocket-enhancement` 分支上的**消息中心**功能迁移到队友的代码库，
> 由她在自己的服务器上**宿主机直接启动**（不用 Docker），端口 **8080**。
> 功能代码入口：`695092c`（feat: add notification center ...）。

---

## 1. 接口要不要改？——不用改

| 问题 | 答案 |
|---|---|
| 4 个 REST 接口路径要改吗 | **不用**。`/api/v1/notifications*` 是相对路径，与端口、部署方式无关 |
| `/ws/notifications` 要改吗 | **不用**。端点注册代码原样迁移即可 |
| 端口 8080 怎么生效 | 代码默认 `server.port=18080`，启动时用参数覆盖即可（见第 3 节），**不要改代码里的端口** |
| 前端要改什么 | 只改**连接地址的 host 和端口**，路径、消息格式、心跳协议全部不变（见第 4 节） |

迁移后的访问地址（假设她的服务器 IP 为 `<HOST>`）：

```
REST: http://<HOST>:8080/api/v1/notifications
WS:   ws://<HOST>:8080/ws/notifications?token=<accessToken>
```

---

## 2. 需要迁移的文件清单

**新增文件（整体复制）：**

```
entity/Notification.java                       实体
enums/NotificationType.java, NotificationLevel.java
mapper/NotificationMapper.java
dto/response/NotificationResponse.java
dto/response/NotificationUnreadCountResponse.java
dto/realtime/DispatchCommandCreatedEvent.java
dto/realtime/NotificationWsEvent.java
dto/realtime/NotificationWsMessage.java
service/NotificationService.java               核心：查询/已读/生成通知
service/NotificationEventListener.java         AFTER_COMMIT 事件监听
handler/NotificationWebSocketHandler.java      /ws/notifications 推送
controller/NotificationController.java         4 个 REST 接口
```

（包路径：`backend/src/main/java/com/smart_logistics/backend/`）

**需要修改的已有文件（对比合入）：**

| 文件 | 改动 |
|---|---|
| `config/WebSocketConfig.java` | 注册 `/ws/notifications` handler（复用 JWT 握手拦截器） |
| `config/SecurityConfig.java` | GET 放行列表加 `/ws/notifications`（WS 握手走 query token） |
| `security/JwtWebSocketHandshakeInterceptor.java` | 握手属性写入 `USER_ID` |
| `security/WsSessionAttributes.java` | 新增 `USER_ID` 常量 |
| `service/DispatchCommandService.java` | `createCommand` 成功后发布 `DispatchCommandCreatedEvent`（一行） |

**配套资源：**

| 文件 | 用途 |
|---|---|
| `docs/sql/014_notification.sql` | **部署前必须执行**的建表脚本 |
| `docs/api/frontend-notification-integration.md` | 前端对接文档 |
| 3 个测试类（可选） | `NotificationServiceTest`、`NotificationEventListenerTest`、`NotificationWebSocketHandlerTest` |

> 另外本分支还把"手动消警、下发调度指令"收紧为**仅调度员**（管理员只读），
> 涉及 `AlarmService` / `AlarmController` / `DispatchCommandController`。
> 她如果也要这套权限规则，一并合入；不需要可以跳过，不影响通知功能。

---

## 3. 部署队友必读（Checklist）

### 3.1 启动前

1. **先执行建表 SQL**：在她要连的 MySQL 里执行 `docs/sql/014_notification.sql`。
   没建表不会崩，但 `/unread-count` 会 500、通知静默失败。
2. **同一个 MySQL 里必须有业务数据**：通知生成时要查 `user`（按角色找接收人）、
   `transport_task` / `cargo` / `owner`（找货主）、`driver`（找司机用户账号）、
   `alarm`、`dispatch_command`。如果她用全新的空库，功能正常但没有任何接收人。
3. **JDK 21**，构建：`mvnw clean package -DskipTests`（或跑全量测试，当前 549 个全绿）。

### 3.2 启动命令（宿主机直接启动）

```powershell
# Windows 示例；Linux 同理
$env:SERVER_PORT = '8080'
$env:DB_URL = 'jdbc:mysql://<她的MySQL地址>:3306/smart_logistics?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
$env:DB_USERNAME = '<用户名>'
$env:DB_PASSWORD = '<密码>'
$env:JWT_SECRET = '<必须与她的前端登录用的是同一个>'
$env:CORS_ALLOWED_ORIGINS = 'http://前端实际地址'
$env:MQTT_BROKER_URL = 'tcp://<broker地址>:1883'      # 注意变量名是 MQTT_BROKER_URL，不是 MQTT_BROKER！
$env:MQTT_USERNAME = '<...>'
$env:MQTT_PASSWORD = '<...>'
java -jar backend-0.0.1-SNAPSHOT.jar
```

要点：

- **端口**：`SERVER_PORT=8080`（Spring Boot 自动映射到 `server.port`），
  或命令行 `--server.port=8080`。WS 与 REST 共用这一个端口。
- **防火墙**放行 8080（TCP）。
- **`JWT_SECRET` 必须与前端登录用的后端一致**：`/ws/notifications` 握手和 4 个 REST
  都校验 JWT，密钥不一致全部 401。
- **`CORS_ALLOWED_ORIGINS` 必须包含前端真实地址**（逗号分隔多个）。
  不配只会放行 localhost:5173，会出现"WS 能连、REST 全 403/跨域报错"的迷惑现象。
- **MQTT 环境变量名是 `MQTT_BROKER_URL`**。写错会静默回退到 `127.0.0.1:1883`，
  表现为：服务正常启动，但告警永远不入库、通知永远不产生。

### 3.3 多实例红线（最重要）

- **两个后端实例不能同时消费同一个 MQTT broker 的告警主题。**
  告警通知走的是 **Spring 进程内事件**：谁的实例把告警插入库，通知就在谁的进程里
  生成和推送，**不会**跨实例传递。之前我们环境出的问题就是这个：
  旧实例抢先入库（它没有通知模块）→ 新实例收到重复消息被去重 → 永远没有通知。
- 如果两边必须同时连同一个 broker：
  1. 给两个实例设置**不同的 `MQTT_CLIENT_ID`**（默认都是 `smart_logistics_sub`，
     相同会互踢连接）；
  2. 两边都部署**包含通知模块的同一份代码**；
  3. 前端必须连"实际消费告警的那个实例"的 `/ws/notifications`。
- 推荐做法：**只有一个实例消费 MQTT**，另一个只做 REST/展示。

---

## 4. 前端接入要点（与现有对接文档一致）

| 项 | 说明 |
|---|---|
| 地址 | `ws://<HOST>:8080/ws/notifications?token=<accessToken>`（query 传 token，与 `/ws/alarms` 相同） |
| 心跳 | 每 25 秒发纯文本 `ping`，服务端回 `pong` |
| 推送消息 | `{"event":"NOTIFICATION_CREATED","notification":{...}}`，`notification` 与 REST 列表项同构 |
| 响应信封 | 所有 REST 返回 `{code, message, data}`；未读数取 `data.count` |
| 跳转 | 点击通知统一 `router.push(item.targetPath)`，前端不用维护 type→页面映射 |
| 已读行为 | 单条已读幂等；标记他人通知返回 404（不泄露）；`read-all` 只清本人未读 |
| 断线补偿 | 重连后调 `GET /notifications` + `/unread-count` 全量刷新（离线期间服务端不缓存推送，只保证落库） |
| 归属 | 所有接口只操作 JWT 当前用户本人的通知，前端**不传也不能传** userId |

前端**唯一要改的就是 host 和端口**；如果同时存在两个后端环境，务必让前端连
"告警实际入库的那个实例"，否则会收不到实时推送（数据在库里，REST 能查到）。

---

## 5. 链路说明

### 5.1 告警 → 通知（以模拟器偏航告警为例）

```
① MQTT broker 推送告警报文（主题 iot/carla/alert）
② MqttAlertMessageHandler.handle() 消费、校验
③ MqttAlertIngestionService.ingest()：事务内插入 alarm 表
   （幂等：event_key = SHA256(schema+车辆+类型+时间戳)，重复消息直接丢弃）
   插入成功后事务内发布 AlarmWsEvent(ALARM_CREATED, alarmId)
④ 事务提交后（@TransactionalEventListener AFTER_COMMIT）：
   ├─ AlarmWebSocketHandler → /ws/alarms 推告警事件（原有功能）
   └─ NotificationEventListener → NotificationService.generateAlarmNotifications()
        · 计算接收人：全部启用调度员 + HIGH 级加启用管理员 + 有归属任务时加货主
        · 每个接收人插一行 notification（唯一键 type+business_type+business_id+receiver 去重）
        · 每插成功一行发布 NotificationWsEvent
⑤ NotificationWebSocketHandler 按 receiverUserId 找到该用户的在线会话
⑥ 浏览器（已连 /ws/notifications）收到 NOTIFICATION_CREATED 推送
```

告警消除（自动/人工）走同一条链，事件类型为 `ALARM_RESOLVED`。
**注意：重复时间戳的告警消息会被去重，不产生通知；部署前的历史告警不追溯补通知。**

### 5.2 调度指令 → 通知

```
调度员调 POST /api/v1/dispatch-commands（仅调度员可下发）
→ DispatchCommandService.createCommand() 事务内入库
→ 发布 DispatchCommandCreatedEvent(commandId)
→ AFTER_COMMIT：NotificationService.generateDispatchCommandNotification()
→ 接收人 = 仅目标司机对应的用户账号（绝不广播给所有司机）
→ 插 notification 行 → NotificationWsEvent → 司机的浏览器收到推送
```

### 5.3 四个 REST 接口的角色

| 接口 | 作用 |
|---|---|
| `GET /api/v1/notifications` | 分页列表（权威数据源）：首屏加载 + 断线补偿 |
| `GET /api/v1/notifications/unread-count` | 未读数角标 |
| `PUT /api/v1/notifications/{id}/read` | 单条已读（幂等，非本人 404） |
| `PUT /api/v1/notifications/read-all` | 一键全部已读（只改本人未读行） |

协作顺序：页面加载拉列表+未读数 → 建 WS 长连接等实时推送 → 点击通知调单条已读并
`router.push(targetPath)` → "全部已读"按钮调 read-all → 断线重连后用两个 GET 补偿。

### 5.4 接收人规则总表

| 事件 | 接收人 |
|---|---|
| `ALARM_CREATED` | 全部启用调度员；HIGH 级另加启用管理员；告警有归属任务时加该任务货主 |
| `ALARM_RESOLVED` | 同上 |
| `DISPATCH_COMMAND_CREATED` | 仅目标司机对应的用户账号 |

接收人由服务端计算，前端收到即展示，**不需要也不允许**在客户端做二次过滤。
