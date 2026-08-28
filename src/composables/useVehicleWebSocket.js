import { onBeforeUnmount, ref } from 'vue'

const DEFAULT_WS_URL = 'ws://111.170.148.177:58083/ws/vehicle-locations'

export function useVehicleWebSocket() {
  const status = ref('idle')
  const vehicles = ref([])
  const vehicleIndex = new Map()
  const locationIndex = new Map()
  let socket = null
  let reconnectTimer = null
  let reconnectAttempts = 0
  let manuallyClosed = false

  function setVehicleDictionary(records = []) {
    vehicleIndex.clear()
    records.forEach(vehicle => {
      const id = vehicle.id ?? vehicle.vehicleId ?? vehicle.vehicle_id
      const simCode = vehicle.simCode ?? vehicle.sim_code ?? vehicle.deviceCode ?? vehicle.device_code
      if (id != null) vehicleIndex.set(String(id), vehicle)
      if (simCode) vehicleIndex.set(String(simCode), vehicle)
    })
  }

  function acceptGps(raw) {
    const gps = raw?.gps ?? raw?.data?.gps ?? raw?.data ?? raw
    if (Array.isArray(gps)) return gps.forEach(acceptGps)
    const longitude = Number(gps?.longitude ?? gps?.lon ?? gps?.lng)
    const latitude = Number(gps?.latitude ?? gps?.lat)
    if (!Number.isFinite(longitude) || !Number.isFinite(latitude) || Math.abs(longitude) > 180 || Math.abs(latitude) > 90 || (longitude === 0 && latitude === 0)) return

    // 当前推送合同中的 vehicleId 可能实际为 sim_code；映射字段完成后无需修改这里。
    const deviceKey = gps.vehicleId ?? gps.vehicle_id ?? gps.simCode ?? gps.sim_code ?? gps.deviceId ?? gps.device_id
    const vehicle = vehicleIndex.get(String(deviceKey))
    const vehicleId = vehicle?.id ?? vehicle?.vehicleId ?? vehicle?.vehicle_id ?? deviceKey
    if (vehicleId == null) return
    const plateNumber = vehicle?.plateNumber ?? vehicle?.plate_number ?? vehicle?.licensePlate ?? vehicle?.license_plate ?? `设备 ${deviceKey}`
    const sequence = Number(gps.sequence ?? gps.seq)
    const previous = locationIndex.get(String(vehicleId))
    if (previous && Number.isFinite(sequence) && Number.isFinite(previous.sequence) && sequence <= previous.sequence) return

    locationIndex.set(String(vehicleId), {
      vehicle_id: String(vehicleId),
      task_id: gps.taskId ?? gps.task_id,
      sim_code: vehicle?.simCode ?? vehicle?.sim_code ?? deviceKey,
      online: gps.online ?? gps.isOnline ?? true,
      sequence,
      gps: {
        lon: longitude,
        lat: latitude,
        speed_kmh: Number(gps.speed ?? gps.speed_kmh ?? 0),
        heading: Number(gps.direction ?? gps.heading ?? 0),
        timestamp: gps.timestamp ?? gps.collectedAt ?? gps.collectTime
      },
      display: { plate_number: plateNumber, task_no: gps.taskNo ?? gps.task_no }
    })
    vehicles.value = [...locationIndex.values()]
  }

  function scheduleReconnect() {
    if (manuallyClosed || reconnectTimer) return
    const delay = Math.min(15000, 1000 * 2 ** reconnectAttempts++)
    status.value = 'reconnecting'
    reconnectTimer = window.setTimeout(() => { reconnectTimer = null; connect() }, delay)
  }

  function connect() {
    if (socket && [WebSocket.OPEN, WebSocket.CONNECTING].includes(socket.readyState)) return
    manuallyClosed = false
    status.value = 'connecting'
    try {
      socket = new WebSocket(import.meta.env.VITE_VEHICLE_WS_URL || DEFAULT_WS_URL)
      socket.onopen = () => { status.value = 'open'; reconnectAttempts = 0 }
      socket.onmessage = event => {
        try { acceptGps(JSON.parse(event.data)) }
        catch (error) { console.warn('忽略无法解析的车辆 GPS WebSocket 消息', error) }
      }
      socket.onerror = () => { status.value = 'error' }
      socket.onclose = () => { socket = null; scheduleReconnect() }
    } catch (error) {
      console.warn('车辆 GPS WebSocket 建立失败', error)
      scheduleReconnect()
    }
  }

  function disconnect() {
    manuallyClosed = true
    if (reconnectTimer) window.clearTimeout(reconnectTimer)
    reconnectTimer = null
    if (socket) socket.close()
    socket = null
    status.value = 'closed'
  }

  onBeforeUnmount(disconnect)
  return { status, vehicles, setVehicleDictionary, connect, disconnect }
}
