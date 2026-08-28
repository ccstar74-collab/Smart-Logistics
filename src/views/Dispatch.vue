<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '../components/PageHeader.vue'
import { api, extractList } from '../api/http'
import { useAuth } from '../stores/auth-session'

const { state } = useAuth()
const loading = ref(false), drivers = ref([]), vehicles = ref([]), tasks = ref([]), selectedDriverId = ref(null), content = ref('')
const isDriver = computed(() => state.currentUser.role === 'DRIVER')
const idOf = item => item?.id ?? item?.userId ?? item?.driverId ?? item?.value
const accountOf = item => item?.username ?? item?.account ?? item?.label ?? item?.name ?? `司机 #${idOf(item)}`
const driverMap = computed(() => Object.fromEntries(drivers.value.map(driver => [Number(idOf(driver)), driver])))
const driverVehicles = computed(() => vehicles.value.filter(vehicle => Number(vehicle.driverId) === Number(selectedDriverId.value)))
const driverTasks = computed(() => {
  const ids = new Set(driverVehicles.value.map(vehicle => Number(vehicle.id ?? vehicle.vehicleId)))
  return tasks.value.filter(task => ids.has(Number(task.vehicleId)))
})
const currentDriverId = computed(() => idOf(state.currentUser))

async function load() {
  loading.value = true
  try {
    const calls = [api.vehicles.list({ page:1, pageSize:100 }), api.transportTasks.list({ page:1, pageSize:100 })]
    if (!isDriver.value) calls.push(api.drivers.options())
    const [vehicleResult, taskResult, driverResult] = await Promise.all(calls)
    vehicles.value = extractList(vehicleResult); tasks.value = extractList(taskResult)
    drivers.value = isDriver.value ? [state.currentUser] : extractList(driverResult)
    selectedDriverId.value = isDriver.value ? currentDriverId.value : (selectedDriverId.value ?? idOf(drivers.value[0]))
  } catch (error) { ElMessage.error(`司机与任务数据加载失败：${error.message}`) }
  finally { loading.value = false }
}

function send() {
  if (!selectedDriverId.value || !content.value.trim()) return ElMessage.warning('请选择真实司机账号并填写指令内容')
  ElMessage.warning('后端尚未确定调度指令写入接口，本页面不会生成模拟指令；接口开放后即可接入发送。')
}
const formatTime = value => value ? value.replace('T',' ').slice(0,16) : '--'
onMounted(load)
</script>

<template>
  <PageHeader :title="isDriver ? '调度指令' : '调度指令下发'" subtitle="司机、车辆和任务来自真实云端 API；指令接口尚待后端确定" />
  <section v-loading="loading" class="dispatch-grid">
    <article class="panel form-card">
      <h2>{{isDriver ? '当前司机账号' : '下发新指令'}}</h2>
      <label>目标司机
        <select v-model.number="selectedDriverId" :disabled="isDriver"><option v-for="driver in drivers" :key="idOf(driver)" :value="idOf(driver)">{{accountOf(driver)}}</option></select>
      </label>
      <div class="api-scope-note dispatch-vehicle-note">关联车辆：{{driverVehicles.map(v=>v.plateNumber).join('、') || '暂无已绑定车辆'}}</div>
      <label v-if="!isDriver">指令内容<textarea v-model="content" rows="5" placeholder="例如：切换至备用路线 B"></textarea></label>
      <button v-if="!isDriver" class="primary" :disabled="!selectedDriverId||!content.trim()" @click="send">发送指令（等待后端接口）</button>
      <div v-else class="empty-state">调度指令查询与反馈接口尚未开放，目前不展示模拟指令。</div>
    </article>
    <article class="panel page-panel dispatch-task-panel">
      <div class="panel-title"><div><h2>该司机关联的运输任务</h2><span>由司机 API、车辆 API 和运输任务 API 交叉匹配</span></div><el-button @click="load">刷新</el-button></div>
      <div class="command-list dispatch-task-list">
        <div v-for="task in driverTasks" :key="task.id" class="command-row"><div class="command-row-copy"><strong>{{task.taskNo}} · {{driverMap[selectedDriverId]?.username || driverMap[selectedDriverId]?.label || accountOf(state.currentUser)}}</strong><p>{{task.startLocation}} → {{task.endLocation}}　ETA {{formatTime(task.estimatedArrivalTime || task.planEndTime)}}</p></div><span class="task-status">{{task.status}}</span></div>
        <div v-if="!driverTasks.length" class="empty-state dispatch-empty-state">该司机暂无关联运输任务</div>
      </div>
      <div class="api-scope-note dispatch-api-note">
        <strong>接口接入状态</strong>
        <span>调度指令的创建、列表和司机反馈接口尚未开放。待后端提供接口路径、请求字段和状态枚举后即可启用。</span>
      </div>
    </article>
  </section>
</template>
