<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import AMapView from '../components/AMapView.vue'
import DataState from '../components/DataState.vue'
import LocationPicker from '../components/LocationPicker.vue'
import PageHeader from '../components/PageHeader.vue'
import { api, extractList } from '../api/http'

const DRAFT_KEY = 'warehouseInitialRouteDecisionDraft'
const loading = ref(false), recommending = ref(false), loadingResources = ref(false)
const planningRoutes = ref(false), saving = ref(false), restoring = ref(false)
const loadError = ref(''), locationPickerVisible = ref(false), routeDialogVisible = ref(false), warehouseDialogVisible = ref(false)
const owners = ref([]), cargoTypes = ref([]), warehouses = ref([]), warehouseCandidates = ref([])
const availableCargos = ref([]), availableVehicles = ref([]), createdTask = ref(null)
const tasks = ref([]), allCargos = ref([]), allVehicles = ref([])
const routeDecision = ref(null), selectedRouteId = ref(''), routeSelectionRemark = ref('')
const planningIdempotencyKey = ref(''), creationIdempotencyKey = ref('')
const clock = ref(Date.now())
let clockTimer = null

const form = reactive({
  ownerId:null, cargoTypeId:null, endLocation:'', endLongitude:null, endLatitude:null,
  originWarehouseId:null, cargoId:null, vehicleId:null, planTime:[]
})

const idOf = item => item?.ownerId ?? item?.id ?? item?.userId ?? item?.value
const ownerLabel = item => [item?.username ?? item?.account, item?.name ?? item?.realName ?? item?.ownerName].filter(Boolean).join(' · ') || `货主 #${idOf(item)}`
const selectedWarehouse = computed(() => warehouses.value.find(item => Number(item.id) === Number(form.originWarehouseId)) || warehouseCandidates.value.find(item => Number(item.warehouseId) === Number(form.originWarehouseId)) || null)
const selectedVehicle = computed(() => availableVehicles.value.find(item => Number(item.id) === Number(form.vehicleId)) || null)
const outboundCargoIds = computed(() => new Set(tasks.value.map(task => Number(task.cargoId ?? task.cargo_id)).filter(Number.isFinite)))
const cargoMap = computed(() => Object.fromEntries(allCargos.value.map(item => [Number(item.id ?? item.cargoId), item])))
const vehicleMap = computed(() => Object.fromEntries(allVehicles.value.map(item => [Number(item.id ?? item.vehicleId), item])))
const warehouseMap = computed(() => Object.fromEntries(warehouses.value.map(item => [Number(item.id), item])))
const inventoryRows = computed(() => allCargos.value.map(cargo => ({
  ...cargo,
  inStock: !outboundCargoIds.value.has(Number(cargo.id ?? cargo.cargoId)) && (!cargo.status || cargo.status === 'WAITING')
})))
const statusText = { WAITING:'待运输', TRANSPORTING:'运输中', COMPLETED:'已完成', ABNORMAL:'异常', CANCELLED:'已取消', ACTIVE:'使用中', READY:'备用', INACTIVE:'已停用' }
const dateText = value => value ? String(value).replace('T',' ').slice(0,16) : '—'
const canRecommendWarehouse = computed(() => form.ownerId && form.cargoTypeId && form.endLocation && [form.endLongitude, form.endLatitude].map(Number).every(Number.isFinite))
const canPlanRoutes = computed(() => canRecommendWarehouse.value && form.originWarehouseId && form.cargoId && form.vehicleId && !planningRoutes.value)
const sortedRoutes = computed(() => [...(routeDecision.value?.routes || [])].sort((a,b) => Number(a.rank ?? 999) - Number(b.rank ?? 999)))
const isSingleRouteDecision = computed(() => Boolean(routeDecision.value?.degraded) || Number(routeDecision.value?.candidateCount) === 1 || sortedRoutes.value.length === 1)
const recommendationTitle = computed(() => {
  if (isSingleRouteDecision.value || routeDecision.value?.recommendationSource === 'SINGLE_ROUTE') return '单路线方案'
  if (['AGENT', 'AGENT_EXPLANATION'].includes(routeDecision.value?.recommendationSource)) return '智能体解释推荐'
  return '系统综合推荐'
})
const selectedRoute = computed(() => sortedRoutes.value.find(route => String(route.routeId) === String(selectedRouteId.value)) || null)
const previewRoutes = computed(() => sortedRoutes.value.map(route => ({
  id: route.routeId,
  path: route.points || [],
  startLabel: routeDecision.value?.start?.location || selectedWarehouse.value?.warehouseName || selectedWarehouse.value?.name || '发货仓',
  endLabel: routeDecision.value?.destination?.location || form.endLocation
})))
const expiresAtMs = computed(() => Date.parse(routeDecision.value?.expiresAt || ''))
const remainingSeconds = computed(() => Number.isFinite(expiresAtMs.value) ? Math.max(0, Math.ceil((expiresAtMs.value - clock.value) / 1000)) : 0)
const decisionExpired = computed(() => Boolean(routeDecision.value) && remainingSeconds.value <= 0)
const countdownText = computed(() => `${String(Math.floor(remainingSeconds.value / 60)).padStart(2,'0')}:${String(remainingSeconds.value % 60).padStart(2,'0')}`)
const canConfirm = computed(() => selectedRoute.value && !decisionExpired.value && !saving.value)
const disablePastDate = date => date.getTime() < new Date().setHours(0,0,0,0)
const formatDistance = meters => {
  const value = Number(meters)
  if (!Number.isFinite(value)) return '距离暂不可用'
  return value >= 1000 ? `${(value / 1000).toFixed(value >= 100000 ? 0 : 1)} 公里` : `${Math.round(value)} 米`
}
const formatDuration = seconds => {
  const minutes = Math.max(1, Math.ceil(Number(seconds) / 60))
  if (!Number.isFinite(minutes)) return '用时暂不可用'
  const days = Math.floor(minutes / 1440), hours = Math.floor((minutes % 1440) / 60), rest = minutes % 60
  return [days ? `${days} 天` : '', hours ? `${hours} 小时` : '', rest ? `${rest} 分钟` : ''].filter(Boolean).join(' ')
}
const trafficText = route => {
  if (route.trafficDataAvailable === false || String(route.trafficLevel || '').toUpperCase() === 'UNKNOWN') return '路线生成时未取得完整路况快照'
  const traffic = route.traffic
  if (traffic && typeof traffic === 'object') {
    const parts = [
      Number(traffic.slowDistanceMeters) > 0 ? `缓行 ${formatDistance(traffic.slowDistanceMeters)}` : '',
      Number(traffic.congestedDistanceMeters) > 0 ? `拥堵 ${formatDistance(traffic.congestedDistanceMeters)}` : '',
      Number(traffic.severeCongestedDistanceMeters) > 0 ? `严重拥堵 ${formatDistance(traffic.severeCongestedDistanceMeters)}` : ''
    ].filter(Boolean)
    if (parts.length) return `路况快照显示：${parts.join('，')}`
    if (Number(traffic.smoothDistanceMeters) > 0) return `已采集路段以畅通为主（畅通约 ${formatDistance(traffic.smoothDistanceMeters)}）`
    if (traffic.description) return traffic.description
  }
  return route.dimensionSummaries?.traffic || route.trafficDescription || '暂无可解释的路况快照'
}
const weatherText = route => {
  if (route.weatherDataAvailable === false || route.scoreDetails?.weather == null) return '—'
  return `${Number(route.scoreDetails.weather).toFixed(1)} 分`
}
const resourceList = value => Array.isArray(value) ? value : extractList(value)
const cargoNumber = cargo => cargo?.cargoNo ?? cargo?.cargo_no ?? `#${cargo?.id ?? cargo?.cargoId}`
const plateNumber = vehicle => vehicle?.plateNumber ?? vehicle?.plate_number ?? vehicle?.licensePlate ?? `#${vehicle?.id ?? vehicle?.vehicleId}`
const availableInventory = value => resourceList(value).filter(cargo => !outboundCargoIds.value.has(Number(cargo.id ?? cargo.cargoId ?? cargo.cargo_id)))
const dimensionCopy = (route, key, label) => {
  const summary = route.dimensionSummaries?.[key]
  if (summary) return summary
  const score = route.scoreDetails?.[key]
  if (score == null) return `${label}数据暂不完整，请结合路线信息判断`
  const value = Number(score)
  const judgement = value >= 95 ? '表现突出' : value >= 85 ? '整体较好' : value >= 70 ? '基本可接受' : '需要重点权衡'
  return `${judgement}（${value.toFixed(1)} 分）`
}

