<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import DataState from '../components/DataState.vue'
import PageHeader from '../components/PageHeader.vue'
import UiIcon from '../components/UiIcon.vue'
import { api, extractList } from '../api/http'
import { mergeEtaUpdate, useLogisticsEtaWebSocket } from '../composables/useLogisticsEtaWebSocket'

const loading = ref(false)
const error = ref('')
const vehicles = ref([])
const tasks = ref([])
const cargos = ref([])
const alarms = ref([])
const etaWs = useLogisticsEtaWebSocket((message) => {
  tasks.value = tasks.value.map(task => mergeEtaUpdate(task, message))
})
let etaRefreshTimer = null
const count = (list, field, value) => list.filter(item => item[field] === value).length
const vehicleMap = computed(() => Object.fromEntries(vehicles.value.map(item => [Number(item.id ?? item.vehicleId), item])))
const cargoMap = computed(() => Object.fromEntries(cargos.value.map(item => [Number(item.id ?? item.cargoId), item])))
const activeTasks = computed(() => count(tasks.value, 'status', 'TRANSPORTING'))
const completedTasks = computed(() => count(tasks.value, 'status', 'COMPLETED'))
const abnormalTasks = computed(() => count(tasks.value, 'status', 'ABNORMAL'))
const waitingTasks = computed(() => count(tasks.value, 'status', 'WAITING'))
const alarmTypeCounts = computed(() => alarms.value.reduce((result, item) => {
  const key = item.alarmType ?? item.alarm_type ?? 'UNKNOWN'
  result[key] = (result[key] || 0) + 1
  return result
}, {}))
const alarmTypeText = { ROUTE_DEVIATION: '路线偏离', ABNORMAL_STOP: '异常停留', ABNORMAL_OPEN: '异常开箱', SPEEDING: '超速', OFFLINE: '设备离线', UNKNOWN: '其他告警' }
const statusText = { WAITING: '待运输', TRANSPORTING: '运输中', COMPLETED: '已完成', ABNORMAL: '异常', CANCELLED: '已取消' }
const vehicleStatusRows = computed(() => [
  { label: '运输中', value: count(vehicles.value, 'status', 'TRANSPORTING'), tone: 'blue' },
  { label: '空闲', value: count(vehicles.value, 'status', 'IDLE'), tone: 'green' },
  { label: '停用/离线', value: vehicles.value.filter(item => ['DISABLED', 'OFFLINE'].includes(item.status)).length, tone: 'gray' },
  { label: '维护中', value: count(vehicles.value, 'status', 'MAINTENANCE'), tone: 'amber' },
])
const taskStatusRows = computed(() => [
  { label: '待运输', value: waitingTasks.value, tone: 'amber' },
  { label: '运输中', value: activeTasks.value, tone: 'blue' },
  { label: '异常', value: abnormalTasks.value, tone: 'red' },
  { label: '已完成', value: completedTasks.value, tone: 'green' },
])
const alarmRows = computed(() => Object.entries(alarmTypeCounts.value).map(([key, value], index) => ({ label: alarmTypeText[key] || key, value, tone: ['red', 'amber', 'violet', 'blue'][index % 4] })))
const percent = (value, total) => total ? Math.max(value ? 6 : 0, Math.round(value / total * 100)) : 0
const eta = task => task.estimatedArrivalTime?.replace('T', ' ').slice(0, 16) || '待计算'

async function refreshActiveEta() {
  const active = tasks.value.filter(task => ['WAITING', 'TRANSPORTING'].includes(task.status))
  const results = await Promise.allSettled(active.map(task => api.transportTasks.get(task.id)))
  const details = new Map(active.map((task, index) => [Number(task.id), results[index]?.status === 'fulfilled' ? results[index].value : null]))
  tasks.value = tasks.value.map(task => details.get(Number(task.id)) ? { ...task, ...details.get(Number(task.id)) } : task)
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [vehicleResult, taskResult, cargoResult, alarmResult] = await Promise.all([
      api.vehicles.list({ page: 1, pageSize: 100 }),
      api.transportTasks.list({ page: 1, pageSize: 100 }),
      api.cargos.list({ page: 1, pageSize: 100 }),
      api.alarms.list({ page: 1, pageSize: 100 }),
    ])
    vehicles.value = extractList(vehicleResult)
    tasks.value = extractList(taskResult)
    cargos.value = extractList(cargoResult)
    alarms.value = extractList(alarmResult)
  } catch (cause) {
    error.value = `统计数据加载失败：${cause.message}`
    ElMessage.error(error.value)
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await load()
  etaWs.connect()
  refreshActiveEta().catch(() => {})
  etaRefreshTimer = window.setInterval(() => refreshActiveEta().catch(() => {}), 30000)
})
onBeforeUnmount(() => { if (etaRefreshTimer) window.clearInterval(etaRefreshTimer) })
</script>

