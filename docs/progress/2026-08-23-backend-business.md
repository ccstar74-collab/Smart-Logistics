# 2026-08-23 后端业务开发进度

## 今日完成

- Vehicle V1 完成最终接口回归
- 完成 Vehicle Apifox Mock 响应示例整理
- Cargo V1 后端开发完成
- Cargo Entity / DTO / Mapper / Service / Controller 完成
- CargoStatus 统一为 WAITING / TRANSPORTING / COMPLETED / ABNORMAL
- Cargo 分页 / keyword / status 筛选完成
- Cargo Validation 完成
- Cargo 重复 cargoNo 和资源不存在处理完成
- Cargo 自动化测试完成
- Vehicle + Cargo 共 29 项自动化测试通过
- Cargo 真实 Spring Boot + MySQL + HTTP 集成测试全部通过
- Cargo 创建后 status 自动初始化为 WAITING
- User → Owner → Cargo 外键关系完成真实验证

### CargoItem V1

- 完成 CargoItem Entity
- 完成 CargoItem DTO
- 完成 CargoItem Mapper
- 完成 CargoItem Service
- 完成 CargoItem Validation
- 明确 Cargo 与 CargoItem 为 `1:N` 关系
- `Cargo.weight` / `Cargo.volume` 保持为整票运输总量
- CargoItem 不参与 Cargo / Vehicle / TransportTask 状态联动

### CargoItem REST API V1

完成接口：

```http
POST /api/v1/cargos/{cargoId}/items
GET  /api/v1/cargos/{cargoId}/items
GET  /api/v1/cargos/{cargoId}/items/{itemId}
```

- 完成 Cargo 存在校验
- 完成 CargoItem 资源归属校验
- Cargo 存在但无 Item 时返回空数组
- Item 不存在时返回 Not Found
- 跨 Cargo 查询 Item 时返回 Not Found
- 完成 itemName、quantity、weight、volume、数值精度、字段长度和 PathVariable Validation 验证

### CargoItem 测试与真实集成验收

- CargoItem 自动化测试共 38 项
- 全项目开发完成时基线为 67 项自动化测试，全部通过
- 已完成 Apifox + Spring Boot + MySQL 的 CargoItem REST API 真实集成验收
- 已验证 POST 实际写入 MySQL、GET 列表、GET 详情、空列表、Not Found、跨 Cargo 访问和 Validation
- 已验证 CargoItem 操作不会修改 Cargo 总重量、总体积和状态

## 当前进度

```text
Vehicle V1      ✅
Cargo V1        ✅
CargoItem V1    ✅
CargoItem REST API V1 ✅
TransportTask V1 ⏳ 并行开发中
```

## 下一步计划

- 等待 TransportTask V1 完成
- 合并 CargoItem / TransportTask
- 执行全项目回归测试
- 设计运输状态联动 V1
- 与前端同步 CargoItem API
- 后续进行共享开发服务器部署
