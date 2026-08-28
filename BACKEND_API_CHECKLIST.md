# 智慧物流前端所需后端接口清单

> 根据当前 Vue 前端 21 个页面、5 种身份整理。用于初版答辩前的前后端分工，不表示要求后端在两天内一次性完成全部接口。

## 一、答辩前建议完成的最小闭环（P0）

目标演示链路：登录 → 创建车辆 → 创建货物 → 创建运输任务 → 开始运输 → 查看车辆位置 → 完成运输 → 查看三者状态联动。

### 1. 认证与当前用户

- [ ] `POST /api/v1/auth/login`：登录并返回 Token
- [ ] `GET /api/v1/users/me`：返回当前用户、角色及关联的 `ownerId/driverId`
- [ ] 所有业务接口根据当前用户角色过滤数据，不能仅靠前端隐藏菜单

### 2. 下拉选择所需基础数据（当前最急缺）

- [ ] `GET /api/v1/users?role=DRIVER&status=ENABLED`：司机选择列表
- [ ] `GET /api/v1/users?role=OWNER&status=ENABLED`：货主选择列表
- [ ] 或分别提供 `GET /api/v1/drivers/options`、`GET /api/v1/owners/options`
- [ ] 返回至少包含 `id/name/username/role/status`

### 3. 车辆（目前已部分联通）

- [x] `GET /api/v1/vehicles?page&pageSize&status`
- [x] `POST /api/v1/vehicles`
- [x] `GET /api/v1/vehicles/{id}`
- [x] `PUT /api/v1/vehicles/{id}`
- [x] `DELETE /api/v1/vehicles/{id}`
- [ ] 列表增加 `keyword`、`driverId` 筛选
- [ ] 响应增加 `driverName`，避免页面只显示司机 ID

### 4. 货物（目前已部分联通）

- [x] `GET /api/v1/cargos?page&pageSize`
- [x] `POST /api/v1/cargos`
- [x] `GET /api/v1/cargos/{id}`
- [ ] 列表增加 `ownerId/status/keyword` 筛选
- [ ] 响应增加 `ownerName`

### 5. 运输任务（答辩核心）

- [ ] `GET /api/v1/transport-tasks?page&pageSize&status&driverId&ownerId&vehicleId&cargoId`
- [ ] `POST /api/v1/transport-tasks`
- [ ] `GET /api/v1/transport-tasks/{id}`
- [ ] `PUT /api/v1/transport-tasks/{id}/status`
- [ ] `GET /api/v1/transport-tasks/current`：当前用户的执行中任务
- [ ] 状态联动：任务开始后 Task/Cargo/Vehicle 同为 `TRANSPORTING`
- [ ] 状态联动：任务完成后 Task/Cargo 为 `COMPLETED`，Vehicle 为 `IDLE`
- [ ] 详情返回任务编号、货物名称、车牌、司机、起终点、进度、ETA 和时间字段

### 6. 地图最小能力

- [ ] `GET /api/v1/vehicles/locations/latest`
- [ ] `GET /api/v1/vehicles/{id}/location/latest`：司机只查询本人车辆
- [ ] 位置返回 `vehicleId/plateNumber/longitude/latitude/speed/direction/recordedAt/online`
- [ ] 任务详情返回规划路线点，或增加 `GET /api/v1/transport-tasks/{id}/planned-route`

## 二、答辩前有余力再完成（P1）

### 7. 司机状态上报与历史

- [ ] `POST /api/v1/cargos/{cargoId}/status`
- [ ] `GET /api/v1/cargos/{cargoId}/status-records`
- [ ] 请求支持 `status/note/longitude/latitude`
- [ ] 状态枚举与 TransportTask 状态统一，避免前端 `LOADED/DELIVERED` 与后端枚举不一致

### 8. 历史轨迹

- [ ] `GET /api/v1/transport-tasks/{id}/track-points`
- [ ] 支持 `startTime/endTime` 或分页
- [ ] 轨迹点返回经纬度、速度、方向、时间

### 9. 告警

- [ ] `GET /api/v1/alarms?page&pageSize&status&level&type&vehicleId&taskId&ownerId`
- [ ] `GET /api/v1/alarms/{id}`
- [ ] `PUT /api/v1/alarms/{id}/status`
- [ ] 详情返回任务、车辆、位置、说明、等级、处理人和处理时间

### 10. 调度指令

- [ ] `POST /api/v1/dispatch-commands`
- [ ] `GET /api/v1/dispatch-commands?page&pageSize&driverId&vehicleId&taskId&status`
- [ ] `GET /api/v1/dispatch-commands/{id}`
- [ ] `PUT /api/v1/dispatch-commands/{id}/status`：司机确认已读/已执行

### 11. 角色首页汇总

- [ ] `GET /api/v1/dashboard/summary`
- [ ] 根据角色返回货主在途/完成/告警、司机当前任务、仓库待分配、调度车辆告警、管理员用户车辆统计
- [ ] `GET /api/v1/dashboard/recent-alarms`
- [ ] `GET /api/v1/dashboard/recent-tasks`

