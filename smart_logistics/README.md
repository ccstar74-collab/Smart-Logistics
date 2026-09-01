# 智慧物流智能体（Java MVP）

这是智慧物流项目的第一阶段智能问答服务。它以纯 Java 8 实现，不依赖 Maven 或第三方库，可以完成本地物流知识检索、连续对话、模型增强回答和实时数据防幻觉拦截。

交给页面开发同学时，请优先阅读 [FRONTEND_HANDOFF.md](FRONTEND_HANDOFF.md)，其中包含启动方式、完整 API 契约、错误结构和前端调用示例。

## 当前能力

- 从 `knowledge/` 自动加载 UTF-8 Markdown 或文本资料；
- 针对中文字符、中文二元词组和英文单词进行本地检索；
- `POST /api/chat` 提供带会话记忆的物流问答；
- 配置模型密钥后，通过 Chat Completions 或 Responses 风格的 HTTP API 生成有依据的回答；
- 未配置模型时仍可运行，直接返回相关知识片段；
- 通过只读云端业务工具查询 Spring Boot/MySQL 中的车辆档案、货物、运输任务和告警；
- 对尚未接入的数据类型继续执行实时查询拦截，避免编造；
- 使用管理员令牌保护在线知识导入接口；
- 不记录或提交模型 API 密钥。

## 环境要求

需要 JDK 8 或更高版本，即 `java` 和 `javac` 都可用。当前机器只检测到 Java 8 JRE，没有 `javac`，需要先安装完整 JDK 并把其 `bin` 目录加入 `PATH`。

验证环境：

```powershell
java -version
javac -version
```

## 编译、测试和启动

在项目根目录执行：

```powershell
.\scripts\test.ps1
.\scripts\run.ps1
```

不配置模型也能启动，此时服务处于本地检索模式。

## 配置通义千问

先在阿里云百炼控制台开通模型服务并创建 API Key。模型密钥通过当前终端的环境变量提供，不要写进代码或提交到 Git。为了避免密钥进入 PowerShell 历史，可隐藏输入：

```powershell
$SecureModelKey = Read-Host "请输入百炼 API Key" -AsSecureString
$env:MODEL_API_KEY = [System.Net.NetworkCredential]::new("", $SecureModelKey).Password
Remove-Variable SecureModelKey

$env:MODEL_API_STYLE = "chat_completions"
$env:MODEL_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1"
$env:MODEL_NAME = "qwen-plus"
.\scripts\run.ps1
```

程序会向 `https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions` 发送请求。设置环境变量后必须重启服务；`/health` 中的 `modelEnabled` 应为 `true`、`modelApiStyle` 应为 `chat_completions`。模型适配代码集中在 `ModelClient.java`，切换协议不影响知识检索和对外的 `/api/chat` 接口。

如需切回 Responses 风格服务，设置：

```powershell
$env:MODEL_API_STYLE = "responses"
$env:MODEL_BASE_URL = "https://api.openai.com/v1"
$env:MODEL_NAME = "服务商实际可用的模型名"
```

## 调用接口

启动服务后，浏览器访问 `http://localhost:8080/` 即可打开极简聊天网页。网页与智能体接口由同一个 Java 服务提供，不需要另外启动前端开发服务器。

健康检查：

```powershell
Invoke-RestMethod http://localhost:8080/health
```

发起问答：

```powershell
$Body = @{
    sessionId = "owner-001"
    question = "运输途中发生偏航应该怎么处理？"
} | ConvertTo-Json

Invoke-RestMethod `
    -Method Post `
    -Uri http://localhost:8080/api/chat `
    -ContentType "application/json; charset=utf-8" `
    -Body $Body
```

响应示例：

```json
{
  "sessionId": "owner-001",
  "answer": "……",
  "mode": "extractive",
  "sources": [
    {"source": "异常告警处理.md", "score": 8.215}
  ]
}
```

`mode` 的含义：

| 值 | 含义 |
|---|---|
| `model` | 使用知识库和模型生成回答；知识库未命中时可回答通用物流知识 |
| `extractive` | 未配置模型，直接返回检索片段 |
| `no_context` | 知识库没有足够依据且模型未启用 |
| `guardrail` | 问题需要尚未接入的实时业务数据 |
| `tool` | 使用云端业务数据工具返回结构化事实 |

## 云端业务数据

智能体默认以只读 GET 请求连接团队 Spring Boot：

```text
http://111.170.148.177:58080
```

当前支持：

