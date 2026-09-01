import { computed, reactive } from 'vue'
import { api, setAccessToken } from '../api/http'

export const roleLabelMap = {
  OWNER: '货主', DRIVER: '司机', WAREHOUSE_MANAGER: '仓库管理员', DISPATCHER: '调度员', ADMIN: '系统管理员',
}

const roleMenus = {
  OWNER: [['⌂', '概览', '/dashboard'], ['▣', '货物追踪', '/tracking'], ['◉', '轨迹回放', '/trajectory'], ['◷', '预计到达时间', '/eta'], ['△', '告警通知', '/alarms'], ['○', '智能问答', '/agent'], ['♙', '个人中心', '/profile']],
  DRIVER: [['▣', '我的任务', '/dashboard'], ['▤', '运输任务', '/tasks'], ['✓', '状态上报', '/status-report'], ['➤', '调度指令', '/dispatch'], ['○', '智能问答', '/agent'], ['♙', '个人中心', '/profile']],
  WAREHOUSE_MANAGER: [['⌂', '概览', '/dashboard'], ['▣', '车辆监控', '/tracking'], ['▣', '车辆管理', '/vehicles'], ['⌘', '货物出库', '/binding'], ['⇅', '货物信息录入', '/warehouse-io'], ['○', '智能问答', '/agent'], ['♙', '个人中心', '/profile']],
  DISPATCHER: [['⌂', '概览', '/dashboard'], ['▣', '车辆监控', '/tracking'], ['△', '告警管理', '/alarms'], ['➤', '调度指令', '/dispatch'], ['▥', '数据统计', '/stats'], ['○', '智能问答', '/agent'], ['♙', '个人中心', '/profile']],
  ADMIN: [['⌂', '概览', '/dashboard'], ['▣', '车辆监控', '/tracking'], ['♙', '用户管理', '/users'], ['▣', '车辆管理', '/vehicles'], ['▣', '货物管理', '/cargos'], ['△', '告警管理', '/alarms'], ['▤', '告警日志', '/alarm-logs'], ['⚙', '系统设置', '/settings'], ['♙', '个人中心', '/profile']],
}

function readSavedUser() {
  try {
    const user = JSON.parse(localStorage.getItem('currentUser') || 'null')
    return user?.role ? user : null
  } catch {
    return null
  }
}

const state = reactive({ currentUser: readSavedUser(), showSwitcher: false, isAuthenticated: Boolean(localStorage.getItem('accessToken')) })

function persistUser(user) {
  state.currentUser = user
  localStorage.setItem('currentUser', JSON.stringify(user))
}

export function useAuth() {
  const menus = computed(() => roleMenus[state.currentUser?.role] || [])
  const roleLabel = computed(() => roleLabelMap[state.currentUser?.role] || state.currentUser?.label || '用户')
  const isAuthenticated = computed(() => state.isAuthenticated)

  async function login({ username, password }) {
    const account = username?.trim()
    if (!account) throw new Error('请输入账号')
    if (!password) throw new Error('请输入密码')
    const credentials = { username: account, password }
    const result = await api.login(credentials)
    if (!result?.accessToken) throw new Error('登录响应中未包含 accessToken')
    setAccessToken(result.accessToken)
    // All REST and WebSocket endpoints share the JWT issued by port 58080.
    localStorage.removeItem('realtimeAccessToken')
    localStorage.removeItem('etaAccessToken')
    const user = await api.me()
    if (!roleMenus[user?.role]) {
      setAccessToken('')
      throw new Error('无法获取有效的当前用户角色')
    }
    persistUser({ ...user, label: roleLabelMap[user.role] })
    state.isAuthenticated = true
    return user
  }

  async function register({ name, username, password, role, confirmPassword: _confirmPassword, ...profile }) {
    if (!['OWNER', 'DRIVER', 'WAREHOUSE_MANAGER'].includes(role)) throw new Error('该身份不开放自行注册')
    if (!name?.trim() || !username?.trim() || !password) throw new Error('请完整填写注册信息')
    await api.register({ name: name.trim(), username: username.trim(), password, role, ...profile })
    return login({ username, password })
  }

  async function initializeSession() {
    if (!localStorage.getItem('accessToken')) return null
    try {
      const user = await api.me()
      if (!roleMenus[user?.role]) throw new Error('用户角色无效')
      persistUser({ ...user, label: roleLabelMap[user.role] })
      state.isAuthenticated = true
      return user
    } catch {
      logout()
      return null
    }
  }

  function logout() {
    state.currentUser = null
    state.isAuthenticated = false
    state.showSwitcher = false
    localStorage.removeItem('currentUser')
    localStorage.removeItem('isAuthenticated')
    setAccessToken('')
    localStorage.removeItem('realtimeAccessToken')
    localStorage.removeItem('etaAccessToken')
  }

  function updateCurrentUser(user) {
    if (!user) return
    persistUser({ ...state.currentUser, ...user, label: roleLabelMap[user.role ?? state.currentUser?.role] })
  }

  return { state, menus, login, register, initializeSession, updateCurrentUser, logout, isAuthenticated, roleLabel, roleLabelMap }
}
