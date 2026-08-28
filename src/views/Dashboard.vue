<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import AMapView from '../components/AMapView.vue'
import StatCard from '../components/StatCard.vue'
import UiIcon from '../components/UiIcon.vue'
import { useAuth } from '../stores/auth-session'
import { api, extractList } from '../api/http'
import { mergeEtaUpdate, useLogisticsEtaWebSocket } from '../composables/useLogisticsEtaWebSocket'
import { useVehicleWebSocket } from '../composables/useVehicleWebSocketV2'

const router = useRouter()
const { state } = useAuth()
const role = computed(() => state.currentUser.role)
const selectedVehicleId = ref('')
const latestLocations = ref([])
const latestLocationMap = computed(() => Object.fromEntries(latestLocations.value.map(location => [String(location.vehicleId ?? location.vehicle_id), location])))
const gpsWs = useVehicleWebSocket()
const gpsLocationMap = computed(() => {
  const result = Object.create(null)
  gpsWs.vehicles.value.forEach((location) => {
    if (location.vehicle_id != null) result[String(location.vehicle_id)] = location
    if (location.sim_code) result[String(location.sim_code)] = location
  })
  return result
})
const gpsConnectionText = computed(() => ({ open: '定位已连接', connecting: '定位连接中', reconnecting: '定位重连中', error: '定位异常' }[gpsWs.status.value] || '定位未连接'))
const routeList = payload => {
  const source = Array.isArray(payload) ? payload : payload?.points ?? payload?.path ?? payload?.routePoints ?? []
  return source.map(point => Array.isArray(point) ? point.map(Number) : [Number(point?.longitude ?? point?.lon ?? point?.lng), Number(point?.latitude ?? point?.lat)]).filter(point => point.every(Number.isFinite))
}
function mapVehicle(vehicle, task, fallbackPoint = null) {
  if (!vehicle) return null
  const id = vehicle.id ?? vehicle.vehicleId ?? vehicle.vehicle_id
  const simCode = vehicle.simCode ?? vehicle.sim_code ?? vehicle.deviceCode ?? vehicle.device_code
  const live = gpsLocationMap.value[String(id)] ?? (simCode ? gpsLocationMap.value[String(simCode)] : null)
  if (live?.gps && Number.isFinite(Number(live.gps.lon)) && Number.isFinite(Number(live.gps.lat))) {
    return {
      ...live,
      vehicle_id: String(id),
      task_id: task?.id ?? live.task_id,
      display: { ...live.display, plate_number: vehicle.plateNumber ?? vehicle.plate_number ?? live.display?.plate_number, task_no: task?.taskNo ?? live.display?.task_no }
    }
  }
  const location = latestLocationMap.value[String(id)]
  const lon = Number(location?.longitude ?? location?.lon)
  const lat = Number(location?.latitude ?? location?.lat)
  if (!Number.isFinite(lon) || !Number.isFinite(lat) || (lon === 0 && lat === 0)) {
    const [fallbackLon, fallbackLat] = Array.isArray(fallbackPoint) ? fallbackPoint.map(Number) : [NaN, NaN]
    if (Number.isFinite(fallbackLon) && Number.isFinite(fallbackLat) && !(fallbackLon === 0 && fallbackLat === 0)) {
      return {
        vehicle_id: String(id), task_id: task?.id, online: true,
        gps: { lon: fallbackLon, lat: fallbackLat, speed_kmh: 0, heading: 0, timestamp: null, coordinate_system: 'GCJ02' },
        display: { plate_number: vehicle.plateNumber ?? vehicle.plate_number, task_no: task?.taskNo }
      }
    }
    return null
  }
  return { vehicle_id: String(id), task_id: task?.id, online: location?.online ?? true, gps: { lon, lat, speed_kmh: Number(location?.speed ?? 0), heading: Number(location?.direction ?? 0), timestamp: location?.collectedAt }, display: { plate_number: location?.plateNumber ?? vehicle.plateNumber, task_no: task?.taskNo } }
}
const etaWs = useLogisticsEtaWebSocket((message) => {
  ownerTasks.value = ownerTasks.value.map(task => mergeEtaUpdate(task, message))
  warehouseTasks.value = warehouseTasks.value.map(task => mergeEtaUpdate(task, message))
  adminTasks.value = adminTasks.value.map(task => mergeEtaUpdate(task, message))
  dispatcherTasks.value = dispatcherTasks.value.map(task => mergeEtaUpdate(task, message))
  driverApiTask.value = mergeEtaUpdate(driverApiTask.value, message)
})
const etaConnectionText = computed(() => ({ open: '实时 ETA 已连接', connecting: 'ETA 连接中', reconnecting: 'ETA 重连中', error: 'ETA 异常' }[etaWs.status.value] || 'ETA 未连接'))
let realtimeTimer = null
const driverLoading = ref(false), driverApiTask = ref(null), driverVehicles = ref([]), driverCargos = ref([])
const driverTask = computed(() => driverApiTask.value)
const driverVehicleRecord = computed(() => driverVehicles.value.find(v => Number(v.id ?? v.vehicleId) === Number(driverTask.value?.vehicleId)) || null)
const driverCargoRecord = computed(() => driverCargos.value.find(c => Number(c.id ?? c.cargoId) === Number(driverTask.value?.cargoId)) || null)
const driverVehicle = computed(() => {
  return mapVehicle(driverVehicleRecord.value, driverTask.value)
})
const driverCommands = computed(() => [])
const driverRoute = ref([])
const warehouseLoading = ref(false)
const warehouseVehicles = ref([]), warehouseCargos = ref([]), warehouseAvailableCargos = ref([]), warehouseTasks = ref([])
const idleWarehouseVehicles = computed(() => warehouseVehicles.value.filter(v => v.status === 'IDLE'))
const waitingWarehouseCargos = computed(() => warehouseAvailableCargos.value.filter(c => c.status == null || c.status === 'WAITING'))
const warehouseCargoMap = computed(() => Object.fromEntries(warehouseCargos.value.map(c => [c.id, c])))
const warehouseVehicleMap = computed(() => Object.fromEntries(warehouseVehicles.value.map(v => [v.id, v])))
const transportingCargoCount = computed(() => warehouseCargos.value.filter(c => c.status === 'TRANSPORTING').length)
const warehouseTaskVehicleIds = computed(() => new Set(warehouseTasks.value.map(task => Number(task.vehicleId))))
const selectedWarehouseTaskId = ref(null)
const selectedWarehouseTask = computed(() => warehouseTasks.value.find(task => Number(task.id) === Number(selectedWarehouseTaskId.value)) || warehouseTasks.value.find(task => ['WAITING', 'TRANSPORTING'].includes(task.status)) || warehouseTasks.value[0] || null)
const warehouseMapVehicles = computed(() => warehouseVehicles.value.flatMap(vehicle => {
  if (!warehouseTaskVehicleIds.value.has(Number(vehicle.id))) return []
  const task = warehouseTasks.value.find(item => Number(item.vehicleId) === Number(vehicle.id))
  const mapped = mapVehicle(vehicle, task)
  if (mapped) return [mapped]
  const routeStart = warehouseRoutesMap[String(task?.id)]?.[0]
  const routeStartFallback = Array.isArray(routeStart) ? routeStart : null
  const taskStart = [Number(task?.startLongitude), Number(task?.startLatitude)]
  const taskStartFallback = taskStart.every(Number.isFinite) && !(taskStart[0] === 0 && taskStart[1] === 0) ? taskStart : null
  const fallback = mapVehicle(vehicle, task, routeStartFallback ?? taskStartFallback)
  return fallback ? [fallback] : []
}))
const warehouseMapSelectedId = computed(() => warehouseMapVehicles.value.some(vehicle => vehicle.vehicle_id === selectedVehicleId.value) ? selectedVehicleId.value : warehouseMapVehicles.value[0]?.vehicle_id || '')
const warehouseRoutesMap = reactive({})
const adminRoutesMap = reactive({})
const dispatcherRoutesMap = reactive({})

