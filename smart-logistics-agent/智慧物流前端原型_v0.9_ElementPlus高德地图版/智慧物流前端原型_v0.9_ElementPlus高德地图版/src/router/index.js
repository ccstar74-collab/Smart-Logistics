import { createRouter, createWebHistory } from 'vue-router'
import Dashboard from '../views/Dashboard.vue'
import Vehicles from '../views/Vehicles.vue'
import Cargos from '../views/Cargos.vue'
import Tasks from '../views/Tasks.vue'
import Tracking from '../views/Tracking.vue'
import Alarms from '../views/Alarms.vue'
import Dispatch from '../views/Dispatch.vue'
import Agent from '../views/Agent.vue'

const routes = [
  { path: '/', redirect: '/dashboard' },
  { path: '/dashboard', component: Dashboard, meta: { title: '监控大屏' } },
  { path: '/vehicles', component: Vehicles, meta: { title: '车辆管理' } },
  { path: '/cargos', component: Cargos, meta: { title: '货物管理' } },
  { path: '/tasks', component: Tasks, meta: { title: '运输任务' } },
  { path: '/tracking', component: Tracking, meta: { title: '实时追踪' } },
  { path: '/alarms', component: Alarms, meta: { title: '告警中心' } },
  { path: '/dispatch', component: Dispatch, meta: { title: '调度指令' } },
  { path: '/agent', component: Agent, meta: { title: '智能问答' } }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

const roleAccess = {
  OWNER: ['/dashboard','/cargos','/tasks','/tracking','/alarms','/agent'],
  DRIVER: ['/tasks','/tracking','/dispatch','/cargos'],
  ADMIN: ['/dashboard','/vehicles','/cargos','/tasks','/tracking','/alarms','/dispatch','/agent']
}

router.beforeEach((to) => {
  const role = localStorage.getItem('demoRole') || 'ADMIN'
  const allowed = roleAccess[role] || roleAccess.ADMIN
  if (!allowed.includes(to.path)) {
    return role === 'DRIVER' ? '/tasks' : '/dashboard'
  }
})

export default router
