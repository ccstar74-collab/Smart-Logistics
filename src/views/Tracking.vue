<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '../components/PageHeader.vue'
import AMapView from '../components/AMapView.vue'
import { api, extractList } from '../api/http'
import { useAuth } from '../stores/auth-session'
import { useVehicleWebSocket } from '../composables/useVehicleWebSocket'

const { state } = useAuth()
const { status: wsStatus, vehicles: wsVehicles, setVehicleDictionary, connect: connectWebSocket } = useVehicleWebSocket()
const loading = ref(false), cargos = ref([]), tasks = ref([]), vehicles = ref([]), selectedTaskId = ref(null)
const cargoMap = computed(() => Object.fromEntries(cargos.value.map(c => [Number(c.id), c])))
const vehicleMap = computed(() => Object.fromEntries(vehicles.value.map(v => [Number(v.id), v])))
const selectedTask = computed(() => tasks.value.find(t => Number(t.id) === Number(selectedTaskId.value)) || tasks.value[0] || null)
const selectedCargo = computed(() => selectedTask.value ? cargoMap.value[Number(selectedTask.value.cargoId)] : null)
const selectedVehicle = computed(() => selectedTask.value ? vehicleMap.value[Number(selectedTask.value.vehicleId)] : null)
const validCoordinate = (lon, lat) => Number.isFinite(Number(lon)) && Number.isFinite(Number(lat)) && Math.abs(Number(lon)) <= 180 && Math.abs(Number(lat)) <= 90 && !(Number(lon) === 0 && Number(lat) === 0)
const apiMapVehicle = computed(() => !selectedVehicle.value || !validCoordinate(selectedVehicle.value?.lastLongitude, selectedVehicle.value?.lastLatitude) ? null : ({
  vehicle_id: String(selectedVehicle.value.id), task_id: selectedTask.value?.id, online: true,
  gps: { lon: Number(selectedVehicle.value.lastLongitude), lat: Number(selectedVehicle.value.lastLatitude), speed_kmh: selectedVehicle.value.speed ?? 0, timestamp: selectedVehicle.value.locationUpdatedAt || selectedVehicle.value.updatedAt },
  display: { plate_number: selectedVehicle.value.plateNumber, driver_name: selectedVehicle.value.driverName || '--', task_no: selectedTask.value?.taskNo }
}))
const selectedRealtimeVehicle = computed(() => wsVehicles.value.find(item => String(item.vehicle_id) === String(selectedTask.value?.vehicleId) || String(item.sim_code) === String(selectedVehicle.value?.simCode ?? selectedVehicle.value?.sim_code)))
const selectedMapVehicle = computed(() => selectedRealtimeVehicle.value || apiMapVehicle.value)
const showAllRealtimeVehicles = computed(() => ['DISPATCHER', 'ADMIN'].includes(state.currentUser.role))
const mapVehicles = computed(() => showAllRealtimeVehicles.value && wsVehicles.value.length ? wsVehicles.value : selectedMapVehicle.value ? [selectedMapVehicle.value] : [])
const hasLocation = computed(() => Boolean(selectedMapVehicle.value?.gps))
const currentPosition = computed(() => selectedMapVehicle.value?.gps ? `${selectedMapVehicle.value.gps.lat}, ${selectedMapVehicle.value.gps.lon}` : '暂无 GPS')
const statusText = { WAITING: '待运输', TRANSPORTING: '运输中', COMPLETED: '已完成', ABNORMAL: '异常', CANCELLED: '已取消' }

function mergeLatestLocations(vehicleRows, payload) {
  const locations = extractList(payload)
  const byId = new Map(locations.map(location => [Number(location.vehicleId ?? location.vehicle_id ?? location.id), location]))
  return vehicleRows.map(vehicle => {
    const location = byId.get(Number(vehicle.id ?? vehicle.vehicleId))
    return !location ? vehicle : { ...vehicle, lastLongitude: location.longitude ?? location.lon ?? location.lng, lastLatitude: location.latitude ?? location.lat, speed: location.speed ?? location.speed_kmh, direction: location.direction ?? location.heading, locationUpdatedAt: location.collectedAt ?? location.timestamp ?? location.collectTime, online: location.online ?? location.isOnline }
  })
}

