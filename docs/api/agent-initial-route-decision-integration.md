# 初始路线决策智能体联调合同

## 1. 职责边界

初始多路线采用“业务后端确定性评分，智能体只负责解释”的边界：

```text
58080 业务后端
→ 生成真实路线、路况和天气快照
→ 按 initial-route-score-v1 计算正式分数和排名
→ 将不可修改的正式结果交给智能体
→ 校验智能体返回内容
→ 保存解释并返回前端
```

智能体不能修改：

- `routeId`
- `rank`
- `totalScore`
- `scoreDetails`
- 路线几何、距离、用时、路况和天气快照

仓库管理员拥有最终选择权；智能体只有推荐解释权。

## 2. 当前业务后端接入点

代码边界为：

```text
InitialRouteExplanationPort
├─ ExplanationRequest
└─ ExplanationResult
```

当前默认实现为 `RuleFallbackInitialRouteExplanationService`，所以即使智能体尚未部署，
业务后端也能返回稳定评分、排名和模板解释，且
`recommendationSource=RULE_FALLBACK`。

智能体同学接入时建议新增一个实现或包装器，不要改动评分服务。智能体成功时返回
`AGENT_EXPLANATION`；超时、5xx、结构不合法或业务校验失败时立即回退到现有模板。

## 3. 建议的服务间请求合同

智能体内部地址和鉴权方式由部署环境配置，不暴露给前端。建议请求体：

```json
{
  "requestId": "ird_xxx",
  "planningMode": "INITIAL_MULTI_OBJECTIVE",
  "scoringRuleVersion": "initial-route-score-v1",
  "weather": {
    "source": "AMAP_WEATHER_V3",
    "weather": "小雨",
    "windPower": "3",
    "reportTime": "2026-08-31T15:00:00+08:00"
  },
  "routes": [
    {
      "routeId": "preview_route_xxx",
      "rank": 1,
      "totalScore": 88.60,
      "distanceMeters": 829700,
      "referenceDurationSeconds": 33720,
      "trafficLevel": "SLOW",
      "scoreDetails": {
        "time": 100.00,
        "distance": 92.40,
        "traffic": 75.00,
        "weather": 80.00
      },
      "ruleReasons": ["预计用时最短", "整体拥堵程度较低"]
    }
  ]
}
```

智能体只需返回解释：

```json
{
  "requestId": "ird_xxx",
  "scoringRuleVersion": "initial-route-score-v1",
  "recommendedRouteId": "preview_route_xxx",
  "explanation": "综合评分推荐候选路线 A。该路线预计用时最短，整体拥堵程度较低。",
  "reasonsByRouteId": {
    "preview_route_xxx": [
      "预计用时最短",
      "整体拥堵程度较低"
    ]
  }
}
```

`recommendedRouteId` 只能等于业务后端已经排在第 1 位的路线；它不是重新评分结果。

## 4. 业务后端必须执行的响应校验

- `requestId` 与本次请求一致；
- `scoringRuleVersion` 必须是 `initial-route-score-v1`；
- `recommendedRouteId` 必须存在且等于正式排名第 1 的路线；
- `reasonsByRouteId` 不得包含未知路线；
- 不接受智能体返回的新分数或新排名；
- 解释和单条理由应限制长度，拒绝空文本；
- 智能体响应到达时决策上下文仍有效；
- 日志记录 requestId、decisionId、来源和耗时，不记录 JWT、服务凭证或完整敏感请求头。

任一校验失败都不能影响正式评分和任务创建，应改用规则模板。

## 5. 超时与降级

建议：

- 连接和总请求超时由配置控制，智能体解释不应长时间阻塞仓库出库；
- 超时、网络异常、5xx、非法 JSON、未知 routeId 一律降级；
- 降级后保持 `recommendedRouteId`、正式分数和排名不变；
- 降级返回 `recommendationSource=RULE_FALLBACK`；
- 智能体成功返回 `recommendationSource=AGENT_EXPLANATION`。

智能体失败不是路线规划失败。只要真实候选和确定性评分成功，仓库管理员仍可人工确认。

## 6. 联调验证

智能体联调至少覆盖：

1. 正常返回中文推荐摘要和每条路线理由；
2. 相同输入产生稳定、不自相矛盾的解释；
3. 智能体超时后业务接口仍返回 200 和 `RULE_FALLBACK`；
4. 返回未知 routeId 时被业务后端拒绝并降级；
5. 尝试修改分数或排名不会改变业务后端结果；
6. 智能体不可用时仍能确认决策并创建唯一 `v1 ACTIVE` 路线。

## 7. 双方待确认的部署参数

智能体同学提供：

- 内网服务 URL 和 path；
- 鉴权 Header 名称和凭证注入方式；
- 超时建议；
- 请求和响应版本号；
- 健康检查地址。

业务后端同学完成：

- `InitialRouteExplanationPort` 的 HTTP 客户端实现；
- 响应白名单校验；
- 超时、重试边界和模板降级；
- 环境变量配置，禁止把凭证提交到 Git。
