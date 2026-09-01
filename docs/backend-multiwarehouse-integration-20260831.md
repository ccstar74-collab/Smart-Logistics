# 智慧物流前端联调说明：多仓库货物出库与智能选仓

> 文档用途：前端开发 / 前后端联调  
> 当前后端集成基线：`integration/warehouse-route-ws`  
> 当前基线 Commit：`211689f0807539fc99eb9e3f8b2080f90818728b`  
> 联调环境 API Base URL：`http://111.170.148.177:58080/api/v1`  
> 文档状态：以当前已部署后端和真实云端 E2E 结果为准  
> 更新时间：2026-08-31

---

## 1. 当前后端完成状态

多仓库功能已经完成代码开发、合并、数据库迁移、云端部署和主链路 E2E 验证。

当前最终集成分支已包含：

- Arrival Geofence
- Multi-Warehouse
- Multi-Objective Route
- WebSocket Enhancement
- 原有 Replan / Playback / ETA / Alarm 等能力

后端当前全量测试结果：

```text
Tests run: 665
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

多仓库真实云端主链路已验证：

```text
Warehouse recommendation             PASS
Cargo availability                   PASS
Vehicle availability                 PASS
POST /from-warehouse                 PASS

originWarehouseId 正确               PASS
start* = Warehouse 快照              PASS
driverId = Vehicle.driverId          PASS

Task 创建后 = WAITING                PASS
Cargo 创建任务后仍 = WAITING         PASS
Vehicle 创建任务后仍 = IDLE          PASS

v1 route = ACTIVE                    PASS
同一任务仅 1 条 ACTIVE route         PASS
```

因此，前端可以直接基于本文档开始真实联调，不需要再按旧原型中的假数据接口实现。

---

## 2. 与原多仓设计 Word 文档相比，当前真实实现必须以这里为准

原设计文档的核心“两阶段出库”思路保持不变：

```text
先选择 CargoType
→ 推荐 Warehouse
→ 再选具体 Cargo/cargoNo
→ 再选 Warehouse 内 Vehicle
→ 后端自动确定 Driver 和运输起点
→ 创建 TransportTask
```

但后端实现已经落地，部分早期“建议接口/状态名”已经发生变化。前端必须使用当前真实合同。

### 2.1 三个 Cargo 标识必须严格区分

| 字段 | 当前真实含义 | 前端用途 |
|---|---|---|
| `cargoTypeId` | 货物种类 `CargoType.id` | 第一阶段智能选仓 |
| `cargoId` | 某一条具体 Cargo 数据库主键 | 最终创建运输任务 |
| `cargoNo` | 具体货物唯一业务编号 | 页面展示、识别、追踪 |

必须遵守：

```text
cargoTypeId != cargoId != cargoNo
```

尤其禁止把 `cargoId` 当成货物种类 ID。

正确示例：

```text
货物种类：
cargoTypeId = 10
name = 苹果

具体货物：
cargoId = 368
cargoNo = CG-008
```

### 2.2 Cargo 状态不要使用 Word 初稿中的 IN_STOCK

当前真实后端 `CargoStatus` 为：

```text
WAITING
TRANSPORTING
COMPLETED
ABNORMAL
```

当前多仓出库可用 Cargo 条件之一是：

```text
status = WAITING
```

不要在前端发送或判断：

```text
IN_STOCK
OUTBOUND
IN_TRANSIT
DELIVERED
```

这些不是当前 Java 业务状态机的权威值。

### 2.3 创建多仓任务必须使用新接口

新前端必须调用：

```http
POST /api/v1/transport-tasks/from-warehouse
```

不要调用旧入口：

```http
POST /api/v1/transport-tasks
```

旧接口为了兼容历史调用仍然保留，但它允许客户端提供完整起点，不属于新的多仓两阶段流程。

### 2.4 TransportTask 不持久化 cargoTypeId / driverId

当前模型中：

```text
TransportTask.cargoId
    ↓
Cargo.cargoTypeId
```

CargoType 的 source of truth 在 Cargo。

同时：

```text
TransportTask.vehicleId
    ↓
Vehicle.driverId
```

`driverId` 由 Vehicle 派生。

因此前端创建多仓任务时：

- 要发送 `cargoTypeId`，用于严格一致性校验；
- 要发送 `cargoId`，确定具体 Cargo；
- 要发送 `vehicleId`；
- **不要发送 `driverId`**；
- **不要发送任何 `start*` 字段**。

---

## 3. 前端最终业务流程

建议出库页面按下面顺序实现：

```text
① 选择 / 绑定货主 ownerId

