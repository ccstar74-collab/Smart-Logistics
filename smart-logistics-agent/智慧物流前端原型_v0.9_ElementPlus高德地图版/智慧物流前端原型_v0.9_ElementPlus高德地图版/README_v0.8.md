# v0.8 高德真实地图动态版

本版本在 v0.7 动态快照演示基础上，把 CARLA Town10HD 静态路网替换为高德 JS API 2.0 的真实城市底图。

## 1. 新增内容

- `src/components/AMapView.vue`：真实城市地图组件。
- `src/views/Dashboard.vue`：首页使用 `AMapView`。
- `src/views/Tracking.vue`：实时追踪页使用 `AMapView`。
- `src/stores/realtime.js`：模拟数据改为直接更新 WGS84 经纬度。
- `.env.example`：高德 Key 与 securityJsCode 配置模板。
- `package.json`：新增 `@amap/amap-jsapi-loader`。

页面仍然使用后端约定的快照字段：`vehicle_id`、`online`、`transport_status`、`gps.lat/lon/speed_kmh/heading/timestamp`、`has_active_alert`、`latest_alert`。

## 2. 配置高德 Key

在项目根目录复制：

```powershell
Copy-Item .env.example .env.local
```

打开 `.env.local`：

```env
VITE_AMAP_KEY=你的Web端JSAPIKey
VITE_AMAP_SECURITY_CODE=你的securityJsCode
```

不要把真实 Key 提交到 Git 或发给其他人。

## 3. 安装运行

```powershell
npm install
npm run dev
```

如果之前已经安装过旧版依赖，也建议重新执行一次 `npm install`，因为 v0.8 新增了高德加载器依赖。

## 4. 动态效果

进入“物流监控大屏”或“实时追踪”后点击“开始模拟”：

- 车辆 GPS 经纬度持续变化；
- 地图车辆 Marker 同步移动；
- 速度、航向、GPS 时间持续刷新；
- 当前选中车辆保留动态轨迹；
- 仓库显示电子围栏；
- sim_000 周期性触发异常停留；
- sim_002 周期性触发路线偏离；
- 页面告警数量和快照卡片同步刷新。

## 5. 坐标系

模拟/后端快照采用 WGS84。高德底图采用 GCJ-02，因此 `AMapView.vue` 内部只负责显示转换，不修改原始快照中的 WGS84 字段。

后续接入真实后端时，只需要把 `src/stores/realtime.js` 的 Mock 更新逻辑替换为 HTTP/WebSocket 数据写入，页面结构可以继续沿用。
