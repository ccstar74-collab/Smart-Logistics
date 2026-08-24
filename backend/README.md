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

### 5. 配置本地数据库环境变量

数据库密码绝不能提交到 GitHub。Windows PowerShell 示例：

```powershell
$env:DB_URL="jdbc:mysql://localhost:3306/smart_logistics"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="你的本地MySQL密码"
```

这些 `$env` 环境变量只对当前 PowerShell 会话有效，关闭终端后需要重新设置。开发者也可以自行配置 Windows 用户环境变量，但不要创建或提交包含真实密码的配置文件。

### 6. Cargo 测试数据依赖

`Cargo.ownerId` 对应数据库中的 `owner.id`，数据关系为：

```text
user
  ↓
owner
  ↓
cargo
```

手动测试 Cargo 创建接口前，应先确认本地数据库中存在真实、合法的 `owner.id`。不要假定或硬编码固定的 Owner ID，不同成员数据库中的实际 ID 可能不同。

### 7. 确认 8080 端口

启动 Spring Boot 前可以执行：

```powershell
Get-NetTCPConnection -LocalPort 8080 -State Listen
```

如果没有输出，通常说明 8080 端口可以使用。如果端口已被占用，应先根据命令输出中的 PID 确认进程：

```powershell
Get-Process -Id <PID>
```

不要直接结束未知进程。

### 8. 运行自动化测试

从项目根目录进入后端目录并执行：

```powershell
cd backend
.\mvnw.cmd clean test
```

确认输出为 `BUILD SUCCESS` 后再启动服务。

### 9. 启动 Spring Boot

```powershell
.\mvnw.cmd spring-boot:run
```

成功启动后的默认地址是 [http://localhost:8080](http://localhost:8080)，REST API 统一前缀为 `/api/v1`。

### 10. 推荐启动顺序

1. 启动 MySQL。
2. 确认 `smart_logistics` 数据库存在。
3. 执行 `docs/sql/001_core_schema.sql`。
4. 设置 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`。
5. 执行 `.\mvnw.cmd clean test`。
6. 执行 `.\mvnw.cmd spring-boot:run`。
7. 使用 Apifox 或前端调用 REST API。

## REST API

统一前缀：`/api/v1`

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

下一阶段：

- 单独确认并执行 TransportTask / Alarm 安全集成
- 集成后执行全项目回归测试
- CargoStatusRecord
