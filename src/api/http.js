const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '/api/v1').replace(/\/$/, '')
const REALTIME_API_BASE_URL = (import.meta.env.VITE_REALTIME_API_BASE_URL || '/api').replace(/\/$/, '')
// 单个请求的最长等待时间，防止后端无响应时页面加载动画永远转圈
const REQUEST_TIMEOUT_MS = 15000

function getToken() {
  return localStorage.getItem('accessToken') || import.meta.env.VITE_API_TOKEN || ''
}

export function setAccessToken(token) {
  if (token) localStorage.setItem('accessToken', token)
  else localStorage.removeItem('accessToken')
}

// 只有“登录态校验”接口（GET /users/me）返回 401 才视为登录过期，强制登出；
// 其他业务接口的 401（例如后端数据权限拒绝）只报错，不清 Token、不踢回登录页。
function isSessionCheckRequest(path, method) {
  const normalized = String(path ?? '').split('?')[0].replace(/\/+$/, '')
  return normalized === '/users/me' && String(method || 'GET').toUpperCase() === 'GET'
}

export async function request(path, options = {}) {
  const { absolutePath = false, timeout = REQUEST_TIMEOUT_MS, ...fetchOptions } = options
  const token = getToken()
  const headers = new Headers(fetchOptions.headers)
  headers.set('Accept', 'application/json')
  if (options.body !== undefined && !(options.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json')
  }
  if (token && options.auth !== false) headers.set('Authorization', `Bearer ${token}`)

  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeout)
  let response
  try {
    response = await fetch(absolutePath ? path : `${API_BASE_URL}${path}`, {
      ...fetchOptions,
      headers,
      signal: controller.signal,
      body: options.body === undefined || options.body instanceof FormData
        ? options.body
        : JSON.stringify(options.body)
    })
  } catch (error) {
    if (error?.name === 'AbortError') {
      throw new Error(`请求超时（${Math.round(timeout / 1000)} 秒未响应），后端可能正在重启，请稍后刷新重试`)
    }
    throw new Error(`无法连接 API，请检查服务地址、端口和 CORS/代理配置（${error.message}）`)
  } finally {
    clearTimeout(timer)
  }

  const type = response.headers.get('content-type') || ''
  const payload = type.includes('application/json') ? await response.json() : await response.text()
  if (!response.ok) {
    const detail = payload?.detail || payload?.message || payload?.error || payload
    if (response.status === 401 && options.auth === false) throw new Error('账号或密码错误')
    if (response.status === 401) {
      if (isSessionCheckRequest(path, fetchOptions.method)) {
        setAccessToken('')
        localStorage.removeItem('isAuthenticated')
        localStorage.removeItem('currentUser')
        window.dispatchEvent(new CustomEvent('auth:unauthorized'))
        throw new Error('登录已过期或 Token 无效，请重新登录')
      }
      throw new Error('当前登录身份无权访问该数据（HTTP 401），请稍后重试或联系管理员')
    }
    if (response.status === 403) throw new Error('当前角色无权访问此功能（HTTP 403）')
    if (response.status === 404) {
      const endpoint = absolutePath ? path : `${API_BASE_URL}${path}`
      throw new Error(`接口不存在（HTTP 404）：${endpoint}。请确认后端服务已启动且 Vite API 代理配置正确。`)
    }
    throw new Error(typeof detail === 'string' ? detail : `请求失败（HTTP ${response.status}）`)
  }
  if (payload && typeof payload === 'object' && 'code' in payload) {
    if (payload.code !== 200) throw new Error(payload.message || `业务请求失败（code ${payload.code}）`)
    return payload.data
  }
  return payload
}

export function extractList(payload) {
  if (Array.isArray(payload)) return payload
  return payload?.records || payload?.items || payload?.data?.records || payload?.data?.items || payload?.data || payload?.results || payload?.list || []
}

const VALID_VEHICLE_STATUSES = ['IDLE', 'TRANSPORTING', 'MAINTENANCE', 'DISABLED']

function isInvalidVehicleStatusError(error) {
  return /invalid vehicle status in database/i.test(error?.message || '')
}

async function listVehicles(params = {}) {
  try {
    return await request(`/vehicles${queryString(params)}`)
  } catch (error) {
    // A legacy/dirty status in one database row makes the backend's unfiltered
    // enum conversion fail. Querying each supported status keeps valid cloud
    // records available until that row is migrated on the server.
    if (params.status || !isInvalidVehicleStatusError(error)) throw error

    const results = await Promise.allSettled(
      VALID_VEHICLE_STATUSES.map(status => request(`/vehicles${queryString({ ...params, status })}`))
    )
    const successful = results.filter(result => result.status === 'fulfilled')
    if (!successful.length) throw error

    const records = successful.flatMap(result => extractList(result.value))
    const uniqueRecords = [...new Map(records.map((vehicle, index) => [
      vehicle?.id ?? vehicle?.vehicleId ?? vehicle?.vehicle_id ?? `vehicle-${index}`,
      vehicle
    ])).values()]
    console.warn('车辆表包含后端无法识别的状态记录，已仅加载合法状态车辆；请清洗数据库中的异常 vehicle.status。')
    return { records: uniqueRecords, total: uniqueRecords.length, degraded: true }
  }
}