② 选择货物种类 cargoTypeId

③ 填写运输终点
   endLocation
   endLongitude
   endLatitude

④ 请求起点仓推荐
   POST /transport-tasks/origin-recommendation

⑤ 管理员确认推荐 Warehouse
   也允许人工选择其他候选 Warehouse

⑥ 根据 ownerId + cargoTypeId + warehouseId
   查询该仓具体可用 Cargo

⑦ 管理员按 cargoNo 选择具体 Cargo
   前端保存对应 cargoId

⑧ 根据 warehouseId
   查询该仓可用 Vehicle

⑨ 管理员选择 vehicleId
   页面可展示 Vehicle.driverName / driverId
   但创建 Task 时不发送 driverId

⑩ 页面展示自动运输起点
   使用已选 Warehouse 的 address/lng/lat
   只展示，不允许编辑

⑪ 选择计划开始/结束时间（可选）

⑫ POST /transport-tasks/from-warehouse

⑬ 创建成功后跳转 Task 详情
```

---

## 4. 登录与权限

### 4.1 登录接口

```http
POST /api/v1/auth/login
Content-Type: application/json
```

请求：

```json
{
  "username": "xxx",
  "password": "xxx"
}
```

登录成功后 JWT 位于：

```text
data.accessToken
```

后续请求：

```http
Authorization: Bearer <accessToken>
```

### 4.2 多仓主流程必须使用 WAREHOUSE_MANAGER

多仓出库核心接口面向：

```text
WAREHOUSE_MANAGER
```

实际联调中使用 `DISPATCHER` 调 `/cargos/available` 会返回：

```http
403 Forbidden
```

这是正常 RBAC 行为，不是接口故障。

前端建议：

- 仅 `WAREHOUSE_MANAGER` 显示“多仓出库 / 智能选仓”入口；
- 不要为了绕过 403 修改前端角色判断或后端权限；
- token 失效返回 401 时走现有重新登录逻辑。

---

## 5. 通用响应结构

当前 REST API 使用统一结构：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

列表接口有两种。

分页：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [],
    "total": 0,
    "page": 1,
    "pageSize": 10
  }
}
```

非分页：

```json
{
  "code": 200,
  "message": "success",
  "data": []
}
```

前端不要假设所有列表都在 `data.records`。

---

## 6. API 一览

| 功能 | Method | Endpoint | 多仓页面用途 |
|---|---|---|---|
| 登录 | POST | `/auth/login` | 获取 JWT |
| 查询 CargoType | GET | `/cargo-types` | 选择货物种类 |
| 新增 CargoType | POST | `/cargo-types` | 可选的“新增货物种类” |
| 查询 Warehouse | GET | `/warehouses` | 仓库信息 / 起点展示 |
| Warehouse 详情 | GET | `/warehouses/{id}` | 单仓详情 |
| 查询 Cargo | GET | `/cargos` | 普通货物页面 |
| 查询可用 Cargo | GET | `/cargos/available` | **仓库确认后的具体货物选择** |
| 查询可用 Vehicle | GET | `/vehicles/available` | **仓库确认后的车辆选择** |
| 推荐发货仓 | POST | `/transport-tasks/origin-recommendation` | **核心智能选仓接口** |
| 创建多仓 Task | POST | `/transport-tasks/from-warehouse` | **核心创建接口** |
| Task 详情 | GET | `/transport-tasks/{id}` | 创建成功后的详情展示 |
| 当前计划路线 | GET | `/transport-tasks/{id}/planned-route` | Task 创建后查看 v1 ACTIVE |

所有路径均基于 `/api/v1`。

---

## 7. CargoType 接口

### 7.1 查询货物种类

```http
GET /api/v1/cargo-types?page=1&pageSize=20&keyword=
Authorization: Bearer <token>
```

