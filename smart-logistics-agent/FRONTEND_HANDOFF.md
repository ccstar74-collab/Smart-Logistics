# 智慧物流智能体：前端交接说明

## 交接范围

这个包提供一个可独立运行的 Java 智能体后端，以及一个极简参考页面。页面同学不需要接触通义千问 API Key，也不应在浏览器代码中直接调用百炼接口。

调用关系：

```text
页面 → Java 智能体 /api/chat → 本地知识库 → 通义千问
```

参考页面启动后位于：

```text
http://localhost:8080/
```

## 启动后端

环境要求：JDK 21，且 `java -version`、`javac -version` 均可用。

在项目根目录运行：

```powershell
.\scripts\start-handoff.ps1
```

脚本会隐藏输入百炼 API Key，并使用以下默认配置：

```text
MODEL_API_STYLE=chat_completions
MODEL_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
MODEL_NAME=qwen-plus
AGENT_PORT=8080
```

API Key 只保存在当前进程环境变量中，不会写入项目文件。输入密钥时请在经典 PowerShell 中使用鼠标右键或 `Ctrl + Shift + V` 粘贴；如果暂时没有密钥，可运行本地知识检索模式：

```powershell
.\scripts\start-handoff.ps1 -WithoutModel
```

指定其他端口：

```powershell
.\scripts\start-handoff.ps1 -Port 8081
```

## 前端配置

建议把后端根地址放入前端环境配置，不要散落在组件里。

本机开发：

```text
http://localhost:8080
```

示例：

```javascript
const AGENT_BASE_URL = 'http://localhost:8080';
```

后端已允许开发期跨域请求：

```text
Access-Control-Allow-Origin: *
Access-Control-Allow-Headers: Content-Type, X-Admin-Token
Access-Control-Allow-Methods: GET, POST, OPTIONS
```

生产环境应把 `*` 收紧为正式前端域名，并增加用户认证。

## 健康检查

```http
GET /health
```

示例响应：

```json
{
  "status": "UP",
  "knowledgeChunks": 2,
  "modelEnabled": true,
  "modelApiStyle": "chat_completions",
  "modelName": "qwen-plus"
}
```

字段说明：

| 字段 | 类型 | 说明 |
|---|---|---|
| `status` | string | `UP` 表示 Java 服务可用 |
| `knowledgeChunks` | number | 已加载的知识片段数 |
| `modelEnabled` | boolean | 是否读取到模型 API Key |
| `modelApiStyle` | string | 当前模型协议 |
| `modelName` | string | 当前模型名 |

## 对话接口

```http
POST /api/chat
Content-Type: application/json; charset=utf-8
```

请求：

```json
{
  "sessionId": "user-001",
  "question": "运输途中发生偏航应该怎么处理？"
}
```

约束：

| 字段 | 必填 | 约束 |
|---|---|---|
| `sessionId` | 否 | 缺省为 `anonymous`；建议每个浏览器会话生成一个；只允许字母、数字、点、下划线和横线，最长 80 字符 |
| `question` | 是 | 去除首尾空格后不能为空，最长 2000 字符 |

成功响应：

```json
{
  "sessionId": "user-001",
  "answer": "发现偏航后，应先查看异常位置并联系司机核实情况……",
  "mode": "model",
  "sources": [
    {
      "source": "异常告警处理.md",
      "score": 21.401
    }
  ]
}
```

`mode` 取值：

| 值 | 页面建议 |
|---|---|
| `model` | 正常展示模型组织后的回答 |
| `extractive` | 展示本地知识检索结果，可提示“模型未启用” |
| `no_context` | 提示知识库没有足够依据 |
| `guardrail` | 提示问题需要尚未接入的订单、告警或真实生产数据 |
| `tool` | 展示实时工具回答；地图可直接使用响应中的 `toolData` |

## 实时车辆接口

全部 CARLA 模拟车辆最新位置：

```http
GET /api/v1/vehicles/locations/latest
```

单车查询：

```http
GET /api/v1/vehicles/location?identifier=sim_000
```

`identifier` 支持车辆编号、设备编号和车牌。返回的经纬度是 WGS84，可直接交给支持 WGS84 的地图组件；接入高德或腾讯地图时，应按地图服务要求处理坐标系转换。

自然语言问答进入实时工具后，`/api/chat` 响应包含：

```json
{
  "mode": "tool",
  "answer": "车辆渝A10000……经度 106.730553，纬度 29.613528……",
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

前端应以 `toolData` 中的数字作为地图数据，不要从自然语言 `answer` 中反向解析坐标。

最小调用示例：

```javascript
async function askAgent(question, sessionId) {
  const response = await fetch(`${AGENT_BASE_URL}/api/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json; charset=utf-8' },
    body: JSON.stringify({ question, sessionId })
  });

  const data = await response.json();
  if (!response.ok) {
    throw new Error(data.error?.message || `HTTP ${response.status}`);
  }
  return data;
}
```

## 错误响应

统一结构：

```json
{
  "error": {
    "code": "BAD_REQUEST",
    "message": "question 不能为空"
  }
}
```

常见状态码：

| HTTP | `code` | 含义 |
|---:|---|---|
| 400 | `BAD_REQUEST` | 请求字段或 JSON 格式错误 |
| 401 | `UNAUTHORIZED` | 管理接口凭证错误；普通对话目前不要求登录 |
| 404 | `NOT_FOUND` | 路径不存在 |
| 405 | `METHOD_NOT_ALLOWED` | HTTP 方法错误 |
| 502 | `AGENT_ERROR` | 上游模型网络、密钥、权限、额度或响应异常 |

前端应在 `fetch` 外层使用 `try/catch`，同时处理网络异常和非 2xx 响应。不要把上游百炼密钥放进浏览器请求。

## 会话行为

- 后端在内存中为每个 `sessionId` 保存最近 3 轮问答；
- Java 服务重启后会话丢失；
- 多实例之间暂不共享会话；
- 点击“新对话”时，前端应生成新的 `sessionId`；
- 推荐使用 `crypto.randomUUID()`，并保存在 `sessionStorage` 或 `localStorage`。

## 当前边界

- 当前不是流式接口，页面应显示“正在思考”状态，等待完整 JSON 返回；
- 尚未接入用户登录和权限；
- 尚未接入真实运单、车辆、GPS 和告警业务接口；
- 不要让页面声称已经查询实时物流状态；
- `/api/knowledge` 是管理员写入接口，不应暴露在普通用户页面；
- 模型回答必须使用 `textContent` 或框架的默认文本插值渲染，不要把回答直接赋给 `innerHTML`。

## 交付前自检

```powershell
.\scripts\test.ps1
```

预期：

```text
Compilation completed: ...\out
全部自检通过（5/5）
```

启动后检查：

```powershell
Invoke-RestMethod http://localhost:8080/health
```

浏览器参考页面：

```text
http://localhost:8080/
```
