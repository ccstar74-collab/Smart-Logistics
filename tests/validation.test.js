import test from 'node:test'
import assert from 'node:assert/strict'
import { isLatitude, isLongitude, validateCargoForm, validateShipmentForm, validateTimeRange } from '../src/utils/validation.js'

test('经纬度范围校验', () => {
  assert.equal(isLongitude(180), true)
  assert.equal(isLongitude(181), false)
  assert.equal(isLatitude(-90), true)
  assert.equal(isLatitude(-91), false)
})

test('重量和体积必须大于零', () => {
  assert.match(validateCargoForm({ cargoNo: 'C1', name: '测试', weight: 0, volume: 1 }), /重量/)
  assert.match(validateCargoForm({ cargoNo: 'C1', name: '测试', weight: 1, volume: -1 }), /体积/)
  assert.equal(validateCargoForm({ cargoNo: 'C1', name: '测试', weight: 1, volume: 1 }), '')
})

test('计划时间结束必须晚于开始', () => {
  assert.match(validateTimeRange(['2026-08-26T12:00:00', '2026-08-26T11:00:00']), /晚于/)
  assert.equal(validateTimeRange(['2026-08-26T11:00:00', '2026-08-26T12:00:00']), '')
})

test('创建运输任务时计划开始时间必须晚于当前时间', () => {
  const now = new Date('2026-08-27T10:00:00+08:00').getTime()
  assert.match(validateTimeRange(['2026-08-27T09:00:00+08:00', '2026-08-27T12:00:00+08:00'], { requireFuture: true, now }), /当前时间/)
  assert.equal(validateTimeRange(['2026-08-27T11:00:00+08:00', '2026-08-27T12:00:00+08:00'], { requireFuture: true, now }), '')
})

test('出库表单要求有效且不同的起终点', () => {
  const form = { ownerId: 1, cargoId: 2, vehicleId: 3, startLocation: 'A', startLongitude: 106.5, startLatitude: 29.5, endLocation: 'B', endLongitude: 106.6, endLatitude: 29.6, planTime: ['2099-08-26T11:00:00', '2099-08-26T12:00:00'] }
  assert.equal(validateShipmentForm(form), '')
  assert.match(validateShipmentForm({ ...form, endLongitude: 106.5, endLatitude: 29.5 }), /不能相同/)
})
