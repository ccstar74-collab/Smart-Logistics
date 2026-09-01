<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '../components/PageHeader.vue'
import AMapView from '../components/AMapView.vue'
import { api, extractList } from '../api/http'

const loading = ref(false)
const saving = ref(false)
const eligibilityLoading = ref(false)
const currentTask = ref(null)
const eligibility = ref(null)
const currentRoute = ref([])
const initialRoute = ref([])
const hasReplannedRoute = ref(false)
const mapVehicles = ref([])
let timer = null

const reasonText = {
  ARRIVAL_ALLOWED: '车辆已进入终点围栏，可以完成任务',
  TASK_NOT_TRANSPORTING: '任务当前不是运输中状态',
  DESTINATION_COORDINATES_MISSING: '任务缺少终点坐标，请联系调度员补充',
  LOCATION_NOT_FOUND: '尚未收到车辆定位，暂不能完成任务',
  LOCATION_OFFLINE: '车辆定位已离线，请恢复定位后重试',
  OUTSIDE_GEOFENCE: '车辆尚未进入终点围栏'
}

const nextStatus = computed(() => {
  if (currentTask.value?.status === 'WAITING') return 'TRANSPORTING'
  if (currentTask.value?.status === 'TRANSPORTING') return 'COMPLETED'
  return ''
})
const nextLabel = computed(() => nextStatus.value === 'TRANSPORTING' ? '开始运输' : '确认到达并完成')
const isCompleting = computed(() => nextStatus.value === 'COMPLETED')
const canSubmit = computed(() => Boolean(nextStatus.value) && (!isCompleting.value || eligibility.value?.eligible === true))
const eligibilityText = computed(() => {
  const item = eligibility.value
  if (!item) return '正在检查车辆是否到达终点'
  return reasonText[item.reason] || item.message || '暂时无法确认是否到达终点'
})
const statusText = { WAITING:'待运输', TRANSPORTING:'运输中', COMPLETED:'已完成', ABNORMAL:'异常', CANCELLED:'已取消' }

function normalizeTask(task = {}) {
  return {
    ...task,
    id: task.id ?? task.taskId,
    taskNo: task.taskNo ?? task.task_no ?? task.id,
    startName: task.startName ?? task.startLocation ?? task.start_location ?? task.start_name,
    endName: task.endName ?? task.endLocation ?? task.end_location ?? task.end_name,
    startLongitude: task.startLongitude ?? task.start_lng,
    startLatitude: task.startLatitude ?? task.start_lat,
    endLongitude: task.endLongitude ?? task.end_lng,
    endLatitude: task.endLatitude ?? task.end_lat,
    cargoId: task.cargoId ?? task.cargo_id,
    vehicleId: task.vehicleId ?? task.vehicle_id,
    cargoName: task.cargoName ?? task.cargo_name ?? task.cargo?.name,
    vehicleNo: task.vehicleNo ?? task.plateNo ?? task.plateNumber ?? task.vehicle_no ?? task.vehicle?.plateNumber
  }
}

function localDateTime(value) {
  if (!value) return '--'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false
  }).format(date)
}

function distanceText(value) {
  const meters = Number(value)
  if (!Number.isFinite(meters)) return '--'
  return meters >= 1000 ? `${(meters / 1000).toFixed(1)} 公里` : `${Math.round(meters)} 米`
}

function routePoints(route) {
  return route?.path || route?.points || route?.routePoints || []
}

async function refreshLatestLocation(task = currentTask.value) {
  if (!task?.vehicleId && !task?.vehicle_id) return
  try {
    const latest = await api.vehicles.latestLocation(task.vehicleId ?? task.vehicle_id)
    const longitude = Number(latest.longitude ?? latest.lng)
    const latitude = Number(latest.latitude ?? latest.lat)
    mapVehicles.value = Number.isFinite(longitude) && Number.isFinite(latitude)
      ? [{
          ...latest,
          vehicle_id: String(task.vehicleId ?? task.vehicle_id),
          task_id: task.id,
          plate_number: task.vehicleNo,
          gps: {
            lon: longitude,
            lat: latitude,
            speed_kmh: Number(latest.speed ?? latest.speedKmh ?? 0),
            heading_deg: Number(latest.direction ?? latest.heading ?? 0),
            coordinate_system: latest.coordinateSystem || 'WGS84',
            collected_at: latest.collectedAt ?? latest.timestamp
          }
        }]
      : []
  } catch {
    mapVehicles.value = []
  }
}

async function loadMapData(task) {
  const id = task?.id
  if (!id) return
  const [plannedResult, versionsResult] = await Promise.allSettled([
    api.transportTasks.plannedRoute(id),
    api.transportTasks.routes.list(id)
  ])
  if (plannedResult.status === 'fulfilled') currentRoute.value = routePoints(plannedResult.value)
  if (versionsResult.status === 'fulfilled') {
    const versions = extractList(versionsResult.value)
    const sorted = [...versions].sort((a, b) => Number(a.routeVersion ?? a.version ?? 0) - Number(b.routeVersion ?? b.version ?? 0))
    const initial = sorted[0]
    const active = versions.find(item => (item.routeStatus ?? item.status) === 'ACTIVE')
    const initialVersion = Number(initial?.routeVersion ?? initial?.version ?? 0)
    const activeVersion = Number(active?.routeVersion ?? active?.version ?? 0)
    hasReplannedRoute.value = Boolean(initial && active && activeVersion > initialVersion)
    initialRoute.value = hasReplannedRoute.value ? routePoints(initial) : []
    if (active) currentRoute.value = routePoints(active)
  }
  await refreshLatestLocation(task)
}

