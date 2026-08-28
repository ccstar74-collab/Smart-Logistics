export function isNonEmpty(value) {
  return String(value ?? '').trim().length > 0
}

export function isPositiveNumber(value) {
  const number = Number(value)
  return Number.isFinite(number) && number > 0
}

export function isLongitude(value) {
  const number = Number(value)
  return Number.isFinite(number) && number >= -180 && number <= 180
}

export function isLatitude(value) {
  const number = Number(value)
  return Number.isFinite(number) && number >= -90 && number <= 90
}

export function validateCoordinatePair(longitude, latitude, label = '坐标') {
  if (!isLongitude(longitude)) return `${label}经度必须在 -180 至 180 之间`
  if (!isLatitude(latitude)) return `${label}纬度必须在 -90 至 90 之间`
  return ''
}

export function validateTimeRange(range, { requireFuture = false, now = Date.now() } = {}) {
  if (!Array.isArray(range) || range.length !== 2) return '请选择完整的计划时间'
  const start = new Date(range[0]).getTime()
  const end = new Date(range[1]).getTime()
  if (!Number.isFinite(start) || !Number.isFinite(end)) return '计划时间格式不正确'
  if (requireFuture && start <= now) return '计划开始时间必须晚于当前时间'
  if (start >= end) return '计划结束时间必须晚于开始时间'
  return ''
}

export function validateShipmentForm(form) {
  if (!form?.ownerId) return '请选择货主'
  if (!form?.cargoId) return '请选择出库货物'
  if (!form?.vehicleId) return '请选择运输车辆'
  if (!isNonEmpty(form.startLocation)) return '请选择运输起点'
  if (!isNonEmpty(form.endLocation)) return '请选择运输终点'
  const startError = validateCoordinatePair(form.startLongitude, form.startLatitude, '起点')
  if (startError) return startError
  const endError = validateCoordinatePair(form.endLongitude, form.endLatitude, '终点')
  if (endError) return endError
  if (Number(form.startLongitude) === Number(form.endLongitude) && Number(form.startLatitude) === Number(form.endLatitude)) {
    return '起点和终点不能相同'
  }
  return validateTimeRange(form.planTime, { requireFuture: true })
}

export function validateCargoForm(form) {
  if (!isNonEmpty(form?.cargoNo)) return '请输入货物编号'
  if (!isNonEmpty(form?.name)) return '请输入货物名称'
  if (!isPositiveNumber(form?.weight)) return '货物重量必须大于 0'
  if (!isPositiveNumber(form?.volume)) return '货物体积必须大于 0'
  return ''
}
