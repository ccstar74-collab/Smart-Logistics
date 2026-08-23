import { reactive, computed } from 'vue'
import data from '../mock/data.json'

const savedRole = localStorage.getItem('demoRole') || 'ADMIN'
const initialUser = data.users.find(u => u.role === savedRole) || data.users[2]

const state = reactive({
  currentUser: initialUser,
  showSwitcher: false
})

const roleMenus = {
  OWNER: [
    ['📊','运输概览','/dashboard'],
    ['📦','我的货物','/cargos'],
    ['📋','我的任务','/tasks'],
    ['📍','实时追踪','/tracking'],
    ['⚠','异常消息','/alarms'],
    ['💬','智能问答','/agent']
  ],
  DRIVER: [
    ['📋','我的运输任务','/tasks'],
    ['📍','实时追踪','/tracking'],
    ['➤','调度指令','/dispatch'],
    ['📦','货物状态','/cargos']
  ],
  ADMIN: [
    ['📊','监控大屏','/dashboard'],
    ['🚚','车辆管理','/vehicles'],
    ['📦','货物管理','/cargos'],
    ['📋','运输任务','/tasks'],
    ['📍','实时追踪','/tracking'],
    ['⚠','告警中心','/alarms'],
    ['➤','调度指令','/dispatch'],
    ['💬','智能问答','/agent']
  ]
}

export function useAuth() {
  const menus = computed(() => roleMenus[state.currentUser.role] || roleMenus.ADMIN)
  function switchUser(user) {
    state.currentUser = user
    localStorage.setItem('demoRole', user.role)
    state.showSwitcher = false
  }
  return { state, menus, users: data.users, switchUser }
}
