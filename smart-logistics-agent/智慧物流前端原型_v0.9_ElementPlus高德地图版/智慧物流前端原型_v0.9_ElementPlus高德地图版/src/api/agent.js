const rawBaseUrl = (import.meta.env.VITE_AGENT_BASE_URL || '').trim()
const baseUrl = rawBaseUrl.replace(/\/$/, '')

async function request(path, options = {}) {
  const response = await fetch(`${baseUrl}${path}`, {
    ...options,
    headers: {
      Accept: 'application/json',
      ...(options.body ? { 'Content-Type': 'application/json; charset=utf-8' } : {}),
      ...(options.headers || {})
    }
  })

  const data = await response.json().catch(() => null)
  if (!response.ok) {
    const message = data?.error?.message || data?.message || `请求失败（HTTP ${response.status}）`
    const error = new Error(message)
    error.status = response.status
    error.payload = data
    throw error
  }
  return data
}

export function getAgentHealth() {
  return request('/health')
}

export function chatWithAgent(question, sessionId) {
  return request('/api/chat', {
    method: 'POST',
    body: JSON.stringify({ question, sessionId })
  })
}

export function getLatestVehicles() {
  return request('/api/v1/vehicles/locations/latest')
}
