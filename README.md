# Smart Logistics

Smart Logistics 是一个“智慧物流运输全过程追踪与异常调度系统”，由 6 人团队协作开发。项目围绕车辆管理、货物管理、运输任务、实时位置追踪、运输轨迹、ETA、异常告警、调度和物流智能问答展开。

目前项目仍在持续开发中，下文会明确区分已完成能力与开发中功能。

## 项目状态

### 已完成

- Spring Boot 后端基础工程
- MySQL 数据库连接
- MyBatis-Plus 数据访问
- Vehicle 车辆管理模块
- REST API 统一响应结构
- DTO
- Validation 参数校验
- 分页与状态筛选
- 全局异常处理
- Vehicle 模块自动化测试

### 开发中

- Cargo 货物管理
- CargoItem
- TransportTask 运输任务
- MQTT 实时数据接入
- GPS 模拟
- WebSocket
- 运输轨迹
- ETA
- 异常告警
- 调度
- 物流智能问答

## 技术栈

### 前端

- Vue 3
- Element Plus
- ECharts
- 地图服务

### 后端

- Java 21
- Spring Boot 4.1.1
- MyBatis-Plus
- Maven
- REST API

### 数据库

- MySQL 8.4

### 实时通信

- MQTT
- WebSocket

### 开发与协作

- Git
- GitHub
- VS Code
- DBeaver
- Apifox

## 项目结构

```text
Smart-Logistics/
├── backend/        # Spring Boot 后端
├── frontend/       # Vue 前端（规划目录）
├── simulator/      # GPS / MQTT 数据模拟（规划目录）
├── docs/           # 项目文档和数据库脚本
└── README.md
```

> `frontend/` 与 `simulator/` 为规划目录，当前尚未创建。

## 后端接口规范

REST API 统一前缀：`/api/v1/**`

Vehicle 车辆接口示例：

```http
GET    /api/v1/vehicles
POST   /api/v1/vehicles
GET    /api/v1/vehicles/{id}
PUT    /api/v1/vehicles/{id}
DELETE /api/v1/vehicles/{id}
```

- 普通业务接口使用 REST API。
- 实时位置、告警等信息计划由 WebSocket 推送。
- 设备或模拟器计划通过 MQTT 与后端通信。

## Git 分支规范

团队约定的主要分支：

- `main`：只保留稳定版本。
- `develop`：日常开发的集成分支。

功能分支命名示例：

- `feature/backend-business`
- `feature/realtime-service`
- `feature/frontend-business`
- `feature/map-monitor`
- `feature/iot-simulator`

协作流程：

1. 功能分支从 `develop` 创建。
2. 功能完成并验证后，通过 Pull Request 合并回 `develop`。
3. `main` 只接收达到稳定发布条件的版本。

以上为团队协作规范，不代表示例分支当前均已建立。

## 数据库

- 数据库名称：`smart_logistics`
- 数据库初始化或建表 SQL 统一存放在 `docs/sql/`。

团队成员不应各自在 DBeaver 中维护不同版本的表结构。数据库结构发生修改后，应同步更新对应 SQL 文件并提交到 Git。

## 注意事项

请勿向 GitHub 提交以下内容：

- MySQL 真实密码
- API Key
- JWT Secret
- `.env`
- `target/`
- `node_modules/`
- IDE 本地配置

敏感信息应通过环境变量或仅供本地使用的配置提供，不应写入仓库中的配置文件或文档。

## 后端启动

后端环境配置、数据库配置、测试和启动方式详见 [backend/README.md](backend/README.md)。