真实返回结构示例：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [
      {
        "id": 1,
        "name": "TEST-TYPE-001",
        "unit": "box",
        "unitWeight": null,
        "unitVolume": null,
        "description": "Multi-warehouse E2E test cargo type",
        "createdAt": "2026-08-30T15:33:16+08:00",
        "updatedAt": "2026-08-30T15:33:16+08:00"
      }
    ],
    "total": 1,
    "page": 1,
    "pageSize": 20
  }
}
```

前端下拉框建议：

```text
label = name
value = id
```

不要显示让管理员手输 `cargoTypeId`。

### 7.2 新增 CargoType

后端已提供：

```http
POST /api/v1/cargo-types
```

适用于原 Word 方案里的“没有该货物种类 → 新增货物种类”。

建议表单业务字段：

```json
{
  "name": "苹果",
  "unit": "箱",
  "unitWeight": null,
  "unitVolume": null,
  "description": "..."
}
```

`id` 由数据库生成，前端不要提交。

---

## 8. Warehouse 接口

### 8.1 查询仓库

```http
GET /api/v1/warehouses
Authorization: Bearer <token>
```

也支持分页/关键字参数：

```http
GET /api/v1/warehouses?page=1&pageSize=20&keyword=
```

响应字段包括：

```text
id
warehouseNo
name
address
longitude
latitude
contactName
contactPhone
status
createdAt
updatedAt
```

Warehouse 坐标为 `GCJ-02`，与 AMap 路线规划使用同一坐标系。

### 8.2 Warehouse 详情

```http
GET /api/v1/warehouses/{id}
```

### 8.3 当前没有 Warehouse 写接口

当前 MVP 只有 Warehouse 查询接口。

前端暂时不要实现：

```text
新增仓库
修改仓库
删除仓库
```

即当前不要调用：

```text
POST   /warehouses
PUT    /warehouses/{id}
DELETE /warehouses/{id}
```

仓库主数据当前由后端 / DB 控制。

---

## 9. Cargo 入库 / 编辑页面需要增加的字段

当前 Cargo 已支持：

```text
cargoTypeId
warehouseId
```

因此现有“新增货物 / 编辑货物”页面应在原字段基础上增加：

```text
货物种类 → CargoType 下拉选择 → cargoTypeId
入库仓库 → Warehouse 下拉选择 → warehouseId
```

不要提供 `cargoTypeId` / `warehouseId` 数字输入框。

页面显示名称，内部保存 ID。

Cargo 的 `cargoNo` 仍然是具体货物唯一业务编号，不变。

---

## 10. Vehicle 页面需要增加的字段

当前 Vehicle 已支持：

```text
warehouseId
```

其含义是车辆当前调度 / 归属仓库，不是车辆 GPS 实时位置。

车辆创建 / 编辑页面建议增加：

```text
归属仓库：[ Warehouse 下拉框 ]
```

多仓任务选择车辆时不要查询所有车，而要使用：

```http
GET /api/v1/vehicles/available?warehouseId={originWarehouseId}
```

---

## 11. 查询具体可用 Cargo

这是原 Word 方案“仓库确定后，再选择具体 cargoNo”的最终真实接口。

```http
GET /api/v1/cargos/available
    ?ownerId={ownerId}
    &cargoTypeId={cargoTypeId}
    &warehouseId={warehouseId}
```

示例：

```http
GET /api/v1/cargos/available?ownerId=8&cargoTypeId=1&warehouseId=1
```

真实响应示例：

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 11,
      "cargoNo": "TEST-MW-CARGO-001",
      "cargoTypeId": 1,
      "warehouseId": 1,
      "ownerId": 8,
      "ownerName": "mike",
      "status": "WAITING",
      "name": "Multi-Warehouse Test Cargo 001",
      "description": "Dedicated cargo for multi-warehouse E2E test",
      "weight": 10.00,
      "volume": 0.50
    }
  ]
}
```

当前 available Cargo 的真实条件：

```text
cargoTypeId 匹配
warehouseId 匹配
status = WAITING
ownerId 为空或与请求 ownerId 兼容
不存在 WAITING / TRANSPORTING 的活动 Task
```

因此 `WAITING != 一定可用`。

前端不能使用：

```http
GET /cargos?status=WAITING
```

来替代 `/cargos/available`。

---

## 12. 查询某仓可用 Vehicle

```http
GET /api/v1/vehicles/available?warehouseId={warehouseId}
```

当前 available Vehicle 的真实条件：

```text
warehouseId 匹配
status = IDLE
driverId != null
simCode 非空
不存在 WAITING / TRANSPORTING 的活动 Task
```

选择车辆后：

- 前端可展示响应中的 `driverId / driverName`；
- 最终创建任务请求中不要发送 `driverId`。

