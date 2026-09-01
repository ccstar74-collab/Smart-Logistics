车辆位置实时推送	ws://{业务后端}:58084/ws/vehicle-locations	已存在	，	只能作为车辆实时数据，不作为通用通知
告警 REST	/api/v1/alarms	已存在	，可作为通知事件来源
告警 WebSocket	ws://{业务后端}:58084/ws/alarms	已存在	，可触发告警页刷新，但不承担所有通知
调度指令 REST	/api/v1/dispatch-commands 等	已存在	，可作为司机通知事件来源
通知列表 REST	GET /api/v1/notifications，需要新增	消息中心权威数据源
通知未读/已读 REST	/unread-count、/{id}/read、/read-all	尚缺	，需要新增	侧栏角标和已读状态
通知 WebSocket	/ws/notifications	尚缺	推荐新增	，实时弹窗/角标刷新

业务数据与用户权限，Notification 持久化；通知 REST；/ws/notifications；JWT 鉴权；角色/用户范围过滤；把 Alarm、DispatchCommand、Task、WarehouseOperation映射为通知，不要把高频 GPS和 ETA 刷新存成通知。
数据流如下：
业务事件（Alarm / DispatchCommand / Task / WarehouseOperation）
↓
业务后端 NotificationService
↓
写 Notification 表（权威数据）
↓ 事务提交后
/ws/notifications → 在线用户立即弹窗
↓
GET /api/v1/notifications → 离线补偿 / 历史追溯 / 未读管理

业务后端必须补充的接口：
P0：查询我的通知
GET /api/v1/notifications?page=1&pageSize=20&read=false&type=
规则：前端不传 userId；后端从 JWT 获取 currentUserId，只返回当前用户自己的通知。
参数	类型	必填	说明
page	int	否	页码，建议默认 1
pageSize	int	否	每页条数，建议默认 20，限制最大值
read	boolean	否	true 已读；false 未读；不传表示全部
type	string	否	按通知类型筛选
响应示例
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
"total": 1
}
P0：查询未读数量
GET /api/v1/notifications/unread-count
响应示例
{
"count": 5
}
P0：单条标记已读
PUT /api/v1/notifications/{notificationId}/read
后端必须校验 notification.receiverUserId == currentUserId。
重复调用应幂等：已经已读时继续返回成功，不重复产生副作用。
建议写入 readAt。
P0：全部标记已读
PUT /api/v1/notifications/read-all
只修改 JWT 当前用户的未读通知，不能全表更新。
P1：统一通知 WebSocket
ws://{业务后端}:58084/ws/notifications?token=<accessToken>

WebSocket 规则
握手时解析 JWT，绑定 userId、role 和必要的业务范围。
服务端按 userId 精确推送，不按角色直接广播。
建议维护 userId → Set<WebSocketSession>，兼容同一用户多设备登录。
心跳可复用现有 Alarm WebSocket：约 25 秒 ping/pong；连续失败后重连。
Token 无效或过期时拒绝/关闭连接，前端停止无限重连并回到登录/刷新 Token 逻辑。

WebSocket 消息示例
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

Notification 数据模型建议
字段	建议类型	说明
id	BIGINT	通知主键，前端去重唯一键
receiver_user_id	BIGINT	最终接收用户；服务端权限过滤核心字段
type	VARCHAR	通知类型，如 ALARM_CREATED
title	VARCHAR	简短标题
content	TEXT/VARCHAR	消息正文
level	VARCHAR	INFO / SUCCESS / WARNING / ERROR
is_read	BOOLEAN	未读/已读
read_at	DATETIME	已读时间，可空
business_type	VARCHAR	ALARM / DISPATCH_COMMAND / TRANSPORT_TASK / WAREHOUSE 等
business_id	VARCHAR/BIGINT	对应业务对象 ID
task_id	BIGINT	可选；便于跨模块定位任务
target_path	VARCHAR	前端点击通知后的跳转地址
created_at	DATETIME	通知创建时间

前端无需维护大量 type → 页面映射逻辑。
后端可以直接给出 /dispatch?commandId=82、/alarms?alarmId=101、/tasks/30 等目标。
消息中心点击通知后统一执行 router.push(item.targetPath)。


