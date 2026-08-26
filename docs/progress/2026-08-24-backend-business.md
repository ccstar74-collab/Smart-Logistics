# 2026-08-24 后端业务开发进度

## 今日完成

### TransportTask V1

- 完成 TransportTask Entity / Enum / DTO / Mapper / Service / Controller
- 完成运输任务创建并绑定 Cargo / Vehicle
- 完成后端唯一 `taskNo` 生成
- 完成 Cargo / Vehicle 存在性和创建状态检查
- 完成 Cargo / Vehicle 活动任务冲突检查
- 完成分页、status、keyword 查询和任务详情
- 完成 TransportTask 状态转换矩阵
- 完成 Cargo / Vehicle / TransportTask 状态联动；CargoItem 不参与联动
- 使用 `@Transactional` 定义创建和状态更新事务边界
- 完成 `actualStartTime` / `actualEndTime` 写入
- 返回 `estimatedArrivalTime` 预留字段，当前不实现 ETA 算法

完成接口：

```http
POST /api/v1/transport-tasks
GET  /api/v1/transport-tasks
GET  /api/v1/transport-tasks/{id}
PUT  /api/v1/transport-tasks/{id}/status
```

状态转换：

- `WAITING → TRANSPORTING`
- `TRANSPORTING → COMPLETED`
- `TRANSPORTING → ABNORMAL`
- `WAITING → CANCELLED`

### 测试与真实 API 验收

- 全项目 114 项自动化测试全部通过，Failures = 0、Errors = 0、Skipped = 0
- `clean test` 和 `clean compile` 均为 `BUILD SUCCESS`
- Spring Boot 启动验证成功
- TransportTask REST API 已完成真实 Apifox + Spring Boot + MySQL API 验收
- 当前自动化测试验证事务边界和失败传播；未将其表述为真实 MySQL physical rollback 验收

### 团队协作进度

- 已在 `feature/backend-business` 按 TransportTask、Alarm 的顺序完成受控集成
- TransportTask 来源为 `origin/feature/transport-task`，Alarm 来源为 `origin/feature/alarm-dispatch`
- Alarm 对应提交为 `d51d26e`，该远程分支最新提交 `aa5b57b` 同时包含 Dispatch command query foundation
- 两次 merge 均无冲突，TransportTask 的 Cargo / Vehicle 状态联动逻辑完整保留
- Alarm 仅实现自身查询和状态处理，未修改 Cargo、Vehicle 或 TransportTask，也未引入 MQTT / GPS / WebSocket 联动
- 仓库未提供 `alarm` 表建表 SQL；Alarm Java API 已集成，但真实数据库 API 验收被 schema 缺失阻塞

### 集成验证

- 全项目 143 项自动化测试通过，Failures = 0、Errors = 0、Skipped = 0
- `clean test` 与 `clean compile` 均为 `BUILD SUCCESS`
- Spring Boot 已在本地 8080 端口启动成功
- 当前终端未设置 `DB_PASSWORD`，本地 MySQL 拒绝空密码连接，因此 Vehicle / Cargo / TransportTask 数据接口未完成本轮运行验收
- 同一数据库连接阻塞导致无法确认本地是否存在 `alarm` 表；仓库侧仍明确缺少 Alarm schema，未执行 Alarm 详情或状态更新接口

## 当前进度

```text
Vehicle V1                 ✅
Cargo V1                   ✅
CargoItem V1 + REST API V1 ✅
TransportTask V1 + REST API V1 ✅
Alarm Java API V1          ✅ 已集成（数据库 schema 尚待补齐）
```

## 下一步计划

- 补充并评审独立 Alarm schema，不修改既有 `001_core_schema.sql`
- 在本地数据库具备 `alarm` 表后完成 Alarm REST API 真实验收
- 继续推进 TrackPoint、latest location、Dispatch、Auth 和 Agent 等后续模块

## Phase 0 / Phase 1 frontend P0 API increment

- Froze `UserStatus` as `ACTIVE / DISABLED` and `UserRole` as `OWNER / DRIVER / WAREHOUSE_MANAGER / DISPATCHER / ADMIN`.
- Kept the Alarm filter name as `alarmType` and Dispatch status as `PENDING / EXECUTED / CANCELLED / FAILED`.
- Added `POST /api/v1/auth/login` and authenticated `GET /api/v1/users/me` using stateless JWT and BCrypt. Existing business APIs remain temporarily `permitAll`.
- Runtime deployment must provide a strong `JWT_SECRET`; `JWT_EXPIRES_SECONDS` defaults to 28800.
- Added active Driver/Owner option APIs and `driverName` / `ownerName` response enrichment with batched relationship lookups.
- Added Vehicle/Cargo available APIs using the same `WAITING / TRANSPORTING` active-task definition as TransportTask creation.
- Did not add Cargo direct status, bindings, Dispatch V1 expansion, dashboard, RBAC, refresh/logout, or realtime backend features.
- Final verification: 185 tests, Failures 0, Errors 0, Skipped 0; `clean test` and `clean compile` both succeeded with the repository Maven Wrapper on Windows.

## Phase 2–4 auth, RBAC, data scope, and business API increment

- Added public self-registration for OWNER, DRIVER, and WAREHOUSE_MANAGER with BCrypt,
  explicit internal-role denial, username race handling, and transactional Owner/Driver
  identity creation. Registration never issues a token.
- Enforced authentication for all `/api/v1/**` routes except login, registration, and
  CORS preflight; standardized JSON 401 and 403 responses.
- Enabled method security and introduced one database-reloaded current-user identity for
  user profile, role authorization, and data-scope enforcement.
- Added exact OWNER and DRIVER scopes across Cargo, Vehicle, TransportTask, CargoItem, and
  Alarm relationships. User filters are composed with, and never replace, security scope.
- Applied the frozen explicit role matrix. ADMIN is not a super-role; Alarm status and the
  existing DispatchCommand web surface are denied to every frontend role.
- Added Vehicle driver binding, Cargo base update, CargoItem update/delete, DRIVER current
  task, WAITING Task base update, and the frozen Vehicle/Cargo/Task/Alarm filters.
- Preserved the TransportTask state machine and its Cargo/Vehicle status propagation.
  Driver web status reporting exposes only WAITING→TRANSPORTING and
  TRANSPORTING→COMPLETED; internal ABNORMAL/CANCELLED transitions remain available to
  internal service workflows.
- Did not add Cargo deletion, Cargo direct status, bindings, warehouse/dispatcher tables,
  realtime location, track, ETA, MQTT, WebSocket, dashboard, or DispatchCommand V1.
- Stage regressions: Phase 2 = 199 tests, Phase 2.5 = 203 tests, Phase 3 = 217 tests,
  Phase 4 = 235 tests; all completed with zero failures, errors, or skips.