## 三、覆盖当前全部页面的完整接口（P2）

### 12. 用户和角色管理

- [ ] `GET /api/v1/users?page&pageSize&role&status&keyword`
- [ ] `POST /api/v1/users`
- [ ] `GET /api/v1/users/{id}`
- [ ] `PUT /api/v1/users/{id}`
- [ ] `PUT /api/v1/users/{id}/status`
- [ ] `GET /api/v1/roles`
- [ ] `GET /api/v1/roles/{id}/permissions`
- [ ] `PUT /api/v1/roles/{id}/permissions`

### 13. 个人中心

- [ ] `PUT /api/v1/users/me`
- [ ] `PUT /api/v1/users/me/password`
- [ ] `GET /api/v1/users/me/login-records`

### 14. 货物明细

- [ ] `GET /api/v1/cargos/{cargoId}/items`
- [ ] `POST /api/v1/cargos/{cargoId}/items`
- [ ] `GET /api/v1/cargos/{cargoId}/items/{itemId}`
- [ ] `PUT /api/v1/cargos/{cargoId}/items/{itemId}`
- [ ] `DELETE /api/v1/cargos/{cargoId}/items/{itemId}`

### 15. 货物、车辆、司机绑定记录

若“创建运输任务”已经代表绑定，可直接由任务接口替代独立绑定模块；否则需要：

- [ ] `GET /api/v1/bindings?page&pageSize&cargoId&vehicleId&driverId&active`
- [ ] `POST /api/v1/bindings`
- [ ] `DELETE /api/v1/bindings/{id}` 或 `PUT /api/v1/bindings/{id}/unbind`
- [ ] `GET /api/v1/cargos?status=WAITING&unbound=true`

### 16. 仓库出入库

- [ ] `GET /api/v1/warehouse-records?page&pageSize&type&cargoId&date`
- [ ] `POST /api/v1/warehouse-records`
- [ ] `GET /api/v1/warehouse-records/summary?date=YYYY-MM-DD`
- [ ] 可选基础数据：`GET /api/v1/warehouses`、`GET /api/v1/delivery-points`

### 17. 消息通知

- [ ] `GET /api/v1/notifications?page&pageSize&read`
- [ ] `GET /api/v1/notifications/unread-count`
- [ ] `PUT /api/v1/notifications/{id}/read`
- [ ] `PUT /api/v1/notifications/read-all`

### 18. 数据统计

- [ ] `GET /api/v1/statistics/overview?startDate&endDate`
- [ ] `GET /api/v1/statistics/vehicle-status`
- [ ] `GET /api/v1/statistics/task-status`
- [ ] `GET /api/v1/statistics/alarm-types`
- [ ] `GET /api/v1/statistics/task-performance`

### 19. 告警日志和操作审计

- [ ] 告警日志可复用 `GET /api/v1/alarms`，增加完整筛选及导出即可
- [ ] `GET /api/v1/audit-logs?page&pageSize&operatorId&module&startTime&endTime`

### 20. 系统设置

- [ ] `GET /api/v1/settings`
- [ ] `PUT /api/v1/settings`
- [ ] 配置包含通知开关、告警阈值、MQTT 状态和日志保留周期
- [ ] 密钥和数据库密码不得通过该接口返回前端

### 21. 智能问答

- [ ] `POST /api/v1/agent/chat`
- [ ] 请求建议包含 `message/sessionId`
- [ ] 响应建议包含 `answer/sessionId/references`

### 22. 实时推送（后续增强）

- [ ] `WebSocket /ws/vehicle-locations`
- [ ] `WebSocket /ws/alarms`
- [ ] `WebSocket /ws/notifications`
- [ ] 初版答辩来不及可以使用 5～10 秒轮询代替

## 四、统一接口约定

- [ ] 统一响应 `{ code, message, data }`
- [ ] 分页统一 `data.records/total/page/pageSize`
- [ ] JSON 字段统一 camelCase
- [ ] 时间统一 ISO 8601（带时区）
- [ ] 业务冲突返回明确消息，例如“司机不存在”“货物编号已存在”，不要统一返回 `data conflicts with an existing record`
- [ ] 401 表示未登录，403 表示无权限，404 表示资源不存在，409 表示业务冲突
- [ ] 列表接口统一支持分页和必要筛选
- [ ] 关联对象除 ID 外同时返回显示名称
- [ ] 后端 CORS 白名单允许实际前端地址；开发期可通过 Vite 代理

## 五、答辩取舍建议

### 必须演示真实数据

- 车辆列表/新增/详情
- 货物列表/新增/详情
- 创建运输任务
- 任务状态流转及 Vehicle/Cargo 联动
- 至少一辆车的最新位置

### 可以暂时保留 Mock，但须主动说明

- 智能问答固定回答
- 统计图和历史统计
- 通知中心
- 仓库出入库
- 角色权限配置
- 系统设置
- WebSocket 实时推送（可用轮询演示）

### 答辩时不建议现场操作

- 删除真实云端数据
- 猜测 `ownerId/driverId`
- 临时修改系统配置
- 无测试数据情况下演示告警处理
