# 智慧物流智能体（Java MVP）

这是智慧物流项目的第一阶段智能问答服务。它以纯 Java 8 实现，不依赖 Maven 或第三方库，可以完成本地物流知识检索、连续对话、模型增强回答和实时数据防幻觉拦截。

交给页面开发同学时，请优先阅读 [FRONTEND_HANDOFF.md](FRONTEND_HANDOFF.md)，其中包含启动方式、完整 API 契约、错误结构和前端调用示例。

## 当前能力

- 从 `knowledge/` 自动加载 UTF-8 Markdown 或文本资料；
- 针对中文字符、中文二元词组和英文单词进行本地检索；
- `POST /api/chat` 提供带会话记忆的物流问答；
- 配置模型密钥后，通过 Chat Completions 或 Responses 风格的 HTTP API 生成有依据的回答；
- 未配置模型时仍可运行，直接返回相关知识片段；
- 对“货物现在在哪”等实时查询明确说明尚未连接业务系统，避免编造；
- 从 CARLA 测试快照热加载 20 辆车的位置、状态、速度与方向，支持自然语言实时查询；
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
| `model` | 使用检索结果和模型生成回答 |
| `extractive` | 未配置模型，直接返回检索片段 |
| `no_context` | 知识库没有足够依据 |
| `guardrail` | 问题需要尚未接入的实时业务数据 |
| `tool` | 使用 CARLA 实时车辆工具返回精确位置和结构化数据 |

## CARLA 实时车辆数据

默认读取：

```text
planning_guoyuan_20v_30tasks/vehicles_latest_api.json
planning_guoyuan_20v_30tasks/locations.json
```

每次查询会检查文件修改时间；CARLA 模拟器覆盖 `vehicles_latest_api.json` 后，下一次请求会自动加载新快照，不需要重启 Java 服务。当前测试文件是模拟快照，回答会明确标记 `CARLA_SIMULATION`，不代表真实道路车辆。

自然语言示例：

```text
渝A10000现在在哪？
sim_005的实时位置和状态是什么？
车辆1的坐标是多少？
显示所有车辆分布
```

查询全部车辆：

```http
GET /api/v1/vehicles/locations/latest
```

查询单车，可使用车辆编号、设备编号或车牌：

```http
GET /api/v1/vehicles/location?identifier=sim_000
GET /api/v1/vehicles/location?identifier=1
GET /api/v1/vehicles/location?identifier=%E6%B8%9DA10000
```

智能体工具回答会返回：

```json
{
  "mode": "tool",
  "toolData": {
    "tool": "vehicle_realtime_lookup",
    "sourceType": "CARLA_SIMULATION",
    "found": true,
    "vehicle": {
      "vehicleId": 1,
      "deviceCode": "sim_000",
      "plateNumber": "渝A10000",
      "longitude": 106.730553,
      "latitude": 29.613528,
      "recordedAt": "2026-08-22T16:34:00+08:00"
    }
  }
}
```

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
| `CARLA_DATA_DIR` | `planning_guoyuan_20v_30tasks` | CARLA 最新车辆快照与位置节点目录 |
| `MODEL_API_KEY` | 空 | 为空时使用检索模式 |
| `MODEL_API_STYLE` | `chat_completions` | `chat_completions` 或 `responses` |
| `MODEL_BASE_URL` | `https://dashscope.aliyuncs.com/compatible-mode/v1` | 模型 API 根地址 |
| `MODEL_NAME` | `qwen-plus` | 模型名；应以百炼控制台实际开通范围为准 |
| `ADMIN_TOKEN` | 空 | 知识写入接口令牌；为空即禁用 |
| `RAG_TOP_K` | `4` | 最大召回片段数 |
| `MAX_QUESTION_LENGTH` | `2000` | 问题最大字符数 |

## API 边界与下一步

当前智能体只掌握静态知识，并没有权限访问真实订单、车辆、GPS、告警或用户身份数据。下一步应新增受权限控制的业务工具接口，例如 `queryShipment(trackingNo, currentUser)`，由智能体调用后回答实时物流问题；在该接口完成前，不应移除现有的实时查询拦截。

生产化时还需要引入身份认证、数据库持久化、多实例共享会话、请求限流、审计日志、向量检索和模型调用可观测性。届时建议迁移到 JDK 21 + Spring Boot，并保留当前 `KnowledgeBase`、`ModelClient` 和 `LogisticsAgent` 的职责边界。
