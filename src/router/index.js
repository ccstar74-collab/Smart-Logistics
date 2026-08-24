import { createRouter, createWebHistory } from 'vue-router'
import Dashboard from '../views/Dashboard.vue'
import Vehicles from '../views/Vehicles.vue'
import Cargos from '../views/Cargos.vue'
import Tasks from '../views/Tasks.vue'
import Tracking from '../views/Tracking.vue'
import Alarms from '../views/Alarms.vue'
import Dispatch from '../views/Dispatch.vue'
import Agent from '../views/Agent.vue'
import Trajectory from '../views/Trajectory.vue'
import Eta from '../views/Eta.vue'
import StatusReport from '../views/StatusReport.vue'
import Notifications from '../views/Notifications.vue'
import Binding from '../views/Binding.vue'
import WarehouseIO from '../views/WarehouseIO.vue'
import BindingRecords from '../views/BindingRecords.vue'
import Stats from '../views/Stats.vue'
import Users from '../views/Users.vue'
import Roles from '../views/Roles.vue'
import AlarmLogs from '../views/AlarmLogs.vue'
import Settings from '../views/Settings.vue'
import Profile from '../views/Profile.vue'

const routes = [
  { path: '/', redirect: '/dashboard' },
  { path: '/dashboard', component: Dashboard },
  { path: '/vehicles', component: Vehicles },
  { path: '/cargos', component: Cargos },
  { path: '/tasks', component: Tasks },
  { path: '/tracking', component: Tracking },
  { path: '/trajectory', component: Trajectory },
  { path: '/eta', component: Eta },
  { path: '/alarms', component: Alarms },
  { path: '/dispatch', component: Dispatch },
  { path: '/agent', component: Agent },
  { path: '/status-report', component: StatusReport },
  { path: '/notifications', component: Notifications },
  { path: '/binding', component: Binding },
  { path: '/warehouse-io', component: WarehouseIO },
  { path: '/binding-records', component: BindingRecords },
  { path: '/stats', component: Stats },
  { path: '/users', component: Users },
  { path: '/roles', component: Roles },
  { path: '/alarm-logs', component: AlarmLogs },
  { path: '/settings', component: Settings },
  { path: '/profile', component: Profile }
]

const roleAccess = {
  OWNER: ['/dashboard','/tracking','/trajectory','/eta','/alarms','/agent','/cargos','/profile'],
  DRIVER: ['/dashboard','/tasks','/status-report','/dispatch','/notifications','/profile','/tracking'],
  WAREHOUSE: ['/dashboard','/vehicles','/binding','/warehouse-io','/binding-records','/tasks','/profile'],
  DISPATCHER: ['/dashboard','/tracking','/alarms','/dispatch','/stats','/notifications','/profile'],
  ADMIN: ['/dashboard','/users','/roles','/vehicles','/cargos','/alarms','/alarm-logs','/settings','/profile','/tracking']
}

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to) => {
  const role = localStorage.getItem('demoRole') || 'OWNER'
  const allowed = roleAccess[role] || roleAccess.OWNER
  if (!allowed.includes(to.path)) return '/dashboard'
})

export default router