---

## 13. 智能推荐发货仓

### 13.1 Endpoint

```http
POST /api/v1/transport-tasks/origin-recommendation
Authorization: Bearer <token>
Content-Type: application/json
```

### 13.2 Request

当前真实请求：

```json
{
  "ownerId": 8,
  "cargoTypeId": 1,
  "endLocation": "重庆市某目的地",
  "endLongitude": 106.5743,
  "endLatitude": 29.5577
}
```

注意：早期 Word 示例没有 `ownerId`，**当前真实接口必须传 `ownerId`**。

推荐阶段不要传：

```text
cargoId
cargoNo
vehicleId
originWarehouseId
startLocation
startLongitude
startLatitude
```

### 13.3 后端候选仓筛选逻辑

```text
Warehouse.status = ACTIVE
        ↓
该仓存在符合 ownerId + cargoTypeId 的 available Cargo
        ↓
该仓存在 available Vehicle
        ↓
AMap: Warehouse GCJ02 → Destination GCJ02
        ↓
distanceMeters / durationSeconds
```

只有“有货 + 有车”的仓库才进入候选。

### 13.4 Response

当前真实响应：

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "warehouseId": 1,
      "warehouseNo": "TEST-WH-001",
      "warehouseName": "A",
      "warehouseAddress": "A",
      "longitude": 106.4618,
      "latitude": 29.552,
      "distanceMeters": 14159,
      "durationSeconds": 1458,
      "availableCargoCount": 1,
      "availableVehicleCount": 1,
      "recommended": true
    }
  ]
}
```

重要：**当前 `data` 本身就是候选数组。**

不要按旧 Word 示例解析成：

```json
{
  "recommendedWarehouseId": 1,
  "candidates": []
}
```

当前不存在这个外层结构。

前端可直接：

```js
const candidates = response.data
const recommended = candidates.find(x => x.recommended)
```

### 13.5 排序规则

后端已经排序，前端不要覆盖。

```text
1. durationSeconds ASC
2. distanceMeters ASC
3. warehouseId ASC
```

第一名 `recommended = true`，其余为 `false`。

前端仍允许管理员人工改选候选仓。

### 13.6 推荐不是资源预留

Recommendation：

```text
只读
不加锁
不 reservation
不修改 Cargo
不修改 Vehicle
```

因此可能出现：

```text
推荐成功
→ 用户停留
→ Cargo / Vehicle 被其他任务占用
→ 最终创建返回冲突
```

如果创建阶段返回 409，前端应该：

```text
提示“资源状态已变化，请重新选择”
→ 重新调用 origin-recommendation
→ 重新查询 cargos/available
→ 重新查询 vehicles/available
```

不要自动重复提交原请求。

---

## 14. 创建多仓运输任务

### 14.1 Endpoint

```http
POST /api/v1/transport-tasks/from-warehouse
Authorization: Bearer <token>
Content-Type: application/json
```

这是新多仓页面唯一应使用的任务创建入口。

### 14.2 Request

```json
{
  "ownerId": 8,
  "cargoTypeId": 1,
  "originWarehouseId": 1,
  "cargoId": 11,
  "vehicleId": 3,
  "endLocation": "Chongqing Test Destination",
  "endLongitude": 106.5743,
  "endLatitude": 29.5577,
  "planStartTime": "2026-08-31T00:10:56+08:00",
  "planEndTime": "2026-08-31T02:10:56+08:00"
}
```

| 字段 | 必填 | 来源 |
|---|---:|---|
| `ownerId` | 是 | 第一步选择货主 |
| `cargoTypeId` | 是 | CargoType 下拉 |
| `originWarehouseId` | 是 | 推荐仓 / 人工候选仓 |
| `cargoId` | 是 | 选中的具体 cargoNo 对应 id |
| `vehicleId` | 是 | 选中的该仓 Vehicle |
| `endLocation` | 是 | 目的地 |
| `endLongitude` | 是 | 地图选点 |
| `endLatitude` | 是 | 地图选点 |
| `planStartTime` | 否 | 计划开始时间 |
| `planEndTime` | 否 | 计划结束时间 |

时间建议使用带时区 ISO-8601：

```text
2026-09-01T09:00:00+08:00
```

### 14.3 前端绝对不要发送的字段

不要发送：

```text
driverId

startLocation
startLongitude
startLatitude

taskNo
status