<template>
  <PageHeader title="数据统计" subtitle="车辆、任务、货物与告警的实时运营概览" />
  <DataState :loading="loading" :error="error" @retry="load">
    <section class="dispatcher-insight-grid">
      <article class="panel insight-card">
        <div class="insight-head"><div><span class="insight-eyebrow">FLEET</span><h3>车辆状态分布</h3></div><span class="insight-total">{{ vehicles.length }}<small>辆</small></span></div>
        <div class="distribution-list"><div v-for="item in vehicleStatusRows" :key="item.label" class="distribution-row"><div class="distribution-label"><span><i :class="`tone-${item.tone}`" />{{ item.label }}</span><strong>{{ item.value }}</strong></div><div class="distribution-track"><i :class="`tone-${item.tone}`" :style="{ width: `${percent(item.value, vehicles.length)}%` }" /></div></div></div>
      </article>
      <article class="panel insight-card alarm-insight">
        <div class="insight-head"><div><span class="insight-eyebrow">ALERT</span><h3>告警类型统计</h3></div><span class="insight-total danger">{{ alarms.length }}<small>条</small></span></div>
        <div v-if="alarmRows.length" class="distribution-list"><div v-for="item in alarmRows" :key="item.label" class="distribution-row"><div class="distribution-label"><span><i :class="`tone-${item.tone}`" />{{ item.label }}</span><strong>{{ item.value }}</strong></div><div class="distribution-track"><i :class="`tone-${item.tone}`" :style="{ width: `${percent(item.value, alarms.length)}%` }" /></div></div></div>
        <div v-else class="insight-empty"><UiIcon name="shield" /><span>当前暂无告警记录</span></div>
      </article>
      <article class="panel insight-card">
        <div class="insight-head"><div><span class="insight-eyebrow">TASK</span><h3>任务执行情况</h3></div><span class="insight-total">{{ tasks.length }}<small>单</small></span></div>
        <div class="distribution-list"><div v-for="item in taskStatusRows" :key="item.label" class="distribution-row"><div class="distribution-label"><span><i :class="`tone-${item.tone}`" />{{ item.label }}</span><strong>{{ item.value }}</strong></div><div class="distribution-track"><i :class="`tone-${item.tone}`" :style="{ width: `${percent(item.value, tasks.length)}%` }" /></div></div></div>
      </article>
    </section>

    <section class="panel role-table-card dispatcher-task-table">
      <div class="panel-title"><div><h2>任务明细</h2><span>真实 ETA 由任务详情初始化并通过 WebSocket 更新；当前共 {{ tasks.length }} 条</span></div><el-button @click="load">刷新数据</el-button></div>
      <div class="table-wrap"><table><thead><tr><th>任务</th><th>车辆</th><th>货物</th><th>运输路线</th><th>ETA</th><th>状态</th></tr></thead><tbody><tr v-for="task in tasks" :key="task.id"><td><strong class="task-number-cell">{{ task.taskNo }}</strong></td><td>{{ vehicleMap[Number(task.vehicleId)]?.plateNumber || `#${task.vehicleId}` }}</td><td>{{ cargoMap[Number(task.cargoId)]?.name || `#${task.cargoId}` }}</td><td><span class="route-cell"><b>{{ task.startLocation || '未填写' }}</b><i>→</i><b>{{ task.endLocation || '未填写' }}</b></span></td><td>{{ eta(task) }}</td><td><span class="binding-status stats-status" :class="`status-${String(task.status).toLowerCase()}`">{{ statusText[task.status] || task.status }}</span></td></tr><tr v-if="!tasks.length"><td colspan="6">暂无任务数据</td></tr></tbody></table></div>
    </section>
  </DataState>
</template>
