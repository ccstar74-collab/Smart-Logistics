<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import DataState from '../components/DataState.vue'
import PageHeader from '../components/PageHeader.vue'
import AMapView from '../components/AMapView.vue'
import { api, extractList } from '../api/http'
import { useAuth } from '../stores/auth-session'

const { state } = useAuth()
const route = useRoute()
const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const vehicles = ref([])
const tasks = ref([])
const commands = ref([])
const taskRoutes = ref([])
const plannedRoutePoints = ref([])
const routeLoading = ref(false)
const replanning = ref(false)
const newRouteId = ref(null)
const routePreviewVisible = ref(false)
const deviationCompareVisible = ref(false)
const deviationOriginalRoute = ref(null)
const deviationReplannedRoute = ref(null)
const contextAlarmId = ref(null)
const contextAlarm = ref(null)
const statusFilter = ref('')
const form = reactive({ taskId: null, commandType: 'TEXT', content: '', routeId: null })
const isDriver = computed(() => state.currentUser?.role === 'DRIVER')
const vehicleMap = computed(() => Object.fromEntries(vehicles.value.map(item => [Number(item.id ?? item.vehicleId), item])))
const commandTypeText = { TEXT: '文字指令', ROUTE_CHANGE: '路线切换' }
const statusText = { PENDING: '待确认', SENT: '待确认', ACKNOWLEDGED: '已确认', EXECUTING: '执行中', COMPLETED: '已完成', REJECTED: '已拒绝' }
const routeStatusText = { READY: '备用', ACTIVE: '当前', INACTIVE: '已停用' }
const alarmText = { ROUTE_DEVIATION: '路线偏离', ABNORMAL_STOP: '异常停留', ABNORMAL_OPEN: '异常开箱' }
const isDeviationRecovery = computed(() => contextAlarm.value?.alarmType === 'ROUTE_DEVIATION')
const dateText = value => value ? String(value).replace('T', ' ').slice(0, 19) : '—'
const numeric = value => {
  const result = Number(value)
  return Number.isFinite(result) ? result : null
}
const formatDistance = value => {
  const meters = numeric(value)
  if (meters == null) return ''
  return meters >= 1000 ? `${(meters / 1000).toFixed(meters >= 10000 ? 0 : 1)} km` : `${Math.round(meters)} m`
}
const formatDuration = value => {
  const seconds = numeric(value)
  if (seconds == null) return ''
  const minutes = Math.max(1, Math.round(seconds / 60))
  if (minutes < 60) return `${minutes} 分钟`
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return rest ? `${hours} 小时 ${rest} 分钟` : `${hours} 小时`
}
const routePointList = payload => {
  const source = Array.isArray(payload)
    ? payload
    : payload?.points ?? payload?.path ?? payload?.routePoints ?? payload?.route_points ?? payload?.polyline
      ?? payload?.geometry?.points ?? payload?.geometry?.polyline ?? []
  if (typeof source === 'string') {
    return source.split(';').map(pair => pair.split(',').map(Number)).filter(point => point.length >= 2 && point.every(Number.isFinite))
  }
  return (Array.isArray(source) ? source : [])
    .map(point => Array.isArray(point)
      ? point.map(Number)
      : [Number(point?.longitude ?? point?.lon ?? point?.lng), Number(point?.latitude ?? point?.lat)])
    .filter(point => point.length >= 2 && point.every(Number.isFinite))
}
const normalizeRoute = route => ({
  ...route,
  // 创建 ROUTE_CHANGE 指令时后端要求 routeId 满足 ^route_[A-Za-z0-9-]+$，
  // 因此优先取业务 routeId，避免误把数字主键 id 当作 routeId 提交。
  id: route.routeId ?? route.route_id ?? route.id,
  routeVersion: route.routeVersion ?? route.route_version ?? route.version,
  distanceMeters: numeric(route.distanceMeters ?? route.distance_meters ?? route.distance ?? route.lengthMeters),
  durationSeconds: numeric(route.referenceDurationSeconds ?? route.reference_duration_seconds ?? route.durationSeconds ?? route.duration_seconds ?? route.duration ?? route.estimatedDurationSeconds),
  provider: route.provider ?? route.routeProvider ?? route.source ?? '',
  coordinateSystem: String(route.coordinateSystem ?? route.coordinate_system ?? 'GCJ02').toUpperCase(),
  routePoints: routePointList(route),
  createdAt: route.createdAt ?? route.created_at ?? route.generatedAt ?? '',
  status: route.status ?? route.routeStatus ?? route.route_status ?? ''
})
const normalize = item => ({
  ...item,
  id: item.id ?? item.commandId ?? item.command_id,
  driverId: item.driverId ?? item.driver_id,
  driverName: item.driverName ?? item.driver_name,
  vehicleId: item.vehicleId ?? item.vehicle_id,
  plateNumber: item.plateNumber ?? item.plate_number,
  taskId: item.taskId ?? item.task_id,
  taskNo: item.taskNo ?? item.task_no,
  commandType: item.commandType ?? item.command_type ?? 'TEXT',
  routeId: item.routeId ?? item.route_id ?? null,
  content: item.content ?? item.command ?? item.message,
  feedback: item.feedback ?? item.result,
  createdAt: item.createdAt ?? item.created_at ?? item.sentAt,
  status: item.status ?? 'SENT'
})