export const api = {
  login: credentials => request('/auth/login', { method: 'POST', body: credentials, auth: false }),
  register: body => request('/auth/register', { method: 'POST', body, auth: false }),
  me: () => request('/users/me'),
  users: {
    list: params => request(`/users${queryString(params)}`),
    get: id => request(`/users/${id}`),
    create: body => request('/users', { method: 'POST', body }),
    update: (id, body) => request(`/users/${id}`, { method: 'PUT', body }),
    updateStatus: (id, status) => request(`/users/${id}/status`, { method: 'PUT', body: { status } }),
    updateMe: body => request('/users/me', { method: 'PUT', body }),
    changePassword: body => request('/users/me/password', { method: 'PUT', body })
  },
  drivers: { options: () => request('/drivers/options') },
  owners: { options: () => request('/owners/options') },
  realtimeVehicles: {
    // 实时定位服务当前提供的车辆字典接口不带 /v1。
    list: () => request(`${REALTIME_API_BASE_URL}/vehicle/list`, { absolutePath: true })
  },
  vehicles: {
    list: listVehicles,
    get: id => request(`/vehicles/${id}`),
    latestLocations: () => request('/vehicles/locations/latest'),
    latestLocation: id => request(`/vehicles/${id}/location/latest`),
    // 批量最新位置接口故障（503/挂起）时的降级方案：
    // 逐辆调用单车位置接口，返回与批量接口一致的 { records, degraded } 结构。
    latestLocationsWithFallback: async (vehicleIds = []) => {
      try {
        return await request('/vehicles/locations/latest', { timeout: 6000 })
      } catch (bulkError) {
        const ids = Array.from(new Set((vehicleIds || []).map(id => Number(id)).filter(id => Number.isFinite(id))))
        if (!ids.length) throw bulkError
        const results = await Promise.allSettled(
          ids.map(id => request(`/vehicles/${id}/location/latest`).catch(() => null))
        )
        const records = results
          .filter(result => result.status === 'fulfilled' && result.value)
          .map(result => result.value)
        if (!records.length) throw bulkError
        return { records, degraded: true }
      }
    },
    locationHistory: (id, params) => request(`/vehicles/${id}/location-history${queryString(params)}`),
    create: body => request('/vehicles', { method: 'POST', body }),
    update: (id, body) => request(`/vehicles/${id}`, { method: 'PUT', body }),
    remove: id => request(`/vehicles/${id}`, { method: 'DELETE' }),
    bindDriver: (id, driverId) => request(`/vehicles/${id}/driver`, { method: 'PUT', body: { driverId } }),
    available: () => request('/vehicles/available')
  },
  cargos: {
    list: params => request(`/cargos${queryString(params)}`),
    get: id => request(`/cargos/${id}`),
    create: body => request('/cargos', { method: 'POST', body }),
    update: (id, body) => request(`/cargos/${id}`, { method: 'PUT', body }),
    available: () => request('/cargos/available'),
    items: {
      list: cargoId => request(`/cargos/${cargoId}/items`),
      get: (cargoId, itemId) => request(`/cargos/${cargoId}/items/${itemId}`),
      create: (cargoId, body) => request(`/cargos/${cargoId}/items`, { method: 'POST', body }),
      update: (cargoId, itemId, body) => request(`/cargos/${cargoId}/items/${itemId}`, { method: 'PUT', body }),
      remove: (cargoId, itemId) => request(`/cargos/${cargoId}/items/${itemId}`, { method: 'DELETE' })
    }
  },
  transportTasks: {
    list: params => request(`/transport-tasks${queryString(params)}`),
    get: id => request(`/transport-tasks/${id}`),
    create: body => request('/transport-tasks', { method: 'POST', body }),
    update: (id, body) => request(`/transport-tasks/${id}`, { method: 'PUT', body }),
    current: () => request('/transport-tasks/current'),
    updateStatus: (id, status) => request(`/transport-tasks/${id}/status`, { method: 'PUT', body: { status } }),
    track: (id, params) => request(`/tasks/${id}/track${queryString(params)}`),
    plannedRoute: id => request(`/transport-tasks/${id}/planned-route`),
    routes: {
      list: taskId => request(`/transport-tasks/${taskId}/routes`),
      create: (taskId, body) => request(`/transport-tasks/${taskId}/routes`, { method: 'POST', body }),
      activate: (taskId, routeId) => request(`/transport-tasks/${taskId}/routes/${routeId}/activate`, { method: 'PUT' })
    }
  },
  alarms: {
    list: params => request(`/alarms${queryString(params)}`),
    get: id => request(`/alarms/${id}`),
    updateStatus: (id, status, remark = '') => request(`/alarms/${id}/status`, { method: 'PUT', body: { status, remark } })
  },
  dispatchCommands: {
    list: params => request(`/dispatch-commands${queryString(params)}`),
    get: id => request(`/dispatch-commands/${id}`),
    // 司机收件箱：目标司机来自 JWT，不接受 driverId 参数
    mine: params => request(`/drivers/me/dispatch-commands${queryString(params)}`),
    create: body => request('/dispatch-commands', { method: 'POST', body }),
    // 第四阶段接口合同：PATCH 更新状态（SENT→ACKNOWLEDGED→EXECUTING→COMPLETED / REJECTED）
    updateStatus: (id, status, feedback = '') => request(`/dispatch-commands/${id}/status`, { method: 'PATCH', body: { status, feedback } })
  },
  notifications: {
    list: params => request(`/notifications${queryString(params)}`),
    unreadCount: () => request('/notifications/unread-count'),
    markRead: id => request(`/notifications/${id}/read`, { method: 'PUT' }),
    markAllRead: () => request('/notifications/read-all', { method: 'PUT' })
  },
  settings: {
    get: () => request('/settings'),
    update: body => request('/settings', { method: 'PUT', body })
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
