import { onBeforeUnmount, ref } from 'vue'

const DEFAULT_WS_URL = 'ws://111.170.148.177:58083/ws/vehicle-locations'

function timestampMs(value) {
  const numeric = Number(value)
  if (Number.isFinite(numeric)) return numeric < 1e12 ? numeric * 1000 : numeric
  const parsed = Date.parse(value)
  return Number.isFinite(parsed) ? parsed : Date.now()
}

function distanceKm(a, b) {
  const toRadians = value => value * Math.PI / 180
  const dLat = toRadians(b.lat - a.lat), dLon = toRadians(b.lon - a.lon)
  const lat1 = toRadians(a.lat), lat2 = toRadians(b.lat)
  const value = Math.sin(dLat / 2) ** 2 + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) ** 2
  return 6371 * 2 * Math.atan2(Math.sqrt(value), Math.sqrt(1 - value))
}

export function isPlausibleGpsTransition(previous, next) {
  if (!previous) return true
  if (next.timestampMs <= previous.timestampMs) return false
  const elapsedMs = next.timestampMs - previous.timestampMs
  if (elapsedMs > 120000) return true
  const distance = distanceKm(previous.gps, next.gps)
  if (distance < 0.3) return true
  return distance / (elapsedMs / 3600000) <= 220
}

export function useVehicleWebSocket() {
  const status = ref('idle')
  const vehicles = ref([])
  const reconnectCount = ref(0)
  const lastMessageAt = ref('')
  const lastError = ref('')
  const nextReconnectIn = ref(0)
  const vehicleIndex = new Map()
  const locationIndex = new Map()
  let socket = null
  let reconnectTimer = null
  let attemptCount = 0
  let manuallyClosed = false

  function setVehicleDictionary(records = []) {
    vehicleIndex.clear()
    records.forEach((vehicle) => {
      const id = vehicle.id ?? vehicle.vehicleId ?? vehicle.vehicle_id
      const simCode = vehicle.simCode ?? vehicle.sim_code ?? vehicle.deviceCode ?? vehicle.device_code
      if (id != null) vehicleIndex.set(String(id), vehicle)
      if (simCode) vehicleIndex.set(String(simCode), vehicle)
    })
  }

  function acceptGps(message, source = 'websocket') {
    const gps = message?.gps ?? message?.data?.gps ?? message?.data ?? message
    if (Array.isArray(gps)) {
      gps.forEach(item => acceptGps(item, source))
      return
    }
    const longitude = Number(gps?.longitude ?? gps?.lon ?? gps?.lng)
    const latitude = Number(gps?.latitude ?? gps?.lat)
    if (!Number.isFinite(longitude) || !Number.isFinite(latitude) || Math.abs(longitude) > 180 || Math.abs(latitude) > 90 || (longitude === 0 && latitude === 0)) return
    const deviceKey = gps.vehicleId ?? gps.vehicle_id ?? gps.simCode ?? gps.sim_code ?? gps.deviceId ?? gps.device_id
    const registered = vehicleIndex.get(String(deviceKey))
    const vehicleId = registered?.id ?? registered?.vehicleId ?? registered?.vehicle_id ?? deviceKey
    if (vehicleId == null) return
    const sequence = Number(gps.sequence ?? gps.seq)
    const previous = locationIndex.get(String(vehicleId))
    if (previous && Number.isFinite(sequence) && Number.isFinite(previous.sequence) && sequence <= previous.sequence) return
    const candidate = {
      vehicle_id: String(vehicleId),
      task_id: gps.taskId ?? gps.task_id,
      sim_code: registered?.simCode ?? registered?.sim_code ?? deviceKey,
      online: gps.online ?? gps.isOnline ?? true,
      sequence,
      timestampMs: timestampMs(gps.timestamp ?? gps.collectedAt ?? gps.collectTime),
      source,
      gps: { lon: longitude, lat: latitude, speed_kmh: Number(gps.speed ?? gps.speed_kmh ?? 0), heading: Number(gps.direction ?? gps.heading ?? 0), timestamp: gps.timestamp ?? gps.collectedAt ?? gps.collectTime },
      display: { plate_number: registered?.plateNumber ?? registered?.plate_number ?? `设备 ${deviceKey}`, task_no: gps.taskNo ?? gps.task_no },
    }
    if (!isPlausibleGpsTransition(previous, candidate)) return
    locationIndex.set(String(vehicleId), candidate)
    vehicles.value = [...locationIndex.values()]
  }

  function seedLocations(records = []) {
    records.forEach(item => acceptGps(item, 'rest'))
  }

  function scheduleReconnect() {
    if (manuallyClosed || reconnectTimer) return
    const delay = Math.min(15000, 1000 * 2 ** attemptCount)
    attemptCount += 1
    reconnectCount.value += 1
    nextReconnectIn.value = delay
    status.value = 'reconnecting'
    reconnectTimer = window.setTimeout(() => {
      reconnectTimer = null
      nextReconnectIn.value = 0
      connect()
    }, delay)
  }

  function connect() {
    if (socket && [WebSocket.OPEN, WebSocket.CONNECTING].includes(socket.readyState)) return
    manuallyClosed = false
    status.value = 'connecting'
    try {
      socket = new WebSocket(import.meta.env.VITE_VEHICLE_WS_URL || DEFAULT_WS_URL)
      socket.onopen = () => {
        status.value = 'open'
        attemptCount = 0
        lastError.value = ''
      }
      socket.onmessage = (event) => {
        try {
          acceptGps(JSON.parse(event.data))
          lastMessageAt.value = new Date().toISOString()
          lastError.value = ''
        } catch (error) {
          lastError.value = `消息解析失败：${error.message}`
        }
      }
      socket.onerror = () => {
        status.value = 'error'
        lastError.value = 'WebSocket 网络连接发生错误'
      }
      socket.onclose = (event) => {
        socket = null
        if (!manuallyClosed) lastError.value = event.reason || `连接已断开（状态码 ${event.code}）`
        scheduleReconnect()
      }
    } catch (error) {
      lastError.value = error.message || 'WebSocket 建立失败'
      scheduleReconnect()
    }
  }

  function disconnect() {
    manuallyClosed = true
    if (reconnectTimer) window.clearTimeout(reconnectTimer)
    reconnectTimer = null
    socket?.close()
    socket = null
    status.value = 'closed'
  }

  onBeforeUnmount(disconnect)
  return { status, vehicles, reconnectCount, lastMessageAt, lastError, nextReconnectIn, setVehicleDictionary, seedLocations, connect, disconnect }
}