业务模块	现有能力	新增通知触发点	建议接收人
Alarm	REST + /ws/alarms 已存在	ALARM_CREATED、ALARM_RESOLVED	相关货主、调度员；高等级可给 ADMIN
DispatchCommand	司机可接收/处理指令	DISPATCH_COMMAND_CREATED；可选 ACK/REJECT/COMPLETED	对应司机；关键状态可回推调度员
TransportTask	任务创建/开始/完成	TASK_ASSIGNED、TASK_STARTED、TASK_COMPLETED	对应货主、司机；必要时仓库管理员
Warehouse/Cargo	多仓库后续加入现在先不做	CARGO_INBOUND_COMPLETED、CARGO_OUTBOUND_COMPLETED 等	对应仓库管理员

通知模块不重新实现 Alarm、DispatchCommand、Task 的业务逻辑。
业务操作成功后产生通知；Notification 是“结果提醒”，不是业务真相本身。
告警页继续以 /api/v1/alarms 为权威数据源；调度页继续以 dispatch 接口为权威；消息中心只负责提醒和跳转。


五种身份的通知路由规则
角色	P0 先实现	后续可扩展	过滤原则
OWNER	本人任务的 ALARM_CREATED、ALARM_RESOLVED、TASK_COMPLETED	TASK_STARTED、ETA_DELAYED、货物送达	task.ownerId 必须属于本人
DRIVER	TASK_ASSIGNED、DISPATCH_COMMAND_CREATED	ROUTE_CHANGED、ETA_DELAYED、指令撤销/修改	task/command.driverId 必须对应本人
WAREHOUSE_MANAGER	先不做 TASK_CREATED	入库、出库、本仓车辆返回/异常	按其可管理仓库范围过滤
DISPATCHER	ALARM_CREATED、DISPATCH_REJECTED/COMPLETED	RECOVERED、REPLAN 成败、ROUTE_CHANGED、ETA_DELAYED	按调度范围；当前若为全局调度可按现规则
ADMIN	HIGH/ERROR 级系统/业务异常	服务异常、接口失败、车辆长时间离线、审计异常	避免接收所有普通业务通知

安全红线
不能把某司机的调度指令广播给所有 DRIVER。
不能把某货主的任务告警广播给所有 OWNER。
不能依赖前端收到消息以后再自行按角色过滤；服务端必须先算出具体 receiverUserId。

前端需要补充/确认的接口使用
侧边栏消息通知	GET /notifications/unread-count	显示“x 条待查看”；定时刷新或 WS 驱动
消息中心页面	GET /notifications	分页显示历史通知
点击单条通知	PUT /notifications/{id}/read	先标记已读，再跳 targetPath
全部已读	PUT /notifications/read-all	刷新列表和未读数
实时弹窗	/ws/notifications	NOTIFICATION_CREATED → 列表插入 + unread+1 + Element Plus Notification
重连补偿	GET /notifications + unread-count	避免断线期间通知丢失

前端实时处理流程
收到 NOTIFICATION_CREATED
↓
按 notification.id 去重
↓
消息列表顶部插入
↓
unreadCount + 1
↓
Element Plus 弹窗
↓
用户点击 → markRead(id) → router.push(targetPath)

/ws/notifications	用户级消息提醒	新增	直接推送已持久化的 Notification

第 1 步（P0）	Notification 表 + Entity/Mapper/Service
第 2 步（P0）	GET list + unread-count + mark-read + read-all
第 3 步（P0）	接入 DISPATCH_COMMAND_CREATED
第 4 步（P0）	接入 ALARM_CREATED / ALARM_RESOLVED
第 5 步（P1）	/ws/notifications + JWT + userId 会话映射
第 6 步（P1）	重连补偿 + 去重
第 7 步（P1）	接 TASK_ASSIGNED / TASK_COMPLETED
第 9 步（P2）	多仓库事件（先不做)
场景	建议状态码/错误	前端行为
JWT 缺失/无效	401 UNAUTHORIZED	退出通知 WS，进入登录/刷新 Token 流程
用户无权访问通知	403 FORBIDDEN	提示无权限
通知不存在	404 NOT_FOUND	刷新列表
通知属于其他用户	404 或 403	不泄露其他用户通知内容
分页参数非法	400 BAD_REQUEST	使用默认参数重试或提示
WebSocket Token 过期	关闭连接/鉴权失败	停止无限重连
内部实时事件重复	200/幂等返回	eventId 去重，不生成重复通知

先接入新调度指令、告警创建/恢复、任务分配/完成四类核心事件。
和多仓库的相关部分先不做，重点先完成告警和调度指令通知部分