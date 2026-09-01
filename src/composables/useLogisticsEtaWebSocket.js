import { onBeforeUnmount, ref } from 'vue'

const DEFAULT_URL = 'ws://111.170.148.177:58080/ws/logistics'
const HEARTBEAT_INTERVAL_MS = 25000
const HEARTBEAT_MISS_LIMIT = 3

export function mergeEtaUpdate(task, message) {
  if (!task || !message || message.type !== 'ETA_UPDATED' || Number(task.id) !== Number(message.taskId)) return task
  return {
    ...task,
    estimatedArrivalTime: message.estimatedArrivalTime ?? task.estimatedArrivalTime,
    etaCalculatedAt: message.etaCalculatedAt ?? task.etaCalculatedAt,
    remainingDistanceMeters: message.remainingDistanceMeters ?? task.remainingDistanceMeters,
    effectiveSpeedKmh: message.effectiveSpeedKmh ?? task.effectiveSpeedKmh,
  }
}

export function useLogisticsEtaWebSocket(onEtaUpdated) {
  const status = ref('idle')
  const reconnectCount = ref(0)
  const lastMessageAt = ref('')
  const lastError = ref('')
  let socket = null
  let reconnectTimer = null
  let heartbeatTimer = null
  let attempts = 0
  let missedPongs = 0
  let manuallyClosed = false

  function stopHeartbeat() {
    if (heartbeatTimer) window.clearInterval(heartbeatTimer)
    heartbeatTimer = null
  }

  function startHeartbeat() {
    stopHeartbeat()
    heartbeatTimer = window.setInterval(() => {
      if (socket?.readyState !== WebSocket.OPEN) return
      missedPongs += 1
      if (missedPongs >= HEARTBEAT_MISS_LIMIT) {
        lastError.value = 'ETA 连接连续 3 个心跳周期未收到 pong，正在重新连接'
        socket.close()
        return
      }
      socket.send('ping')
    }, HEARTBEAT_INTERVAL_MS)
  }

  function scheduleReconnect() {
    if (manuallyClosed || reconnectTimer) return
    status.value = 'reconnecting'
    const delay = Math.min(30000, 1000 * (2 ** attempts++))
    reconnectCount.value += 1
    reconnectTimer = window.setTimeout(() => {
      reconnectTimer = null
      connect()
    }, delay)
  }

  function connect() {
    if (socket && [WebSocket.OPEN, WebSocket.CONNECTING].includes(socket.readyState)) return
    // WebSocket 必须使用当前登录响应 data.accessToken，并通过 query 参数 token 传递。
    const token = localStorage.getItem('accessToken') || import.meta.env.VITE_API_TOKEN || ''
    if (!token) {
      status.value = 'error'
      lastError.value = '缺少登录 JWT，请重新登录后连接 ETA'
      return
    }
    manuallyClosed = false
    status.value = 'connecting'
    try {
      const base = import.meta.env.VITE_LOGISTICS_WS_URL || DEFAULT_URL
      const url = `${base}${base.includes('?') ? '&' : '?'}token=${encodeURIComponent(token)}`
      socket = new WebSocket(url)
      socket.onopen = () => {
        status.value = 'open'
        attempts = 0
        missedPongs = 0
        lastError.value = ''
        startHeartbeat()
      }
      socket.onmessage = (event) => {
        if (event.data === 'pong') {
          missedPongs = 0
          return
        }
        try {
          const message = JSON.parse(event.data)
          lastMessageAt.value = new Date().toISOString()
          if (message?.type === 'ETA_UPDATED' && message.taskId != null) onEtaUpdated?.(message)
        } catch (error) {
          lastError.value = `ETA 消息解析失败：${error.message}`
        }
      }
      socket.onerror = () => {
        status.value = 'error'
        lastError.value = 'ETA WebSocket 网络连接异常'
      }
      socket.onclose = (event) => {
        stopHeartbeat()
        socket = null
        if (!manuallyClosed) {
          lastError.value = event.reason || `ETA 连接已断开（状态码 ${event.code}）`
          scheduleReconnect()
        }
      }
    } catch (error) {
      lastError.value = error.message || 'ETA WebSocket 建立失败'
      scheduleReconnect()
    }
  }

  function disconnect() {
    manuallyClosed = true
    stopHeartbeat()
    if (reconnectTimer) window.clearTimeout(reconnectTimer)
    reconnectTimer = null
    socket?.close()
    socket = null
    status.value = 'closed'
  }

  onBeforeUnmount(disconnect)
  return { status, reconnectCount, lastMessageAt, lastError, connect, disconnect }
}
