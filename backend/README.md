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

下一阶段：

- CargoItem
- TransportTask
- Cargo / Vehicle / Task 状态联动
- CargoStatusRecord
