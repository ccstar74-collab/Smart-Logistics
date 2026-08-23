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

## 数据库

- 数据库名称：`smart_logistics`
- 数据库初始化 SQL 约定路径：`../docs/sql/001_core_schema.sql`

如该 SQL 文件尚未提供，请联系团队成员确认最新数据库结构，不要各自在本地维护互不一致的表结构。

## 数据库配置

项目支持通过环境变量提供数据库连接信息。Windows PowerShell 示例：

```powershell
$env:DB_URL="jdbc:mysql://localhost:3306/smart_logistics"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="你的本地数据库密码"
```

不要把真实数据库密码提交到 GitHub，也不要在 README 或其他仓库文件中填写真实密码。

## 启动项目

从项目根目录进入后端目录：

```powershell
cd backend
```

运行测试：

```powershell
.\mvnw.cmd clean test
```

启动项目：

```powershell
.\mvnw.cmd spring-boot:run
```

默认服务端口：`8080`

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

Vehicle 模块已经包含：

- DTO
- Validation
- CRUD
- 分页
- 状态筛选
- 软停用
- 全局异常处理
- 自动化测试
  
Cargo模块已包含：

- Cargo Entity
- CargoStatus
- Cargo DTO
- Validation
- CargoMapper
- CargoService
- CargoController
- POST /api/v1/cargos
- GET /api/v1/cargos
- GET /api/v1/cargos/{id}
- 分页
- keyword 搜索
- status 筛选
- cargoNo 唯一性检查
- Not Found 处理
- 数据冲突处理
- 自动化测试

后续计划：
- CargoItem
- TransportTask
