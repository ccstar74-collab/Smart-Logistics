<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '../components/PageHeader.vue'
import { api, extractList } from '../api/http'

const loading = ref(false), saving = ref(false), currentTask = ref(null)
const nextStatus = computed(() => currentTask.value?.status === 'WAITING' ? 'TRANSPORTING' : currentTask.value?.status === 'TRANSPORTING' ? 'COMPLETED' : '')
const nextLabel = computed(() => nextStatus.value === 'TRANSPORTING' ? '开始运输' : nextStatus.value === 'COMPLETED' ? '完成运输' : '')
const statusText = { WAITING: '待运输', TRANSPORTING: '运输中', COMPLETED: '已完成' }
const normalizeTask = task => task ? ({
  ...task,
  id: task.id ?? task.taskId ?? task.task_id,
  taskNo: task.taskNo ?? task.task_no,
  startLocation: task.startLocation ?? task.start_location,
  endLocation: task.endLocation ?? task.end_location
}) : null

async function load() {
  loading.value = true
  try {
    const result = await api.transportTasks.current()
    currentTask.value = normalizeTask(Array.isArray(result) ? result[0] : extractList(result)[0] || result || null)
  } catch (error) { ElMessage.error(error.message) }
  finally { loading.value = false }
}
async function submit() {
  if (!currentTask.value || !nextStatus.value) return
  if (currentTask.value.id == null) return ElMessage.error('当前任务缺少任务 ID，无法调用状态更新接口')
  try {
    await ElMessageBox.confirm(`确定${nextLabel.value}吗？`, '任务状态确认', { type: 'warning' })
    saving.value = true
    await api.transportTasks.updateStatus(currentTask.value.id, nextStatus.value)
    ElMessage.success('任务状态已同步到云端')
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      const message = /database operation failed/i.test(error?.message || '')
        ? '状态接口已连接，但后端更新任务/货物/车辆的数据库事务失败，请后端结合该任务 ID 查看服务日志'
        : error.message
      ElMessage.error(message)
      await load().catch(() => {})
    }
  }
  finally { saving.value = false }
}
onMounted(load)
</script>

<template>
  <PageHeader title="状态上报" subtitle="按 WAITING → TRANSPORTING → COMPLETED 更新当前运输任务" />
  <section class="panel page-panel" v-loading="loading">
    <div v-if="currentTask" class="detail-lines task-card">
      <div><span>运单号</span><strong>{{ currentTask.taskNo }}</strong></div>
      <div><span>任务 ID</span><strong>{{ currentTask.id ?? '--' }}</strong></div>
      <div><span>起点</span><strong>{{ currentTask.startLocation }}</strong></div>
      <div><span>终点</span><strong>{{ currentTask.endLocation }}</strong></div>
      <div><span>当前状态</span><strong>{{ statusText[currentTask.status] || currentTask.status }}</strong></div>
      <button v-if="nextStatus" class="primary" :disabled="saving" @click="submit">{{ saving ? '正在上报…' : nextLabel }}</button>
      <span v-else class="muted-note">当前任务无需继续流转</span>
    </div>
    <div v-else class="muted-note">当前司机暂无运输任务</div>
  </section>
</template>
