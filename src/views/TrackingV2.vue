<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import AMapView from '../components/AMapView.vue'
import DataState from '../components/DataState.vue'
import PageHeader from '../components/PageHeader.vue'
import { api, extractList } from '../api/http'
import { useVehicleWebSocket } from '../composables/useVehicleWebSocketV2'
import { mergeEtaUpdate, useLogisticsEtaWebSocket } from '../composables/useLogisticsEtaWebSocket'
import { useAuth } from '../stores/auth-session'
import { filterRegisteredVehicles, filterTaskVehicles } from '../utils/realtimeScope'

const { state } = useAuth()
const ws = useVehicleWebSocket()
const etaWs = useLogisticsEtaWebSocket((message) => {
  tasks.value = tasks.value.map(task => mergeEtaUpdate(task, message))
})
const loading = ref(false)
const loadError = ref('')
const cargos = ref([])
const tasks = ref([])
const registeredVehicles = ref([])
const selectedTaskId = ref(null)
const taskSearch = ref('')
const taskStatusFilter = ref('ACTIVE')
const showAllTasks = ref(false)
const selectedPlannedRoute = ref([])
const restLastUpdatedAt = ref('')
const restError = ref('')
let restTimer = null
const isWarehouseManager = computed(() => state.currentUser?.role === 'WAREHOUSE_MANAGER')
const cargoMap = computed(() => Object.fromEntries(cargos.value.map((item) => [Number(item.id), item])))
const vehicleMap = computed(() => Object.fromEntries(registeredVehicles.value.map((item) => [Number(item.id), item])))
const currentWarehouseId = computed(() => state.currentUser?.warehouseId ?? state.currentUser?.warehouse_id)
const warehouseVehicles = computed(() => registeredVehicles.value.filter((vehicle) => {
  const vehicleWarehouseId = vehicle.warehouseId ?? vehicle.warehouse_id
  return currentWarehouseId.value == null || vehicleWarehouseId == null || String(vehicleWarehouseId) === String(currentWarehouseId.value)
}))
const warehouseVehicleIds = computed(() => new Set(warehouseVehicles.value.map((vehicle) => String(vehicle.id ?? vehicle.vehicleId ?? vehicle.vehicle_id))))
const visibleTasks = computed(() => isWarehouseManager.value
  ? tasks.value.filter((task) => warehouseVehicleIds.value.has(String(task.vehicleId ?? task.vehicle_id)))
  : tasks.value)
const isFleetMonitor = computed(() => isWarehouseManager.value || showFleet.value)
const filteredTasks = computed(() => visibleTasks.value.filter(task => {
  const keyword = taskSearch.value.trim().toLowerCase()
  const cargo = cargoMap.value[Number(task.cargoId)]
  const matchesKeyword = !keyword || [task.taskNo, task.startLocation, task.endLocation, cargo?.name, cargo?.cargoNo].some(value => String(value ?? '').toLowerCase().includes(keyword))
  const matchesStatus = taskStatusFilter.value === 'ALL'
    || (taskStatusFilter.value === 'ACTIVE' && ['WAITING', 'TRANSPORTING', 'ABNORMAL'].includes(task.status))
    || task.status === taskStatusFilter.value
  return matchesKeyword && matchesStatus
}))
const displayedTasks = computed(() => isFleetMonitor.value && !showAllTasks.value ? filteredTasks.value.slice(0, 6) : filteredTasks.value)
const selectedTask = computed(() => visibleTasks.value.find((item) => Number(item.id) === Number(selectedTaskId.value)) || visibleTasks.value[0] || null)
const selectedCargo = computed(() => selectedTask.value ? cargoMap.value[Number(selectedTask.value.cargoId)] : null)
const selectedVehicle = computed(() => selectedTask.value ? vehicleMap.value[Number(selectedTask.value.vehicleId)] : null)
const scopedRealtimeVehicles = computed(() => filterTaskVehicles(ws.vehicles.value, visibleTasks.value, registeredVehicles.value))
const warehouseRealtimeVehicles = computed(() => filterRegisteredVehicles(ws.vehicles.value, warehouseVehicles.value))
const selectedRealtimeVehicle = computed(() => scopedRealtimeVehicles.value.find((item) => String(item.vehicle_id) === String(selectedTask.value?.vehicleId) || String(item.sim_code) === String(selectedVehicle.value?.simCode ?? selectedVehicle.value?.sim_code)))
const showFleet = computed(() => ['DISPATCHER', 'ADMIN'].includes(state.currentUser?.role))
const mapVehicles = computed(() => isWarehouseManager.value ? warehouseRealtimeVehicles.value : showFleet.value ? scopedRealtimeVehicles.value : selectedRealtimeVehicle.value ? [selectedRealtimeVehicle.value] : [])
const currentPosition = computed(() => selectedRealtimeVehicle.value?.gps ? `${selectedRealtimeVehicle.value.gps.lat}, ${selectedRealtimeVehicle.value.gps.lon}` : '暂无 GPS')
const statusText = { WAITING: '待运输', TRANSPORTING: '运输中', COMPLETED: '已完成', ABNORMAL: '异常', CANCELLED: '已取消' }
const wsStatusText = computed(() => ({ open: '已连接', reconnecting: '自动重连中', connecting: '连接中', error: '连接异常', closed: '已关闭', idle: '未连接' })[ws.status.value] || ws.status.value)
const lastMessageText = computed(() => ws.lastMessageAt.value ? new Date(ws.lastMessageAt.value).toLocaleString('zh-CN', { hour12: false }) : '尚未收到消息')
const etaWsStatusText = computed(() => ({ open: '实时 ETA 已连接', reconnecting: 'ETA 自动重连中', connecting: 'ETA 连接中', error: 'ETA 连接异常', closed: 'ETA 已关闭', idle: 'ETA 未连接' })[etaWs.status.value] || etaWs.status.value)
const restUpdatedText = computed(() => restLastUpdatedAt.value ? new Date(restLastUpdatedAt.value).toLocaleTimeString('zh-CN', { hour12: false }) : '等待首次刷新')
const dateText = (value) => value ? String(value).replace('T', ' ').slice(0, 16) : '—'
const routeList = payload => {
  const source = Array.isArray(payload) ? payload : payload?.points ?? payload?.path ?? payload?.routePoints ?? []
  return source.map(point => Array.isArray(point) ? point.map(Number) : [Number(point?.longitude ?? point?.lon ?? point?.lng), Number(point?.latitude ?? point?.lat)]).filter(point => point.every(Number.isFinite))
}

