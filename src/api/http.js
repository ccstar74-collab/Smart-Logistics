const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '/api/v1').replace(/\/$/, '')

function getToken() {
  return localStorage.getItem('access_token') || import.meta.env.VITE_API_TOKEN || ''
}

export function setAccessToken(token) {
  if (token) localStorage.setItem('access_token', token)
  else localStorage.removeItem('access_token')
}

export async function request(path, options = {}) {
  const token = getToken()
  const headers = new Headers(options.headers)
  headers.set('Accept', 'application/json')
  if (options.body !== undefined && !(options.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json')
  }
  if (token) headers.set('Authorization', `Bearer ${token}`)

  let response
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...options,
      headers,
      body: options.body === undefined || options.body instanceof FormData
        ? options.body
        : JSON.stringify(options.body)
    })
  } catch (error) {
    throw new Error(`无法连接 API，请检查服务地址、端口和 CORS/代理配置（${error.message}）`)
  }

  const type = response.headers.get('content-type') || ''
  const payload = type.includes('application/json') ? await response.json() : await response.text()
  if (!response.ok) {
    const detail = payload?.detail || payload?.message || payload?.error || payload
    if (response.status === 401) throw new Error('认证失败或登录已过期，请重新设置访问令牌')
    throw new Error(typeof detail === 'string' ? detail : `请求失败（HTTP ${response.status}）`)
  }
  return payload
}

export function extractList(payload) {
  if (Array.isArray(payload)) return payload
  return payload?.items || payload?.data?.items || payload?.data || payload?.results || payload?.list || []
}

export const api = {
  login: credentials => request('/auth/login', { method: 'POST', body: credentials }),
  me: () => request('/users/me'),
  vehicles: {
    list: params => request(`/vehicles${queryString(params)}`),
    get: id => request(`/vehicles/${id}`),
    create: body => request('/vehicles', { method: 'POST', body }),
    update: (id, body) => request(`/vehicles/${id}`, { method: 'PUT', body }),
    remove: id => request(`/vehicles/${id}`, { method: 'DELETE' })
  },
  cargos: {
    list: params => request(`/cargos${queryString(params)}`),
    get: id => request(`/cargos/${id}`),
    create: body => request('/cargos', { method: 'POST', body })
  }
}

function queryString(params = {}) {
  const query = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== '' && value !== undefined && value !== null) query.set(key, value)
  })
  const value = query.toString()
  return value ? `?${value}` : ''
}
