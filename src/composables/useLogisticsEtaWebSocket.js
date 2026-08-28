import { onBeforeUnmount, ref } from 'vue'

const DEFAULT_URL = 'ws://111.170.148.177:58080/ws/logistics'

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
  let attempts = 0
  let manuallyClosed = false

  function scheduleReconnect() {
    if (manuallyClosed || reconnectTimer) return
    status.value = 'reconnecting'
    const delay = Math.min(15000, 1000 * (2 ** attempts++))
    reconnectCount.value += 1
    reconnectTimer = window.setTimeout(() => {
      reconnectTimer = null
      connect()
    }, delay)
  }

  function connect() {
    if (socket && [WebSocket.OPEN, WebSocket.CONNECTING].includes(socket.readyState)) return
    const token = localStorage.getItem('accessToken') || import.meta.env.VITE_API_TOKEN || ''
    if (!token) {
      status.value = 'error'
      lastError.value = '缺少登录 Token，无法订阅 ETA'
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
        lastError.value = ''
      }
      socket.onmessage = (event) => {
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
    if (reconnectTimer) window.clearTimeout(reconnectTimer)
    reconnectTimer = null
    socket?.close()
    socket = null
    status.value = 'closed'
  }

  onBeforeUnmount(disconnect)
  return { status, reconnectCount, lastMessageAt, lastError, connect, disconnect }
}