const selectedTask = computed(() => tasks.value.find(item => Number(item.id) === Number(form.taskId)) || null)
const selectedVehicle = computed(() => vehicleMap.value[Number(selectedTask.value?.vehicleId ?? selectedTask.value?.vehicle_id)] || null)
const selectedDriverId = computed(() => selectedVehicle.value?.driverId ?? selectedVehicle.value?.driver_id ?? selectedTask.value?.driverId ?? selectedTask.value?.driver_id)
const selectedDriverLabel = computed(() => selectedVehicle.value?.driverName ?? selectedVehicle.value?.driver_name ?? selectedVehicle.value?.driver?.name ?? selectedTask.value?.driverName ?? selectedTask.value?.driver_name ?? (selectedDriverId.value == null ? '' : '已绑定司机'))
const readyRoutes = computed(() => taskRoutes.value.filter(route => route.status === 'READY'))
const activeRoute = computed(() => taskRoutes.value.find(route => route.status === 'ACTIVE') || null)
const initialRoute = computed(() => [...taskRoutes.value]
  .sort((a, b) => (numeric(a.routeVersion) ?? Infinity) - (numeric(b.routeVersion) ?? Infinity))
  .find(route => route.routePoints?.length >= 2)?.routePoints || [])
const selectedReadyRoute = computed(() => readyRoutes.value.find(route => String(route.id) === String(form.routeId)) || null)
const routeEndpointText = computed(() => {
  if (!selectedTask.value) return '请选择运输任务'
  return `${selectedTask.value.startLocation || '未设置起点'} → ${selectedTask.value.endLocation || '未设置终点'}`
})
const currentRouteTitle = computed(() => activeRoute.value?.routeVersion != null
  ? `当前路线 v${activeRoute.value.routeVersion}`
  : '任务原计划路线')
const currentRouteMeta = computed(() => {
  const route = activeRoute.value
  const parts = []
  if (route?.distanceMeters != null) parts.push(formatDistance(route.distanceMeters))
  if (route?.durationSeconds != null) parts.push(`预计 ${formatDuration(route.durationSeconds)}`)
  if (route?.provider) parts.push(String(route.provider).toUpperCase())
  if (!parts.length && plannedRoutePoints.value.length >= 2) parts.push(`已加载 ${plannedRoutePoints.value.length} 个路线点`)
  if (!parts.length) parts.push('已设置任务起点和终点')
  return parts.join(' · ')
})
const currentRouteNote = computed(() => {
  if (activeRoute.value) return '后端已将该路线标记为 ACTIVE，调度切换成功后新的路线会成为当前路线。'
  if (plannedRoutePoints.value.length >= 2) return '计划路线坐标已存在，但路线版本接口暂未返回 ACTIVE 记录，因此这里按任务原计划路线展示。'
  return '当前任务已有起点和终点，但后端暂未返回 ACTIVE 路线版本或计划路线坐标。'
})
const selectedRouteMeta = computed(() => {
  const route = selectedReadyRoute.value
  if (!route) return ''
  const parts = []
  if (route.distanceMeters != null) parts.push(formatDistance(route.distanceMeters))
  if (route.durationSeconds != null) parts.push(`预计 ${formatDuration(route.durationSeconds)}`)
  if (route.provider) parts.push(String(route.provider).toUpperCase())
  if (route.createdAt) parts.push(`生成于 ${dateText(route.createdAt)}`)
  return parts.join(' · ') || '后端已生成该备用路线版本'
})
const routeDifferenceText = computed(() => {
  const current = activeRoute.value
  const target = selectedReadyRoute.value
  if (!target) return ''
  if (!current) return '当前接口没有 ACTIVE 路线的距离/耗时基准，暂不能计算数值差异；备用路线以版本、距离、耗时等后端返回信息区分。'
  const differences = []
  if (current.distanceMeters != null && target.distanceMeters != null) {
    const diff = target.distanceMeters - current.distanceMeters
    if (Math.abs(diff) >= 1) differences.push(`距离${diff > 0 ? '增加' : '减少'} ${formatDistance(Math.abs(diff))}`)
    else differences.push('距离基本一致')
  }
  if (current.durationSeconds != null && target.durationSeconds != null) {
    const diff = target.durationSeconds - current.durationSeconds
    if (Math.abs(diff) >= 30) differences.push(`预计用时${diff > 0 ? '增加' : '减少'} ${formatDuration(Math.abs(diff))}`)
    else differences.push('预计用时基本一致')
  }
  return differences.length
    ? `与当前路线相比：${differences.join('，')}。`
    : '当前接口暂未返回足够的距离/耗时字段；路线几何差异以后可在地图路线对比中展示。'
})
const deviationDifferenceText = computed(() => {
  const original = deviationOriginalRoute.value
  const replanned = deviationReplannedRoute.value
  if (!original || !replanned) return ''
  const parts = []
  if (original.distanceMeters != null && replanned.distanceMeters != null) {
    const difference = replanned.distanceMeters - original.distanceMeters
    parts.push(Math.abs(difference) < 1
      ? '距离基本一致'
      : `距离${difference > 0 ? '增加' : '减少'} ${formatDistance(Math.abs(difference))}`)
  }
  if (original.durationSeconds != null && replanned.durationSeconds != null) {
    const difference = replanned.durationSeconds - original.durationSeconds
    parts.push(Math.abs(difference) < 30
      ? '预计用时基本一致'
      : `预计用时${difference > 0 ? '增加' : '减少'} ${formatDuration(Math.abs(difference))}`)
  }
  return parts.length ? `与原路线相比：${parts.join('，')}。` : '原路线与新路线的几何走向已在地图中同时展示。'
})

