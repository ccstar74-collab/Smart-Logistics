# 初始多路线决策部署与验收清单

## 1. 变更范围

本次新增：

- `016_initial_route_decision.sql`
- `initial_route_decision`、`initial_route_candidate` 两张表
- `POST/GET /api/v1/initial-route-decisions`
- `/transport-tasks/from-warehouse` 的决策确认字段和事务逻辑
- 固定 40/20/30/10 评分、规则解释降级、5 分钟有效期、幂等和并发保护
- 取消普通任务创建和 task-bound 初始候选生成的公开映射

未修改 `014_multi_warehouse_inventory.sql` 和
`015_route_traffic_snapshot.sql`。

## 2. 部署顺序

项目当前没有自动 Flyway/Liquibase，必须人工执行并核验 migration。

```text
备份数据库
→ 核验 014 多仓字段
→ 核验 015 traffic_snapshot
→ 执行 016_initial_route_decision.sql
→ SHOW CREATE TABLE 核验
→ 部署新 JAR/镜像
→ 健康和鉴权检查
→ 初始路线 E2E
```

不能先启动新代码再补 016，否则预规划接口会因表不存在返回 500。

## 3. 数据库核验

执行 016 前：

```sql
USE smart_logistics;

SHOW COLUMNS
FROM transport_task_route
LIKE 'traffic_snapshot';

SHOW COLUMNS
FROM transport_task
LIKE 'origin_warehouse_id';
```

执行：

```text
docs/sql/016_initial_route_decision.sql
```

执行后：

```sql
SHOW CREATE TABLE initial_route_decision\G
SHOW CREATE TABLE initial_route_candidate\G

SHOW INDEX FROM initial_route_decision;
SHOW INDEX FROM initial_route_candidate;
```

必须看到：

- 决策 ID、首次预规划 Key、确认 Key、taskId 唯一约束；
- `(decision_id, preview_route_id)` 和 `(decision_id, rank_no)` 唯一约束；
- 候选到决策的外键；
- 起终点、天气、交通、points、分项评分和理由快照字段。

不要用 DROP TABLE 作为常规回滚。若部署失败，保留 016 数据表，回滚应用镜像即可；
确认没有任何决策数据且经团队批准后再讨论 schema 回滚。

## 4. 应用配置

必需：

```text
AMAP_WEB_SERVICE_KEY=<服务端高德 Web Service Key>
INITIAL_ROUTE_DECISION_TTL=PT5M
```

智能体尚未接入时无需阻塞部署，系统会使用
`RULE_FALLBACK`。接入智能体后再由环境变量提供内部 URL、凭证和超时，凭证不能写入
Git、前端或本文档。

## 5. 构建和回归

在 `backend` 目录：

```powershell
mvn test
mvn -DskipTests package
```

本分支提交前基线结果：

```text
672 tests
0 failures
0 errors
0 skipped
```

## 6. 部署后冒烟测试

使用仓库管理员 JWT：

1. POST 预规划，确认返回 `PENDING`、2～3 条候选和 5 分钟 `expiresAt`；
2. 用 GET 查询同一 `decisionId`；
3. 使用相同首次 Key 重试 POST，确认返回相同 `decisionId`；
4. 用推荐或人工选中的 `routeId` 调用 `/from-warehouse`；
5. 使用相同确认 Key 重试，确认返回相同 `taskId`；
6. 查询正式路线，确认只有一条 `routeVersion=1,status=ACTIVE`；
7. 比较预览和正式路线的 points、距离、用时；
8. 确认未选候选没有写入 `transport_task_route`；
9. 等待决策过期后确认返回 HTTP 410 / code 41001；
10. 用调度员调用初始决策接口，确认返回 403；
11. 验证偏航 `replan-from-latest-location` 仍可直接生成新 ACTIVE。

数据库审计示例：

```sql
SELECT decision_id, status, recommended_route_id, selected_route_id,
       recommendation_source, expires_at, confirmed_at, task_id
FROM initial_route_decision
ORDER BY id DESC
LIMIT 10;

SELECT decision_id, preview_route_id, rank_no, total_score,
       distance_meters, duration_seconds, traffic_level
FROM initial_route_candidate
WHERE decision_id = '<本次 decisionId>'
ORDER BY rank_no;

SELECT task_id, route_id, route_version, status,
       distance_meters, reference_duration_seconds, activated_at
FROM transport_task_route
WHERE task_id = <本次 taskId>
ORDER BY route_version;
```

## 7. 发布注意事项

- 公网联调仍使用 `http://111.170.148.177:58080/api/v1`；
- 服务器内部端口由部署容器映射决定，前端不直接使用内部端口；
- 不要把测试账号、密码、JWT、高德 Key 或数据库密码写入 commit/PR；
- 前端切换到新流程后再发布，避免继续调用已取消的旧 POST 接口；
- 智能体接入属于独立小提交，不能修改业务后端正式评分和排名。
