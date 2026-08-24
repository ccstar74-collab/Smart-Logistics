# v0.7 动态数据演示版

纯前端模拟“后端实时快照持续推送”的效果，不需要后端、MQTT 或 CARLA Server。

新增：车辆在 Town10HD 路网上移动、速度/航向/GPS时间戳变化、周期性异常停留和路线偏离告警、统计与告警列表联动；支持开始/暂停/单步/倍速/重置。

启动：在能看到 package.json 的目录运行 `npm install`，再运行 `npm run dev`。进入“物流监控大屏”或“实时追踪”，点击“开始模拟”。

以后接 WebSocket 时，只需把 `src/stores/realtime.js` 中的模拟更新替换成后端收到的实时快照即可。
