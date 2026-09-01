<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '../components/PageHeader.vue'
import AMapView from '../components/AMapView.vue'
import SkeletonScreen from '../components/SkeletonScreen.vue'
import { api, extractList } from '../api/http'

const loading = ref(false), trackLoading = ref(false), tasks = ref([]), cargos = ref([]), vehicles = ref([]), selectedTaskId = ref(null), trackPoints = ref([]), plannedRoute = ref([])
const routeVersions = ref([]), playbackEvents = ref([]), playbackAlarms = ref([]), playbackCommands = ref([]), playbackDegraded = ref(false), playbackNotice = ref('')
const initialRoute = computed(() => [...routeVersions.value]
  .sort((a, b) => Number(a.routeVersion ?? Infinity) - Number(b.routeVersion ?? Infinity))
  .find(route => route.points?.length >= 2)?.points || [])
const playbackCache = new Map()
let playbackUnavailableUntil = 0
let loadSequence = 0
const cargoMap = computed(() => Object.fromEntries(cargos.value.map(c => [Number(c.id), c])))
const vehicleMap = computed(() => Object.fromEntries(vehicles.value.map(v => [Number(v.id), v])))
const selectedTask = computed(() => tasks.value.find(t => Number(t.id) === Number(selectedTaskId.value)) || tasks.value[0] || null)
const selectedVehicle = computed(() => selectedTask.value ? vehicleMap.value[Number(selectedTask.value.vehicleId)] : null)
const taskPoint = (task, prefix) => {
  const lon = Number(task?.[`${prefix}Longitude`] ?? task?.[`${prefix}_longitude`])
  const lat = Number(task?.[`${prefix}Latitude`] ?? task?.[`${prefix}_latitude`])
  return Number.isFinite(lon) && Number.isFinite(lat) && !(lon === 0 && lat === 0) ? [lon, lat] : null
}
const originalStartPoint = computed(() => taskPoint(selectedTask.value, 'start'))
const originalEndPoint = computed(() => taskPoint(selectedTask.value, 'end'))
const mapVehicles = computed(() => {
  const lastTrackPoint = trackPoints.value[trackPoints.value.length - 1]
  const lon = Number(selectedVehicle.value?.lastLongitude ?? lastTrackPoint?.[0])
  const lat = Number(selectedVehicle.value?.lastLatitude ?? lastTrackPoint?.[1])
  return selectedVehicle.value && Number.isFinite(lon) && Number.isFinite(lat) ? [{ vehicle_id: String(selectedVehicle.value.id), online: true, gps: { lon, lat }, display: { plate_number: selectedVehicle.value.plateNumber } }] : []
})
const pointOf = point => {
  const lon = Number(point?.longitude ?? point?.lon ?? point?.lng ?? point?.gps?.lon)
  const lat = Number(point?.latitude ?? point?.lat ?? point?.gps?.lat)
  return Number.isFinite(lon) && Number.isFinite(lat) ? [lon, lat] : null
}
const trackList = payload => [...extractList(payload?.points ?? payload?.track ?? payload?.locations ?? payload)]
  .sort((a, b) => new Date(a?.collectedAt ?? a?.timestamp ?? 0) - new Date(b?.collectedAt ?? b?.timestamp ?? 0))
  .map(pointOf).filter(Boolean)