async function refreshRestSnapshot() {
  const taskId = selectedTask.value?.id
  const [latestResult, detailResult] = await Promise.all([
    api.vehicles.latestLocationsWithFallback(registeredVehicles.value.map(v => v.id)),
    taskId ? api.transportTasks.get(taskId).catch(() => null) : null,
  ])
  ws.seedLocations(extractList(latestResult))
  if (detailResult) tasks.value = tasks.value.map(task => Number(task.id) === Number(taskId) ? { ...task, ...detailResult } : task)
  restLastUpdatedAt.value = new Date().toISOString()
  restError.value = ''
}

async function safeRefreshRestSnapshot() {
  try { await refreshRestSnapshot() }
  catch (error) { restError.value = error.message || 'REST 实时位置刷新失败' }
}

async function loadSelectedRoute() {
  selectedPlannedRoute.value = selectedTask.value?.id
    ? routeList(await api.transportTasks.plannedRoute(selectedTask.value.id))
    : []
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const [cargoResult, taskResult, vehicleResult] = await Promise.all([
      api.cargos.list({ page: 1, pageSize: 100 }),
      api.transportTasks.list({ page: 1, pageSize: 100 }),
      api.vehicles.list({ page: 1, pageSize: 100 }),
    ])
    cargos.value = extractList(cargoResult)
    tasks.value = extractList(taskResult)
    registeredVehicles.value = extractList(vehicleResult)
    ws.setVehicleDictionary(registeredVehicles.value)
    api.realtimeVehicles.list().then(result => {
      const mapping = extractList(result)
      if (mapping.length) ws.setVehicleDictionary(mapping)
    }).catch(() => {})
    api.vehicles.latestLocationsWithFallback(registeredVehicles.value.map(v => v.id)).then(result => ws.seedLocations(extractList(result))).catch(() => {})
    if (!visibleTasks.value.some((item) => Number(item.id) === Number(selectedTaskId.value))) selectedTaskId.value = visibleTasks.value[0]?.id ?? null
  } catch (error) {
    loadError.value = `车辆监控数据加载失败：${error.message}`
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await load()
  ws.connect()
  etaWs.connect()
  await Promise.all([
    safeRefreshRestSnapshot(),
    loadSelectedRoute().catch(() => { selectedPlannedRoute.value = [] }),
  ])
  restTimer = window.setInterval(safeRefreshRestSnapshot, 5000)
})
watch(selectedTaskId, () => {
  safeRefreshRestSnapshot()
  loadSelectedRoute().catch(() => { selectedPlannedRoute.value = [] })
})
onBeforeUnmount(() => { if (restTimer) window.clearInterval(restTimer) })
</script>

