# 智慧物流追踪系统前端原型 v0.2

## 本版变化
- 接入 Vue Router
- 左侧 8 个菜单全部可以点击切换页面
- Dashboard 中“查看全部”按钮可跳转
- 新增车辆管理、货物管理、运输任务、实时追踪、告警中心、调度指令、智能问答页面
- 保留自定义二维地图，不依赖高德/百度地图
- 告警可在前端演示“标记已处理”
- 调度指令可在前端模拟新增
- 智能问答提供 Mock 交互

## 运行
```bash
npm install
npm run dev
```

默认访问：
http://localhost:5173

## 数据
Mock 数据位于：
src/mock/data.json

后续后端联调时，将 Mock 替换为：
- REST API：车辆、货物、任务、告警、调度
- WebSocket：车辆位置、告警、任务状态
- Agent API：/api/v1/agent/chat

## v0.3 角色切换
点击左下角当前登录人员，可在以下三种角色间切换：
- 货主 OWNER：运输概览、我的货物、我的任务、实时追踪、异常消息、智能问答
- 司机 DRIVER：我的运输任务、实时追踪、调度指令、货物状态
- 管理员 ADMIN：全部管理与调度页面

当前为前端演示登录，不包含真实密码认证；正式联调时可替换为 `/api/v1/auth/login`。

## v0.4 CARLA Town10HD static map integration

- The dashboard and tracking pages keep the original layout.
- The original hand-drawn static map is replaced by a Town10HD road base map generated from `reference/Town10HD.xodr`.
- Browser-ready road data is stored at `public/data/town10hd-map.json`.
- Vehicle/facility locations are currently mock front-end data; no CARLA server or backend is required.
- Future integration can replace the mock vehicle positions with WebSocket/API data without changing the page structure.

## v0.5 Town10HD 清晰路网版
- Town10HD 非 junction 道路作为主路宽线显示；junction 连接线改为浅色细线，减少路口“弯线堆叠”。
- 仓库/配送点改为道路锚点 + 引导线 + 标签卡片，位置关系更清晰。
- 模拟运输路线改为一条连续路线，避免零散蓝色道路高亮。
- 车辆位置调整到主路上，避免与仓库/配送点重叠。