const routeList = payload => {
  const source = Array.isArray(payload) ? payload : payload?.points ?? payload?.path ?? payload?.routePoints ?? []
  return source.map(point => Array.isArray(point) ? point.map(Number) : pointOf(point)).filter(point => point && point.every(Number.isFinite))
}
const normalizeRouteVersion = route => ({
  ...route,
  routeId: route?.routeId ?? route?.route_id ?? route?.id,
  routeVersion: route?.routeVersion ?? route?.route_version ?? route?.version,
  routeStatus: route?.routeStatus ?? route?.route_status ?? route?.status,
  points: routeList(route)
})
const eventText = {
  TASK_STARTED: '任务开始', TASK_COMPLETED: '任务完成', ROUTE_GENERATED: '生成路线',
  ROUTE_ACTIVATED: '切换路线', ALARM_TRIGGERED: '触发告警', ALARM_RECOVERED: '告警恢复',
  ALARM_RESOLVED: '告警闭环', COMMAND_SENT: '指令已发送', COMMAND_ACKNOWLEDGED: '指令已确认',
  COMMAND_EXECUTING: '指令执行中', COMMAND_COMPLETED: '指令已完成'
}
async function loadPlaybackWithFallback(task, vehicleId) {
  if (Date.now() >= playbackUnavailableUntil) {
    try {
      return await api.transportTasks.playback(task.id, { timeout: 5000 })
    } catch (playbackError) {
      if (!/HTTP 404|请求超时/.test(playbackError?.message || '')) throw playbackError
      // 当前部署未提供或无法及时响应聚合接口时，5 分钟内直接走轻量降级路径。
      playbackUnavailableUntil = Date.now() + 5 * 60 * 1000
    }
  }
  try {
    const [trackResult, routesResult] = await Promise.all([
      api.transportTasks.track(task.id, {}, { timeout: 8000 }).catch(async trackError => {
        if (!/HTTP 404/.test(trackError?.message || '')) throw trackError
        const startTime = task.actualStartTime ?? task.actual_start_time ?? task.planStartTime ?? task.plan_start_time
        const endTime = task.actualEndTime ?? task.actual_end_time ?? task.planEndTime ?? task.plan_end_time ?? new Date().toISOString()
        return api.vehicles.locationHistory(vehicleId, { startTime, endTime })
      }),
      api.transportTasks.routes.list(task.id).catch(() => [])
    ])
    playbackNotice.value = '当前后端未部署 Playback 聚合接口，已降级加载历史轨迹与路线版本；事件、告警和指令时间线需后端升级后显示。'
    return {
      actualTrack: { coordinateSystem: 'WGS84', points: trackResult?.points ?? trackResult?.track ?? trackResult?.locations ?? extractList(trackResult) },
      routeVersions: extractList(routesResult), alarms: [], dispatchCommands: [], events: []
    }
  } catch (fallbackError) {
    throw fallbackError
  }
}
function applyPlayback(playbackResult, routeResult, latestResult) {
  trackPoints.value = trackList(playbackResult?.actualTrack?.points ?? [])
  routeVersions.value = extractList(playbackResult?.routeVersions).map(normalizeRouteVersion)
  const activeRoute = routeVersions.value.find(route => route.routeStatus === 'ACTIVE')
  plannedRoute.value = activeRoute?.points?.length ? activeRoute.points : routeList(routeResult)
  playbackAlarms.value = extractList(playbackResult?.alarms)
  playbackCommands.value = extractList(playbackResult?.dispatchCommands)
  playbackEvents.value = extractList(playbackResult?.events)
    .slice().sort((a, b) => new Date(a?.time ?? 0) - new Date(b?.time ?? 0))
  playbackDegraded.value = false
  if (latestResult && selectedVehicle.value) {
    selectedVehicle.value.lastLongitude = latestResult.longitude
    selectedVehicle.value.lastLatitude = latestResult.latitude
  }
}
const statusText = { WAITING: '待运输', TRANSPORTING: '运输中', COMPLETED: '已完成', ABNORMAL: '异常', CANCELLED: '已取消' }
const routeStatusText = { ACTIVE:'使用中', READY:'备用', INACTIVE:'已停用' }
const coordinateText = { GCJ02:'高德坐标', WGS84:'全球定位坐标' }
const dateText = value => value ? String(value).replace('T', ' ').slice(0, 16) : '--'
async function load() {
  loading.value = true
  try {
    const [taskResult, cargoResult, vehicleResult] = await Promise.all([api.transportTasks.list({ page: 1, pageSize: 100 }), api.cargos.list({ page: 1, pageSize: 100 }), api.vehicles.list({ page: 1, pageSize: 100 })])
    tasks.value = extractList(taskResult); cargos.value = extractList(cargoResult); vehicles.value = extractList(vehicleResult)
    if (!tasks.value.some(t => Number(t.id) === Number(selectedTaskId.value))) selectedTaskId.value = tasks.value[0]?.id ?? null
  } catch (error) { ElMessage.error(`运输记录加载失败：${error.message}`) }
  finally { loading.value = false }
}
async function loadTrack() {
  if (!selectedTask.value?.id) { trackPoints.value = []; return }
  const sequence = ++loadSequence
  const task = selectedTask.value
  const cached = playbackCache.get(Number(task.id))
  if (cached) {
    applyPlayback(cached.playbackResult, cached.routeResult, cached.latestResult)
    playbackNotice.value = `已立即显示缓存（${dateText(cached.loadedAt)}），正在后台检查更新。`
  }
  trackLoading.value = true
  try {
    const vehicleId = task.vehicleId ?? task.vehicle_id
    if (!vehicleId) throw new Error('当前任务未关联车辆')
    if (!cached) playbackNotice.value = '正在加载轨迹数据…'
    const [playbackResult, routeResult, latestResult] = await Promise.all([
      loadPlaybackWithFallback(task, vehicleId),
      api.transportTasks.plannedRoute(task.id).catch(() => []),
      api.vehicles.latestLocation(vehicleId).catch(() => null),
    ])
    if (sequence !== loadSequence) return
    playbackCache.set(Number(task.id), { playbackResult, routeResult, latestResult, loadedAt: new Date().toISOString() })
    applyPlayback(playbackResult, routeResult, latestResult)
    if (!playbackNotice.value.includes('未部署 Playback')) playbackNotice.value = ''
  } catch (error) {
    if (sequence !== loadSequence) return
    if (cached) {
      playbackNotice.value = `后台刷新失败，继续显示 ${dateText(cached.loadedAt)} 的缓存：${error.message}`
      return
    }
    trackPoints.value = []
    plannedRoute.value = []
    routeVersions.value = []
    playbackEvents.value = []
    playbackAlarms.value = []
    playbackCommands.value = []
    playbackDegraded.value = true
    playbackNotice.value = `轨迹接口暂不可用：${error.message}`
    ElMessage.warning(`任务回放加载失败：${error.message}`)
  } finally { if (sequence === loadSequence) trackLoading.value = false }
}
watch(selectedTaskId, loadTrack)
onMounted(load)
</script>