<template>
  <PageHeader :title="showFleet || isWarehouseManager ? '实时车辆监控' : '货物追踪'" :subtitle="isWarehouseManager ? '展示本仓库车辆；选择运输订单可定位并高亮对应车辆' : '地图仅展示当前账号可见任务所关联的车辆'" />
  <DataState :loading="loading" :error="loadError" @retry="load">
    <section class="panel owner-order-switcher" :class="{ 'fleet-order-switcher': isFleetMonitor }">
      <div class="panel-title order-switcher-title"><div><h2>选择运输订单</h2><span>共 {{ visibleTasks.length }} 单，筛选后 {{ filteredTasks.length }} 单</span></div><div class="order-monitor-actions"><input v-model="taskSearch" class="order-search" placeholder="搜索运单、货物或地点" /><select v-model="taskStatusFilter" class="order-status-filter"><option value="ACTIVE">活跃任务</option><option value="WAITING">待运输</option><option value="TRANSPORTING">运输中</option><option value="ABNORMAL">异常</option><option value="COMPLETED">已完成</option><option value="ALL">全部状态</option></select><button class="mini" @click="load">刷新</button></div></div>
      <div class="owner-order-tabs"><button v-for="task in displayedTasks" :key="task.id" class="owner-order-card" :class="{ active: Number(selectedTaskId) === Number(task.id) }" @click="selectedTaskId = task.id"><span class="owner-order-main"><strong>{{ cargoMap[Number(task.cargoId)]?.name || `货物 #${task.cargoId}` }}</strong><span>{{ task.taskNo }}</span></span><span class="owner-order-meta"><b>{{ statusText[task.status] || task.status }}</b><span>ETA {{ dateText(task.estimatedArrivalTime) }}</span></span></button><div v-if="!displayedTasks.length" class="compact-empty">没有符合筛选条件的运输订单</div></div>
      <button v-if="isFleetMonitor && filteredTasks.length > 6" class="order-expand-button" @click="showAllTasks = !showAllTasks">{{ showAllTasks ? '收起订单' : `查看其余 ${filteredTasks.length - 6} 单` }}</button>
    </section>
    <DataState :empty="!visibleTasks.length" empty-text="当前账号暂无关联运输任务">
      <section v-if="selectedTask" class="tracking-grid owner-tracking-grid">
        <article class="panel">
          <div class="panel-title realtime-panel-title"><div><h2>{{ isWarehouseManager ? '本仓库车辆实时分布' : showFleet ? '任务车辆实时分布' : '当前货物运输位置' }}</h2><span>{{ isWarehouseManager ? `已定位 ${mapVehicles.length} 辆本仓库车辆` : '已在前端按运输任务关联关系二次过滤' }} · REST 每 5 秒刷新（{{ restUpdatedText }}）</span></div><div class="ws-diagnostics"><strong :class="['realtime-connection', ws.status.value]">定位 {{ wsStatusText }}</strong><strong :class="['realtime-connection', etaWs.status.value]">{{ etaWsStatusText }}</strong><span>定位重连：{{ ws.reconnectCount.value }}；ETA 重连：{{ etaWs.reconnectCount.value }}</span><span>定位最后消息：{{ lastMessageText }}</span><span v-if="ws.status.value === 'open' && selectedTask && !selectedRealtimeVehicle" class="ws-error">连接正常，但所选车辆暂无匹配的 sim_code GPS</span><span v-if="restError" class="ws-error" :title="restError">REST：{{ restError }}</span><span v-if="ws.lastError.value" class="ws-error" :title="ws.lastError.value">{{ ws.lastError.value }}</span><span v-if="etaWs.lastError.value" class="ws-error" :title="etaWs.lastError.value">{{ etaWs.lastError.value }}</span></div></div>
          <AMapView :selected-vehicle-id="selectedRealtimeVehicle?.vehicle_id || ''" :external-vehicles="mapVehicles" :planned-route="selectedPlannedRoute" planned-route-coordinate-system="GCJ02" :route-start-label="selectedTask?.startLocation || ''" :route-end-label="selectedTask?.endLocation || ''" :show-track="false" :show-facilities="false" focus-selected />
        </article>
        <article class="panel detail-card owner-shipment-detail"><div class="shipment-detail-head"><div><h2>{{ selectedCargo?.name || `货物 #${selectedTask.cargoId}` }}</h2><p class="vehicle-id-line">{{ selectedTask.taskNo }}</p></div><span class="task-status">{{ statusText[selectedTask.status] || selectedTask.status }}</span></div><dl><div><dt>货物编号</dt><dd>{{ selectedCargo?.cargoNo || '—' }}</dd></div><div><dt>运输车辆</dt><dd>{{ selectedVehicle?.plateNumber || selectedRealtimeVehicle?.display?.plate_number || `#${selectedTask.vehicleId}` }}</dd></div><div><dt>GPS 设备</dt><dd>{{ selectedVehicle?.simCode || selectedVehicle?.sim_code || selectedRealtimeVehicle?.sim_code || '未配置' }}</dd></div><div><dt>司机</dt><dd>{{ selectedVehicle?.driverName || '—' }}</dd></div><div><dt>起点</dt><dd>{{ selectedTask.startLocation || '—' }}</dd></div><div><dt>终点</dt><dd>{{ selectedTask.endLocation || '—' }}</dd></div><div><dt>当前位置</dt><dd>{{ currentPosition }}</dd></div><div><dt>实时 ETA</dt><dd>{{ dateText(selectedTask.estimatedArrivalTime) }}</dd></div><div><dt>ETA 更新时间</dt><dd>{{ dateText(selectedTask.etaCalculatedAt) }}</dd></div><div><dt>剩余路程</dt><dd>{{ selectedTask.remainingDistanceMeters == null ? '—' : `${(Number(selectedTask.remainingDistanceMeters) / 1000).toFixed(1)} km` }}</dd></div></dl></article>
      </section>
    </DataState>
  </DataState>
</template>