function taskRouteItems(tasks, routesMap) {
  return tasks.map(task => ({
    id: task.id,
    path: routesMap[String(task.id)] ?? [],
    startLabel: task.startLocation || '',
    endLabel: task.endLocation || ''
  })).filter(item => Array.isArray(item.path) && item.path.length >= 2)
}

async function loadAllTaskRoutes(tasks, routesMap) {
  const entries = await Promise.all(tasks.map(async task => {
    try { return [String(task.id), routeList(await api.transportTasks.plannedRoute(task.id))] }
    catch { return [String(task.id), []] }
  }))
  Object.keys(routesMap).forEach(key => { delete routesMap[key] })
  entries.forEach(([id, path]) => { routesMap[id] = path })
}

const warehouseTaskRoutes = computed(() => taskRouteItems(warehouseTasks.value, warehouseRoutesMap))
const adminTaskRoutes = computed(() => taskRouteItems(adminTasks.value, adminRoutesMap))
const dispatcherTaskRoutes = computed(() => taskRouteItems(dispatcherTasks.value, dispatcherRoutesMap))
const adminLoading = ref(false), adminUsers = ref([]), adminVehicles = ref([]), adminTasks = ref([]), adminAlarms = ref([])
const adminActiveTaskCount = computed(() => adminTasks.value.filter(t => ['WAITING','TRANSPORTING'].includes(t.status)).length)
const selectedAdminTaskId = ref(null)
const selectedAdminTask = computed(() => adminTasks.value.find(task => Number(task.id) === Number(selectedAdminTaskId.value)) || adminTasks.value.find(task => ['WAITING', 'TRANSPORTING'].includes(task.status)) || null)
const adminMapVehicles = computed(() => adminVehicles.value.flatMap(vehicle => { const mapped = mapVehicle(vehicle, adminTasks.value.find(task => Number(task.vehicleId) === Number(vehicle.id))); return mapped ? [mapped] : [] }))
const dispatcherLoading = ref(false), dispatcherVehicles = ref([]), dispatcherTasks = ref([]), dispatcherAlarms = ref([])
const dispatcherOnlineCount = computed(() => dispatcherVehicles.value.filter(v => !['DISABLED','OFFLINE'].includes(v.status)).length)
const selectedDispatcherTaskId = ref(null)
const selectedDispatcherTask = computed(() => dispatcherTasks.value.find(task => Number(task.id) === Number(selectedDispatcherTaskId.value)) || dispatcherTasks.value.find(task => ['WAITING', 'TRANSPORTING'].includes(task.status)) || null)
const dispatcherMapVehicles = computed(() => dispatcherVehicles.value.flatMap(vehicle => { const mapped = mapVehicle(vehicle, dispatcherTasks.value.find(task => Number(task.vehicleId) === Number(vehicle.id))); return mapped ? [{ ...mapped, has_active_alert: dispatcherAlarms.value.some(a => Number(a.vehicleId) === Number(vehicle.id) && a.status !== 'RESOLVED') }] : [] }))
const ownerLoading = ref(false), ownerCargos = ref([]), ownerTasks = ref([]), ownerVehicles = ref([]), selectedOwnerTaskId = ref(null)
const currentOwnerId = computed(() => Number(state.currentUser?.ownerId ?? state.currentUser?.id))
const myOwnerCargos = computed(() => ownerCargos.value.filter(c => Number(c.ownerId) === currentOwnerId.value))
const myCargoIds = computed(() => new Set(myOwnerCargos.value.map(c => Number(c.id))))
const myOwnerTasks = computed(() => ownerTasks.value.filter(t => myCargoIds.value.has(Number(t.cargoId))))
const selectedOwnerTask = computed(() => myOwnerTasks.value.find(t => t.id === selectedOwnerTaskId.value) || myOwnerTasks.value[0] || null)
const ownerVehicleMap = computed(() => Object.fromEntries(ownerVehicles.value.map(v => [v.id, v])))
const ownerCargoMap = computed(() => Object.fromEntries(myOwnerCargos.value.map(c => [c.id, c])))
const selectedOwnerVehicle = computed(() => selectedOwnerTask.value ? ownerVehicleMap.value[selectedOwnerTask.value.vehicleId] : null)
const ownerRoute = ref([])
const ownerPopupVisible = ref(false)
const ownerPopupPosition = reactive({ x: 0, y: 0 })
let ownerPopupTimer = null
function progressForTask(task) {
  if (!task) return 0
  if (task.status === 'COMPLETED') return 100
  if (task.status === 'WAITING') return 10
  const start = new Date(task.planStartTime).getTime(), end = new Date(task.planEndTime).getTime(), now = Date.now()
  if (Number.isFinite(start) && Number.isFinite(end) && end > start) return Math.max(15, Math.min(95, Math.round((now-start)/(end-start)*100)))
  return task.status === 'ABNORMAL' ? 60 : 50
}
const ownerMapVehicles = computed(() => {
  const mapped = mapVehicle(selectedOwnerVehicle.value, selectedOwnerTask.value)
  return mapped ? [{ ...mapped, has_active_alert: selectedOwnerTask.value?.status === 'ABNORMAL' }] : []
})
const ownerTaskProgress = computed(() => {
  return progressForTask(selectedOwnerTask.value)
})
const ownerStatusCount = status => myOwnerTasks.value.filter(t => t.status === status).length

