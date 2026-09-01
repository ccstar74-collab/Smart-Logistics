import test from 'node:test'
import assert from 'node:assert/strict'
import { mergeEtaUpdate } from '../src/composables/useLogisticsEtaWebSocket.js'

test('ETA_UPDATED 只更新匹配任务且保留计划结束时间', () => {
  const task = { id: 12, planEndTime: '2026-09-01T00:00:00', estimatedArrivalTime: null }
  const update = {
    type: 'ETA_UPDATED', taskId: 12,
    estimatedArrivalTime: '2026-08-27T18:30:00', etaCalculatedAt: '2026-08-27T17:00:00',
    remainingDistanceMeters: 23500, effectiveSpeedKmh: 42,
  }
  assert.deepEqual(mergeEtaUpdate(task, update), {
    ...task,
    estimatedArrivalTime: update.estimatedArrivalTime,
    etaCalculatedAt: update.etaCalculatedAt,
    remainingDistanceMeters: 23500,
    effectiveSpeedKmh: 42,
  })
})

test('非 ETA 消息和其他任务消息不会污染任务', () => {
  const task = { id: 12, estimatedArrivalTime: null }
  assert.equal(mergeEtaUpdate(task, { type: 'GPS', taskId: 12 }), task)
  assert.equal(mergeEtaUpdate(task, { type: 'ETA_UPDATED', taskId: 13 }), task)
})
