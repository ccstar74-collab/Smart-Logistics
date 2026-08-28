function valueKeys(value) {
  return value === null || value === undefined || value === '' ? [] : [String(value)]
}

export function taskVehicleKeys(tasks = [], vehicles = []) {
  const keys = new Set()
  tasks.forEach((task) => valueKeys(task?.vehicleId ?? task?.vehicle_id).forEach((key) => keys.add(key)))
  vehicles.forEach((vehicle) => {
    const id = String(vehicle?.id ?? vehicle?.vehicleId ?? vehicle?.vehicle_id ?? '')
    if (!id || !keys.has(id)) return
    valueKeys(vehicle?.simCode ?? vehicle?.sim_code).forEach((key) => keys.add(key))
  })
  return keys
}

export function filterTaskVehicles(realtimeVehicles = [], tasks = [], vehicles = []) {
  const keys = taskVehicleKeys(tasks, vehicles)
  return realtimeVehicles.filter((vehicle) => {
    const candidates = [vehicle?.vehicle_id, vehicle?.vehicleId, vehicle?.sim_code, vehicle?.simCode]
    return candidates.some((value) => valueKeys(value).some((key) => keys.has(key)))
  })
}

export function filterRegisteredVehicles(realtimeVehicles = [], vehicles = []) {
  const keys = new Set()
  vehicles.forEach((vehicle) => {
    ;[vehicle?.id, vehicle?.vehicleId, vehicle?.vehicle_id, vehicle?.simCode, vehicle?.sim_code]
      .flatMap(valueKeys)
      .forEach((key) => keys.add(key))
  })
  return realtimeVehicles.filter((vehicle) => [vehicle?.vehicle_id, vehicle?.vehicleId, vehicle?.sim_code, vehicle?.simCode]
    .some((value) => valueKeys(value).some((key) => keys.has(key))))
}
