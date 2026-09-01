import { onBeforeUnmount, ref } from 'vue'

const DEFAULT_URL = 'ws://111.170.148.177:58080/ws/notifications'
const HEARTBEAT_INTERVAL_MS = 25000
const HEARTBEAT_MISS_LIMIT = 3
const MAX_RECONNECT_ATTEMPTS = 8

export function useNotificationWebSocket(onNotification, onReconnected) {
  const status = ref('idle')
  const lastError = ref('')
  let socket = null
  let reconnectTimer = null
  let heartbeatTimer = null
  let attempts = 0
  let missedPongs = 0
  let manuallyClosed = false
  let openedBefore = false

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
        lastError.value = '通知连接连续 3 个心跳周期未收到 pong'
        socket.close()
        return
      }
      socket.send('ping')
    }, HEARTBEAT_INTERVAL_MS)
  }

  function scheduleReconnect() {
    if (manuallyClosed || reconnectTimer) return
    if (attempts >= MAX_RECONNECT_ATTEMPTS) {
      status.value = 'error'
      lastError.value = '通知连接多次失败，已停止自动重连；请刷新登录状态后重试'
      return
    }
    status.value = 'reconnecting'
    const delay = Math.min(30000, 1000 * 2 ** attempts++)
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
      lastError.value = '缺少登录 JWT，请重新登录'
      return
    }
    manuallyClosed = false
    status.value = 'connecting'
    const base = import.meta.env.VITE_NOTIFICATION_WS_URL || DEFAULT_URL
    const url = `${base}${base.includes('?') ? '&' : '?'}token=${encodeURIComponent(token)}`
    try {
      socket = new WebSocket(url)
      socket.onopen = () => {
        const reconnected = openedBefore
        openedBefore = true
        status.value = 'open'
        lastError.value = ''
        attempts = 0
        missedPongs = 0
        startHeartbeat()
        if (reconnected) onReconnected?.()
      }
      socket.onmessage = event => {
        if (event.data === 'pong') {
          missedPongs = 0
          return
        }
        try {
          const message = JSON.parse(event.data)
          const eventName = String(message?.event || message?.type || '').toUpperCase()
          const notification = message?.notification ?? message?.data?.notification ?? message?.data
          if (eventName === 'NOTIFICATION_CREATED' && notification) onNotification?.(notification)
        } catch (error) {
          lastError.value = `通知消息解析失败：${error.message}`
        }
      }
      socket.onerror = () => {
        lastError.value = '通知 WebSocket 连接异常'
        socket?.close()
      }
      socket.onclose = event => {
        stopHeartbeat()
        socket = null
        if (!manuallyClosed) {
          status.value = 'reconnecting'
          lastError.value = event.reason || `通知连接已断开（${event.code}）`
          scheduleReconnect()
        }
      }
    } catch (error) {
      lastError.value = error.message || '通知 WebSocket 建立失败'
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
  return { status, lastError, connect, disconnect }
}
