import test from 'node:test'
import assert from 'node:assert/strict'
import { filterRegisteredVehicles, filterTaskVehicles } from '../src/utils/realtimeScope.js'
import { isPlausibleGpsTransition } from '../src/composables/useVehicleWebSocketV2.js'

test('WebSocket 车辆仅保留任务关联车辆，并兼容 sim_code', () => {
  const realtime = [
    { vehicle_id: '16', sim_code: 'sim_001' },
    { vehicle_id: '17', sim_code: 'sim_002' },
    { vehicle_id: 'sim_003', sim_code: 'sim_003' },
  ]
  const tasks = [{ vehicleId: 16 }, { vehicleId: 18 }]
  const vehicles = [{ id: 16, simCode: 'sim_001' }, { id: 18, simCode: 'sim_003' }]
  assert.deepEqual(filterTaskVehicles(realtime, tasks, vehicles).map((item) => item.sim_code), ['sim_001', 'sim_003'])
})

test('仓库车辆范围保留已登记车辆，不要求已关联任务', () => {
  const realtime = [{ vehicle_id: '16' }, { vehicle_id: '17' }, { vehicle_id: 'sim_003', sim_code: 'sim_003' }]
  const registered = [{ id: 16 }, { id: 18, simCode: 'sim_003' }]
  assert.deepEqual(filterRegisteredVehicles(realtime, registered).map((item) => item.vehicle_id), ['16', 'sim_003'])
})

test('实时定位拒绝同一设备短时间跨城或跨区跳点', () => {
  const previous = { timestampMs: 100000, gps: { lon: 106.4489, lat: 29.5656 } }
  const nearby = { timestampMs: 101000, gps: { lon: 106.4490, lat: 29.5657 } }
  const teleport = { timestampMs: 101200, gps: { lon: 106.2048, lat: 29.5774 } }
  assert.equal(isPlausibleGpsTransition(previous, nearby), true)
  assert.equal(isPlausibleGpsTransition(previous, teleport), false)
})

test('实时定位拒绝晚到的旧时间戳', () => {
  const previous = { timestampMs: 200000, gps: { lon: 106.44, lat: 29.56 } }
  const stale = { timestampMs: 199000, gps: { lon: 106.4401, lat: 29.5601 } }
  assert.equal(isPlausibleGpsTransition(previous, stale), false)
})
