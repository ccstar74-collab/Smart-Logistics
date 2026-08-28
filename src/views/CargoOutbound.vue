<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute } from 'vue-router'
import DataState from '../components/DataState.vue'
import LocationPicker from '../components/LocationPicker.vue'
import PageHeader from '../components/PageHeader.vue'
import { api, extractList } from '../api/http'
import { validateShipmentForm } from '../utils/validation'

const route = useRoute()
const loading = ref(false)
const saving = ref(false)
const loadError = ref('')
const locationTarget = ref('')
const cargos = ref([])
const availableCargos = ref([])
const vehicles = ref([])
const tasks = ref([])
const drivers = ref([])
const owners = ref([])
const form = reactive({ ownerId: null, cargoId: null, vehicleId: null, startLocation: '', startLongitude: null, startLatitude: null, endLocation: '', endLongitude: null, endLatitude: null, planTime: [] })

const idOf = (item, type) => item?.id ?? item?.[`${type}Id`] ?? item?.userId ?? item?.value
const labelOf = (item, type) => {
  if (!item) return `未知${type}`
  const account = item.username ?? item.account ?? item.userName ?? item.label ?? ''
  const name = item.name ?? item.realName ?? item.ownerName ?? item.driverName ?? item.companyName ?? ''
  return [account, name].filter(Boolean).join(' · ') || `${type} #${idOf(item, type === '司机' ? 'driver' : 'owner')}`
}
const cargoMap = computed(() => Object.fromEntries(cargos.value.map((item) => [Number(item.id), item])))
const vehicleMap = computed(() => Object.fromEntries(vehicles.value.map((item) => [Number(item.id), item])))
const ownerMap = computed(() => Object.fromEntries(owners.value.map((item) => [Number(idOf(item, 'owner')), item])))
const driverMap = computed(() => Object.fromEntries(drivers.value.map((item) => [Number(idOf(item, 'driver')), item])))
const activeCargoIds = computed(() => new Set(tasks.value.filter((item) => ['WAITING', 'TRANSPORTING'].includes(item.status)).map((item) => Number(item.cargoId))))
const waitingCargos = computed(() => availableCargos.value.filter((item) => (item.status == null || item.status === 'WAITING') && !activeCargoIds.value.has(Number(item.id))))
const idleVehicles = computed(() => vehicles.value.filter((item) => item.status === 'IDLE'))
const selectedVehicle = computed(() => vehicleMap.value[Number(form.vehicleId)])
const selectedDriver = computed(() => driverMap.value[Number(selectedVehicle.value?.driverId)])
const statusText = { WAITING: '待运输', TRANSPORTING: '运输中', COMPLETED: '已完成', ABNORMAL: '异常', CANCELLED: '已取消' }
const statusClass = (status) => `binding-status status-${String(status || '').toLowerCase()}`
const dateText = (value) => value ? String(value).replace('T', ' ').slice(0, 16) : '待计算'
const pickerProps = computed(() => locationTarget.value === 'start'
  ? { title: '选择运输起点', initialName: form.startLocation, initialLongitude: form.startLongitude, initialLatitude: form.startLatitude }
  : { title: '选择运输终点', initialName: form.endLocation, initialLongitude: form.endLongitude, initialLatitude: form.endLatitude })
const disablePastDate = (date) => date.getTime() < new Date().setHours(0, 0, 0, 0)

function submitErrorText(error) {
  const candidates = [
    typeof error === 'string' ? error : '',
    error?.message,
    error?.detail,
    error?.error,
    error?.response?.data?.message,
    error?.response?.data?.detail,
  ]
  return candidates.find(value => typeof value === 'string' && value.trim())?.trim()
    || '创建运输任务失败，请检查所选货物、车辆和计划时间后重试'
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const results = await Promise.all([
      api.cargos.list({ page: 1, pageSize: 100 }),
      api.cargos.available().catch(() => api.cargos.list({ page: 1, pageSize: 100, status: 'WAITING' })),
      api.vehicles.list({ page: 1, pageSize: 100 }),
      api.transportTasks.list({ page: 1, pageSize: 100 }),
      api.drivers.options(),
      api.owners.options(),
    ])
    ;[cargos.value, availableCargos.value, vehicles.value, tasks.value, drivers.value, owners.value] = results.map(extractList)
    const queryOwner = Number(route.query.ownerId)
    const queryCargo = Number(route.query.cargoId)
    form.ownerId = owners.value.some((item) => Number(idOf(item, 'owner')) === queryOwner) ? queryOwner : (idOf(owners.value[0], 'owner') ?? null)
    form.cargoId = waitingCargos.value.some((item) => Number(item.id) === queryCargo) ? queryCargo : (waitingCargos.value[0]?.id ?? null)
    if (!idleVehicles.value.some((item) => Number(item.id) === Number(form.vehicleId))) form.vehicleId = idleVehicles.value[0]?.id ?? null
  } catch (error) {
    loadError.value = `货物出库数据加载失败：${error.message}`
  } finally {
    loading.value = false
  }
}

function applyLocation(point) {
  const prefix = locationTarget.value === 'start' ? 'start' : 'end'
  form[`${prefix}Location`] = point.name
  form[`${prefix}Longitude`] = point.longitude
  form[`${prefix}Latitude`] = point.latitude
}