routeId
routeVersion
routeStatus
```

后端派生：

```text
driverId = Vehicle.driverId

startLocation  = Warehouse.address
startLongitude = Warehouse.longitude
startLatitude  = Warehouse.latitude
```

### 14.4 后端创建阶段会重新校验

即使推荐通过，创建阶段仍会重新验证：

```text
Owner 存在
CargoType 存在
Warehouse 存在且 ACTIVE

Cargo:
- 存在
- status = WAITING
- cargoTypeId 匹配
- warehouseId == originWarehouseId
- ownerId 兼容
- 无 WAITING / TRANSPORTING 活动 Task

Vehicle:
- 存在
- status = IDLE
- warehouseId == originWarehouseId
- driverId 非空
- simCode 非空
- 无 WAITING / TRANSPORTING 活动 Task

Warehouse:
- 地址 / 坐标仍与路线规划快照一致
```

前端无法通过修改请求制造跨仓脏数据。

---

## 15. 创建成功后的真实状态

成功创建以后：

```text
TransportTask.status = WAITING
Cargo.status         = WAITING
Vehicle.status       = IDLE
```

不要因为 Task 已创建就在前端立即把 Cargo 或 Vehicle 显示成 `TRANSPORTING`。

同时后端会生成：

```text
routeVersion = 1
routeStatus  = ACTIVE
provider     = AMAP
coordinateSystem = GCJ02
```

当前 E2E 已验证同一新 Task 只有 1 条 ACTIVE route。

---

## 16. Task Response 中的关键展示字段

创建成功后建议至少展示：

```text
id / taskNo

originWarehouseId

cargoId

vehicleId
driverId / driverName
plateNumber

status

startLocation
startLongitude
startLatitude

endLocation
endLongitude
endLatitude

planStartTime
planEndTime
```

注意：Response 中的 `driverId` 由 Vehicle 派生，不是 `transport_task.driver_id` 数据库列。

---

## 17. 页面联动与清空规则

### 17.1 ownerId 改变

清空：

```text
推荐仓
originWarehouseId
cargoId
vehicleId
```

重新推荐。

### 17.2 cargoTypeId 改变

清空：

```text
推荐仓
originWarehouseId
cargoId
vehicleId
```

重新推荐。

### 17.3 目的地变化

只要任一变化：

```text
endLocation
endLongitude
endLatitude
```

旧推荐结果视为失效。

清空：

```text
originWarehouseId
cargoId
vehicleId
```

重新推荐。

### 17.4 originWarehouseId 改变

清空：

```text
cargoId
vehicleId
```

重新请求：

```http
GET /cargos/available?...&warehouseId=<newWarehouseId>
GET /vehicles/available?warehouseId=<newWarehouseId>
```

### 17.5 Cargo 改变

只更新 `cargoId / cargoNo`，不需要重新推荐仓。

### 17.6 Vehicle 改变

更新 `vehicleId`，司机展示同步更新为该 Vehicle 的 `driverId / driverName`。

最终创建请求仍只传 `vehicleId`。

---

## 18. 推荐的前端页面状态模型

```ts
interface MultiWarehouseTaskFormState {
  ownerId: number | null

  cargoTypeId: number | null

  endLocation: string
  endLongitude: number | null
  endLatitude: number | null

  candidates: OriginWarehouseCandidate[]
  originWarehouseId: number | null

  availableCargos: CargoOption[]
  cargoId: number | null

  availableVehicles: VehicleOption[]
  vehicleId: number | null

  planStartTime: string | null
  planEndTime: string | null
}
```

推荐候选：

```ts
interface OriginWarehouseCandidate {
  warehouseId: number
  warehouseNo: string
  warehouseName: string
  warehouseAddress: string
  longitude: number
  latitude: number
  distanceMeters: number
  durationSeconds: number
  availableCargoCount: number
  availableVehicleCount: number
  recommended: boolean
}
```

---

## 19. 页面展示建议

```text
办理货物出库

① 货主
[ mike ▼ ]

② 出库货物种类
[ TEST-TYPE-001 ▼ ]

③ 运输终点
[ 地址输入 ] [地图选点]

④ 推荐发货仓

★ Warehouse A
  预计：24 min
  距离：14.2 km
  可用货物：1
  可用车辆：1
  [推荐] [选择]

  Warehouse B
  ...
  [选择]

⑤ 具体出库货物
[ TEST-MW-CARGO-001 ▼ ]