const dashboardTitle = computed(() => ({
  OWNER:'运输概览', DRIVER:'我的任务', WAREHOUSE_MANAGER:'仓储作业',
  DISPATCHER:'调度总览', ADMIN:'系统总览'
}[role.value]))


function selectVehicle(v){ selectedVehicleId.value = v.vehicle_id }
function selectOwnerTask(taskId) { selectedOwnerTaskId.value = Number(taskId); ownerPopupVisible.value = true }
function hoverOwnerTask(payload) { clearTimeout(ownerPopupTimer); selectedOwnerTaskId.value = Number(payload.id); ownerPopupPosition.x = payload.x; ownerPopupPosition.y = payload.y; ownerPopupVisible.value = true }
function leaveOwnerTask() { clearTimeout(ownerPopupTimer); ownerPopupTimer = setTimeout(() => { ownerPopupVisible.value = false }, 80) }
function shortTime(iso){
  if(!iso) return '--'
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? '--' : d.toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit',hour12:false})
}
function applyLocationRecords(payload) {
  const extracted = extractList(payload)
  const records = extracted.length ? extracted : (payload && typeof payload === 'object' ? [payload] : [])
  latestLocations.value = records
  gpsWs.seedLocations(records)
}
function visibleVehicleRecords() {
  return ({
    OWNER: ownerVehicles.value,
    DRIVER: driverVehicles.value,
    WAREHOUSE_MANAGER: warehouseVehicles.value,
    DISPATCHER: dispatcherVehicles.value,
    ADMIN: adminVehicles.value
  })[role.value] || []
}
function syncGpsVehicleDictionary() {
  gpsWs.setVehicleDictionary(visibleVehicleRecords())
}
async function refreshPositionSnapshot() {
  const scopedTask = role.value === 'DRIVER' ? driverTask.value : role.value === 'OWNER' ? selectedOwnerTask.value : null
  if (scopedTask?.vehicleId != null) {
    applyLocationRecords(await api.vehicles.latestLocation(scopedTask.vehicleId))
    return
  }
  if (role.value === 'OWNER' || role.value === 'DRIVER') {
    applyLocationRecords([])
    return
  }
  applyLocationRecords(await api.vehicles.latestLocationsWithFallback(visibleVehicleRecords().map(v => v.id ?? v.vehicleId)))
}
async function loadWarehouseData(){
  if(role.value!=='WAREHOUSE_MANAGER') return
  warehouseLoading.value=true
  try{
    const [vehicleResult,cargoResult,availableResult,taskResult]=await Promise.all([api.vehicles.list({page:1,pageSize:100}),api.cargos.list({page:1,pageSize:100}),api.cargos.available().catch(()=>api.cargos.list({page:1,pageSize:100,status:'WAITING'})),api.transportTasks.list({page:1,pageSize:100})])
    warehouseVehicles.value=extractList(vehicleResult);warehouseCargos.value=extractList(cargoResult);warehouseAvailableCargos.value=extractList(availableResult);warehouseTasks.value=extractList(taskResult)
    api.vehicles.latestLocationsWithFallback(warehouseVehicles.value.map(v => v.id)).then(applyLocationRecords).catch(()=>{})
    loadAllTaskRoutes(warehouseTasks.value, warehouseRoutesMap).catch(()=>{})
    if(!warehouseTasks.value.some(task=>Number(task.id)===Number(selectedWarehouseTaskId.value))) selectedWarehouseTaskId.value=selectedWarehouseTask.value?.id??null
  }catch(error){ElMessage.error(`仓储数据加载失败：${error.message}`)}finally{warehouseLoading.value=false}
}
async function loadOwnerData(){
  if(role.value!=='OWNER') return
  ownerLoading.value=true
  try{
    const [cargoResult,taskResult,vehicleResult]=await Promise.all([api.cargos.list({page:1,pageSize:100}),api.transportTasks.list({page:1,pageSize:100}),api.vehicles.list({page:1,pageSize:100})])
    ownerCargos.value=extractList(cargoResult);ownerTasks.value=extractList(taskResult);ownerVehicles.value=extractList(vehicleResult)
    if(!myOwnerTasks.value.some(t=>t.id===selectedOwnerTaskId.value)) selectedOwnerTaskId.value=myOwnerTasks.value.find(t=>t.status==='TRANSPORTING')?.id??myOwnerTasks.value[0]?.id??null
  }catch(error){ElMessage.error(`货主运输数据加载失败：${error.message}`)}finally{ownerLoading.value=false}
}
async function loadAdminData(){
  if(role.value!=='ADMIN') return
  adminLoading.value=true
  try{
    const [ownersResult,driversResult,vehicleResult,taskResult,alarmResult]=await Promise.all([api.owners.options(),api.drivers.options(),api.vehicles.list({page:1,pageSize:100}),api.transportTasks.list({page:1,pageSize:100}),api.alarms.list({page:1,pageSize:100})])
    adminUsers.value=[...extractList(ownersResult).map(u=>({...u,role:'OWNER'})),...extractList(driversResult).map(u=>({...u,role:'DRIVER'})),state.currentUser]
    adminVehicles.value=extractList(vehicleResult);adminTasks.value=extractList(taskResult);adminAlarms.value=extractList(alarmResult)
    api.vehicles.latestLocationsWithFallback(adminVehicles.value.map(v => v.id)).then(applyLocationRecords).catch(()=>{})
    loadAllTaskRoutes(adminTasks.value, adminRoutesMap).catch(()=>{})
    if(!adminTasks.value.some(task=>Number(task.id)===Number(selectedAdminTaskId.value))) selectedAdminTaskId.value=selectedAdminTask.value?.id??null
  }catch(error){ElMessage.error(`系统概览数据加载失败：${error.message}`)}finally{adminLoading.value=false}
}
async function loadDriverData(){
  if(role.value!=='DRIVER') return
  driverLoading.value=true
  try{
    const [currentResult,vehicleResult,cargoResult]=await Promise.all([api.transportTasks.current().catch(()=>null),api.vehicles.list({page:1,pageSize:100}),api.cargos.list({page:1,pageSize:100})])
    driverApiTask.value=Array.isArray(currentResult)?currentResult[0]:(currentResult?.record??currentResult?.item??currentResult??null)
    driverVehicles.value=extractList(vehicleResult);driverCargos.value=extractList(cargoResult)
  }catch(error){ElMessage.error(`司机当前任务加载失败：${error.message}`)}finally{driverLoading.value=false}
}
async function loadDispatcherData(){
  if(role.value!=='DISPATCHER') return
  dispatcherLoading.value=true
  try{
    const [vehicleResult,taskResult,alarmResult]=await Promise.all([api.vehicles.list({page:1,pageSize:100}),api.transportTasks.list({page:1,pageSize:100}),api.alarms.list({page:1,pageSize:100})])
    dispatcherVehicles.value=extractList(vehicleResult);dispatcherTasks.value=extractList(taskResult);dispatcherAlarms.value=extractList(alarmResult)
    api.vehicles.latestLocationsWithFallback(dispatcherVehicles.value.map(v => v.id)).then(applyLocationRecords).catch(()=>{})
    loadAllTaskRoutes(dispatcherTasks.value, dispatcherRoutesMap).catch(()=>{})
    if(!dispatcherTasks.value.some(task=>Number(task.id)===Number(selectedDispatcherTaskId.value))) selectedDispatcherTaskId.value=selectedDispatcherTask.value?.id??null
  }catch(error){ElMessage.error(`调度概览加载失败：${error.message}`)}finally{dispatcherLoading.value=false}
}
function dateText(value){return value?String(value).replace('T',' ').slice(0,16):'—'}
async function loadRoute(task, target) {
  target.value = task?.id ? routeList(await api.transportTasks.plannedRoute(task.id)) : []
}
async function refreshSelectedTaskDetail(task, target) {
  if (!task?.id) return
  const detail = await api.transportTasks.get(task.id)
  if (target === driverApiTask) driverApiTask.value = { ...driverApiTask.value, ...detail }
  else target.value = target.value.map(item => Number(item.id) === Number(task.id) ? { ...item, ...detail } : item)
}
async function refreshRealtime() {
  await refreshPositionSnapshot()
  if (role.value === 'DRIVER') await refreshSelectedTaskDetail(driverTask.value, driverApiTask)
  else if (role.value === 'OWNER') await refreshSelectedTaskDetail(selectedOwnerTask.value, ownerTasks)
  else if (role.value === 'WAREHOUSE_MANAGER') await refreshSelectedTaskDetail(selectedWarehouseTask.value, warehouseTasks)
  else if (role.value === 'ADMIN') await refreshSelectedTaskDetail(selectedAdminTask.value, adminTasks)
}
watch(() => selectedOwnerTask.value?.id, () => { loadRoute(selectedOwnerTask.value, ownerRoute).catch(()=>{ownerRoute.value=[]}); refreshSelectedTaskDetail(selectedOwnerTask.value, ownerTasks).catch(()=>{}); if (role.value === 'OWNER') refreshPositionSnapshot().catch(()=>{}) }, { immediate: true })
watch(() => selectedWarehouseTask.value?.id, () => { refreshSelectedTaskDetail(selectedWarehouseTask.value, warehouseTasks).catch(()=>{}) }, { immediate: true })
watch(() => selectedAdminTask.value?.id, () => { refreshSelectedTaskDetail(selectedAdminTask.value, adminTasks).catch(()=>{}) }, { immediate: true })
watch(() => driverTask.value?.id, () => { loadRoute(driverTask.value, driverRoute).catch(()=>{driverRoute.value=[]}); refreshSelectedTaskDetail(driverTask.value, driverApiTask).catch(()=>{}); if (role.value === 'DRIVER') refreshPositionSnapshot().catch(()=>{}) }, { immediate: true })
onMounted(async()=>{
  await Promise.all([loadOwnerData(),loadWarehouseData(),loadAdminData(),loadDriverData(),loadDispatcherData()])
  syncGpsVehicleDictionary()
  try {
    const realtimeMapping = extractList(await api.realtimeVehicles.list())
    if (realtimeMapping.length) gpsWs.setVehicleDictionary([...visibleVehicleRecords(), ...realtimeMapping])
  } catch {}
  await refreshPositionSnapshot().catch(()=>{})
  gpsWs.connect()
  etaWs.connect()
  realtimeTimer=window.setInterval(()=>refreshRealtime().catch(()=>{}),5000)
})
onBeforeUnmount(()=>{if(realtimeTimer)window.clearInterval(realtimeTimer);gpsWs.disconnect();etaWs.disconnect()})
</script>