async function bind() {
  const validationError = validateShipmentForm(form)
  if (validationError) return ElMessage.warning(validationError)
  if (!selectedDriver.value) return ElMessage.warning('所选车辆尚未绑定真实司机账号')
  saving.value = true
  try {
    await api.transportTasks.create({
      cargoId: form.cargoId, ownerId: form.ownerId, vehicleId: form.vehicleId,
      startLocation: form.startLocation.trim(), endLocation: form.endLocation.trim(),
      startLongitude: Number(form.startLongitude), startLatitude: Number(form.startLatitude),
      endLongitude: Number(form.endLongitude), endLatitude: Number(form.endLatitude),
      planStartTime: new Date(form.planTime[0]).toISOString(), planEndTime: new Date(form.planTime[1]).toISOString(),
    })
    ElMessage.success('出库成功，运输任务已创建')
    Object.assign(form, { cargoId: null, vehicleId: null, startLocation: '', startLongitude: null, startLatitude: null, endLocation: '', endLongitude: null, endLatitude: null, planTime: [] })
    await load()
  } catch (error) {
    ElMessage.error({ message: submitErrorText(error), showClose: true, duration: 6000 })
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <PageHeader title="货物出库" subtitle="一次完成货主、货物、车辆、司机及起终点绑定，并创建运输任务" />
  <DataState :loading="loading" :error="loadError" @retry="load">
    <section class="warehouse-main">
      <article class="panel">
        <div class="panel-title"><div><h2>办理货物出库</h2><span>提交成功后生成运输任务并正式绑定货主</span></div></div>
        <div class="binding-form">
          <label><span>绑定货主</span><select v-model.number="form.ownerId"><option :value="null">请选择货主</option><option v-for="owner in owners" :key="idOf(owner, 'owner')" :value="idOf(owner, 'owner')">{{ labelOf(owner, '货主') }}</option></select></label>
          <label><span>出库货物</span><select v-model.number="form.cargoId"><option :value="null">请选择可出库库存</option><option v-for="cargo in waitingCargos" :key="cargo.id" :value="cargo.id">{{ cargo.cargoNo }} · {{ cargo.name }}</option></select></label>
          <label><span>运输车辆</span><select v-model.number="form.vehicleId"><option :value="null">请选择空闲车辆</option><option v-for="vehicle in idleVehicles" :key="vehicle.id" :value="vehicle.id">{{ vehicle.plateNumber }} · {{ vehicle.type }}</option></select></label>
          <label><span>绑定司机</span><input :value="selectedDriver ? labelOf(selectedDriver, '司机') : '所选车辆未绑定司机，无法出库'" disabled /></label>
          <label><span>运输起点</span><span class="location-field"><input v-model="form.startLocation" readonly placeholder="请在地图上选择运输起点" /><button type="button" class="mini" @click="locationTarget = 'start'">地图选点</button></span></label>
          <label><span>运输终点</span><span class="location-field"><input v-model="form.endLocation" readonly placeholder="请在地图上选择运输终点" /><button type="button" class="mini" @click="locationTarget = 'end'">地图选点</button></span></label>
          <label><span>计划时间</span><el-date-picker v-model="form.planTime" type="datetimerange" :disabled-date="disablePastDate" start-placeholder="计划开始（须晚于当前时间）" end-placeholder="计划结束" style="width:100%" /></label>
          <button class="primary" :disabled="saving" @click="bind">{{ saving ? '正在创建任务…' : '确认出库并创建任务' }}</button>
        </div>
      </article>
      <article class="panel role-table-card"><div class="panel-title"><div><h2>可出库货物</h2><span>未进入活跃任务，共 {{ waitingCargos.length }} 条</span></div><button class="mini" @click="load">刷新</button></div><div class="table-wrap"><table><thead><tr><th>货物编号</th><th>名称</th><th>当前货主</th><th>重量</th><th>状态</th></tr></thead><tbody><tr v-for="cargo in waitingCargos" :key="cargo.id"><td>{{ cargo.cargoNo }}</td><td>{{ cargo.name }}</td><td>{{ cargo.ownerId == null ? '未分配库存' : labelOf(ownerMap[Number(cargo.ownerId)], '货主') }}</td><td>{{ cargo.weight }} kg</td><td><span :class="statusClass(cargo.status)">{{ statusText[cargo.status] || cargo.status }}</span></td></tr><tr v-if="!waitingCargos.length"><td colspan="5">暂无可出库货物</td></tr></tbody></table></div></article>
    </section>
    <section class="panel role-table-card"><div class="panel-title"><div><h2>出库记录与任务状态</h2><span>ETA 仅展示后端实时计算结果</span></div></div><div class="table-wrap"><table><thead><tr><th>任务编号</th><th>货物</th><th>货主账号</th><th>车牌</th><th>司机账号</th><th>ETA</th><th>状态</th></tr></thead><tbody><tr v-for="task in tasks" :key="task.id"><td>{{ task.taskNo }}</td><td>{{ cargoMap[Number(task.cargoId)]?.name || `#${task.cargoId}` }}</td><td>{{ labelOf(ownerMap[Number(task.ownerId ?? cargoMap[Number(task.cargoId)]?.ownerId)], '货主') }}</td><td>{{ vehicleMap[Number(task.vehicleId)]?.plateNumber || `#${task.vehicleId}` }}</td><td>{{ labelOf(driverMap[Number(vehicleMap[Number(task.vehicleId)]?.driverId)], '司机') }}</td><td>{{ dateText(task.estimatedArrivalTime) }}</td><td><span :class="statusClass(task.status)">{{ statusText[task.status] || task.status }}</span></td></tr><tr v-if="!tasks.length"><td colspan="7">暂无出库任务记录</td></tr></tbody></table></div></section>
  </DataState>
  <LocationPicker v-if="locationTarget" :model-value="Boolean(locationTarget)" v-bind="pickerProps" :show-coordinates="false" @update:model-value="value => { if (!value) locationTarget = '' }" @confirm="applyLocation" />
</template>
