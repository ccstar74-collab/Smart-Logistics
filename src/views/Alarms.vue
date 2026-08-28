<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import PageHeader from '../components/PageHeader.vue'
import { api, extractList } from '../api/http'
import { useAuth } from '../stores/auth-session'

const { state } = useAuth()
const router = useRouter()
const loading = ref(false), alarms = ref([]), page = ref(1), pageSize = ref(20), total = ref(0), status = ref(''), level = ref('')
let refreshTimer = null
const isOwner = computed(() => state.currentUser.role === 'OWNER')
const canHandle = computed(() => state.currentUser.role === 'DISPATCHER')
const alarmText = { ROUTE_DEVIATION: '路线偏离', ABNORMAL_STOP: '异常停留', ABNORMAL_OPEN: '异常开箱' }
const statusText = { UNHANDLED: '未处理', PROCESSING: '处理中', RESOLVED: '已解决' }
const conditionText = { ACTIVE: '持续中', RECOVERED: '已恢复' }
const normalizeAlarm = alarm => ({
  ...alarm,
  id: alarm.id ?? alarm.alarmId ?? alarm.alarm_id,
  vehicleId: alarm.vehicleId ?? alarm.vehicle_id,
  taskId: alarm.taskId ?? alarm.task_id,
  taskNo: alarm.taskNo ?? alarm.task_no,
  plateNumber: alarm.plateNumber ?? alarm.plate_number,
  alarmType: alarm.alarmType ?? alarm.alarm_type ?? alarm.type,
  conditionStatus: alarm.conditionStatus ?? alarm.condition_status,
  message: alarm.message ?? alarm.description,
  resolutionRemark: alarm.resolutionRemark ?? alarm.resolution_remark,
  occurredAt: alarm.occurredAt ?? alarm.occurred_at,
  recoveredAt: alarm.recoveredAt ?? alarm.recovered_at,
  createdAt: alarm.createdAt ?? alarm.created_at ?? alarm.alarmTime ?? alarm.alarm_time
})
function dateText(value) { return value ? String(value).replace('T', ' ').slice(0, 19) : '--' }
async function load() {
  loading.value = true
  try {
    const result = await api.alarms.list({ page: page.value, pageSize: pageSize.value, status: status.value, level: level.value })
    alarms.value = extractList(result).map(normalizeAlarm); total.value = Number(result?.total ?? alarms.value.length)
  } catch (error) { ElMessage.error(`告警数据加载失败：${error.message}`) }
  finally { loading.value = false }
}

function openDispatch(alarm) {
  if (alarm.taskId == null) return ElMessage.warning('该告警未关联运输任务，无法下发调度指令')
  router.push({
    path: '/dispatch',
    query: {
      from: 'alarm',
      alarmId: String(alarm.id),
      taskId: String(alarm.taskId),
      commandType: alarm.alarmType === 'ROUTE_DEVIATION' ? 'ROUTE_CHANGE' : 'TEXT'
    }
  })
}

async function forceClose(alarm) {
  try {
    const result = await ElMessageBox.prompt('请填写强制关闭原因（必填，最长 500 字）', '强制关闭告警', {
      inputPlaceholder: '例如：误报，已人工核实',
      inputValidator: value => {
        if (!value?.trim()) return '请填写关闭原因'
        if (value.length > 500) return '关闭原因最多 500 字'
        return true
      }
    })
    await api.alarms.updateStatus(alarm.id, 'RESOLVED', result.value.trim())
    ElMessage.success('告警已强制关闭')
    await load()
  } catch (cause) { if (cause !== 'cancel' && cause !== 'close') ElMessage.error(cause.message) }
}

onMounted(() => {
  load()
  refreshTimer = setInterval(load, 30000)
})
onBeforeUnmount(() => { if (refreshTimer) clearInterval(refreshTimer) })
</script>

<template>
  <PageHeader :title="isOwner ? '告警通知' : '告警管理'" :subtitle="isOwner ? '仅显示后端授权给当前货主的运输告警' : '未处理告警可进入统一调度指令页面处理；误报可人工强制关闭'" />
  <section class="panel page-panel" v-loading="loading">
    <div class="toolbar"><el-select v-model="status" placeholder="全部状态" clearable style="width:150px" @change="page=1;load()"><el-option v-for="(label,value) in statusText" :key="value" :label="label" :value="value" /></el-select><el-select v-model="level" placeholder="全部级别" clearable style="width:150px" @change="page=1;load()"><el-option label="高" value="HIGH" /><el-option label="中" value="MEDIUM" /><el-option label="低" value="LOW" /></el-select><el-button @click="load">刷新</el-button><span class="muted-note">列表每 30 秒自动刷新一次</span></div>
    <div class="table-wrap"><table><thead><tr><th>时间</th><th>车辆</th><th>任务</th><th>类型</th><th>级别</th><th>说明</th><th>业务状态</th><th>物理状态</th><th>操作</th></tr></thead><tbody><tr v-for="alarm in alarms" :key="alarm.id"><td>{{ dateText(alarm.occurredAt || alarm.createdAt) }}</td><td>{{ alarm.plateNumber || `#${alarm.vehicleId}` }}</td><td>{{ alarm.taskNo || alarm.taskId || '—' }}</td><td>{{ alarmText[alarm.alarmType] || alarm.alarmType }}</td><td>{{ alarm.level }}</td><td class="alarm-message-cell" :title="alarm.message || alarm.resolutionRemark">{{ alarm.message || '--' }}</td><td><span class="status-pill" :class="String(alarm.status).toLowerCase()">{{ statusText[alarm.status] || alarm.status }}</span></td><td><span class="status-pill" :class="String(alarm.conditionStatus || '').toLowerCase()">{{ conditionText[alarm.conditionStatus] || alarm.conditionStatus || '—' }}</span></td><td><template v-if="canHandle"><button v-if="alarm.status === 'UNHANDLED' && alarm.taskId != null" class="mini" @click="openDispatch(alarm)">下发调度指令</button><span v-else-if="alarm.status === 'UNHANDLED'" class="muted-note">无关联任务</span><button v-if="alarm.status !== 'RESOLVED'" class="mini danger" @click="forceClose(alarm)">强制关闭</button></template><span v-else class="muted-note">只读</span></td></tr><tr v-if="!alarms.length"><td colspan="9">暂无告警数据</td></tr></tbody></table></div>
    <div style="padding:16px;display:flex;justify-content:flex-end"><el-pagination v-model:current-page="page" v-model:page-size="pageSize" layout="total, prev, pager, next" :total="total" @current-change="load" /></div>
  </section>
</template>