<template>
  <header class="topbar dashboard-heading">
    <div><h1>{{ dashboardTitle }}</h1></div>
  </header>

  <!-- 货主首页 -->
  <template v-if="role==='OWNER'">
    <section class="stats-grid" v-loading="ownerLoading">
      <StatCard label="在途订单" :value="ownerStatusCount('TRANSPORTING')" foot="当前货主运输任务" icon="truck" tone="blue" />
      <StatCard label="待运输" :value="ownerStatusCount('WAITING')" foot="等待车辆开始运输" icon="check" tone="green" />
      <StatCard label="告警中" :value="ownerStatusCount('ABNORMAL')" foot="当前异常运输任务" icon="alert" tone="red" alarm />
      <StatCard label="已完成" :value="ownerStatusCount('COMPLETED')" foot="当前货主历史任务" icon="package" tone="violet" />
    </section>
    <article class="panel home-map-panel owner-map-panel">
      <div class="panel-title"><div><h2>我的货物运输路线</h2><span>仅显示当前货主所选任务的路线与车辆实时位置</span></div><div class="role-live-status"><span :class="{connected:gpsWs.status.value==='open'}">{{gpsConnectionText}}</span><span :class="{connected:etaWs.status.value==='open'}">{{etaConnectionText}}</span><RouterLink class="link-btn" to="/tracking">查看追踪</RouterLink></div></div>
      <div class="owner-map-wrap">
        <AMapView :selectedVehicleId="selectedOwnerVehicle ? String(selectedOwnerVehicle.id) : ''" :selectedTaskId="selectedOwnerTaskId" :externalVehicles="ownerMapVehicles" :plannedRoute="ownerRoute" planned-route-coordinate-system="GCJ02" :route-start-label="selectedOwnerTask?.startLocation || ''" :route-end-label="selectedOwnerTask?.endLocation || ''" :showFacilities="false" :showTrack="false" focus-selected />
        <div v-if="ownerPopupVisible && selectedOwnerTask" class="owner-map-task-card owner-hover-task-card" :style="{ left: `${ownerPopupPosition.x + 18}px`, top: `${Math.max(12, ownerPopupPosition.y - 18)}px` }">
          <div class="order-row"><span>运单号</span><strong class="order-number">{{selectedOwnerTask.taskNo}}</strong></div><div><span>货物</span><strong>{{ownerCargoMap[selectedOwnerTask.cargoId]?.name||`#${selectedOwnerTask.cargoId}`}}</strong></div><div><span>状态</span><strong class="blue">{{selectedOwnerTask.status}}</strong></div><div><span>车辆</span><strong>{{selectedOwnerVehicle?.plateNumber||`#${selectedOwnerTask.vehicleId}`}}</strong></div><div><span>任务进度</span><strong class="blue">{{ownerTaskProgress}}%</strong></div><el-progress :percentage="ownerTaskProgress" :show-text="false" :stroke-width="8"/><small>{{ownerMapVehicles.length?'最新定位来自实时位置接口':'车辆暂无有效 GPS 定位'}}</small>
        </div>
      </div>
      <div v-if="!selectedOwnerTask" class="owner-location-notice">当前账号没有关联的运输任务。</div>
    </article>
    <section class="owner-dashboard-grid">
      <article class="panel role-table-card">
        <div class="panel-title"><div><h2>我的运输任务</h2><span>点击任务切换地图路线</span></div><RouterLink class="link-btn" to="/eta">查看全部</RouterLink></div>
        <div class="table-wrap"><table><thead><tr><th>运单号</th><th>目的地</th><th>ETA</th><th>状态</th></tr></thead><tbody>
          <tr v-for="t in myOwnerTasks" :key="t.id" :class="{selected:t.id===selectedOwnerTask?.id}" @click="selectOwnerTask(t.id)"><td>{{t.taskNo}}</td><td>{{t.endLocation}}</td><td>{{dateText(t.estimatedArrivalTime)}}</td><td><span class="task-status">{{t.status}}</span></td></tr><tr v-if="!myOwnerTasks.length"><td colspan="4">暂无本人运输任务</td></tr>
        </tbody></table></div>
      </article>
      <article class="panel role-table-card">
        <div class="panel-title"><div><h2>近期告警</h2><span>本人货物运输异常</span></div><RouterLink class="link-btn" to="/alarms">查看全部</RouterLink></div>
        <div class="table-wrap"><table><thead><tr><th>运单号</th><th>告警类型</th><th>车辆</th><th>状态</th></tr></thead><tbody><tr v-for="task in myOwnerTasks.filter(t=>t.status==='ABNORMAL')" :key="task.id"><td>{{task.taskNo}}</td><td><span class="owner-alarm-tag">运输异常</span></td><td>{{ownerVehicleMap[task.vehicleId]?.plateNumber||`#${task.vehicleId}`}}</td><td>{{task.status}}</td></tr><tr v-if="!myOwnerTasks.some(t=>t.status==='ABNORMAL')"><td colspan="4">暂无本人货物相关告警</td></tr></tbody></table></div>
      </article>
    </section>
  </template>

  <!-- 司机首页 -->
  <template v-else-if="role==='DRIVER'">
    <div class="driver-dashboard-layout">
    <section class="driver-top" v-loading="driverLoading">
      <article class="panel task-card">
        <div class="panel-title"><div><h2>当前任务</h2><span>来自 /transport-tasks/current</span></div><span v-if="driverTask" class="badge">{{driverTask.status}}</span></div>
        <dl v-if="driverTask">
          <div><dt>运单号</dt><dd>{{driverTask.taskNo}}</dd></div><div><dt>起点</dt><dd>{{driverTask.startLocation}}</dd></div>
          <div><dt>终点</dt><dd>{{driverTask.endLocation}}</dd></div><div><dt>货物</dt><dd>{{driverCargoRecord?.name || `#${driverTask.cargoId}`}}</dd></div>
          <div><dt>车辆</dt><dd>{{driverVehicleRecord?.plateNumber || `#${driverTask.vehicleId}`}}</dd></div><div><dt>实时 ETA</dt><dd>{{dateText(driverTask.estimatedArrivalTime)}}</dd></div>
        </dl>
        <div v-else class="empty-state">当前没有分配给该司机的任务</div>
      </article>
      <article class="panel home-map-panel">
        <div class="panel-title"><div><h2>路线与车辆位置</h2><span>当前任务路线及车辆实时位置</span></div><div class="role-live-status"><span :class="{connected:gpsWs.status.value==='open'}">{{gpsConnectionText}}</span><span :class="{connected:etaWs.status.value==='open'}">{{etaConnectionText}}</span></div></div>
        <AMapView :selectedVehicleId="driverVehicle?.vehicle_id || ''" :externalVehicles="driverVehicle ? [driverVehicle] : []" :plannedRoute="driverRoute" planned-route-coordinate-system="GCJ02" :routeStartLabel="driverTask?.startLocation || ''" :routeEndLabel="driverTask?.endLocation || ''" :showFacilities="false" focus-selected @select="selectVehicle" />
      </article>
    </section>
    <section class="driver-bottom">
      <article class="panel">
        <div class="panel-title"><div><h2>状态上报</h2><span>手动更新货物运输状态</span></div></div>
        <div class="status-choice-grid"><button class="status-choice current" @click="router.push('/status-report')"><span class="status-choice-icon"><UiIcon name="truck" /></span><strong>前往状态上报</strong><span>按后端任务状态机更新</span></button></div>
      </article>
      <article class="panel">
        <div class="panel-title"><div><h2>调度指令</h2><span>后端指令接口尚待确定</span></div><RouterLink class="link-btn" to="/dispatch">查看说明</RouterLink></div>
        <div class="command-cards"><div class="empty-command">未展示模拟指令；接口开放后接入真实记录。</div></div>
      </article>
    </section>
    </div>
  </template>

  <!-- 仓库管理员首页 -->
  <template v-else-if="role==='WAREHOUSE_MANAGER'">
    <section class="stats-grid" v-loading="warehouseLoading">
      <StatCard label="车辆总数" :value="warehouseVehicles.length" foot="云端数据库车辆" icon="truck" tone="blue" />
      <StatCard label="空闲车辆" :value="idleWarehouseVehicles.length" foot="可分配运输任务" icon="route" tone="amber" />
      <StatCard label="待运输货物" :value="waitingWarehouseCargos.length" foot="等待车辆绑定" icon="download" tone="green" />
      <StatCard label="运输中货物" :value="transportingCargoCount" foot="已进入运输流程" icon="upload" tone="violet" />
    </section>
    <article class="panel home-map-panel warehouse-map-panel">
      <div class="panel-title"><div><h2>本仓库货物运输地图</h2><span>同时展示全部出库任务路线；选中运单路线与车辆高亮，其余路线弱化，切换运单自动聚焦对应路线</span></div><select v-model="selectedWarehouseTaskId" class="mini"><option v-for="task in warehouseTasks" :key="task.id" :value="task.id">{{task.taskNo}}</option></select></div>
      <AMapView :selectedVehicleId="selectedWarehouseTask ? String(selectedWarehouseTask.vehicleId) : warehouseMapSelectedId" :externalVehicles="warehouseMapVehicles" :task-routes="warehouseTaskRoutes" task-routes-coordinate-system="GCJ02" :selected-task-id="selectedWarehouseTask?.id" :showFacilities="false" :showTrack="false" @select="selectVehicle" @select-task="selectedWarehouseTaskId = Number($event)" />
    </article>
    <section class="warehouse-main">
      <article class="panel role-table-card">
        <div class="panel-title"><div><h2>车辆列表</h2><span>查看车辆状态与所属司机</span></div><RouterLink class="link-btn" to="/vehicles">车辆管理</RouterLink></div>
        <div class="table-wrap" v-loading="warehouseLoading"><table><thead><tr><th>车牌号</th><th>车型</th><th>状态</th><th>所属司机</th></tr></thead><tbody><tr v-for="v in warehouseVehicles.slice(0,6)" :key="v.id"><td>{{v.plateNumber}}</td><td>{{v.type}}</td><td><span class="task-status">{{v.status==='TRANSPORTING'?'运输中':v.status==='IDLE'?'空闲':v.status}}</span></td><td>{{v.driverId?`司机 #${v.driverId}`:'未绑定司机'}}</td></tr><tr v-if="!warehouseVehicles.length"><td colspan="4">暂无云端车辆</td></tr></tbody></table></div>
      </article>
      <article class="panel warehouse-outbound-entry">
        <div class="panel-title"><div><h2>货物出库</h2><span>货主、货物、车辆、司机和路线在统一出库流程中绑定</span></div></div>
        <div class="outbound-entry-body"><div class="outbound-entry-icon">⇱</div><h3>前往货物出库</h3><p>概览页不再重复创建运输任务，请在出库页使用地图选点并完成全部绑定。</p><button class="primary" @click="router.push('/binding')">前往货物出库</button></div>
      </article>
    </section>
    <article class="panel role-table-card">
      <div class="panel-title"><div><h2>最近绑定记录</h2><span>货物与运输车辆关联记录</span></div><RouterLink class="link-btn" to="/binding-records">查看更多</RouterLink></div>
      <div class="table-wrap" v-loading="warehouseLoading"><table><thead><tr><th>任务编号</th><th>货物</th><th>车牌号</th><th>司机</th><th>实时 ETA</th><th>状态</th></tr></thead><tbody><tr v-for="task in warehouseTasks.slice(0,5)" :key="task.id" :class="{selected:Number(task.id)===Number(selectedWarehouseTask?.id)}" @click="selectedWarehouseTaskId=task.id"><td>{{task.taskNo}}</td><td>{{warehouseCargoMap[task.cargoId]?.name||`货物 #${task.cargoId}`}}</td><td>{{warehouseVehicleMap[task.vehicleId]?.plateNumber||`车辆 #${task.vehicleId}`}}</td><td>{{warehouseVehicleMap[task.vehicleId]?.driverId?`司机 #${warehouseVehicleMap[task.vehicleId].driverId}`:'未绑定司机'}}</td><td>{{dateText(task.estimatedArrivalTime)}}</td><td>{{task.status}}</td></tr><tr v-if="!warehouseTasks.length"><td colspan="6">暂无云端运输任务</td></tr></tbody></table></div>
    </article>
  </template>

  <!-- 调度员首页 -->
  <template v-else-if="role==='DISPATCHER'">
    <section class="stats-grid" v-loading="dispatcherLoading">
      <StatCard label="在线车辆" :value="dispatcherOnlineCount" foot="云端车辆状态" icon="truck" tone="blue" />
      <StatCard label="未解决告警" :value="dispatcherAlarms.filter(a=>a.status!=='RESOLVED').length" foot="需优先处理" icon="alert" tone="red" alarm />
      <StatCard label="告警总数" :value="dispatcherAlarms.length" foot="当前权限可见" icon="bell" tone="amber" />
      <StatCard label="进行中任务" :value="dispatcherTasks.filter(t=>t.status==='TRANSPORTING').length" foot="真实运输任务" icon="task" tone="violet" />
    </section>
    <article class="panel home-map-panel">
      <div class="panel-title"><div><h2>调度运输路线地图</h2><span>同时展示全部调度任务路线与实时车辆；选中运单路线与车辆高亮，其余路线弱化，切换运单自动聚焦对应路线</span></div><div class="role-live-status"><select v-model="selectedDispatcherTaskId" class="mini"><option v-for="task in dispatcherTasks" :key="task.id" :value="task.id">{{task.taskNo}}</option></select><RouterLink class="link-btn" to="/tracking">车辆监控</RouterLink></div></div>
      <AMapView :selectedVehicleId="selectedDispatcherTask ? String(selectedDispatcherTask.vehicleId) : dispatcherMapVehicles[0]?.vehicle_id||''" :externalVehicles="dispatcherMapVehicles" :task-routes="dispatcherTaskRoutes" task-routes-coordinate-system="GCJ02" :selected-task-id="selectedDispatcherTask?.id" :showTrack="false" @select="selectVehicle" @select-task="selectedDispatcherTaskId = Number($event)" />
    </article>
    <section class="dispatcher-bottom">
      <article class="panel role-table-card">
        <div class="panel-title"><div><h2>待处理告警</h2><span>运输异常处理队列</span></div><RouterLink class="link-btn" to="/alarms">查看全部</RouterLink></div>
        <div class="table-wrap"><table><thead><tr><th>告警类型</th><th>车辆</th><th>说明</th><th>时间</th><th>级别</th></tr></thead><tbody><tr v-for="a in dispatcherAlarms.filter(a=>a.status!=='RESOLVED').slice(0,5)" :key="a.id"><td>{{a.alarmType}}</td><td>{{a.plateNumber||`#${a.vehicleId}`}}</td><td>{{a.message}}</td><td>{{shortTime(a.createdAt||a.alarmTime)}}</td><td>{{a.level}}</td></tr><tr v-if="!dispatcherAlarms.some(a=>a.status!=='RESOLVED')"><td colspan="5">暂无未解决告警</td></tr></tbody></table></div>
      </article>
      <article class="panel"><div class="panel-title"><div><h2>快捷操作</h2><span>调度员常用业务入口</span></div></div><div class="quick-actions">
        <button class="quick-action" @click="router.push('/dispatch')"><span class="quick-action-icon command"><UiIcon name="command" /></span><span class="quick-action-copy"><strong>下发调度指令</strong><span>创建并下发新的调度任务</span></span><span class="quick-action-arrow">→</span></button>
        <button class="quick-action" @click="router.push('/notifications')"><span class="quick-action-icon broadcast"><UiIcon name="broadcast" /></span><span class="quick-action-copy"><strong>批量消息</strong><span>向车辆或司机发送消息</span></span><span class="quick-action-arrow">→</span></button>
        <button class="quick-action" @click="router.push('/stats')"><span class="quick-action-icon analytics"><UiIcon name="analytics" /></span><span class="quick-action-copy"><strong>数据统计</strong><span>查看运输与告警统计</span></span><span class="quick-action-arrow">→</span></button>
      </div></article>
    </section>
  </template>

  <!-- 系统管理员首页 -->
  <template v-else>
    <section class="stats-grid" v-loading="adminLoading">
      <StatCard label="可查账号数" :value="adminUsers.length" foot="货主、司机与当前管理员" icon="users" tone="blue" />
      <StatCard label="车辆总数" :value="adminVehicles.length" foot="云端车辆记录" icon="vehicle" tone="green" />
      <StatCard label="活跃任务" :value="adminActiveTaskCount" foot="待运输与运输中" icon="truck" tone="blue" />
      <StatCard label="告警总数" :value="adminAlarms.length" foot="当前查询范围" icon="alert" tone="amber" alarm />
    </section>
    <article class="panel home-map-panel">
      <div class="panel-title"><div><h2>全局运输监控</h2><span>同时展示全部任务路线与车辆位置；选中运单路线与车辆高亮，其余路线弱化，切换运单自动聚焦对应路线</span></div><select v-model="selectedAdminTaskId" class="mini"><option v-for="task in adminTasks" :key="task.id" :value="task.id">{{task.taskNo}}</option></select></div>
      <AMapView :selectedVehicleId="selectedAdminTask ? String(selectedAdminTask.vehicleId) : adminMapVehicles[0]?.vehicle_id||''" :externalVehicles="adminMapVehicles" :task-routes="adminTaskRoutes" task-routes-coordinate-system="GCJ02" :selected-task-id="selectedAdminTask?.id" :showTrack="false" @select-task="selectedAdminTaskId = Number($event)" />
    </article>
    <section class="admin-bottom"><article class="panel role-table-card" v-loading="adminLoading"><div class="panel-title"><div><h2>云端告警日志</h2><span>数据来自 `/alarms`</span></div><RouterLink class="link-btn" to="/alarms">查看全部</RouterLink></div><div class="table-wrap"><table><thead><tr><th>告警类型</th><th>车辆</th><th>位置/说明</th><th>时间</th><th>级别</th><th>状态</th></tr></thead><tbody><tr v-for="a in adminAlarms.slice(0,8)" :key="a.id"><td>{{a.alarmType}}</td><td>{{a.plateNumber||`#${a.vehicleId}`}}</td><td>{{a.message||a.description||'--'}}</td><td>{{dateText(a.createdAt)}}</td><td>{{a.level}}</td><td>{{a.status}}</td></tr><tr v-if="!adminAlarms.length"><td colspan="6">暂无云端告警</td></tr></tbody></table></div></article></section>
  </template>
</template>