⑥ 运输车辆
[ Vehicle / Plate ▼ ]

司机：xxx（自动展示）
运输起点：Warehouse A / 地址（自动展示，只读）

⑦ 计划时间
[开始时间] [结束时间]

[确认出库并创建运输任务]
```

`durationSeconds` 可格式化为分钟 / 小时，`distanceMeters` 可格式化为 km，但不要修改原始后端值。

---

## 20. 前端错误处理建议

### 401 Unauthorized

```text
未登录 / JWT 失效
```

处理：清理登录态并返回登录页。

### 403 Forbidden

```text
当前角色无权访问
```

多仓核心页面确认当前用户 `role = WAREHOUSE_MANAGER`。

不要把 403 当成接口不存在。

### 404 Not Found

资源已不存在 / ID 失效。

处理：提示并刷新当前列表。

### 409 Conflict

多仓流程最重要的并发错误。

可能包括：

```text
Cargo 已被其他 Task 占用
Vehicle 已被其他 Task 占用
Cargo / Vehicle 状态变化
Cargo 与 Warehouse 不匹配
Vehicle 与 Warehouse 不匹配
Warehouse 信息变化
owner 不兼容
```

处理：

```text
不要自动重复 POST
→ 提示“资源状态已变化，请重新选择”
→ 重新推荐
→ 重新拉 Cargo / Vehicle
```

### 503 Service Unavailable

推荐和创建 Task 都依赖 AMap。

前端提示：

```text
路线规划服务暂时不可用，请稍后重试
```

不要用前端直线距离结果冒充后端推荐结果。

---

## 21. 前端不要做的事情

1. 不要自己计算推荐仓；
2. 不要自己按直线距离覆盖 `recommended`；
3. 不要手输 `cargoTypeId`；
4. 不要手输 `warehouseId`；
5. 不要在推荐仓之前先锁定具体 cargoNo；
6. 创建 Task 时不要提交 `driverId`；
7. 创建 Task 时不要提交 `startLocation/startLongitude/startLatitude`；
8. 不要用普通 `WAITING` Cargo 列表代替 `/cargos/available`；
9. 不要用普通 Vehicle 列表代替 `/vehicles/available`；
10. 推荐成功后不要认为 Cargo / Vehicle 已被预留；
11. 遇到 409 不要自动重复原 POST；
12. 新多仓页面不要继续调用 legacy `POST /transport-tasks`；
13. 不要把 `warehouseId` 当成车辆实时 GPS 位置；
14. 当前版本不要实现 FIFO / 批次 / 保质期 / 跨仓调拨 / 拆单。

---

## 22. 当前 MVP 明确不做

保持原 Word 的轻量范围：

```text
库存数量 / reservation
FIFO
批次
保质期
仓库间自动调拨
一个任务跨仓拆单
多订单全局优化
自动选择具体 cargoNo
```

当前具体 `cargoNo` 仍由仓库管理员确认。

---

## 23. 当前测试环境可用数据

> 仅用于当前云端联调，不得在前端代码中硬编码。

```text
CargoType:
id = 1
name = TEST-TYPE-001

Warehouse:
id = 1
warehouseNo = TEST-WH-001
status = ACTIVE

id = 2
warehouseNo = TEST-WH-002
status = ACTIVE

测试 Cargo:
id = 11
cargoNo = TEST-MW-CARGO-001
cargoTypeId = 1
warehouseId = 1
ownerId = 8
status = WAITING

