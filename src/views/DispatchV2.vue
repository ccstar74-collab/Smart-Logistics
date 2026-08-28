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
const drivers = ref([])
const vehicles = ref([])
const tasks = ref([])
const commands = ref([])
const taskRoutes = ref([])
const plannedRoutePoints = ref([])
const routeLoading = ref(false)
const replanning = ref(false)
const newRouteId = ref(null)
const routePreviewVisible = ref(false)
const contextAlarmId = ref(null)
const contextAlarm = ref(null)
const statusFilter = ref('')
const form = reactive({ taskId: null, commandType: 'TEXT', content: '', routeId: null })
const isDriver = computed(() => state.currentUser?.role === 'DRIVER')
const idOf = item => item?.id ?? item?.driverId ?? item?.userId ?? item?.value
const labelOf = item => [item?.username ?? item?.account, item?.name ?? item?.realName ?? item?.driverName].filter(Boolean).join(' · ') || `司机 #${idOf(item)}`
const vehicleMap = computed(() => Object.fromEntries(vehicles.value.map(item => [Number(item.id ?? item.vehicleId), item])))
const commandTypeText = { TEXT: '文字指令', ROUTE_CHANGE: '路线切换' }
const statusText = { PENDING: '待确认', SENT: '待确认', ACKNOWLEDGED: '已确认', EXECUTING: '执行中', COMPLETED: '已完成', REJECTED: '已拒绝' }
const routeStatusText = { READY: '备用', ACTIVE: '当前', INACTIVE: '已停用' }
const alarmText = { ROUTE_DEVIATION: '路线偏离', ABNORMAL_STOP: '异常停留', ABNORMAL_OPEN: '异常开箱' }
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
  durationSeconds: numeric(route.durationSeconds ?? route.duration_seconds ?? route.duration ?? route.estimatedDurationSeconds),
  provider: route.provider ?? route.routeProvider ?? route.source ?? '',
  coordinateSystem: String(route.coordinateSystem ?? route.coordinate_system ?? 'WGS84').toUpperCase(),
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
const selectedVehicle = computed(() => vehicleMap.value[Number(selectedTask.value?.vehicleId)] || null)
const selectedDriver = computed(() => {
  const driverId = selectedVehicle.value?.driverId ?? selectedVehicle.value?.driver_id ?? selectedTask.value?.driverId
  if (driverId == null) return null
  return drivers.value.find(item => Number(idOf(item)) === Number(driverId)) || null
})
const readyRoutes = computed(() => taskRoutes.value.filter(route => route.status === 'READY'))
const activeRoute = computed(() => taskRoutes.value.find(route => route.status === 'ACTIVE') || null)
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
    form.commandType = String(route.query.commandType || (contextAlarm.value.alarmType === 'ROUTE_DEVIATION' ? 'ROUTE_CHANGE' : 'TEXT'))
    form.content = contextAlarm.value.alarmType === 'ROUTE_DEVIATION'
      ? '车辆偏离规划路线，请核对备用路线并下发切换指令'
      : `车辆触发“${alarmText[contextAlarm.value.alarmType] || contextAlarm.value.alarmType || '运输异常'}”告警，请确认现场情况并处理`
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
  if (taskId != null && form.commandType === 'ROUTE_CHANGE') await loadRoutes(taskId)
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
  } catch (cause) {
    ElMessage.error(`路线加载失败：${cause.message}`)
  } finally {
    routeLoading.value = false
  }
}

