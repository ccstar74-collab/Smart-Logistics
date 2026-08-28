const AGENT_BASE_URL = (import.meta.env.VITE_AGENT_BASE_URL || '').replace(/\/$/, '')
const DEFAULT_TIMEOUT_MS = 90_000

async function requestJson(path, options = {}) {
  const controller = new AbortController()
  const timeout = window.setTimeout(() => controller.abort(), DEFAULT_TIMEOUT_MS)
  try {
    const response = await fetch(`${AGENT_BASE_URL}${path}`, {
      ...options,
      signal: controller.signal,
      headers: { Accept: 'application/json', ...(options.headers || {}) }
    })
    const text = await response.text()
    let data = {}
    if (text) {
      try { data = JSON.parse(text) }
      catch { throw new Error(`智能体返回了无法解析的数据（HTTP ${response.status}）`) }
    }
    if (!response.ok) throw new Error(data?.error?.message || `智能体请求失败（HTTP ${response.status}）`)
    return data
  } catch (error) {
    if (error?.name === 'AbortError') throw new Error('智能体响应超时，请稍后重试')
    throw error
  } finally {
    window.clearTimeout(timeout)
  }
}

export function getAgentHealth() {
  return requestJson('/health', { method: 'GET', cache: 'no-store' })
}

export function askAgent(question, sessionId) {
  const cleanQuestion = String(question || '').trim()
  if (!cleanQuestion) return Promise.reject(new Error('请输入问题'))
  const token = localStorage.getItem('accessToken') || ''
  return requestJson('/api/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json; charset=utf-8', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: JSON.stringify({ sessionId, question: cleanQuestion })
  })
}

export function getOrCreateAgentSessionId(reset = false) {
  const key = 'smartLogisticsAgentSessionId'
  if (reset) sessionStorage.removeItem(key)
  let sessionId = sessionStorage.getItem(key)
  if (!sessionId) {
    // randomUUID 仅在安全上下文（HTTPS/localhost）中保证可用。
    // 团队联调可能通过局域网 HTTP 地址访问，因此提供兼容回退。
    sessionId = globalThis.crypto?.randomUUID?.()
      || `frontend-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
    sessionStorage.setItem(key, sessionId)
  }
  return sessionId
}
