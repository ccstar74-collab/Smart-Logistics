<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '../components/PageHeader.vue'
import { api, extractList } from '../api/http'

const loading = ref(false), vehicles = ref([]), tasks = ref([]), cargos = ref([]), alarms = ref([])
const count = (list, field, value) => list.filter(item => item[field] === value).length
const vehicleMap = computed(() => Object.fromEntries(vehicles.value.map(v => [v.id ?? v.vehicleId, v])))
const cargoMap = computed(() => Object.fromEntries(cargos.value.map(c => [c.id ?? c.cargoId, c])))
const onlineVehicles = computed(() => vehicles.value.filter(v => v.status !== 'DISABLED' && v.status !== 'OFFLINE').length)
const activeTasks = computed(() => count(tasks.value, 'status', 'TRANSPORTING'))
const completedTasks = computed(() => count(tasks.value, 'status', 'COMPLETED'))
const abnormalTasks = computed(() => count(tasks.value, 'status', 'ABNORMAL'))
const alarmTypeCounts = computed(() => alarms.value.reduce((result, item) => { result[item.alarmType || 'UNKNOWN'] = (result[item.alarmType || 'UNKNOWN'] || 0) + 1; return result }, {}))
const statusText = { WAITING:'待运输', TRANSPORTING:'运输中', COMPLETED:'已完成', ABNORMAL:'异常', CANCELLED:'已取消' }

async function load() {
  loading.value = true
  try {
    const [vehicleResult, taskResult, cargoResult, alarmResult] = await Promise.all([
      api.vehicles.list({ page:1, pageSize:100 }), api.transportTasks.list({ page:1, pageSize:100 }),
      api.cargos.list({ page:1, pageSize:100 }), api.alarms.list({ page:1, pageSize:100 })
    ])
    vehicles.value = extractList(vehicleResult); tasks.value = extractList(taskResult)
    cargos.value = extractList(cargoResult); alarms.value = extractList(alarmResult)
  } catch (error) { ElMessage.error(`统计数据加载失败：${error.message}`) }
  finally { loading.value = false }
}
const eta = task => (task.estimatedArrivalTime || task.planEndTime)?.replace('T',' ').slice(0,16) || '待计算'
onMounted(load)
</script>

<template>
  <PageHeader title="数据统计" subtitle="根据车辆、运输任务、货物和告警云端 API 实时汇总" />
  <div v-loading="loading">
    <section class="stats-grid">
      <article class="stat-card"><div class="stat-label">在线车辆</div><div class="stat-value">{{onlineVehicles}}</div><div class="stat-foot">排除停用/离线车辆</div></article>
      <article class="stat-card"><div class="stat-label">进行中任务</div><div class="stat-value">{{activeTasks}}</div><div class="stat-foot">运输中</div></article>
      <article class="stat-card"><div class="stat-label">告警总数</div><div class="stat-value">{{alarms.length}}</div><div class="stat-foot">当前权限可见记录</div></article>
      <article class="stat-card"><div class="stat-label">已完成任务</div><div class="stat-value">{{completedTasks}}</div><div class="stat-foot">云端累计</div></article>
    </section>
    <section class="simple-card-grid">
      <article class="panel simple-card"><h3>车辆状态分布</h3><div class="detail-lines"><div><span>运输中</span><strong>{{count(vehicles,'status','TRANSPORTING')}}</strong></div><div><span>空闲</span><strong>{{count(vehicles,'status','IDLE')}}</strong></div><div><span>停用</span><strong>{{count(vehicles,'status','DISABLED')}}</strong></div><div><span>维护中</span><strong>{{count(vehicles,'status','MAINTENANCE')}}</strong></div></div></article>
      <article class="panel simple-card"><h3>告警类型统计</h3><div class="detail-lines"><div v-for="(value,key) in alarmTypeCounts" :key="key"><span>{{key}}</span><strong>{{value}}</strong></div><div v-if="!alarms.length"><span>暂无告警</span><strong>0</strong></div></div></article>
      <article class="panel simple-card"><h3>任务执行情况</h3><div class="detail-lines"><div><span>待运输</span><strong>{{count(tasks,'status','WAITING')}}</strong></div><div><span>运输中</span><strong>{{activeTasks}}</strong></div><div><span>异常</span><strong>{{abnormalTasks}}</strong></div><div><span>已完成</span><strong>{{completedTasks}}</strong></div></div></article>
    </section>
    <section class="panel role-table-card"><div class="panel-title"><div><h2>任务明细</h2><span>当前账号可见的真实任务数据</span></div><el-button @click="load">刷新</el-button></div><div class="table-wrap"><table><thead><tr><th>任务</th><th>车辆</th><th>货物</th><th>起点</th><th>终点</th><th>ETA</th><th>状态</th></tr></thead><tbody><tr v-for="task in tasks" :key="task.id"><td>{{task.taskNo}}</td><td>{{vehicleMap[task.vehicleId]?.plateNumber || `#${task.vehicleId}`}}</td><td>{{cargoMap[task.cargoId]?.name || `#${task.cargoId}`}}</td><td>{{task.startLocation}}</td><td>{{task.endLocation}}</td><td>{{eta(task)}}</td><td><span class="binding-status" :class="`status-${String(task.status).toLowerCase()}`">{{statusText[task.status] || task.status}}</span></td></tr><tr v-if="!tasks.length"><td colspan="7">暂无任务数据</td></tr></tbody></table></div></section>
  </div>
</template>
