# 路线规划智能体测试场景

这组数据用于让智能体成员在后端接口尚未全部完成时独立开发和测试。车辆的 `deviceCode` 与 MQTT 模拟器的 `vehicle_id` 一一对应：`sim_000`～`sim_019`。

## 文件说明

- `planning_request.json`：智能体一次规划需要的完整输入，优先使用此文件。
- `vehicles_latest_api.json`：模拟 `GET /api/v1/vehicles/locations/latest` 的返回。
- `transport_tasks_api.json`：模拟待分配运输任务接口返回。
- `locations.json`：10 个规划节点，坐标系为 WGS84。
- `distance_matrix.json`：节点间道路距离和预计时间矩阵，仅供离线测试。
- `expected_checks.json`：数据数量、唯一性、容量可行性等验收条件。

## 建议的智能体输出

```json
{
  "requestId": "原样返回输入 requestId",
  "assignments": [
    {
      "vehicleId": 1,
      "deviceCode": "sim_000",
      "taskIds": [1001, 1008],
      "stopSequence": [
        {"taskId": 1001, "type": "PICKUP", "locationId": "LOC_001"},
        {"taskId": 1001, "type": "DELIVERY", "locationId": "LOC_006"}
      ],
      "estimatedDistanceMeters": 12000,
      "estimatedDurationSeconds": 1800
    }
  ],
  "unassignedTasks": [
    {"taskId": 1002, "reason": "TIME_WINDOW_CONFLICT"}
  ]
}
```

至少检查：每个任务最多分配一次、车辆载重和容积不超限、取货先于送达、时间窗不冲突。正式系统中，智能体应通过后端 API 获取这些数据，不直接读取 MQTT 或前端页面状态。

重新生成：

```powershell
python .\tools\generate_planning_scenario.py `
  --vehicles 20 `
  --tasks 30 `
  --seed 20260822 `
  --output-dir .\samples\planning_guoyuan_20v_30tasks `
  --overwrite
```
