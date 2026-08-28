<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '../components/PageHeader.vue'
import { api, extractList } from '../api/http'
import { mergeEtaUpdate, useLogisticsEtaWebSocket } from '../composables/useLogisticsEtaWebSocket'

const loading = ref(false), tasks = ref([]), cargos = ref([]), vehicles = ref([])
const etaWs = useLogisticsEtaWebSocket((message) => {
  tasks.value = tasks.value.map(task => mergeEtaUpdate(task, message))
})
const cargoMap = computed(() => Object.fromEntries(cargos.value.map(c => [Number(c.id), c])))
const vehicleMap = computed(() => Object.fromEntries(vehicles.value.map(v => [Number(v.id), v])))
const transportingTasks = computed(() => tasks.value.filter(t => t.status === 'TRANSPORTING'))
const delayedTasks = computed(() => transportingTasks.value.filter(t => { const eta = new Date(t.estimatedArrivalTime).getTime(); return Number.isFinite(eta) && eta < Date.now() }))
const completedCount = computed(() => tasks.value.filter(t => t.status === 'COMPLETED').length)
const statusText = { WAITING: '待运输', TRANSPORTING: '运输中', COMPLETED: '已完成', ABNORMAL: '异常', CANCELLED: '已取消' }
function dateText(value) { return value ? String(value).replace('T', ' ').slice(0, 16) : '待后端计算' }
function progress(task) {
  if (task.status === 'COMPLETED') return 100
  if (task.status === 'WAITING') return 0
  const start = new Date(task.planStartTime).getTime(), end = new Date(task.planEndTime).getTime()
  if (!Number.isFinite(start) || !Number.isFinite(end) || end <= start) return null
  return Math.max(1, Math.min(99, Math.round((Date.now() - start) / (end - start) * 100)))
}
const averageProgress = computed(() => { const values = transportingTasks.value.map(progress).filter(Number.isFinite); return values.length ? Math.round(values.reduce((sum, n) => sum + n, 0) / values.length) : 0 })
let refreshTimer = null
async function loadTaskEta(activeOnly = false) {
  const targets = activeOnly ? tasks.value.filter(task => ['WAITING', 'TRANSPORTING'].includes(task.status)) : tasks.value
  const details = await Promise.allSettled(targets.map(task => api.transportTasks.get(task.id)))
  const detailMap = new Map(targets.map((task, index) => [Number(task.id), details[index]?.status === 'fulfilled' ? details[index].value : null]))
  tasks.value = tasks.value.map(task => detailMap.get(Number(task.id)) ? { ...task, ...detailMap.get(Number(task.id)) } : task)
}
async function load() {
  loading.value = true
  try {
    const [taskResult, cargoResult, vehicleResult] = await Promise.all([api.transportTasks.list({ page: 1, pageSize: 100 }), api.cargos.list({ page: 1, pageSize: 100 }), api.vehicles.list({ page: 1, pageSize: 100 })])
    tasks.value = extractList(taskResult); cargos.value = extractList(cargoResult); vehicles.value = extractList(vehicleResult)
  } catch (error) { ElMessage.error(`ETA 数据加载失败：${error.message}`) }
  finally { loading.value = false }
}
onMounted(async () => { await load(); etaWs.connect(); loadTaskEta(true).catch(() => {}); refreshTimer = window.setInterval(() => loadTaskEta(true).catch(() => {}), 30000) })
onBeforeUnmount(() => { if (refreshTimer) window.clearInterval(refreshTimer) })
</script>

<template>
  <PageHeader title="预计到达时间" subtitle="基于云端运输任务的 ETA 与计划时间" />
  <section class="stats-grid" v-loading="loading"><article class="stat-card"><div class="stat-label">运输中任务</div><div class="stat-value">{{ transportingTasks.length }}</div><div class="stat-foot">当前货主在途</div></article><article class="stat-card"><div class="stat-label">平均计划进度</div><div class="stat-value">{{ averageProgress }}%</div><div class="stat-foot">按计划时间推算</div></article><article class="stat-card"><div class="stat-label">已完成</div><div class="stat-value">{{ completedCount }}</div><div class="stat-foot">云端已完成任务</div></article><article class="stat-card"><div class="stat-label">超过 ETA</div><div class="stat-value">{{ delayedTasks.length }}</div><div class="stat-foot">当前仍在途</div></article></section>
  <section class="panel page-panel" v-loading="loading"><div class="panel-title"><div><h2>运输任务 ETA</h2><span>任务详情每 30 秒兜底刷新；实时 ETA WebSocket：{{ etaWs.status.value === 'open' ? '已连接' : etaWs.status.value }}。ETA 为空时不使用计划结束时间冒充</span></div><button class="mini" @click="load">刷新</button></div><div class="table-wrap"><table><thead><tr><th>运单号</th><th>货物</th><th>车辆</th><th>目的地</th><th>预计到达时间</th><th>ETA 更新时间</th><th>剩余路程</th><th>状态</th></tr></thead><tbody><tr v-for="task in tasks" :key="task.id"><td>{{ task.taskNo }}</td><td>{{ cargoMap[Number(task.cargoId)]?.name || `#${task.cargoId}` }}</td><td>{{ vehicleMap[Number(task.vehicleId)]?.plateNumber || `#${task.vehicleId}` }}</td><td>{{ task.endLocation || '--' }}</td><td>{{ dateText(task.estimatedArrivalTime) }}</td><td>{{ dateText(task.etaCalculatedAt) }}</td><td>{{ task.remainingDistanceMeters == null ? '--' : `${(Number(task.remainingDistanceMeters) / 1000).toFixed(1)} km` }}</td><td><span class="task-status">{{ statusText[task.status] || task.status }}</span></td></tr><tr v-if="!tasks.length"><td colspan="8">暂无 ETA 任务数据</td></tr></tbody></table></div></section>
</template>
