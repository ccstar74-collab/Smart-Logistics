# Smart Logistics Backend

智慧物流系统 Spring Boot 后端。本文档用于帮助后端成员首次拉取代码后完成本地环境配置、测试与启动。

## 环境要求

- Java 21
- Spring Boot 4.1.1
- Maven Wrapper（项目已提供）
- MySQL 8.4

推荐开发工具：

- VS Code
- DBeaver
- Apifox

## 项目技术栈

- Java 21
- Spring Boot 4.1.1
- Spring Security
- JWT
- BCrypt
- MyBatis-Plus
- MySQL 8.4
- Maven

## 启动前准备

### 1. 安装并确认 Java 21

在 Windows PowerShell 中执行：

```powershell
java -version
```

确认当前使用的是 Java 21。项目不依赖某个固定的本地 JDK 安装路径。

### 2. 确认 MySQL 8.4 正常运行

项目当前开发环境使用 MySQL 8.4。可以在 PowerShell 中检查本机 3306 端口：

```powershell
Test-NetConnection localhost -Port 3306
```

应看到 `TcpTestSucceeded : True`。也可以使用 DBeaver 检查数据库连接。

### 3. 创建数据库

数据库名称为 `smart_logistics`。如果数据库尚不存在，可以执行：

```sql
CREATE DATABASE smart_logistics
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;
```

### 4. 执行数据库初始化 SQL

数据库结构的统一来源是：

```text
../docs/sql/001_core_schema.sql
```

新成员应在自己的本地 `smart_logistics` 数据库执行这份 SQL。不要各自在 DBeaver 中维护不同版本的表结构；结构变化必须统一更新 SQL 文件并提交到 Git。

当前 `001_core_schema.sql` 尚未包含 `alarm` 表。Alarm Java API 已集成，但在补充并评审 Alarm schema 前，不能进行真实数据库 API 验收，也不要根据 Entity 临时猜建表语句。

### 5. 配置本地数据库环境变量

数据库密码绝不能提交到 GitHub。Windows PowerShell 示例：

```powershell
$env:DB_URL="jdbc:mysql://localhost:3306/smart_logistics"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="你的本地MySQL密码"
```

这些 `$env` 环境变量只对当前 PowerShell 会话有效，关闭终端后需要重新设置。开发者也可以自行配置 Windows 用户环境变量，但不要创建或提交包含真实密码的配置文件。

### 6. 配置 JWT 环境变量

应用启动时必须提供至少 32 个 UTF-8 字节的 JWT 密钥。真实密钥不得提交到 GitHub：

```powershell
$env:JWT_SECRET="<至少32字节的本地随机密钥>"
$env:JWT_EXPIRES_SECONDS="28800"
```

`JWT_EXPIRES_SECONDS` 可省略，默认值为 28800 秒。以上示例仅表示变量格式，不是可用于部署的真实密钥。

### 7. Cargo 测试数据依赖

`Cargo.ownerId` 可为空；非空时对应数据库中的 `owner.id`，数据关系为：

```text
user
  ↓
owner
  ↓
cargo
```

入库调用 `POST /api/v1/cargos` 时可以省略 `ownerId`，Cargo 将作为未分配货主的 `WAITING` 库存保存。创建运输任务时，`POST /api/v1/transport-tasks` 必须携带合法 `ownerId`；后端会在同一事务中将未分配 Cargo 绑定给该 Owner 并创建任务。已有非空且不同的 `ownerId` 不会被覆盖。

手动测试兼容旧调用或创建运输任务前，应先确认本地数据库中存在真实、合法的 `owner.id`。不要假定或硬编码固定的 Owner ID，不同成员数据库中的实际 ID 可能不同。

### 8. 确认 8080 端口

启动 Spring Boot 前可以执行：

```powershell
Get-NetTCPConnection -LocalPort 8080 -State Listen
```

如果没有输出，通常说明 8080 端口可以使用。如果端口已被占用，应先根据命令输出中的 PID 确认进程：