async function replanRoutes() {
  const taskId = form.taskId
  if (taskId == null) return ElMessage.warning('请先选择运输任务')
  replanning.value = true
  const before = new Set(readyRoutes.value.map(route => String(route.id)))
  try {
    // 合同：POST /transport-tasks/{taskId}/routes 无 RequestBody，成功后刷新 READY 路线。
    await api.transportTasks.routes.create(taskId)
    await loadRoutes(taskId)
    const generated = readyRoutes.value.find(route => !before.has(String(route.id)))
      || [...readyRoutes.value].sort((a, b) => (numeric(b.routeVersion) ?? 0) - (numeric(a.routeVersion) ?? 0))[0]
      || null
    if (generated) {
      form.routeId = generated.id
      newRouteId.value = generated.id
      routePreviewVisible.value = true
      ElMessage.success(`已生成并选中备用路线 v${generated.routeVersion ?? '—'}，请核对路线信息后下发指令`)
    } else {
      ElMessage.warning('重新规划请求已完成，但后端未返回新的 READY 路线，请稍后刷新')
    }
  } catch (cause) {
    ElMessage.error(`路线重新规划失败：${cause.message}`)
  } finally {
    replanning.value = false
  }
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [vehicleResult, taskResult, driverResult, commandResult] = await Promise.all([
      api.vehicles.list({ page: 1, pageSize: 100 }),
      api.transportTasks.list({ page: 1, pageSize: 100 }),
      isDriver.value ? Promise.resolve([state.currentUser]) : api.drivers.options(),
      isDriver.value
        ? api.dispatchCommands.mine({ page: 1, pageSize: 100, status: statusFilter.value })
        : api.dispatchCommands.list({ page: 1, pageSize: 100, status: statusFilter.value })
    ])
    vehicles.value = extractList(vehicleResult)
    tasks.value = extractList(taskResult)
    drivers.value = extractList(driverResult)
    commands.value = extractList(commandResult).map(normalize)
  } catch (cause) {
    error.value = `调度指令加载失败：${cause.message}`
  } finally {
    loading.value = false
  }
}