function routeOptionText(route) {
  const parts = [`方案 v${route.routeVersion ?? '—'}`]
  if (route.distanceMeters != null) parts.push(formatDistance(route.distanceMeters))
  if (route.durationSeconds != null) parts.push(formatDuration(route.durationSeconds))
  return parts.join(' · ')
}

function handleRouteSelection(value) {
  form.routeId = value
  if (value != null && value !== '') routePreviewVisible.value = true
}

async function applyDispatchContext() {
  if (isDriver.value) return
  const taskId = Number(route.query.taskId)
  if (Number.isFinite(taskId) && tasks.value.some(item => Number(item.id) === taskId)) form.taskId = taskId
  const alarmId = route.query.alarmId
  if (alarmId == null || alarmId === '') return
  contextAlarmId.value = alarmId
  try {
    const alarm = await api.alarms.get(alarmId)
    contextAlarm.value = {
      ...alarm,
      id: alarm.id ?? alarm.alarmId ?? alarm.alarm_id ?? alarmId,
      alarmType: alarm.alarmType ?? alarm.alarm_type ?? alarm.type,
      plateNumber: alarm.plateNumber ?? alarm.plate_number,
      taskId: alarm.taskId ?? alarm.task_id ?? taskId,
      message: alarm.message ?? alarm.description ?? ''
    }
    if (contextAlarm.value.taskId != null && tasks.value.some(item => Number(item.id) === Number(contextAlarm.value.taskId))) {
      form.taskId = Number(contextAlarm.value.taskId)
    }
    // 第五阶段偏航恢复由模拟器按 R 后调用 replan；调度员仅创建关联告警的 TEXT 指令。
    // 只有调用方显式指定 commandType 时，才进入普通 READY 路线切换流程。
    form.commandType = contextAlarm.value.alarmType === 'ROUTE_DEVIATION'
      ? 'TEXT'
      : String(route.query.commandType || 'TEXT')
    if (contextAlarm.value.alarmType === 'ROUTE_DEVIATION') form.routeId = null
    form.content = contextAlarm.value.alarmType === 'ROUTE_DEVIATION'
      ? '车辆发生偏航，请执行当前位置重新规划并恢复运输'
      : `车辆触发“${alarmText[contextAlarm.value.alarmType] || contextAlarm.value.alarmType || '运输异常'}”告警，请确认现场情况并处理`
    if (contextAlarm.value.alarmType === 'ROUTE_DEVIATION' && form.taskId != null) await loadRoutes(form.taskId)
  } catch (cause) {
    contextAlarm.value = { id: alarmId, taskId: Number.isFinite(taskId) ? taskId : null }
    form.commandType = String(route.query.commandType || 'TEXT')
    ElMessage.warning(`已进入告警联动调度，但告警详情读取失败：${cause.message}`)
  }
}

watch(() => form.taskId, async taskId => {
  form.routeId = null
  newRouteId.value = null
  taskRoutes.value = []
  plannedRoutePoints.value = []
  if (taskId != null && (form.commandType === 'ROUTE_CHANGE' || isDeviationRecovery.value)) await loadRoutes(taskId)
})

