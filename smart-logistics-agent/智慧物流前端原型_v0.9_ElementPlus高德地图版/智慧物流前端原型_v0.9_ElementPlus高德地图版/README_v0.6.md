# v0.6 后端实时快照字段对齐版

本版在 v0.5 Town10HD 清晰路网版基础上，只调整“实时监测”相关前端，不改三角色、任务、货物、调度等整体结构。

## 主要变化

1. 首页与实时追踪页改为读取 `vehicleSnapshots`。
2. `vehicleSnapshots` 字段与后端当前 `车辆实时快照` 对齐：
   - `vehicle_id`
   - `online`
   - `transport_status`
   - `status_timestamp`
   - `gps.lat / lon / speed_kmh / heading / coordinate_system / timestamp`
   - `has_active_alert`
   - `latest_alert.alert_type / description / timestamp / source`
3. 额外的 `display` 仅为当前纯前端原型辅助字段，用于车牌、司机、任务、ETA 与 Town10HD 上的展示位置；后续真实接口可由任务接口/CARLA 坐标接口替换。
4. Town10HD 仍然是静态 OpenDRIVE 路网，车辆位置目前为前端 Mock，并不意味着 WGS84 经纬度已经与 CARLA 坐标完成真实转换。

## 运行

请确保终端进入包含 `package.json` 的项目根目录：

```powershell
npm install
npm run dev
```

## 后续接后端

当前 `src/mock/data.json -> vehicleSnapshots` 可直接替换成后端 HTTP/WebSocket 返回数据；建议保持字段名不变。