function dateText(value) { return value ? String(value).replace('T', ' ').slice(0, 16) : '--' }
async function load() {
  loading.value = true
  try {
    const [cargoResult, taskResult, vehicleResult, locationResult] = await Promise.all([
      api.cargos.list({ page: 1, pageSize: 100 }), api.transportTasks.list({ page: 1, pageSize: 100 }), api.vehicles.list({ page: 1, pageSize: 100 }), api.vehicles.latestLocations().catch(() => [])
    ])
    cargos.value = extractList(cargoResult); tasks.value = extractList(taskResult); vehicles.value = mergeLatestLocations(extractList(vehicleResult), locationResult)
    const dictionaryResult = await api.realtimeVehicles.list().catch(() => vehicleResult)
    setVehicleDictionary(extractList(dictionaryResult).length ? extractList(dictionaryResult) : vehicles.value)
    if (!tasks.value.some(t => Number(t.id) === Number(selectedTaskId.value))) selectedTaskId.value = tasks.value[0]?.id ?? null
  } catch (error) { ElMessage.error(`货物追踪数据加载失败：${error.message}`) }
  finally { loading.value = false }
}
onMounted(async () => { await load(); connectWebSocket() })
</script>

<template>
  <PageHeader :title="showAllRealtimeVehicles ? '实时车辆监控' : '货物追踪'" subtitle="历史快照来自云端 API，实时位置来自车辆 GPS WebSocket" />
  <section class="panel owner-order-switcher" v-loading="loading">
    <div class="panel-title"><div><h2>选择运输订单</h2></div><button class="mini" @click="load">刷新</button></div>
    <div class="owner-order-tabs">
      <button v-for="task in tasks" :key="task.id" class="owner-order-card" :class="{ active: Number(selectedTaskId) === Number(task.id) }" @click="selectedTaskId = task.id">
        <span class="owner-order-main"><strong>{{ cargoMap[Number(task.cargoId)]?.name || `货物 #${task.cargoId}` }}</strong><span>{{ task.taskNo }}</span></span>
        <span class="owner-order-meta"><b>{{ statusText[task.status] || task.status }}</b><span>ETA {{ dateText(task.estimatedArrivalTime || task.planEndTime) }}</span></span>
      </button>
      <div v-if="!tasks.length && !loading" class="muted-note">当前账号暂无关联运输任务</div>
    </div>
  </section>
  <section v-if="selectedTask" class="tracking-grid owner-tracking-grid" v-loading="loading">
    <article class="panel"><div class="panel-title"><div><h2>{{showAllRealtimeVehicles?'车辆实时分布':'当前货物运输位置'}}</h2><span>{{ hasLocation ? '正在使用最新 GPS 经纬度' : '暂无有效 GPS，地图保持默认重庆视角' }}</span></div><span class="realtime-connection" :class="wsStatus">WebSocket {{wsStatus==='open'?'已连接':wsStatus==='reconnecting'?'服务不可达，自动重连中':wsStatus==='connecting'?'连接中':'未连接'}}</span></div><AMapView :selectedVehicleId="selectedMapVehicle?.vehicle_id || mapVehicles[0]?.vehicle_id || ''" :externalVehicles="mapVehicles" :showTrack="false" :showFacilities="false" /></article>
    <article class="panel detail-card owner-shipment-detail">
      <div class="shipment-detail-head"><div><h2>{{ selectedCargo?.name || `货物 #${selectedTask.cargoId}` }}</h2><p class="vehicle-id-line">{{ selectedTask.taskNo }}</p></div><span class="task-status">{{ statusText[selectedTask.status] || selectedTask.status }}</span></div>
      <dl><div><dt>货物编号</dt><dd>{{ selectedCargo?.cargoNo || '--' }}</dd></div><div><dt>运输车辆</dt><dd>{{ selectedVehicle?.plateNumber || selectedMapVehicle?.display?.plate_number || `#${selectedTask.vehicleId}` }}</dd></div><div><dt>司机</dt><dd>{{ selectedVehicle?.driverName || '--' }}</dd></div><div><dt>起点</dt><dd>{{ selectedTask.startLocation || '--' }}</dd></div><div><dt>终点</dt><dd>{{ selectedTask.endLocation || '--' }}</dd></div><div><dt>当前位置</dt><dd>{{ currentPosition }}</dd></div><div><dt>预计到达</dt><dd>{{ dateText(selectedTask.estimatedArrivalTime || selectedTask.planEndTime) }}</dd></div></dl>
    </article>
  </section>
</template>