<template>
  <PageHeader title="运输轨迹" subtitle="查看云端运输任务记录与车辆最新位置" />
  <section class="panel trajectory-map-panel"><div class="panel-title"><div><h2>实际轨迹与计划路线</h2><span>蓝色实线为初始规划路线；发生偏航后，紫色实线为当前 ACTIVE 恢复路线，绿色为实际轨迹</span></div><button class="mini" @click="loadTrack">刷新轨迹</button></div><SkeletonScreen v-if="loading || trackLoading" variant="map" /><AMapView v-else :selectedVehicleId="mapVehicles[0]?.vehicle_id || ''" :selected-task-id="selectedTask?.id" :externalVehicles="mapVehicles" :actualTrack="trackPoints" :initial-route="initialRoute" initial-route-coordinate-system="GCJ02" :plannedRoute="plannedRoute" planned-route-coordinate-system="GCJ02" :routeStartLabel="selectedTask?.startLocation || '任务原始起点'" :routeEndLabel="selectedTask?.endLocation || '任务原始终点'" :route-start-point="originalStartPoint" :route-end-point="originalEndPoint" route-endpoint-coordinate-system="GCJ02" :showTrack="false" :showFacilities="false" /></section>
  <section class="panel role-table-card">
    <div class="panel-title"><div><h2>任务历史回放</h2><span v-if="playbackNotice">{{ playbackNotice }}</span><span v-else-if="playbackDegraded">Playback 暂不可用，请稍后重试（长任务可能返回 503）</span><span v-else>路线 {{ routeVersions.length }} 个版本 · 告警 {{ playbackAlarms.length }} 条 · 指令 {{ playbackCommands.length }} 条</span></div></div>
    <SkeletonScreen v-if="trackLoading" :rows="4" />
    <div v-else class="table-wrap"><table><thead><tr><th>时间</th><th>事件</th><th>路线版本</th><th>告警</th><th>指令</th></tr></thead><tbody><tr v-for="(event, index) in playbackEvents" :key="`${event.type}-${event.time}-${index}`"><td>{{ dateText(event.time) }}</td><td>{{ eventText[event.type] || event.type }}</td><td>{{ event.routeVersion == null ? '—' : `v${event.routeVersion}` }}</td><td>{{ event.alarmId ?? '—' }}</td><td>{{ event.commandId ?? '—' }}</td></tr><tr v-if="!playbackEvents.length"><td colspan="5">暂无回放事件</td></tr></tbody></table></div>
  </section>
  <section v-if="routeVersions.length" class="panel role-table-card">
    <div class="panel-title"><div><h2>路线版本</h2><span>状态字段使用后端返回的 routeStatus</span></div></div>
    <div class="table-wrap"><table><thead><tr><th>版本</th><th>状态</th><th>坐标系</th><th>距离</th><th>生成时间</th><th>激活时间</th></tr></thead><tbody><tr v-for="route in routeVersions" :key="route.routeId"><td>第 {{ route.routeVersion }} 版</td><td><span class="task-status">{{ routeStatusText[route.routeStatus] || route.routeStatus }}</span></td><td>{{ coordinateText[route.coordinateSystem] || '高德坐标' }}</td><td>{{ route.distanceMeters == null ? '—' : `${(Number(route.distanceMeters) / 1000).toFixed(1)} 公里` }}</td><td>{{ dateText(route.generatedAt) }}</td><td>{{ dateText(route.activatedAt) }}</td></tr></tbody></table></div>
  </section>
  <section class="panel role-table-card" v-loading="loading"><div class="panel-title"><div><h2>真实运输任务记录</h2></div></div><div class="table-wrap"><table><thead><tr><th>任务编号</th><th>货物</th><th>车辆</th><th>起点</th><th>终点</th><th>计划开始</th><th>计划结束</th><th>状态</th></tr></thead><tbody><tr v-for="task in tasks" :key="task.id" :class="{ selected: Number(task.id) === Number(selectedTaskId) }" @click="selectedTaskId = task.id"><td>{{ task.taskNo }}</td><td>{{ cargoMap[Number(task.cargoId)]?.name || `#${task.cargoId}` }}</td><td>{{ vehicleMap[Number(task.vehicleId)]?.plateNumber || `#${task.vehicleId}` }}</td><td>{{ task.startLocation }}</td><td>{{ task.endLocation }}</td><td>{{ dateText(task.planStartTime) }}</td><td>{{ dateText(task.planEndTime) }}</td><td><span class="task-status">{{ statusText[task.status] || task.status }}</span></td></tr><tr v-if="!tasks.length"><td colspan="8">暂无运输任务记录</td></tr></tbody></table></div></section>
</template>
