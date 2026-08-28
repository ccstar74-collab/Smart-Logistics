# 告警调度同步与备用路线预览优化

本版针对调度员页面补充两项交互：

1. 告警管理中的“下发调度指令”不再维护一套独立弹窗逻辑，而是跳转到统一 `/dispatch` 页面，并通过 `alarmId + taskId` 自动带入关联告警和运输任务。下发时继续携带 `alarmId` 调用 `POST /api/v1/dispatch-commands`，使告警处理状态与调度指令记录保持同一业务链路。
2. 备用路线选择后自动弹出路线预览。弹窗展示起终点、路线距离、预计耗时、来源及与当前路线的差异；当 `GET /api/v1/transport-tasks/{taskId}/routes` 返回 `polyline`、`routePoints`、`path` 或 `points` 时，直接使用现有高德地图组件绘制该候选路线。

## 后端路线字段建议

每条 READY / ACTIVE 路线建议至少返回：

```json
{
  "routeId": "route_xxx",
  "routeVersion": 2,
  "status": "READY",
  "coordinateSystem": "WGS84",
  "distanceMeters": 18230,
  "durationSeconds": 1680,
  "provider": "AMAP",
  "routePoints": [[106.51,29.55],[106.52,29.56]]
}
```

前端兼容 `routePoints / route_points / polyline / path / points / geometry.polyline / geometry.points`。
若后端暂时只返回距离和耗时而不返回路线坐标，弹窗仍显示方案指标，但地图区域会明确提示“后端暂未返回 polyline / routePoints”。