async function send() {
  if (!form.taskId) return ElMessage.warning('请选择要调度的运输任务')
  if (!form.commandType) return ElMessage.warning('请选择调度类型')
  if (!form.content.trim()) return ElMessage.warning('请输入调度指令内容')
  if (!selectedVehicle.value) return ElMessage.warning('该任务未关联车辆，无法下发指令')
  if (!selectedDriver.value) return ElMessage.warning('该任务车辆未绑定司机，无法下发指令')
  if (form.commandType === 'ROUTE_CHANGE') {
    if (form.routeId == null || form.routeId === '') return ElMessage.warning('请选择要切换到的 READY 备用路线')
    const target = readyRoutes.value.find(route => String(route.id) === String(form.routeId))
    if (!target) return ElMessage.warning('所选路线不是 READY 状态，无法切换')
  }
  saving.value = true
  try {
    // 后端统一按 taskId → Task.vehicleId → Vehicle.driverId 推导目标车辆与司机，前端不提交 driverId / vehicleId。
    const body = { taskId: form.taskId, commandType: form.commandType, content: form.content.trim() }
    if (contextAlarmId.value != null) body.alarmId = contextAlarmId.value
    if (form.commandType === 'ROUTE_CHANGE') body.routeId = form.routeId
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
    <section class="dispatch-grid">
      <article v-if="!isDriver" class="panel form-card">
        <h2>下发新指令</h2>
        <div v-if="contextAlarmId != null" class="dispatch-alarm-link">
          <strong>来自告警管理 · 告警 #{{ contextAlarm?.id || contextAlarmId }}</strong>
          <span>{{ alarmText[contextAlarm?.alarmType] || contextAlarm?.alarmType || '运输异常' }}<template v-if="contextAlarm?.plateNumber"> · 车辆 {{ contextAlarm.plateNumber }}</template></span>
          <small>本次下发会携带 alarmId，与告警管理中的该条告警保持同一处理链路。</small>
        </div>
        <label>关联运输任务<select v-model.number="form.taskId"><option :value="null">请选择运输任务</option><option v-for="task in tasks" :key="task.id" :value="task.id">{{ task.taskNo }} · {{ task.startLocation }} → {{ task.endLocation }}</option></select></label>
        <label>调度类型<select v-model="form.commandType"><option value="TEXT">文字指令 TEXT</option><option value="ROUTE_CHANGE">路线切换 ROUTE_CHANGE</option></select></label>

        <template v-if="form.commandType === 'ROUTE_CHANGE'">
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
              <button type="button" class="mini" :disabled="routeLoading || replanning || !form.taskId" @click="replanRoutes">{{ replanning ? '规划中…' : '重新规划路线' }}</button>
            </div>
            <select :value="form.routeId" :disabled="routeLoading || !readyRoutes.length" @change="handleRouteSelection($event.target.value)">
              <option :value="null">{{ routeLoading ? '正在加载路线…' : readyRoutes.length ? '请选择一个备用路线方案' : '暂无备用路线，请先重新规划' }}</option>
              <option v-for="route in readyRoutes" :key="route.id" :value="route.id">{{ routeOptionText(route) }}</option>
            </select>
            <div v-if="!routeLoading && !readyRoutes.length" class="dispatch-route-empty">当前还没有可切换的 READY 备用路线。点击“重新规划路线”后，后端生成的新路线会出现在这里。</div>
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
        <label>目标司机<input :value="selectedDriver ? labelOf(selectedDriver) : '选择任务后自动匹配'" disabled /></label>
        <label>指令内容<textarea v-model="form.content" rows="6" maxlength="500" placeholder="例如：前方道路拥堵，请切换至所选备用路线" /></label>
        <button class="primary" :disabled="saving" @click="send">{{ saving ? '正在发送…' : '发送调度指令' }}</button>
      </article>
      <article class="panel page-panel dispatch-task-panel">
        <div class="panel-title"><div><h2>{{ isDriver ? '我的调度指令' : '指令发送记录' }}</h2><span>状态按待确认 → 已确认 → 执行中 → 已完成流转</span></div><el-button @click="load">刷新</el-button></div>
        <div class="toolbar"><el-select v-model="statusFilter" placeholder="全部状态" clearable style="width:140px" @change="load"><el-option v-for="(label,value) in statusText" :key="value" :label="label" :value="value" /></el-select><span class="muted-note">{{ isDriver ? '数据来自司机收件箱接口' : '数据来自调度指令列表接口' }}</span></div>
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
          :planned-route="selectedReadyRoute.routePoints"
          :planned-route-coordinate-system="selectedReadyRoute.coordinateSystem || 'WGS84'"
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
</template>

<style scoped>
.dispatch-alarm-link{margin:0 0 16px;padding:12px 14px;border:1px solid #ffd8d1;border-radius:12px;background:#fff8f6;display:flex;flex-direction:column;gap:4px}.dispatch-alarm-link strong{color:#cf4b37}.dispatch-alarm-link span{color:#26364f}.dispatch-alarm-link small{color:#8390a5}.route-preview-button{margin-top:10px;align-self:flex-start}.route-preview-summary{display:grid;grid-template-columns:2fr 1fr 1fr 1fr;gap:12px;margin-bottom:12px}.route-preview-summary>div{padding:12px 14px;border:1px solid #e5eaf2;border-radius:12px;background:#fafcff;display:flex;flex-direction:column;gap:5px}.route-preview-summary span{color:#8090a7}.route-preview-summary strong{color:#17253c}.route-preview-compare{padding:10px 12px;margin-bottom:12px;border-radius:10px;background:#f2f6ff;color:#52709c}.route-preview-map{height:430px;border:1px solid #e3e8f0;border-radius:14px;overflow:hidden}.route-preview-map :deep(.amap-shell),.route-preview-map :deep(.amap-container){height:100%;min-height:430px}.route-preview-map :deep(.amap-info-chip){display:none}@media (max-width:900px){.route-preview-summary{grid-template-columns:1fr 1fr}.route-preview-map{height:360px}.route-preview-map :deep(.amap-shell),.route-preview-map :deep(.amap-container){min-height:360px}}
</style>
