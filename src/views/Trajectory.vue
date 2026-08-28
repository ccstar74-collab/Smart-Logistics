<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '../components/PageHeader.vue'
import AMapView from '../components/AMapView.vue'
import { api, extractList } from '../api/http'

const loading = ref(false), trackLoading = ref(false), tasks = ref([]), cargos = ref([]), vehicles = ref([]), selectedTaskId = ref(null), trackPoints = ref([]), plannedRoute = ref([])
const cargoMap = computed(() => Object.fromEntries(cargos.value.map(c => [Number(c.id), c])))
const vehicleMap = computed(() => Object.fromEntries(vehicles.value.map(v => [Number(v.id), v])))
const selectedTask = computed(() => tasks.value.find(t => Number(t.id) === Number(selectedTaskId.value)) || tasks.value[0] || null)
const selectedVehicle = computed(() => selectedTask.value ? vehicleMap.value[Number(selectedTask.value.vehicleId)] : null)
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
const statusText = { WAITING: '待运输', TRANSPORTING: '运输中', COMPLETED: '已完成', ABNORMAL: '异常', CANCELLED: '已取消' }
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
  trackLoading.value = true
  try {
    const vehicleId = selectedTask.value.vehicleId ?? selectedTask.value.vehicle_id
    if (!vehicleId) throw new Error('当前任务未关联车辆')
    const [historyResult, routeResult, latestResult] = await Promise.all([
      api.vehicles.locationHistory(vehicleId),
      api.transportTasks.plannedRoute(selectedTask.value.id).catch(() => []),
      api.vehicles.latestLocation(vehicleId).catch(() => null),
    ])
    trackPoints.value = trackList(historyResult)
    plannedRoute.value = routeList(routeResult)
    if (latestResult && selectedVehicle.value) {
      selectedVehicle.value.lastLongitude = latestResult.longitude
      selectedVehicle.value.lastLatitude = latestResult.latitude
    }
  } catch (error) {
    trackPoints.value = []
    plannedRoute.value = []
    ElMessage.warning(`任务轨迹加载失败：${error.message}`)
  } finally { trackLoading.value = false }
}
watch(selectedTaskId, loadTrack)
onMounted(load)
</script>

<template>
  <PageHeader title="运输轨迹" subtitle="查看云端运输任务记录与车辆最新位置" />
  <section class="panel" v-loading="loading || trackLoading"><div class="panel-title"><div><h2>实际轨迹与计划路线</h2><span>{{ trackPoints.length ? `已加载 ${trackPoints.length} 个真实 GPS 点；计划路线 ${plannedRoute.length} 个点` : '当前任务暂无可用轨迹点' }}</span></div><button class="mini" @click="loadTrack">刷新轨迹</button></div><AMapView :selectedVehicleId="mapVehicles[0]?.vehicle_id || ''" :externalVehicles="mapVehicles" :actualTrack="trackPoints" :plannedRoute="plannedRoute" planned-route-coordinate-system="GCJ02" :routeStartLabel="selectedTask?.startLocation || '计划起点'" :routeEndLabel="selectedTask?.endLocation || '计划终点'" :showTrack="false" :showFacilities="false" /></section>
  <section class="panel role-table-card" v-loading="loading"><div class="panel-title"><div><h2>真实运输任务记录</h2></div></div><div class="table-wrap"><table><thead><tr><th>任务编号</th><th>货物</th><th>车辆</th><th>起点</th><th>终点</th><th>计划开始</th><th>计划结束</th><th>状态</th></tr></thead><tbody><tr v-for="task in tasks" :key="task.id" :class="{ selected: Number(task.id) === Number(selectedTaskId) }" @click="selectedTaskId = task.id"><td>{{ task.taskNo }}</td><td>{{ cargoMap[Number(task.cargoId)]?.name || `#${task.cargoId}` }}</td><td>{{ vehicleMap[Number(task.vehicleId)]?.plateNumber || `#${task.vehicleId}` }}</td><td>{{ task.startLocation }}</td><td>{{ task.endLocation }}</td><td>{{ dateText(task.planStartTime) }}</td><td>{{ dateText(task.planEndTime) }}</td><td><span class="task-status">{{ statusText[task.status] || task.status }}</span></td></tr><tr v-if="!tasks.length"><td colspan="8">暂无运输任务记录</td></tr></tbody></table></div></section>
</template>