```text
GET /api/v1/vehicles
GET /api/v1/cargos
GET /api/v1/transport-tasks
GET /api/v1/alarms
```

可直接询问：

```text
云端车辆列表有哪些？
TEST-A001是什么状态？
云端货物列表和状态是什么？
目前有哪些运输任务？
最新告警有哪些？
```

工具响应的 `toolData` 会包含：

```json
{
  "tool": "cloud_business_lookup",
  "sourceType": "CLOUD_SPRING_BOOT_MYSQL",
  "resource": "vehicles",
  "readOnly": true,
  "data": {}
}
```

如需切换业务后端：

```powershell
$env:BUSINESS_API_BASE_URL = "http://111.170.148.177:58080"
$env:BUSINESS_API_TIMEOUT_MS = "8000"
.\scripts\run.ps1
```

`BUSINESS_API_TOKEN` 仅为后端将来启用 Bearer 鉴权预留；当前接口没有要求时保持为空。
云端地址目前是明文 HTTP，在后端启用 HTTPS 前不要通过它发送 Bearer Token。
智能体不会通过该工具执行 POST、PUT、PATCH 或 DELETE。云端返回失败、超时或格式不正确时，
回答会明确说明工具不可用，不会让模型补造业务数据。

## 在线导入知识

安全起见，不配置 `ADMIN_TOKEN` 时写入接口默认关闭。启用并启动服务：

```powershell
$env:ADMIN_TOKEN = "请替换为随机长令牌"
.\scripts\run.ps1
```

导入文档：

```powershell
$Body = @{
    title = "签收规则"
    content = "货物送达后，货主核对货物并确认签收。"
} | ConvertTo-Json

Invoke-RestMethod `
    -Method Post `
    -Uri http://localhost:8080/api/knowledge `
    -Headers @{ "X-Admin-Token" = $env:ADMIN_TOKEN } `
    -ContentType "application/json; charset=utf-8" `
    -Body $Body
```

## 环境变量

| 变量 | 默认值 | 用途 |
|---|---|---|
| `AGENT_PORT` | `8080` | HTTP 端口 |
| `KNOWLEDGE_DIR` | `knowledge` | 知识文件目录 |
| `MODEL_API_KEY` | 空 | 为空时使用检索模式 |
| `MODEL_API_STYLE` | `chat_completions` | `chat_completions` 或 `responses` |
| `MODEL_BASE_URL` | `https://dashscope.aliyuncs.com/compatible-mode/v1` | 模型 API 根地址 |
| `MODEL_NAME` | `qwen-plus` | 模型名；应以百炼控制台实际开通范围为准 |
| `ADMIN_TOKEN` | 空 | 知识写入接口令牌；为空即禁用 |
| `BUSINESS_API_BASE_URL` | `http://111.170.148.177:58080` | 云端 Spring Boot 业务 API 根地址 |
| `BUSINESS_API_TOKEN` | 空 | 可选 Bearer Token，不要提交到 Git |
| `BUSINESS_API_TIMEOUT_MS` | `8000` | 云端业务 GET 请求连接与读取超时 |
| `AMAP_WEB_SERVICE_KEY` | 空 | 高德 Web 服务 Key；用于经纬度逆地理编码和出库地址正向地理编码 |
| `AMAP_REVERSE_GEOCODE_URL` | 高德 `/v3/geocode/regeo` | 可选的逆地理编码接口地址 |
| `AMAP_GEOCODE_URL` | 高德 `/v3/geocode/geo` | 可选的文字地址正向地理编码接口地址 |
| `RAG_TOP_K` | `4` | 最大召回片段数 |
| `MAX_QUESTION_LENGTH` | `2000` | 问题最大字符数 |

## API 边界与下一步

当前智能体已能只读访问云端车辆档案、货物、运输任务和告警列表。车辆位置读取云端 Vehicle API
返回的 `lastLongitude`、`lastLatitude` 和 `lastUpdatedAt`；字段为 `null` 时，智能体会明确回答“暂无定位”，不会编造坐标。
目前尚未接入云端 InfluxDB 中的实时 GPS 与历史轨迹，也没有用户身份与数据权限隔离。
下一步应由云端业务后端提供受权限控制的最新位置和轨迹接口，再让智能体调用同一事实数据源。

生产化时还需要引入身份认证、数据库持久化、多实例共享会话、请求限流、审计日志、向量检索和模型调用可观测性。届时建议迁移到 JDK 21 + Spring Boot，并保留当前 `KnowledgeBase`、`ModelClient` 和 `LogisticsAgent` 的职责边界。