```powershell
Get-Process -Id <PID>
```

不要直接结束未知进程。

### 9. 运行自动化测试

从项目根目录进入后端目录并执行：

```powershell
cd backend
.\mvnw.cmd clean test
```

确认输出为 `BUILD SUCCESS` 后再启动服务。

### 10. 启动 Spring Boot

```powershell
.\mvnw.cmd spring-boot:run
```

成功启动后的默认地址是 [http://localhost:8080](http://localhost:8080)，REST API 统一前缀为 `/api/v1`。

### 11. 推荐启动顺序

1. 启动 MySQL。
2. 确认 `smart_logistics` 数据库存在。
3. 执行 `docs/sql/001_core_schema.sql`。
4. 设置 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`。
5. 设置 `JWT_SECRET`，按需设置 `JWT_EXPIRES_SECONDS`。
6. 执行 `.\mvnw.cmd clean test`。
7. 执行 `.\mvnw.cmd spring-boot:run`。
8. 使用 Apifox 或前端调用 REST API。

## REST API

统一前缀：`/api/v1`

## MQTT 告警入库与去重

后端可订阅 `iot/carla/alert`，将 Schema 1.0 告警写入 MySQL `alarm` 表。
首次部署和已有数据库分别使用：

- 新数据库：执行 `docs/sql/001_core_schema.sql` 后执行 `docs/sql/002_alarm_schema.sql`。
- 已执行旧版 Alarm V1 的数据库：再执行一次 `docs/sql/003_mqtt_alert_idempotency.sql`。

告警幂等键由 `schema_version + vehicle_id + 标准化告警类型 + UTC事件时间`
生成 SHA-256，并由 `uk_alarm_event_key` 唯一索引保证并发和 MQTT QoS 1
重发时都不会重复入库。F2 后再次按 F1 会产生新的事件时间，因此可以正常新增下一条告警。

启用告警订阅所需环境变量：

```text
MQTT_ENABLED=true
MQTT_BROKER_URL=tcp://127.0.0.1:1883
MQTT_CLIENT_ID=smart_logistics_sub
MQTT_USERNAME=
MQTT_PASSWORD=
```

`MQTT_REALTIME_ENABLED` 默认是 `false`，此时只订阅告警，避免与服务器上的常驻
GPS 记录服务重复消费定位数据。需要后端同时处理 GPS、状态和指令 ACK 时再设为
`true`。

数据库密码、MQTT 密码和 InfluxDB Token 只能放在环境变量中，不能提交到 Git。

当前已完成 Vehicle 车辆接口：

```http
GET    /api/v1/vehicles
POST   /api/v1/vehicles
GET    /api/v1/vehicles/{id}
PUT    /api/v1/vehicles/{id}
DELETE /api/v1/vehicles/{id}
```

当前已完成 Cargo 货物接口：

```http
POST /api/v1/cargos
GET  /api/v1/cargos
GET  /api/v1/cargos/{id}
```

Cargo 列表支持 `page`、`pageSize`、`keyword` 和 `status` 查询参数。

当前已完成 CargoItem 货物明细接口：

```http
POST /api/v1/cargos/{cargoId}/items
GET  /api/v1/cargos/{cargoId}/items
GET  /api/v1/cargos/{cargoId}/items/{itemId}
```

CargoItem 是 Cargo 的从属资源，当前没有 PUT 或 DELETE CargoItem 接口。Cargo 与 CargoItem 的关系为 `1:N`：`Cargo.weight` 和 `Cargo.volume` 是整票运输总量，CargoItem 只描述票内货物明细，不会自动修改 Cargo 的重量、体积或状态。

当前已完成 TransportTask 运输任务接口：

```http
POST /api/v1/transport-tasks
GET  /api/v1/transport-tasks
GET  /api/v1/transport-tasks/{id}
PUT  /api/v1/transport-tasks/{id}/status
```

TransportTask 状态为 `WAITING`、`TRANSPORTING`、`COMPLETED`、`ABNORMAL`、`CANCELLED`。支持的状态转换为 `WAITING → TRANSPORTING`、`TRANSPORTING → COMPLETED`、`TRANSPORTING → ABNORMAL` 和 `WAITING → CANCELLED`，并同步维护 Cargo / Vehicle 相关状态。

当前已完成 Alarm 异常告警 Java 接口：

```http
GET /api/v1/alarms
GET /api/v1/alarms/{id}
PUT /api/v1/alarms/{id}/status
```

Alarm 状态为 `UNHANDLED`、`PROCESSING`、`RESOLVED`。当前仓库缺少 `alarm` 表 schema，因此尚未完成真实数据库 API 验收。

当前已完成 Phase 0 / Phase 1 认证与前端联调接口：

```http
POST /api/v1/auth/login
GET  /api/v1/users/me
GET  /api/v1/drivers/options
GET  /api/v1/owners/options
GET  /api/v1/vehicles/available
GET  /api/v1/cargos/available
```

用户角色固定为 `OWNER`、`DRIVER`、`WAREHOUSE_MANAGER`、`DISPATCHER`、`ADMIN`。当前 Phase 1 仅保护 `GET /api/v1/users/me`，其他既有业务 API 暂时 `permitAll`；Phase 2.5 再正式收紧业务权限。

## 当前后端模块

当前主要包结构：

```text
common/
config/
controller/
dto/
entity/
enums/
exception/
mapper/
service/
```

Vehicle V1 已完成：

- CRUD
- 分页
- keyword 搜索
- 状态筛选
- Validation
- DTO
- 软停用
- 异常处理
- 自动化测试

Cargo V1 已完成：

- 创建 Cargo
- 查询列表
- 查询详情
- 分页
- keyword 搜索
- status 筛选
- Validation
- DTO
- WAITING 默认状态
- 重复 cargoNo 处理
- Not Found
- 自动化测试

CargoItem V1 + REST API V1 已完成：

- 创建 CargoItem
- 查询某 Cargo 的全部 Item
- 查询某 Cargo 下单条 Item
- Cargo 存在校验
- CargoItem 资源归属校验
- Cargo 存在但无 Item 时返回空列表
- Validation
- DTO / Mapper / Service / Controller
- 自动化测试

TransportTask V1 + REST API V1 已完成：

- 创建运输任务并绑定 Cargo / Vehicle
- 分页、status、keyword 查询与任务详情
- 后端生成唯一 taskNo
- Cargo / Vehicle 状态与活动任务冲突检查
- TransportTask 状态转换及 Cargo / Vehicle 状态联动
- `actualStartTime` / `actualEndTime`
- Spring Transaction 事务边界
- 预留 `estimatedArrivalTime` 字段；当前不实现 ETA 算法
- 自动化测试及真实 Apifox + Spring Boot + MySQL API 验收通过

Alarm V1 Java API 已完成：

- 分页、keyword、status、level、alarmType 查询
- 告警详情查询
- `UNHANDLED → PROCESSING / RESOLVED`、`PROCESSING → RESOLVED` 状态处理
- DTO / Mapper / Service / Controller
- 自动化测试
- 数据库 schema 尚待补齐，真实数据库 API 验收未完成

Phase 0 / Phase 1 前端联调核心能力已完成：

- Spring Security、Stateless JWT 与 BCrypt
- 登录与当前用户身份查询
- Driver / Owner 选项查询
- Vehicle / Cargo 可用资源查询
- Driver / Owner 显示名称增强
- TransportTask 活动资源占用规则复用
- 185 项自动化测试全部通过，`clean test` 与 `clean compile` 均为 `BUILD SUCCESS`
- 本地数据库相关 Smoke Test 因连接环境不可用暂未完成

下一阶段：

- 补充并评审独立 Alarm schema
- 完成 Alarm 真实数据库 API 验收
- CargoStatusRecord
- Phase 0 / Phase 1 前后端联调及 Phase 2.5 权限收紧准备
