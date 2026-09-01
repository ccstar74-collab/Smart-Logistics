import { onBeforeUnmount, ref } from 'vue'

const DEFAULT_WS_URL = 'ws://111.170.148.177:58080/ws/alarms'
const HEARTBEAT_INTERVAL_MS = 25000
const HEARTBEAT_MISS_LIMIT = 3

function authenticatedUrl(base) {
  const token = localStorage.getItem('accessToken') || import.meta.env.VITE_API_TOKEN || ''
  if (!token) return ''
  return `${base}${base.includes('?') ? '&' : '?'}token=${encodeURIComponent(token)}`
}

export function useAlarmWebSocket(onAlarmEvent) {
  const status = ref('idle')
  const lastMessageAt = ref('')
  const lastError = ref('')
  const reconnectCount = ref(0)
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
        lastError.value = '连续 3 个心跳周期未收到 pong，正在重新连接'
        socket.close()
        return
      }
      socket.send('ping')
    }, HEARTBEAT_INTERVAL_MS)
  }

  function scheduleReconnect() {
    if (manuallyClosed || reconnectTimer) return
    status.value = 'reconnecting'
    reconnectCount.value += 1
    const delay = Math.min(30000, 1000 * 2 ** attempts++)
    reconnectTimer = window.setTimeout(() => {
      reconnectTimer = null
      connect()
    }, delay)
  }

  function connect() {
    if (socket && [WebSocket.OPEN, WebSocket.CONNECTING].includes(socket.readyState)) return
    const url = authenticatedUrl(import.meta.env.VITE_ALARM_WS_URL || DEFAULT_WS_URL)
    if (!url) {
      status.value = 'error'
      lastError.value = '缺少登录 Token，无法订阅告警事件'
      return
    }
    manuallyClosed = false
    status.value = 'connecting'
    try {
      socket = new WebSocket(url)
      socket.onopen = () => {
        status.value = 'open'
        attempts = 0
        missedPongs = 0
        lastError.value = ''
        socket.send('ping')
        startHeartbeat()
      }
      socket.onmessage = event => {
        const text = String(event.data).trim()
        lastMessageAt.value = new Date().toISOString()
        if (text.toLowerCase() === 'pong') { missedPongs = 0; return }
        try {
          onAlarmEvent?.(JSON.parse(text))
          lastError.value = ''
        } catch (error) {
          lastError.value = `告警消息解析失败：${error.message}`
        }
      }
      socket.onerror = () => {
        status.value = 'error'
        lastError.value = '告警 WebSocket 网络连接异常'
      }
      socket.onclose = event => {
        stopHeartbeat()
        socket = null
        if (!manuallyClosed) lastError.value = event.reason || `告警连接已断开（状态码 ${event.code}）`
        scheduleReconnect()
      }
    } catch (error) {
      lastError.value = error.message || '告警 WebSocket 建立失败'
      scheduleReconnect()
    }
  }

  function disconnect() {
    manuallyClosed = true
    if (reconnectTimer) window.clearTimeout(reconnectTimer)
    reconnectTimer = null
    stopHeartbeat()
    socket?.close()
    socket = null
    status.value = 'closed'
  }

  onBeforeUnmount(disconnect)
  return { status, lastMessageAt, lastError, reconnectCount, connect, disconnect }
}