watch(() => form.commandType, async commandType => {
  if (commandType === 'ROUTE_CHANGE' && form.taskId != null && !taskRoutes.value.length) await loadRoutes(form.taskId)
})

async function loadRoutes(taskId) {
  routeLoading.value = true
  try {
    const [routeResult, plannedResult] = await Promise.all([
      api.transportTasks.routes.list(taskId),
      api.transportTasks.plannedRoute(taskId).catch(() => [])
    ])
    taskRoutes.value = extractList(routeResult).map(normalizeRoute)
    plannedRoutePoints.value = routePointList(plannedResult)
    // planned-route 是当前 ACTIVE 的权威结果；兼容后端 routes 暂时未同步返回该记录的情况。
    const planned = plannedResult && !Array.isArray(plannedResult) ? normalizeRoute(plannedResult) : null
    if (planned?.id != null && !taskRoutes.value.some(item => String(item.id) === String(planned.id))) {
      taskRoutes.value.push({ ...planned, status: 'ACTIVE' })
    }
  } catch (cause) {
    ElMessage.error(`路线加载失败：${cause.message}`)
  } finally {
    routeLoading.value = false
  }
}

async function replanFromLatestLocation() {
  const taskId = form.taskId
  if (taskId == null) return ElMessage.warning('请先选择运输任务')
  const vehicle = selectedVehicle.value
  const vehicleId = vehicle?.id ?? vehicle?.vehicleId ?? vehicle?.vehicle_id
  const vehicleDeviceCode = vehicle?.simCode ?? vehicle?.sim_code ?? vehicle?.deviceCode ?? vehicle?.device_code
  if (vehicleId == null) return ElMessage.warning('该任务未关联有效车辆')
  if (!vehicleDeviceCode) return ElMessage.warning('该车辆未配置 GPS 设备编码，无法按最新位置重规划')

  try {
    await ElMessageBox.confirm(
      '请确认车辆已经停车，并且停车后的最新 GPS 已上传。重规划成功后，新路线会直接成为当前路线。',
      '按最新位置重规划',
      { confirmButtonText: '确认重规划', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }

  replanning.value = true
  try {
    const originalActive = activeRoute.value
    deviationOriginalRoute.value = originalActive
      ? { ...originalActive, routePoints: [...(originalActive.routePoints?.length >= 2 ? originalActive.routePoints : plannedRoutePoints.value)] }
      : {
          routeVersion: null,
          routePoints: [...plannedRoutePoints.value],
          coordinateSystem: 'GCJ02',
          distanceMeters: null,
          durationSeconds: null,
          provider: ''
        }
    // 偏航恢复合同：先读取正式单车 latest，再把同一 WGS84 anchor 提交给专用 replan 接口。
    const latest = await api.vehicles.latestLocation(vehicleId)
    const longitude = numeric(latest?.longitude ?? latest?.lon ?? latest?.lng)
    const latitude = numeric(latest?.latitude ?? latest?.lat)
    const positionAt = latest?.collectedAt ?? latest?.collected_at ?? latest?.timestamp ?? latest?.collectTime
    if (longitude == null || latitude == null || !positionAt) {
      throw new Error('车辆最新位置缺少有效的经纬度或采集时间')
    }
    await api.transportTasks.routes.replanFromLatestLocation(taskId, {
      vehicleDeviceCode: String(vehicleDeviceCode),
      longitude,
      latitude,
      coordinateSystem: 'WGS84',
      positionAt
    })
    await loadRoutes(taskId)
    const replanned = activeRoute.value
    deviationReplannedRoute.value = replanned
      ? { ...replanned, routePoints: [...(replanned.routePoints?.length >= 2 ? replanned.routePoints : plannedRoutePoints.value)] }
      : null
    if (deviationReplannedRoute.value) deviationCompareVisible.value = true
    ElMessage.success('已按车辆最新位置完成重规划，新路线已成为当前路线')
  } catch (cause) {
    ElMessage.error(`按最新位置重规划失败：${cause.message}`)
  } finally {
    replanning.value = false
  }
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [vehicleResult, taskResult, commandResult] = await Promise.all([
      api.vehicles.list({ page: 1, pageSize: 100 }),
      api.transportTasks.list({ page: 1, pageSize: 100 }),
      isDriver.value
        ? api.dispatchCommands.mine({ page: 1, pageSize: 100, status: statusFilter.value })
        : api.dispatchCommands.list({ page: 1, pageSize: 100, status: statusFilter.value })
    ])
    vehicles.value = extractList(vehicleResult)
    tasks.value = extractList(taskResult)
    commands.value = extractList(commandResult).map(normalize)
  } catch (cause) {
    error.value = `调度指令加载失败：${cause.message}`
  } finally {
    loading.value = false
  }
}

async function send() {
  const commandType = isDeviationRecovery.value ? 'TEXT' : form.commandType
  if (!form.taskId) return ElMessage.warning('请选择要调度的运输任务')
  if (!commandType) return ElMessage.warning('请选择调度类型')
  if (!form.content.trim()) return ElMessage.warning('请输入调度指令内容')
  if (!selectedVehicle.value) return ElMessage.warning('该任务未关联车辆，无法下发指令')
  if (selectedDriverId.value == null) return ElMessage.warning('该任务车辆未绑定司机，无法下发指令')
  if (commandType === 'ROUTE_CHANGE') {
    if (form.routeId == null || form.routeId === '') return ElMessage.warning('请选择要切换到的 READY 备用路线')
    const target = readyRoutes.value.find(route => String(route.id) === String(form.routeId))
    if (!target) return ElMessage.warning('所选路线不是 READY 状态，无法切换')
  }
  saving.value = true
  try {
    // 后端统一按 taskId → Task.vehicleId → Vehicle.driverId 推导目标车辆与司机，前端不提交 driverId / vehicleId。
    const body = { taskId: form.taskId, commandType, content: form.content.trim() }
    if (contextAlarmId.value != null) body.alarmId = contextAlarmId.value
    if (commandType === 'ROUTE_CHANGE') body.routeId = form.routeId
    await api.dispatchCommands.create(body)
    ElMessage.success(contextAlarmId.value != null ? '调度指令已发送，关联告警已同步进入处理中' : '调度指令已发送')
    Object.assign(form, { taskId: null, commandType: 'TEXT', content: '', routeId: null })
    taskRoutes.value = []
    plannedRoutePoints.value = []
    newRouteId.value = null
    contextAlarmId.value = null
    contextAlarm.value = null
    if (route.query.from === 'alarm') await router.replace({ path: '/dispatch' })
    await load()
  } catch (cause) {
    ElMessage.error(cause.message)
  } finally {
    saving.value = false
  }
}

async function updateCommand(command, status) {
  let feedback = ''
  try {
    if (status === 'COMPLETED' || status === 'REJECTED') {
      const isDone = status === 'COMPLETED'
      const result = await ElMessageBox.prompt(isDone ? '请填写执行结果或反馈' : '请填写拒绝原因', isDone ? '完成调度指令' : '拒绝调度指令', {
        inputPlaceholder: isDone ? '例如：已按备用路线行驶' : '例如：当前路线更合适，暂不切换',
        inputValidator: value => value?.trim() ? true : '请填写反馈内容'
      })
      feedback = result.value.trim()
    } else {
      await ElMessageBox.confirm(`确认将指令更新为“${statusText[status]}”吗？`, '指令状态确认')
    }
    await api.dispatchCommands.updateStatus(command.id, status, feedback)
    ElMessage.success('指令状态已更新')
    await load()
  } catch (cause) {
    if (cause !== 'cancel' && cause !== 'close') ElMessage.error(cause.message || '指令状态更新失败')
  }
}

onMounted(async () => {
  await load()
  await applyDispatchContext()
})
</script>

<template>
  <PageHeader :title="isDriver ? '调度指令' : '调度指令下发'" subtitle="调度类型支持文字指令与路线切换；后端按任务自动推导目标车辆和司机" />
  <DataState :loading="loading" :error="error" @retry="load">
    <section class="dispatch-grid" :class="{ 'driver-dispatch-grid': isDriver }">
      <article v-if="!isDriver" class="panel form-card">
        <h2>下发新指令</h2>
        <div v-if="contextAlarmId != null" class="dispatch-alarm-link">
          <strong>来自告警管理 · 告警 #{{ contextAlarm?.id || contextAlarmId }}</strong>
          <span>{{ alarmText[contextAlarm?.alarmType] || contextAlarm?.alarmType || '运输异常' }}<template v-if="contextAlarm?.plateNumber"> · 车辆 {{ contextAlarm.plateNumber }}</template></span>
          <small>本次下发会携带 alarmId，与告警管理中的该条告警保持同一处理链路。</small>
        </div>
        <label>关联运输任务<select v-model.number="form.taskId"><option :value="null">请选择运输任务</option><option v-for="task in tasks" :key="task.id" :value="task.id">{{ task.taskNo }} · {{ task.startLocation }} → {{ task.endLocation }}</option></select></label>
        <label v-if="isDeviationRecovery">调度类型<input value="文字指令（偏航处理）" disabled /></label>
        <label v-else>调度类型<select v-model="form.commandType"><option value="TEXT">文字指令</option><option value="ROUTE_CHANGE">路线切换</option></select></label>

        <div v-if="isDeviationRecovery" class="dispatch-route-section">
          <div class="dispatch-route-label-row">
            <span class="dispatch-route-field-label">偏航快速恢复路线</span>
            <div class="dispatch-route-actions">
              <button type="button" class="mini" :disabled="routeLoading || replanning || !form.taskId" @click="loadRoutes(form.taskId)">{{ routeLoading ? '刷新中…' : '刷新当前路线' }}</button>
              <button type="button" class="mini" :disabled="routeLoading || replanning || !form.taskId" @click="replanFromLatestLocation">{{ replanning ? '重规划中…' : '按最新位置重规划' }}</button>
            </div>
          </div>
          <div class="dispatch-route-card current">
            <div class="dispatch-route-card-head">
              <strong>{{ currentRouteTitle }}</strong>
              <span class="dispatch-route-state active">{{ activeRoute ? 'ACTIVE 当前' : '等待路线' }}</span>
            </div>
            <p>{{ routeEndpointText }}</p>
            <small>{{ currentRouteMeta }}</small>
            <em>司机进入执行中且车辆停车、最新定位上传后，可按车辆最新位置重规划；成功后新路线会直接成为当前路线。</em>
          </div>
        </div>

        <template v-if="form.commandType === 'ROUTE_CHANGE' && !isDeviationRecovery">
          <div class="dispatch-route-section">
            <span class="dispatch-route-field-label">当前路线</span>
            <div class="dispatch-route-card current">
              <div class="dispatch-route-card-head">
                <strong>{{ currentRouteTitle }}</strong>
                <span class="dispatch-route-state active">{{ activeRoute ? 'ACTIVE 当前' : '原计划' }}</span>
              </div>
              <p>{{ routeEndpointText }}</p>
              <small>{{ currentRouteMeta }}</small>
              <em>{{ currentRouteNote }}</em>
            </div>
          </div>

          <div class="dispatch-route-section">
            <div class="dispatch-route-label-row">
              <span class="dispatch-route-field-label">目标备用路线</span>
              <button type="button" class="mini" :disabled="routeLoading || !form.taskId" @click="loadRoutes(form.taskId)">{{ routeLoading ? '刷新中…' : '刷新备用路线' }}</button>
            </div>
            <select :value="form.routeId" :disabled="routeLoading || !readyRoutes.length" @change="handleRouteSelection($event.target.value)">
              <option :value="null">{{ routeLoading ? '正在加载路线…' : readyRoutes.length ? '请选择一个备用路线方案' : '暂无可切换的备用路线' }}</option>
              <option v-for="route in readyRoutes" :key="route.id" :value="route.id">{{ routeOptionText(route) }}</option>
            </select>
            <div v-if="!routeLoading && !readyRoutes.length" class="dispatch-route-empty">当前没有可切换的备用路线。偏航恢复请使用上方“按最新位置重规划”。</div>
            <div v-if="selectedReadyRoute" class="dispatch-route-card candidate" :class="{ fresh: String(newRouteId) === String(selectedReadyRoute.id) }">
              <div class="dispatch-route-card-head">
                <strong>备用方案 v{{ selectedReadyRoute.routeVersion ?? '—' }}</strong>
                <span v-if="String(newRouteId) === String(selectedReadyRoute.id)" class="dispatch-route-state fresh">新生成</span>
                <span v-else class="dispatch-route-state ready">READY 备用</span>
              </div>
              <p>{{ routeEndpointText }}</p>
              <small>{{ selectedRouteMeta }}</small>
              <em>{{ routeDifferenceText }}</em>
              <button type="button" class="mini route-preview-button" @click="routePreviewVisible = true">查看该方案路线</button>
            </div>
            <div class="dispatch-route-explain">选择不同备用方案后会自动打开路线预览；如果后端返回该路线的 polyline / routePoints，可直接在地图中核对路线形状，并比较距离与预计耗时。</div>
          </div>
        </template>

        <label>目标车辆<input :value="selectedVehicle?.plateNumber || '选择任务后自动匹配'" disabled /></label>
        <label>目标司机<input :value="selectedDriverLabel || '选择任务后自动匹配'" disabled /></label>
        <label>指令内容<textarea v-model="form.content" rows="6" maxlength="500" placeholder="例如：前方道路拥堵，请切换至所选备用路线" /></label>
        <button class="primary" :disabled="saving" @click="send">{{ saving ? '正在发送…' : '发送调度指令' }}</button>
      </article>
      <article class="panel page-panel dispatch-task-panel">
        <div class="panel-title"><div><h2>{{ isDriver ? '我的调度指令' : '指令发送记录' }}</h2></div></div>
        <div class="toolbar dispatch-filter-bar"><el-select v-model="statusFilter" placeholder="全部状态" clearable style="width:180px" @change="load"><el-option v-for="(label,value) in statusText" :key="value" :label="label" :value="value" /></el-select></div>
        <div class="command-list">
          <div v-for="command in commands" :key="command.id" class="command-row dispatch-command-row">
            <div class="command-row-copy">
              <strong>{{ command.content }}</strong>
              <p><el-tag size="small" type="info">{{ commandTypeText[command.commandType] || command.commandType }}</el-tag> 任务 #{{ command.taskNo || command.taskId || '—' }} · 车辆 {{ command.plateNumber || vehicleMap[Number(command.vehicleId)]?.plateNumber || command.vehicleId || '—' }}<template v-if="command.driverName"> · 司机 {{ command.driverName }}</template> · {{ dateText(command.createdAt) }}</p>
              <small v-if="command.feedback">执行反馈：{{ command.feedback }}</small>
            </div>
            <div class="command-status-actions">
              <el-tag>{{ statusText[command.status] || command.status }}</el-tag>
              <template v-if="isDriver">
                <el-button v-if="['PENDING','SENT'].includes(command.status)" size="small" type="primary" @click="updateCommand(command, 'ACKNOWLEDGED')">确认收到</el-button>
                <el-button v-if="['PENDING','SENT'].includes(command.status)" size="small" type="danger" @click="updateCommand(command, 'REJECTED')">拒绝</el-button>
                <el-button v-if="command.status === 'ACKNOWLEDGED'" size="small" type="warning" @click="updateCommand(command, 'EXECUTING')">开始执行</el-button>
                <el-button v-if="command.status === 'ACKNOWLEDGED'" size="small" type="danger" @click="updateCommand(command, 'REJECTED')">拒绝</el-button>
                <el-button v-if="command.status === 'EXECUTING'" size="small" type="success" @click="updateCommand(command, 'COMPLETED')">完成并反馈</el-button>
              </template>
            </div>
          </div>
          <div v-if="!commands.length" class="empty-state">暂无调度指令</div>
        </div>
      </article>
    </section>
  </DataState>

  <el-dialog v-model="routePreviewVisible" :title="`备用路线方案 v${selectedReadyRoute?.routeVersion ?? '—'}`" width="880px" destroy-on-close>
    <div v-if="selectedReadyRoute" class="route-preview-dialog">
      <div class="route-preview-summary">
        <div><span>路线</span><strong>{{ routeEndpointText }}</strong></div>
        <div><span>距离</span><strong>{{ selectedReadyRoute.distanceMeters != null ? formatDistance(selectedReadyRoute.distanceMeters) : '后端未返回' }}</strong></div>
        <div><span>预计耗时</span><strong>{{ selectedReadyRoute.durationSeconds != null ? formatDuration(selectedReadyRoute.durationSeconds) : '后端未返回' }}</strong></div>
        <div><span>来源</span><strong>{{ selectedReadyRoute.provider ? String(selectedReadyRoute.provider).toUpperCase() : '后端规划' }}</strong></div>
      </div>
      <div class="route-preview-compare">{{ routeDifferenceText }}</div>
      <div v-if="selectedReadyRoute.routePoints?.length >= 2" class="route-preview-map">
        <AMapView
          :selected-task-id="form.taskId"
          :initial-route="initialRoute"
          initial-route-coordinate-system="GCJ02"
          :planned-route="selectedReadyRoute.routePoints"
          :planned-route-coordinate-system="selectedReadyRoute.coordinateSystem || 'GCJ02'"
          :route-start-label="selectedTask?.startLocation || '起点'"
          :route-end-label="selectedTask?.endLocation || '终点'"
          :external-vehicles="[]"
          :show-facilities="false"
          :show-track="false"
        />
      </div>
      <el-empty v-else description="该备用方案已有距离/耗时信息，但后端暂未返回 polyline / routePoints，当前无法绘制路线地图" />
    </div>
  </el-dialog>

  <el-dialog v-model="deviationCompareVisible" title="偏航重规划路线对比" width="960px" destroy-on-close>
    <div v-if="deviationOriginalRoute && deviationReplannedRoute" class="route-preview-dialog">
      <div class="deviation-route-comparison">
        <div class="deviation-route-summary original">
          <span>原路线</span>
          <strong>第 {{ deviationOriginalRoute.routeVersion ?? '—' }} 版</strong>
          <small>{{ deviationOriginalRoute.distanceMeters != null ? formatDistance(deviationOriginalRoute.distanceMeters) : '距离未返回' }} · {{ deviationOriginalRoute.durationSeconds != null ? formatDuration(deviationOriginalRoute.durationSeconds) : '用时未返回' }}</small>
        </div>
        <div class="deviation-route-arrow">→</div>
        <div class="deviation-route-summary replanned">
          <span>重规划后</span>
          <strong>第 {{ deviationReplannedRoute.routeVersion ?? '—' }} 版（当前路线）</strong>
          <small>{{ deviationReplannedRoute.distanceMeters != null ? formatDistance(deviationReplannedRoute.distanceMeters) : '距离未返回' }} · {{ deviationReplannedRoute.durationSeconds != null ? formatDuration(deviationReplannedRoute.durationSeconds) : '用时未返回' }}</small>
        </div>
      </div>
      <div class="route-preview-compare">{{ deviationDifferenceText }}</div>
      <div v-if="deviationReplannedRoute.routePoints?.length >= 2" class="route-preview-map">
        <AMapView
          :selected-task-id="form.taskId"
          :initial-route="deviationOriginalRoute.routePoints"
          :initial-route-coordinate-system="deviationOriginalRoute.coordinateSystem || 'GCJ02'"
          :planned-route="deviationReplannedRoute.routePoints"
          :planned-route-coordinate-system="deviationReplannedRoute.coordinateSystem || 'GCJ02'"
          :route-start-label="selectedTask?.startLocation || '起点'"
          :route-end-label="selectedTask?.endLocation || '终点'"
          :external-vehicles="[]"
          :show-facilities="false"
          :show-track="false"
        />
      </div>
      <el-empty v-else description="重规划已成功，但后端没有返回可绘制的路线坐标" />
    </div>
  </el-dialog>
</template>

<style scoped>
.driver-dispatch-grid{grid-template-columns:minmax(0,1fr)}.driver-dispatch-grid .dispatch-task-panel{min-height:calc(100vh - 150px)}.dispatch-filter-bar{padding:16px 20px 8px}.driver-dispatch-grid .command-list{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px;padding:16px 20px 24px}.driver-dispatch-grid .command-row{display:grid;grid-template-columns:minmax(0,1fr) auto;align-items:start;padding:18px;border:1px solid #e4eaf2;border-radius:12px;background:#fff}.driver-dispatch-grid .command-row:last-child{border-bottom:1px solid #e4eaf2}.driver-dispatch-grid .command-status-actions{display:flex;flex-wrap:wrap;justify-content:flex-end;gap:8px;max-width:230px}@media(max-width:1100px){.driver-dispatch-grid .command-list{grid-template-columns:1fr}}@media(max-width:720px){.driver-dispatch-grid .command-row{grid-template-columns:1fr}.driver-dispatch-grid .command-status-actions{justify-content:flex-start;max-width:none}}
.dispatch-alarm-link{margin:0 0 16px;padding:12px 14px;border:1px solid #ffd8d1;border-radius:12px;background:#fff8f6;display:flex;flex-direction:column;gap:4px}.dispatch-alarm-link strong{color:#cf4b37}.dispatch-alarm-link span{color:#26364f}.dispatch-alarm-link small{color:#8390a5}.route-preview-button{margin-top:10px;align-self:flex-start}.route-preview-summary{display:grid;grid-template-columns:2fr 1fr 1fr 1fr;gap:12px;margin-bottom:12px}.route-preview-summary>div{padding:12px 14px;border:1px solid #e5eaf2;border-radius:12px;background:#fafcff;display:flex;flex-direction:column;gap:5px}.route-preview-summary span{color:#8090a7}.route-preview-summary strong{color:#17253c}.route-preview-compare{padding:10px 12px;margin-bottom:12px;border-radius:10px;background:#f2f6ff;color:#52709c}.route-preview-map{height:430px;border:1px solid #e3e8f0;border-radius:14px;overflow:hidden}.route-preview-map :deep(.amap-shell),.route-preview-map :deep(.amap-container){height:100%;min-height:430px}.route-preview-map :deep(.amap-info-chip){display:none}@media (max-width:900px){.route-preview-summary{grid-template-columns:1fr 1fr}.route-preview-map{height:360px}.route-preview-map :deep(.amap-shell),.route-preview-map :deep(.amap-container){min-height:360px}}
.deviation-route-comparison{display:grid;grid-template-columns:minmax(0,1fr) auto minmax(0,1fr);align-items:stretch;gap:14px;margin-bottom:12px}.deviation-route-summary{padding:14px 16px;border:1px solid #e3e8f0;border-radius:12px;display:flex;flex-direction:column;gap:5px}.deviation-route-summary.original{background:#f7f9fc}.deviation-route-summary.replanned{background:#f1f6ff;border-color:#bfd1ff}.deviation-route-summary span,.deviation-route-summary small{color:#718199}.deviation-route-summary strong{color:#17253c}.deviation-route-arrow{align-self:center;color:#4774df;font-size:24px;font-weight:700}@media(max-width:700px){.deviation-route-comparison{grid-template-columns:1fr}.deviation-route-arrow{transform:rotate(90deg)}}
</style>
