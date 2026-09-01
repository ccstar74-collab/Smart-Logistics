import { createRouter, createWebHistory } from 'vue-router'
import { useAuth } from '../stores/auth-session'

const Dashboard = () => import('../views/Dashboard.vue')
const Vehicles = () => import('../views/Vehicles.vue')
const Cargos = () => import('../views/Cargos.vue')
const Tasks = () => import('../views/Tasks.vue')
const Tracking = () => import('../views/TrackingV2.vue')
const Alarms = () => import('../views/Alarms.vue')
const Dispatch = () => import('../views/DispatchV2.vue')
const Agent = () => import('../views/Agent.vue')
const Trajectory = () => import('../views/Trajectory.vue')
const Eta = () => import('../views/Eta.vue')
const StatusReport = () => import('../views/StatusReport.vue')
const Notifications = () => import('../views/NotificationsV2.vue')
const Binding = () => import('../views/CargoOutbound.vue')
const WarehouseIO = () => import('../views/WarehouseIO.vue')
const Stats = () => import('../views/StatsV2.vue')
const Users = () => import('../views/Users.vue')
const AlarmLogs = () => import('../views/AlarmLogs.vue')
const Settings = () => import('../views/Settings.vue')
const Profile = () => import('../views/Profile.vue')
const Login = () => import('../views/Login.vue')
const Register = () => import('../views/Register.vue')
const NotFound = () => import('../views/NotFound.vue')

const { state: authState } = useAuth()

const routes = [
  { path: '/', redirect: '/dashboard' },
  { path: '/login', component: Login, meta: { public: true } },
  { path: '/register', component: Register, meta: { public: true } },
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
  { path: '/binding-records', redirect: '/binding' },
  { path: '/stats', component: Stats },
  { path: '/users', component: Users },
  { path: '/alarm-logs', component: AlarmLogs },
  { path: '/settings', component: Settings },
  { path: '/profile', component: Profile },
  { path: '/:pathMatch(.*)*', component: NotFound, meta: { unrestricted: true } }
]

const roleAccess = {
  OWNER: ['/dashboard','/tracking','/trajectory','/eta','/alarms','/agent','/notifications','/profile'],
  DRIVER: ['/dashboard','/tasks','/status-report','/dispatch','/agent','/notifications','/profile','/tracking'],
  WAREHOUSE_MANAGER: ['/dashboard','/tracking','/vehicles','/binding','/warehouse-io','/agent','/notifications','/profile'],
  DISPATCHER: ['/dashboard','/tracking','/alarms','/dispatch','/stats','/notifications','/agent','/profile'],
  ADMIN: ['/dashboard','/users','/vehicles','/cargos','/alarms','/alarm-logs','/settings','/notifications','/profile','/tracking']
}

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to) => {
  const isAuthenticated = Boolean(localStorage.getItem('accessToken'))

  if (to.meta.public) {
    if (isAuthenticated) return '/dashboard'
    return true
  }

  if (!isAuthenticated) return '/login'

  if (to.meta.unrestricted) return true

  const role = authState.currentUser?.role || ''
  const allowed = roleAccess[role]
  if (!allowed) return '/login'
  if (!allowed.includes(to.path)) return '/dashboard'
  return true
})

export default router