测试 Vehicle:
id = 3
warehouseId = 1
status = IDLE
已绑定 driver
已有 simCode
```

真实 E2E 已使用这套数据成功创建 Task。

前端不能写死：

```text
1 / 2 / 3 / 8 / 11
```

正式逻辑必须全部通过接口返回值驱动。

---

## 24. 推荐的前端联调顺序

### Gate 1：登录和权限

```text
WAREHOUSE_MANAGER 登录
→ token 正常
```

### Gate 2：基础数据

```text
GET /cargo-types
GET /warehouses
```

### Gate 3：资源筛选

```text
GET /cargos/available
GET /vehicles/available
```

检查错误 `warehouseId` 返回空，不发生跨仓串数据。

### Gate 4：智能选仓

```text
POST /origin-recommendation
```

检查：

```text
候选仓正确
recommended 正确
durationSeconds > 0
distanceMeters > 0
```

### Gate 5：创建任务

```text
POST /from-warehouse
```

检查：

```text
originWarehouseId
cargoId
vehicleId
driverId
start*
status
```

### Gate 6：任务详情 / 路线

```text
GET /transport-tasks/{id}
GET /transport-tasks/{id}/planned-route
```

检查：

```text
Task = WAITING
v1 = ACTIVE
```

---

## 25. 前端验收清单

### 正常场景

- [ ] CargoType 下拉正常
- [ ] Warehouse 数据正常
- [ ] 选择 owner + CargoType + destination 后可推荐仓库
- [ ] 推荐结果显示预计时间、距离、可用货物数、可用车辆数
- [ ] 推荐第一名正确标记
- [ ] 可以人工改选其他候选仓
- [ ] 改选仓库后 Cargo 和 Vehicle 自动刷新
- [ ] Cargo 下拉展示 cargoNo，但保存 cargoId
- [ ] Vehicle 只显示该仓 available 车辆
- [ ] 选 Vehicle 后自动展示司机
- [ ] 选 Warehouse 后自动展示运输起点
- [ ] `start*` 不可编辑
- [ ] 创建请求不包含 `driverId`
- [ ] 创建请求不包含 `start*`
- [ ] 创建成功后能进入 Task 详情
- [ ] Task 显示 `originWarehouseId` / 起点 / 车辆 / 司机
- [ ] planned-route 能看到 v1 ACTIVE

### 联动场景

- [ ] 修改 owner 后清空推荐仓 / Cargo / Vehicle
- [ ] 修改 cargoType 后清空推荐仓 / Cargo / Vehicle
- [ ] 修改目的地后旧推荐失效
- [ ] 修改 Warehouse 后 Cargo / Vehicle 重新拉取
- [ ] 不会把 Warehouse A 的 Cargo 配给 Warehouse B
- [ ] 不会把 Warehouse A 的 Vehicle 配给 Warehouse B

### 异常场景

- [ ] 非 WAREHOUSE_MANAGER 访问核心接口时正确处理 403
- [ ] token 失效正确处理 401
- [ ] 无可用仓库时能展示空状态
- [ ] 无可用 Cargo 时禁用提交
- [ ] 无可用 Vehicle 时禁用提交
- [ ] 409 时提示资源已变化并要求重新选择
- [ ] 503 时提示路线规划服务不可用

---

## 26. 最终前后端权威业务链

```text
WAREHOUSE_MANAGER
        ↓
选择 owner
        ↓
选择 CargoType（cargoTypeId）
        ↓
选择目的地（GCJ02）
        ↓
POST origin-recommendation
        ↓
ACTIVE Warehouse
        ↓
available Cargo + available Vehicle 过滤
        ↓
AMap 道路时间 / 距离
        ↓
推荐 Warehouse / 人工改选
        ↓
GET cargos/available
        ↓
选择具体 cargoNo（保存 cargoId）
        ↓
GET vehicles/available
        ↓
选择 vehicleId
        ↓
Driver 自动派生
Warehouse 起点自动派生
        ↓
POST transport-tasks/from-warehouse
        ↓
TransportTask WAITING
        ↓
v1 ACTIVE
        ↓
进入后续路线调度 / 运输执行链
```

---

## 27. 前端与后端责任边界

### 前端负责

```text
交互顺序
表单状态
调用接口
展示推荐
管理员确认
选择 cargo / vehicle
时间输入
错误提示
```

### 后端负责

```text
Cargo / Vehicle 真正 availability 判断
Warehouse ACTIVE 判断
owner 兼容
同仓关系
AMap 推荐
推荐排序
并发重校验
driver 派生
start* 派生
Task 创建
v1 ACTIVE 创建
数据一致性和事务
```

前端不需要复制后端资源判定规则，只需调用正确 API 并正确处理响应 / 冲突。

---

## 28. 当前联调结论

当前 Multi-Warehouse 后端已经达到：

```text
可供前端真实联调
```

状态。

前端实现的核心不是重新设计多仓流程，而是严格落地：

```text
CargoType
→ Recommendation
→ Warehouse
→ CargoNo / CargoId
→ Vehicle
→ From-Warehouse Task
```

所有新页面与 API 调用以本文档的“当前真实实现”为准；原 Word 文档中与本文档冲突的接口示例、状态名或 Response 结构，均以本文档为最终联调合同。
