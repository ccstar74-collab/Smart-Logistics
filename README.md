# 智慧物流系统前端

智慧物流系统的 Vue 前端项目，面向货主、司机、仓库管理员、调度员和系统管理员五类用户，提供车辆、货物、运输任务、实时定位、告警调度、仓储出入库和智能路线推荐等功能。

当前版本：`0.4.0`

## 技术栈

- Vue 3
- Vue Router 4
- Vite 7
- Element Plus
- 高德地图 Web JS API
- REST API 与 WebSocket
- Node.js 内置测试框架

## 主要功能

### 货主

- 查看运输概况、车辆位置和历史轨迹
- 查看预计到达时间与告警消息
- 使用智能问答

### 司机

- 查看本人运输任务和规划路线
- 上报运输状态
- 接收并处理调度指令
- 查看通知和实时位置

### 仓库管理员

- 查看车辆监控和车辆信息
- 录入货物及查看入库记录
- 选择货主、货物、终点和运输资源完成货物出库
- 查看货物库存与出库记录
- 生成候选路线并确认运输任务

### 调度员

- 实时车辆监控
- 告警处理和调度指令下发
- 运输数据统计
- 查看通知和智能问答

### 系统管理员

- 用户、车辆和货物管理
- 告警日志管理
- 系统设置和运行状态查看

## 实时与智能能力

- 车辆实时位置：通过车辆位置 WebSocket 接收 GPS 数据，并通过 REST 接口获取车辆字典、最新位置和历史轨迹。
- 实时 ETA：通过物流 WebSocket 接收预计到达时间更新。
- 告警与通知：分别通过告警 WebSocket 和通知 WebSocket接收消息。
- 智能路线推荐：调用路线推荐接口，对候选路线进行综合评分并由用户确认。
- 智能问答：调用智能体健康检查与对话接口。

以上功能是否能够正常返回数据，取决于对应后端、智能体、WebSocket 服务及数据库是否已启动并完成部署。

## 环境要求

- Node.js 20.19+ 或 22.12+
- npm 10+，也可使用兼容版本的 pnpm
- 可访问的业务后端、智能体服务和高德地图 Web JS API

## 本地运行

```bash
npm install
```

复制环境变量示例文件：

```powershell
Copy-Item .env.example .env.local
```

填写 `.env.local` 后启动开发服务器：

```bash
npm run dev
```

默认访问地址：

```text
http://localhost:5173
```

## 环境变量

| 变量 | 用途 |
| --- | --- |
| `VITE_API_BASE_URL` | 浏览器访问的业务 API 前缀，开发环境建议使用 `/api/v1` |
| `VITE_API_PROXY_TARGET` | Vite 转发到的业务后端 HTTP 地址 |
| `VITE_AGENT_BASE_URL` | 智能问答服务地址 |
| `VITE_ROUTE_AGENT_BASE_URL` | 浏览器访问的路线智能体前缀，默认 `/route-agent` |
| `VITE_ROUTE_AGENT_PROXY_TARGET` | Vite 转发到的路线智能体服务地址 |
| `VITE_REALTIME_API_BASE_URL` | 实时车辆 REST API 前缀，默认 `/api` |
| `VITE_VEHICLE_WS_URL` | 车辆实时位置 WebSocket 地址 |
| `VITE_LOGISTICS_WS_URL` | ETA 等物流消息 WebSocket 地址 |
| `VITE_ALARM_WS_URL` | 告警 WebSocket 地址 |
| `VITE_NOTIFICATION_WS_URL` | 通知 WebSocket 地址 |
| `VITE_AMAP_KEY` | 高德地图 Web 端 JS API Key |
| `VITE_AMAP_SECURITY_CODE` | 高德地图安全密钥 |
| `VITE_API_TOKEN` | 仅限本地临时联调的访问令牌，正常情况下由登录接口获取 |

请勿把包含真实令牌、密码或地图密钥的 `.env.local` 提交到 Git 仓库。

## 常用命令

```bash
# 启动开发服务器
npm run dev

# 启动本地模拟接口
npm run mock

# 执行测试
npm test

# 构建生产版本
npm run build

# 测试并构建
npm run check

# 本地预览构建结果
npm run preview
```

## 项目结构

```text
├─ public/                 静态资源和地图数据
├─ src/
│  ├─ api/                 REST API 与智能体请求封装
│  ├─ components/          地图、页面标题、状态展示等公共组件
│  ├─ composables/         GPS、ETA、告警和通知 WebSocket 逻辑
│  ├─ mock/                前端模拟数据
│  ├─ router/              页面路由和角色访问控制
│  ├─ stores/              登录会话与通知状态
│  ├─ utils/               校验与实时数据范围处理
│  └─ views/               各角色业务页面
├─ tests/                  自动化测试
├─ tools/                  本地模拟接口及数据转换工具
├─ docs/                   后端联调和验收说明
├─ .env.example            环境变量示例
├─ package.json            依赖与脚本
└─ vite.config.js          Vite 与开发代理配置
```

## 接口约定

- 常规业务接口默认通过 `/api/v1` 访问。
- 实时车辆字典等接口通过 `/api` 访问。
- 登录成功后，访问令牌保存在浏览器本地存储中，请求时自动携带 `Authorization: Bearer <token>`。
- WebSocket 连接会携带当前登录令牌，并包含断线重连和心跳处理。
- 开发环境由 Vite 将 `/api` 和 `/route-agent` 转发至配置的服务地址，从而减少跨域问题。

具体接口及联调要求请参阅 [`docs`](./docs) 目录。

## 构建与部署

```bash
npm run check
```

构建产物生成在 `dist/`。部署时需要：

1. 将 `dist/` 发布到静态 Web 服务器。
2. 为 Vue Router history 模式配置回退到 `index.html`。
3. 配置 `/api` 和 `/route-agent` 的反向代理，或在构建前设置对应的完整服务地址。
4. 生产环境使用 HTTPS 时，WebSocket 地址应使用 `wss://`，避免浏览器拦截混合内容。
5. 在高德开放平台配置部署域名白名单及安全密钥。

## 注意事项

- 本项目只包含前端代码，不会修改后端数据库结构或接口实现。
- 前端以云端实际部署接口的返回结果为准，不应自行假设后端字段或业务状态。
- GPS 坐标必须是有效数字；向高德地图展示前需要确认坐标系与经纬度顺序一致。
- 删除或修改云端数据后，如页面仍显示旧记录，应刷新对应接口数据并检查浏览器缓存和前端状态。