function createStableKey(prefix) {
  const id = globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(16).slice(2)}`
  return `${prefix}-${id}`
}

function clearRouteDecision() {
  routeDecision.value = null
  selectedRouteId.value = ''
  routeSelectionRemark.value = ''
  routeDialogVisible.value = false
  planningIdempotencyKey.value = ''
  creationIdempotencyKey.value = ''
  sessionStorage.removeItem(DRAFT_KEY)
}

function invalidateRecommendation() {
  clearRouteDecision()
  warehouseDialogVisible.value = false
  warehouseCandidates.value = []
  Object.assign(form, { originWarehouseId:null, cargoId:null, vehicleId:null })
  availableCargos.value = []
  availableVehicles.value = []
}

function persistDecisionDraft() {
  if (!routeDecision.value?.decisionId) return
  sessionStorage.setItem(DRAFT_KEY, JSON.stringify({
    decisionId: routeDecision.value.decisionId,
    selectedRouteId: selectedRouteId.value,
    planningIdempotencyKey: planningIdempotencyKey.value,
    creationIdempotencyKey: creationIdempotencyKey.value,
    routeSelectionRemark: routeSelectionRemark.value,
    form: { ...form, planTime: (form.planTime || []).map(value => new Date(value).toISOString()) }
  }))
}

async function loadBaseData() {
  loading.value = true
  loadError.value = ''
  try {
    const [ownerResult, typeResult, warehouseResult, taskResult, cargoResult, vehicleResult] = await Promise.all([
      api.owners.options(), api.cargoTypes.list({ page:1, pageSize:100 }), api.warehouses.list({ page:1, pageSize:100 }),
      api.transportTasks.list({ page:1, pageSize:100 }), api.cargos.list({ page:1, pageSize:100 }), api.vehicles.list({ page:1, pageSize:100 })
    ])
    owners.value = extractList(ownerResult)
    cargoTypes.value = extractList(typeResult)
    warehouses.value = extractList(warehouseResult)
    tasks.value = extractList(taskResult)
    allCargos.value = extractList(cargoResult)
    allVehicles.value = extractList(vehicleResult)
    if (!owners.value.some(owner => Number(idOf(owner)) === Number(form.ownerId))) form.ownerId = null
    if (!cargoTypes.value.some(type => Number(type.id) === Number(form.cargoTypeId))) form.cargoTypeId = null
  } catch (error) {
    allCargos.value = []
    loadError.value = `多仓库基础数据加载失败：${error.message}`
  } finally { loading.value = false }
}

function applyDestination(point) {
  invalidateRecommendation()
  form.endLocation = point.name
  form.endLongitude = Number(point.longitude)
  form.endLatitude = Number(point.latitude)
  locationPickerVisible.value = false
}

async function recommendWarehouses() {
  if (!canRecommendWarehouse.value) return ElMessage.warning('请先选择货主、货物种类和运输终点')
  clearRouteDecision()
  recommending.value = true
  try {
    const candidates = extractList(await api.transportTasks.recommendOrigin({
      ownerId:Number(form.ownerId), cargoTypeId:Number(form.cargoTypeId),
      endLocation:form.endLocation.trim(), endLongitude:Number(form.endLongitude), endLatitude:Number(form.endLatitude)
    }))
    warehouseCandidates.value = await Promise.all(candidates.map(async item => {
      const warehouseId = Number(item.warehouseId)
      const [cargoResult, vehicleResult] = await Promise.all([
        api.cargos.available({ ownerId:Number(form.ownerId), cargoTypeId:Number(form.cargoTypeId), warehouseId }).catch(() => []),
        api.vehicles.available({ warehouseId }).catch(() => [])
      ])
      return { ...item, availableCargos:availableInventory(cargoResult), availableVehicles:resourceList(vehicleResult) }
    }))
    if (!warehouseCandidates.value.length) {
      invalidateRecommendation()
      return ElMessage.warning('暂无同时具备可用货物和车辆的发货仓库')
    }
    form.originWarehouseId = (warehouseCandidates.value.find(item => item.recommended) || warehouseCandidates.value[0]).warehouseId
    warehouseDialogVisible.value = true
  } catch (error) { ElMessage.error(error.message) }
  finally { recommending.value = false }
}

function selectWarehouseCandidate(item) {
  form.originWarehouseId = item.warehouseId
  warehouseDialogVisible.value = false
}

async function loadWarehouseResources(preferred = {}) {
  if (!form.originWarehouseId) return
  loadingResources.value = true
  Object.assign(form, { cargoId:null, vehicleId:null })
  try {
    const [cargoResult, vehicleResult] = await Promise.all([
      api.cargos.available({ ownerId:Number(form.ownerId), cargoTypeId:Number(form.cargoTypeId), warehouseId:Number(form.originWarehouseId) }),
      api.vehicles.available({ warehouseId:Number(form.originWarehouseId) })
    ])
    availableCargos.value = availableInventory(cargoResult)
    availableVehicles.value = extractList(vehicleResult)
    form.cargoId = availableCargos.value.some(item => Number(item.id) === Number(preferred.cargoId)) ? Number(preferred.cargoId) : availableCargos.value[0]?.id ?? null
    form.vehicleId = availableVehicles.value.some(item => Number(item.id) === Number(preferred.vehicleId)) ? Number(preferred.vehicleId) : availableVehicles.value[0]?.id ?? null
  } catch (error) {
    availableCargos.value = []; availableVehicles.value = []
    ElMessage.error(`仓库可用资源加载失败：${error.message}`)
  } finally { loadingResources.value = false }
}

async function waitForDecision(result) {
  let current = result
  for (let attempt = 0; attempt < 8 && (!current?.routes?.length || ['PROCESSING','GENERATING'].includes(current?.status)); attempt += 1) {
    await new Promise(resolve => window.setTimeout(resolve, 1200))
    current = await api.initialRouteDecisions.get(current.decisionId)
  }
  return current
}

function normalizeDecision(decision) {
  const recommendation = decision?.recommendation || decision?.agentRecommendation || null
  if (!recommendation) return decision
  const scoreMap = new Map((recommendation.routes || []).map(route => [String(route.routeId), route]))
  return {
    ...decision,
    ...recommendation,
    routes: (decision.routes || []).map(route => ({ ...route, ...(scoreMap.get(String(route.routeId)) || {}) }))
  }
}

async function planInitialRoutes() {
  if (!canPlanRoutes.value) return ElMessage.warning('请先确认发货仓、具体货物和可用车辆')
  if (!planningIdempotencyKey.value) planningIdempotencyKey.value = createStableKey('initial-plan')
  if (!creationIdempotencyKey.value) creationIdempotencyKey.value = createStableKey('create-task')
  planningRoutes.value = true
  try {
    const result = await api.initialRouteDecisions.create({
      originWarehouseId:Number(form.originWarehouseId),
      endLocation:form.endLocation.trim(),
      endLongitude:Number(form.endLongitude),
      endLatitude:Number(form.endLatitude),
      coordinateSystem:'GCJ02',
      candidateCount:3,
      planningMode:'INITIAL_MULTI_OBJECTIVE'
    }, planningIdempotencyKey.value)
    routeDecision.value = normalizeDecision(await waitForDecision(result))
    if (!sortedRoutes.value.length) throw new Error('后端未返回可用的候选路线')
    selectedRouteId.value = routeDecision.value.recommendedRouteId || sortedRoutes.value[0]?.routeId || ''
    routeDialogVisible.value = true
    persistDecisionDraft()
  } catch (error) {
    if (/410|41001|expired|过期/i.test(error.message)) {
      planningIdempotencyKey.value = ''
      ElMessage.warning('路线决策已过期，请重新生成候选路线')
    } else if (/503|50301|候选|route provider|service unavailable/i.test(error.message)) {
      ElMessage.error('暂时无法生成可用路线，请稍后使用同一按钮重试')
    } else ElMessage.error(error.message)
  } finally { planningRoutes.value = false }
}

function selectRoute(routeId) {
  selectedRouteId.value = routeId
  persistDecisionDraft()
}

async function createTask() {
  if (!canConfirm.value) return ElMessage.warning(decisionExpired.value ? '路线决策已过期，请重新规划' : '请选择一条候选路线')
  if (form.planTime?.length === 2 && new Date(form.planTime[1]) <= new Date(form.planTime[0])) return ElMessage.warning('计划结束时间必须晚于开始时间')
  saving.value = true
  persistDecisionDraft()
  try {
    const body = {
      routeDecisionId:routeDecision.value.decisionId,
      selectedRouteId:selectedRouteId.value,
      routeSelectionRemark:routeSelectionRemark.value.trim() || undefined,
      ownerId:Number(form.ownerId), cargoTypeId:Number(form.cargoTypeId),
      originWarehouseId:Number(form.originWarehouseId), cargoId:Number(form.cargoId), vehicleId:Number(form.vehicleId),
      endLocation:form.endLocation.trim(), endLongitude:Number(form.endLongitude), endLatitude:Number(form.endLatitude)
    }
    if (form.planTime?.length === 2) {
      body.plannedStartTime = new Date(form.planTime[0]).toISOString()
      body.planEndTime = new Date(form.planTime[1]).toISOString()
    }
    if (import.meta.env.DEV) {
      console.info('[from-warehouse] request', {
        endpoint:'/api/v1/transport-tasks/from-warehouse',
        routeDecisionId:body.routeDecisionId,
        selectedRouteId:body.selectedRouteId,
        ownerId:body.ownerId, cargoTypeId:body.cargoTypeId,
        originWarehouseId:body.originWarehouseId, cargoId:body.cargoId, vehicleId:body.vehicleId
      })
    }
    createdTask.value = await api.transportTasks.createFromWarehouse(body, creationIdempotencyKey.value)
    routeDialogVisible.value = false
    sessionStorage.removeItem(DRAFT_KEY)
    ElMessage.success('运输任务创建成功，所选路线已成为唯一 v1 ACTIVE 路线')
    clearRouteDecision()
    tasks.value = extractList(await api.transportTasks.list({ page:1, pageSize:100 }))
    await loadWarehouseResources()
  } catch (error) {
    if (/410|41001|expired|过期/i.test(error.message)) {
      clearRouteDecision()
      ElMessage.warning('候选路线已过期，出库信息已保留，请重新规划')
    } else if (/404|40401|not found|不存在|已失效/i.test(error.message)) {
      // 最新合同中的 404/40401 是业务资源/决策不存在，不是端口不存在。
      // 保留用户已填写的 owner/type/destination/warehouse，刷新仓内资源并要求重新预规划。
      const detail = error.message
      clearRouteDecision()
      try { await loadWarehouseResources() } catch { /* 页面仍保留表单 */ }
      ElMessage.warning(`业务数据已变化或决策已失效，请重新生成候选路线。${detail}`)
    } else if (/409|40901|40902|conflict|占用|状态已变化|不匹配/i.test(error.message)) {
      ElMessage.warning('决策或资源状态已变化，正在同步最新状态')
      try {
        routeDecision.value = normalizeDecision(await api.initialRouteDecisions.get(routeDecision.value.decisionId))
        if (routeDecision.value?.taskId) createdTask.value = await api.transportTasks.get(routeDecision.value.taskId)
        else await loadWarehouseResources({ cargoId:form.cargoId, vehicleId:form.vehicleId })
      } catch { /* 保留当前表单供用户重新规划 */ }
    } else ElMessage.error(error.message)
  } finally { saving.value = false }
}

async function restoreDecision() {
  const raw = sessionStorage.getItem(DRAFT_KEY)
  if (!raw) return
  restoring.value = true
  try {
    const draft = JSON.parse(raw)
    Object.assign(form, draft.form || {})
    planningIdempotencyKey.value = draft.planningIdempotencyKey || ''
    creationIdempotencyKey.value = draft.creationIdempotencyKey || ''
    routeSelectionRemark.value = draft.routeSelectionRemark || ''
    await loadWarehouseResources({ cargoId:draft.form?.cargoId, vehicleId:draft.form?.vehicleId })
    routeDecision.value = normalizeDecision(await api.initialRouteDecisions.get(draft.decisionId))
    selectedRouteId.value = draft.selectedRouteId || routeDecision.value.recommendedRouteId || sortedRoutes.value[0]?.routeId || ''
    if (routeDecision.value.status === 'CONFIRMED' && routeDecision.value.taskId) {
      createdTask.value = await api.transportTasks.get(routeDecision.value.taskId)
      sessionStorage.removeItem(DRAFT_KEY)
    } else if (!decisionExpired.value && routeDecision.value.status === 'PENDING') routeDialogVisible.value = true
    else sessionStorage.removeItem(DRAFT_KEY)
  } catch {
    sessionStorage.removeItem(DRAFT_KEY)
  } finally { restoring.value = false }
}

watch(() => form.ownerId, (value, oldValue) => { if (!restoring.value && oldValue != null && value !== oldValue) invalidateRecommendation() })
watch(() => form.cargoTypeId, (value, oldValue) => { if (!restoring.value && oldValue != null && value !== oldValue) invalidateRecommendation() })
watch(() => form.originWarehouseId, (value, oldValue) => {
  if (!restoring.value && value && value !== oldValue && warehouseCandidates.value.length) {
    clearRouteDecision()
    loadWarehouseResources()
  }
})
onMounted(async () => {
  clockTimer = window.setInterval(() => { clock.value = Date.now() }, 1000)
  await loadBaseData()
  await restoreDecision()
})
onBeforeUnmount(() => { if (clockTimer) window.clearInterval(clockTimer) })
</script>

<template>
  <PageHeader title="货物出库" subtitle="智能选仓后生成多条初始路线，由评分推荐并交由仓库管理员人工确认" />
  <DataState :loading="loading || restoring" :error="loadError" @retry="loadBaseData">
    <section class="outbound-board">
      <div class="outbound-left-column">
    <section class="warehouse-main">
      <article class="panel">
        <div class="panel-title"><div><h2>确定出库上下文</h2></div></div>
        <div class="binding-form">
          <label><span>货主</span><select v-model.number="form.ownerId"><option :value="null">请选择货主</option><option v-for="owner in owners" :key="idOf(owner)" :value="idOf(owner)">{{ ownerLabel(owner) }}</option></select></label>
          <label><span>货物种类</span><select v-model.number="form.cargoTypeId"><option :value="null">请选择货物</option><option v-for="type in cargoTypes" :key="type.id" :value="type.id">{{ type.name }}{{ type.unit ? `（${type.unit}）` : '' }}</option></select></label>
          <label><span>运输终点</span><span class="location-field"><input :value="form.endLocation" readonly placeholder="请在地图中选择终点" /><button type="button" class="mini" @click="locationPickerVisible=true">地图选点</button></span></label>
          <button class="primary" :disabled="!canRecommendWarehouse || recommending" @click="recommendWarehouses">{{ recommending ? '正在推荐…' : '推荐发货仓库' }}</button>
        </div>
      </article>

      <article class="panel">
        <div class="panel-title"><div><h2>确认资源并预规划</h2></div></div>
        <div class="binding-form" v-loading="loadingResources">
          <label><span>具体货物</span><select v-model.number="form.cargoId" :disabled="!availableCargos.length"><option :value="null">{{ availableCargos.length ? '请选择货物编号' : '该仓暂无可用货物' }}</option><option v-for="cargo in availableCargos" :key="cargo.id" :value="cargo.id">{{ cargo.cargoNo }} · {{ cargo.name }}</option></select></label>
          <label><span>运输车辆</span><select v-model.number="form.vehicleId" :disabled="!availableVehicles.length"><option :value="null">{{ availableVehicles.length ? '请选择车辆' : '该仓暂无可用车辆' }}</option><option v-for="vehicle in availableVehicles" :key="vehicle.id" :value="vehicle.id">{{ vehicle.plateNumber }} · {{ vehicle.type||'车辆' }}</option></select></label>
          <label><span>司机</span><input :value="selectedVehicle ? (selectedVehicle.driverName || selectedVehicle.driver?.name || '已绑定司机') : '选择车辆后自动显示'" disabled /></label>
          <label><span>运输起点</span><input :value="selectedWarehouse ? `${selectedWarehouse.warehouseName||selectedWarehouse.name} · ${selectedWarehouse.warehouseAddress||selectedWarehouse.address}` : '请先完成仓库推荐'" disabled /></label>
          <label><span>计划时间</span><el-date-picker v-model="form.planTime" type="datetimerange" :disabled-date="disablePastDate" start-placeholder="计划开始（可选）" end-placeholder="计划结束（可选）" style="width:100%" /></label>
          <button class="primary" :disabled="!canPlanRoutes" @click="planInitialRoutes">{{ planningRoutes ? '正在生成并评分…' : '生成候选路线并预览' }}</button>
        </div>
      </article>
    </section>

    <section v-if="createdTask" class="panel created-task-card">
      <div class="panel-title"><div><h2>最近创建成功</h2></div></div>
      <dl><div><dt>任务编号</dt><dd>{{ createdTask.taskNo||createdTask.id }}</dd></div><div><dt>起始仓库</dt><dd>#{{ createdTask.originWarehouseId }}</dd></div><div><dt>起点</dt><dd>{{ createdTask.startLocation }}</dd></div><div><dt>终点</dt><dd>{{ createdTask.endLocation }}</dd></div><div><dt>状态</dt><dd>{{ statusText[createdTask.status] || createdTask.status }}</dd></div></dl>
    </section>
      </div>

    <section class="outbound-data-grid">
      <article class="panel role-table-card">
        <div class="panel-title"><div><h2>货物库存</h2></div><button class="mini" @click="loadBaseData">刷新</button></div>
        <div class="table-wrap"><table><thead><tr><th>货物编号</th><th>货物名称</th><th>所在仓库</th><th>库存状态</th></tr></thead><tbody><tr v-for="cargo in inventoryRows" :key="cargo.id"><td>{{ cargoNumber(cargo) }}</td><td>{{ cargo.name || '—' }}</td><td>{{ cargo.warehouseName || warehouseMap[Number(cargo.warehouseId)]?.name || '—' }}</td><td><span :class="['inventory-status', cargo.inStock ? 'in-stock' : 'out-of-stock']">{{ cargo.inStock ? '有库存' : '已出库' }}</span></td></tr><tr v-if="!inventoryRows.length"><td colspan="4">暂无货物库存</td></tr></tbody></table></div>
      </article>
    </section>
    </section>
    <section class="panel role-table-card outbound-records-card">
      <div class="panel-title"><div><h2>出库记录</h2></div></div>
      <div class="table-wrap"><table><thead><tr><th>任务编号</th><th>货物编号</th><th>运输车辆</th><th>出库时间</th><th>状态</th></tr></thead><tbody><tr v-for="task in tasks" :key="task.id"><td>{{ task.taskNo || task.id }}</td><td>{{ cargoNumber(cargoMap[Number(task.cargoId)] || { id:task.cargoId }) }}</td><td>{{ plateNumber(vehicleMap[Number(task.vehicleId)] || { id:task.vehicleId }) }}</td><td>{{ dateText(task.createdAt || task.planStartTime || task.plannedStartTime) }}</td><td>{{ statusText[task.status] || task.status }}</td></tr><tr v-if="!tasks.length"><td colspan="5">暂无出库记录</td></tr></tbody></table></div>
    </section>
  </DataState>

  <div v-if="warehouseDialogVisible" class="warehouse-recommend-mask" @click.self="warehouseDialogVisible=false">
    <section class="warehouse-recommend-dialog" role="dialog" aria-modal="true" aria-label="选择推荐发货仓库">
      <header><div><h2>选择发货仓库</h2></div><button class="close-btn" @click="warehouseDialogVisible=false">×</button></header>
      <div class="warehouse-candidates">
        <button v-for="item in warehouseCandidates" :key="item.warehouseId" type="button" :class="['warehouse-candidate',{selected:Number(form.originWarehouseId)===Number(item.warehouseId)}]" @click="selectWarehouseCandidate(item)">
          <span><strong>{{ item.warehouseName }}</strong><b v-if="item.recommended">推荐</b></span><small>{{ item.warehouseAddress }}</small>
          <em>{{ (Number(item.distanceMeters)/1000).toFixed(1) }} 公里 · 约 {{ Math.ceil(Number(item.durationSeconds)/60) }} 分钟</em>
          <span class="candidate-resources"><small><b>可出库货物</b>{{ item.availableCargos?.length ? item.availableCargos.map(cargoNumber).join('、') : '暂无' }}</small><small><b>可调度车辆</b>{{ item.availableVehicles?.length ? item.availableVehicles.map(plateNumber).join('、') : '暂无' }}</small></span>
        </button>
      </div>
    </section>
  </div>

  <div v-if="routeDialogVisible && routeDecision" class="route-decision-mask" @click.self="routeDialogVisible=false">
    <section class="route-decision-dialog" role="dialog" aria-modal="true" aria-label="初始路线人工确认">
      <header class="route-decision-head">
        <div><span class="eyebrow">创建任务前 · 初始路线决策</span><h2>比较候选路线并人工确认</h2><p>{{ routeDecision.summary || routeDecision.explanation || '候选路线已生成，请结合时间、距离、路况和天气人工选择。' }}</p></div>
        <div class="decision-head-actions"><span :class="['decision-countdown',{expired:decisionExpired}]">{{ decisionExpired ? '决策已过期' : `剩余 ${countdownText}` }}</span><button class="close-btn" @click="routeDialogVisible=false">×</button></div>
      </header>
      <div class="route-decision-body">
        <div class="route-preview-map">
          <AMapView :task-routes="previewRoutes" task-routes-coordinate-system="GCJ02" :selected-task-id="selectedRouteId" task-routes-are-candidates :show-facilities="false" @select-task="selectRoute" />
        </div>
        <aside class="route-choice-panel">
          <div class="recommendation-source"><span>{{ recommendationTitle }}</span><small v-if="isSingleRouteDecision">当前客观上仅有一条可用路线，可直接确认</small><small v-else>评分与解释已由业务后端聚合</small></div>
          <button v-for="(route, routeIndex) in sortedRoutes" :key="route.routeId" type="button" :class="['route-score-card',{selected:String(route.routeId)===String(selectedRouteId)}]" @click="selectRoute(route.routeId)">
            <div class="route-card-top"><span class="route-rank">#{{ route.rank ?? routeIndex + 1 }} {{ route.displayName }}</span><b v-if="isSingleRouteDecision">唯一可用</b><b v-else-if="String(route.routeId)===String(routeDecision.recommendedRouteId)">系统推荐</b></div>
            <div class="route-score-line"><strong>{{ route.totalScore == null ? '—' : Number(route.totalScore).toFixed(1) }}</strong><span>{{ route.totalScore == null ? '待人工判断' : '综合分' }}</span><em>全程 {{ formatDistance(route.distanceMeters) }} · 预计 {{ formatDuration(route.referenceDurationSeconds) }}</em></div>
            <div class="score-grid"><span><b>预计用时</b>全程预计 {{ formatDuration(route.referenceDurationSeconds) }}。{{ dimensionCopy(route, 'time', '用时') }}</span><span><b>行驶距离</b>全程约 {{ formatDistance(route.distanceMeters) }}。{{ dimensionCopy(route, 'distance', '距离') }}</span><span><b>沿途路况</b>{{ trafficText(route) }}</span><span><b>天气</b>{{ weatherText(route) }}</span></div>
            <p class="route-explanation"><b>推荐解读</b>{{ route.explanation || route.reasons?.join('；') || '该路线已根据预计用时、距离、路况和天气综合比较，请结合实际调度安排确认。' }}</p>
            <ul v-if="route.highlights?.length" class="route-highlights"><li v-for="text in route.highlights" :key="text">{{ text }}</li></ul>
            <ul v-if="route.cautions?.length" class="route-cautions"><li v-for="text in route.cautions" :key="text">{{ text }}</li></ul>
          </button>
          <label class="route-remark"><span>人工选择备注（可选）</span><textarea v-model="routeSelectionRemark" maxlength="200" placeholder="例如：道路更熟悉、装卸时间更合适" @input="persistDecisionDraft" /></label>
        </aside>
      </div>
      <footer class="route-decision-footer">
        <div><strong>当前选择：{{ selectedRoute?.displayName || '尚未选择' }}</strong><span>前端只提交路线ID，不回传或修改路线几何与评分。</span></div>
        <div><button class="ghost" @click="routeDialogVisible=false">暂不创建</button><button class="primary" :disabled="!canConfirm" @click="createTask">{{ saving ? '正在创建任务…' : '确认路线并创建运输任务' }}</button></div>
      </footer>
    </section>
  </div>

  <LocationPicker v-if="locationPickerVisible" :model-value="locationPickerVisible" title="选择运输终点" :initial-name="form.endLocation" :initial-longitude="form.endLongitude" :initial-latitude="form.endLatitude" :show-coordinates="false" @update:model-value="locationPickerVisible=$event" @confirm="applyDestination" />
</template>

<style scoped>
.outbound-records-card{margin-top:18px}
.outbound-left-column,.outbound-data-grid{height:clamp(620px,calc(100vh - 280px),820px)}
.outbound-left-column>.warehouse-main{height:100%;grid-template-rows:auto minmax(0,1fr)}
.outbound-data-grid>.panel{display:flex;flex-direction:column;height:100%;overflow:hidden}
.outbound-data-grid .table-wrap{flex:1;min-height:0;overflow-y:auto}
.outbound-data-grid .table-wrap{max-height:680px;overflow-x:auto;overflow-y:auto;scrollbar-gutter:stable}
.outbound-data-grid thead{position:sticky;top:0;z-index:2;background:#f7f9fc}
.warehouse-recommend-mask{position:fixed;inset:0;z-index:1250;display:grid;place-items:center;padding:24px;background:rgba(15,23,42,.52);backdrop-filter:blur(6px)}
.warehouse-recommend-dialog{width:min(760px,94vw);max-height:min(760px,90vh);overflow:hidden;border-radius:16px;background:#f7f9fc;box-shadow:0 28px 70px rgba(15,23,42,.28)}
.warehouse-recommend-dialog>header{display:flex;align-items:center;justify-content:space-between;padding:18px 22px;border-bottom:1px solid #e2e8f0;background:#fff}
.warehouse-recommend-dialog h2{margin:0;font-size:22px}.warehouse-recommend-dialog>.warehouse-candidates{max-height:calc(90vh - 76px);overflow-y:auto;padding:18px}
.outbound-board{align-items:stretch}
.outbound-left-column,.outbound-data-grid{height:auto}
.outbound-left-column>.warehouse-main{height:auto;grid-template-rows:auto}
.outbound-data-grid{height:100%;min-height:0}
.outbound-data-grid>.panel{height:100%;min-height:0}
.outbound-records-card{clear:both;margin-top:20px;overflow:hidden}
.outbound-records-card .table-wrap{max-height:360px;overflow:auto}
.outbound-records-card thead{position:sticky;top:0;z-index:2;background:#f7f9fc}
.outbound-board{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:18px;align-items:start}.outbound-left-column,.warehouse-main,.outbound-data-grid{display:grid;grid-template-columns:1fr;gap:16px;min-width:0}.warehouse-main>.panel{height:auto}.outbound-data-grid{margin-top:0}.outbound-data-grid>.panel{min-width:0}.inventory-status{display:inline-flex;padding:4px 9px;border-radius:999px;font-size:12px;font-weight:700}.inventory-status.in-stock{background:#eaf8f0;color:#228455}.inventory-status.out-of-stock{background:#f2f4f7;color:#7b8798}.outbound-flow{display:grid;grid-template-columns:repeat(8,minmax(0,1fr));gap:8px;margin:0 0 16px;padding:16px;border:1px solid #e1e8f2;border-radius:14px;background:#fff}.outbound-step{display:flex;align-items:center;gap:8px;min-width:0;color:#7b899c;font-size:12px}.outbound-step b{display:grid;place-items:center;flex:0 0 28px;height:28px;border-radius:50%;background:#edf2f8;color:#61728a}.outbound-step.done{color:#2869cd}.outbound-step.done b{background:#e7f0ff;color:#2468df}.outbound-step span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.warehouse-candidates{display:grid;gap:12px;padding:0 18px 18px}.warehouse-candidate{display:grid;gap:8px;width:100%;padding:16px;text-align:left;border:1px solid #dce5f0;border-radius:12px;background:#fff;color:#27364a;cursor:pointer}.warehouse-candidate.selected{border-color:#4b83eb;background:#f3f7ff;box-shadow:0 0 0 2px rgba(75,131,235,.1)}.warehouse-candidate span{display:flex;align-items:center;justify-content:space-between}.warehouse-candidate b{padding:4px 8px;border-radius:8px;background:#e9f2ff;color:#2468d8;font-size:12px}.warehouse-candidate small,.warehouse-candidate em{color:#74849a;font-style:normal}.form-hint{margin:0;color:#78889c;font-size:12px;line-height:1.6}.created-task-card{margin-top:0}.created-task-card dl{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px;padding:18px}.created-task-card dl div{padding:12px;border-radius:8px;background:#f7f9fc}.created-task-card dt{color:#8190a4;font-size:12px}.created-task-card dd{margin:8px 0 0;font-weight:700;color:#26364b}
.route-decision-mask{position:fixed;inset:0;z-index:1200;display:grid;place-items:center;padding:24px;background:rgba(15,23,42,.56);backdrop-filter:blur(8px)}.route-decision-dialog{display:flex;flex-direction:column;width:min(1500px,96vw);height:min(900px,94vh);overflow:hidden;border:1px solid rgba(255,255,255,.42);border-radius:16px;background:#f7f9fc;box-shadow:0 32px 80px rgba(15,23,42,.3)}.route-decision-head{display:flex;justify-content:space-between;gap:24px;padding:20px 24px;border-bottom:1px solid #e2e8f0;background:#fff}.route-decision-head h2{margin:4px 0;font-size:24px}.route-decision-head p{margin:0;color:#64748b}.eyebrow{color:#356fe5;font-size:12px;font-weight:800;letter-spacing:.08em}.decision-head-actions{display:flex;align-items:flex-start;gap:12px}.decision-countdown{padding:8px 12px;border-radius:8px;background:#eaf8f1;color:#18794e;font-weight:800}.decision-countdown.expired{background:#fff0ef;color:#c23b3b}.route-decision-body{display:grid;grid-template-columns:minmax(0,1.7fr) minmax(360px,.8fr);gap:16px;min-height:0;flex:1;padding:16px}.route-preview-map{min-height:0;overflow:hidden;border:1px solid #dfe6ef;border-radius:14px;background:#fff}.route-preview-map :deep(.amap-shell){height:100%;min-height:520px}.route-choice-panel{display:flex;flex-direction:column;gap:12px;min-height:0;overflow-y:auto;padding-right:4px}.recommendation-source{display:flex;justify-content:space-between;gap:12px;padding:12px 14px;border:1px solid #dce5f0;border-radius:10px;background:#fff}.recommendation-source span{font-weight:800;color:#315fba}.recommendation-source small{color:#7b8798}.agent-score-fallback{padding:10px 12px;border:1px solid #f0d49d;border-radius:10px;background:#fff9ed;color:#8b651f;font-size:12px;line-height:1.6}.route-score-card{display:grid;gap:10px;padding:14px;border:1px solid #dce4ee;border-radius:12px;background:#fff;text-align:left;color:#253349;cursor:pointer;transition:.18s}.route-score-card:hover{transform:translateY(-1px);box-shadow:0 10px 24px rgba(34,55,86,.09)}.route-score-card.selected{border-color:#356fe5;box-shadow:0 0 0 3px rgba(53,111,229,.12)}.route-card-top,.route-score-line{display:flex;align-items:center;gap:10px}.route-card-top{justify-content:space-between}.route-card-top b{padding:4px 8px;border-radius:7px;background:#eaf1ff;color:#275fc9;font-size:12px}.route-rank{font-weight:850}.route-score-line strong{color:#245fcf;font-size:26px}.route-score-line span{color:#738197;font-size:12px}.route-score-line em{margin-left:auto;color:#526176;font-style:normal}.score-grid{display:grid;grid-template-columns:1fr 1fr;gap:8px 12px;color:#66758a;font-size:12px}.score-grid span{display:grid;gap:3px;padding:8px;border-radius:8px;background:#f7f9fc;line-height:1.45}.score-grid b{color:#34445a;font-size:11px}.route-score-card p{margin:0;color:#526176;font-size:12px}.route-highlights,.route-cautions{display:grid;gap:4px;margin:0;padding-left:18px;color:#287354;font-size:12px;line-height:1.5}.route-cautions{color:#9a681d}.route-remark{display:grid;gap:8px}.route-remark span{font-weight:700}.route-remark textarea{min-height:72px;resize:vertical}.route-decision-footer{display:flex;align-items:center;justify-content:space-between;gap:24px;padding:16px 24px;border-top:1px solid #dfe6ef;background:#fff}.route-decision-footer>div{display:flex;gap:12px;align-items:center}.route-decision-footer span{color:#748196;font-size:12px}.route-decision-footer button{min-width:120px}
.candidate-resources{display:grid!important;grid-template-columns:1fr 1fr;gap:8px}.candidate-resources small{display:flex;gap:8px;align-items:center;min-width:0;padding:8px 10px;border-radius:8px;background:#f7f9fc}.candidate-resources small b{flex:0 0 auto;padding:0;background:none;color:#42546c}.route-explanation{display:grid;gap:4px;padding:10px 12px;border-left:3px solid #5a86df;border-radius:4px;background:#f3f7ff;line-height:1.55}.route-explanation b{color:#315fba}
@media(max-width:1280px){.outbound-board{grid-template-columns:1fr}.route-decision-body{grid-template-columns:1fr 380px}}@media(max-width:900px){.route-decision-mask{padding:8px}.route-decision-dialog{width:100%;height:98vh}.route-decision-body{grid-template-columns:1fr;overflow-y:auto}.route-preview-map{min-height:420px}.route-choice-panel{overflow:visible}.route-decision-footer,.route-decision-head{align-items:flex-start;flex-direction:column}.created-task-card dl,.candidate-resources{grid-template-columns:1fr}}
@media(max-width:1280px){.outbound-left-column,.outbound-data-grid{height:auto}.outbound-left-column>.warehouse-main{height:auto;grid-template-rows:auto}.outbound-data-grid>.panel{height:auto}.outbound-data-grid .table-wrap{max-height:620px}}
@media(min-width:1281px){.outbound-board{align-items:stretch}.outbound-left-column,.outbound-data-grid{height:auto}.outbound-left-column>.warehouse-main{height:100%;grid-template-rows:auto minmax(0,1fr)}.outbound-left-column>.warehouse-main>.panel:last-child{height:100%}.outbound-data-grid{height:100%;min-height:0}.outbound-data-grid>.panel{height:100%;min-height:0}.outbound-records-card{margin-top:20px;overflow:hidden}}
</style>
