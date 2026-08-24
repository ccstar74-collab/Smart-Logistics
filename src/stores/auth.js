import { reactive, computed } from 'vue'
import data from '../mock/data.json'

const savedRole = localStorage.getItem('demoRole') || 'OWNER'
const initialUser = data.users.find(u => u.role === savedRole) || data.users[0]

const state = reactive({
  currentUser: initialUser,
  showSwitcher: false
})

const roleMenus = {
  OWNER: [
    ['⌂','概览','/dashboard'],
    ['⌕','货物追踪','/tracking'],
    ['⌖','运输轨迹','/trajectory'],
    ['◷','预计到达时间','/eta'],
    ['♧','告警通知','/alarms'],
    ['◌','智能问答','/agent'],
    ['▣','我的货物','/cargos'],
    ['♙','个人中心','/profile']
  ],
  DRIVER: [
    ['▣','我的任务','/dashboard'],
    ['▤','运输任务','/tasks'],
    ['✓','状态上报','/status-report'],
    ['➤','调度指令','/dispatch'],
    ['◌','消息中心','/notifications'],
    ['♙','个人中心','/profile']
  ],
  WAREHOUSE: [
    ['⌂','概览','/dashboard'],
    ['▣','车辆管理','/vehicles'],
    ['⛓','货物车辆绑定','/binding'],
    ['⇅','入库出库','/warehouse-io'],
    ['▤','绑定记录','/binding-records'],
    ['✓','任务分配','/tasks'],
    ['♙','个人中心','/profile']
  ],
  DISPATCHER: [
    ['⌂','概览','/dashboard'],
    ['▣','车辆监控','/tracking'],
    ['△','告警管理','/alarms'],
    ['➤','调度指令','/dispatch'],
    ['▥','数据统计','/stats'],
    ['◌','消息中心','/notifications'],
    ['♙','个人中心','/profile']
  ],
  ADMIN: [
    ['⌂','概览','/dashboard'],
    ['♙','用户管理','/users'],
    ['♙','角色管理','/roles'],
    ['▣','车辆管理','/vehicles'],
    ['▣','货物管理','/cargos'],
    ['△','告警管理','/alarms'],
    ['▤','告警日志','/alarm-logs'],
    ['⚙','系统设置','/settings'],
    ['♙','个人中心','/profile']
  ]
}

const roleLabelMap = {
  OWNER: '货主',
  DRIVER: '司机',
  WAREHOUSE: '仓库管理员',
  DISPATCHER: '调度员',
  ADMIN: '系统管理员'
}

export function useAuth() {
  const menus = computed(() => roleMenus[state.currentUser.role] || roleMenus.OWNER)
  const roleLabel = computed(() => roleLabelMap[state.currentUser.role] || state.currentUser.label)
  function switchUser(user) {
    state.currentUser = user
    localStorage.setItem('demoRole', user.role)
    state.showSwitcher = false
  }
  return { state, menus, users: data.users, switchUser, roleLabel, roleLabelMap }
}