async function refreshEligibility() {
  if (currentTask.value?.status !== 'TRANSPORTING' || !currentTask.value?.id) {
    eligibility.value = null
    return
  }
  eligibilityLoading.value = true
  try {
    eligibility.value = await api.transportTasks.arrivalEligibility(currentTask.value.id)
  } catch (error) {
    eligibility.value = { eligible: false, message: error.message }
  } finally {
    eligibilityLoading.value = false
  }
}

async function load() {
  loading.value = true
  try {
    const data = await api.transportTasks.current()
    currentTask.value = data ? normalizeTask(data) : null
    eligibility.value = null
    currentRoute.value = []
    initialRoute.value = []
    hasReplannedRoute.value = false
    mapVehicles.value = []
    if (currentTask.value) {
      const detail = await api.transportTasks.get(currentTask.value.id).catch(() => null)
      if (detail) currentTask.value = normalizeTask({ ...currentTask.value, ...detail })
      const [cargoResult, vehicleResult] = await Promise.allSettled([
        currentTask.value.cargoId ? api.cargos.get(currentTask.value.cargoId) : Promise.resolve(null),
        currentTask.value.vehicleId ? api.vehicles.get(currentTask.value.vehicleId) : Promise.resolve(null)
      ])
      if (cargoResult.status === 'fulfilled' && cargoResult.value) {
        currentTask.value.cargoName = cargoResult.value.name ?? cargoResult.value.cargoName ?? currentTask.value.cargoName
      }
      if (vehicleResult.status === 'fulfilled' && vehicleResult.value) {
        currentTask.value.vehicleNo = vehicleResult.value.plateNumber ?? vehicleResult.value.plateNo ?? currentTask.value.vehicleNo
      }
      await Promise.all([refreshEligibility(), loadMapData(currentTask.value)])
    }
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (!canSubmit.value) return
  const completing = nextStatus.value === 'COMPLETED'
  try {
    await ElMessageBox.confirm(
      completing ? '系统会再次校验车辆定位和终点围栏，确认提交吗？' : '确认开始执行当前运输任务吗？',
      completing ? '完成任务确认' : '开始运输确认',
      { type: completing ? 'warning' : 'info' }
    )
    saving.value = true
    await api.transportTasks.updateStatus(currentTask.value.id, nextStatus.value)
    ElMessage.success(completing ? '任务已规范完成' : '任务已开始运输')
    await load()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error.message || '状态更新失败')
    if (completing) await refreshEligibility()
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await load()
  timer = window.setInterval(() => {
    refreshEligibility()
    refreshLatestLocation()
  }, 5000)
})
onBeforeUnmount(() => window.clearInterval(timer))
</script>

<template>
  <section>
    <PageHeader title="状态上报" subtitle="司机按运输流程上报状态；完成任务前必须通过终点到达围栏校验" />
    <div v-loading="loading" class="status-grid">
      <article class="panel task-panel">
        <h3>当前任务</h3>
        <template v-if="currentTask">
          <dl>
            <dt>运单号</dt><dd>{{ currentTask.taskNo }}</dd>
            <dt>起点</dt><dd>{{ currentTask.startName || '--' }}</dd>
            <dt>终点</dt><dd>{{ currentTask.endName || '--' }}</dd>
            <dt>货物</dt><dd>{{ currentTask.cargoName || '--' }}</dd>
            <dt>车辆</dt><dd>{{ currentTask.vehicleNo || '--' }}</dd>
            <dt>状态</dt><dd>{{ statusText[currentTask.status] || currentTask.status }}</dd>
          </dl>

          <el-button type="primary" size="large" :disabled="!canSubmit" :loading="saving" @click="submit">
            {{ nextStatus ? nextLabel : '当前状态无需上报' }}
          </el-button>
        </template>
        <el-empty v-else description="暂无当前任务" />
      </article>

      <article class="panel map-panel">
        <h3>路线与车辆位置</h3>
        <AMapView
          :task="currentTask"
          :planned-route="currentRoute"
          :initial-route="initialRoute"
          :vehicles="mapVehicles"
          height="520px"
        />
        <p class="map-note">
          蓝色为任务初始规划路线<span v-if="hasReplannedRoute">，紫色为偏航后当前 ACTIVE 恢复路线</span>。
        </p>
      </article>
    </div>
  </section>
</template>

<style scoped>
.status-grid { display: grid; grid-template-columns: minmax(320px, 430px) minmax(0, 1fr); gap: 20px; }
.panel { background: #fff; border: 1px solid #e7ebf2; border-radius: 16px; padding: 22px; }
.panel h3 { margin: 0 0 20px; }
dl { display: grid; grid-template-columns: 90px 1fr; gap: 14px 12px; margin: 0 0 24px; }
dt { color: #8490a5; }
dd { margin: 0; color: #1f2d43; font-weight: 600; overflow-wrap: anywhere; }
.arrival-card { margin: 0 0 20px; padding: 16px; border-radius: 12px; background: #fff8e8; border: 1px solid #f2d593; color: #9a6713; }
.arrival-card.allowed { background: #effaf4; border-color: #a9dfbf; color: #287846; }
.arrival-title { display: flex; align-items: center; justify-content: space-between; gap: 12px; font-weight: 700; }
.arrival-card p { margin: 10px 0 0; line-height: 1.55; }
.map-panel { min-width: 0; }
.map-note { margin: 12px 0 0; color: #718096; font-size: 13px; }
@media (max-width: 1100px) { .status-grid { grid-template-columns: 1fr; } }
</style>
