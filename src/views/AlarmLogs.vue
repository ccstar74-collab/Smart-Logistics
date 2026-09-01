<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '../components/PageHeader.vue'
import { api, extractList } from '../api/http'

const loading = ref(false), alarms = ref([]), page = ref(1), pageSize = ref(10), total = ref(0), level = ref(''), status = ref('')
const levelType = value => value === 'HIGH' ? 'danger' : value === 'MEDIUM' ? 'warning' : 'info'
const statusText = { UNHANDLED: '未处理', PROCESSING: '处理中', RESOLVED: '已解决' }
const conditionText = { ACTIVE: '持续中', RECOVERED: '已恢复' }

async function load() {
  loading.value = true
  try {
    const result = await api.alarms.list({ page: page.value, pageSize: pageSize.value, level: level.value, status: status.value })
    alarms.value = extractList(result).map(alarm => ({
      ...alarm,
      plateNumber: alarm.plateNumber ?? alarm.plate_number,
      taskNo: alarm.taskNo ?? alarm.task_no,
      alarmType: alarm.alarmType ?? alarm.alarm_type ?? alarm.type,
      message: alarm.message ?? alarm.description,
      conditionStatus: alarm.conditionStatus ?? alarm.condition_status,
      createdAt: alarm.createdAt ?? alarm.created_at ?? alarm.alarmTime ?? alarm.alarm_time
    }))
    total.value = Number(result?.total ?? alarms.value.length)
  } catch (error) {
    ElMessage.error(`告警日志加载失败：${error.message}`)
  } finally { loading.value = false }
}
const formatTime = value => value ? value.replace('T', ' ').slice(0, 19) : '--'
onMounted(load)
</script>

<template>
  <PageHeader title="告警日志" subtitle="数据来自云端告警 API" />
  <section class="panel page-panel">
    <div class="toolbar">
      <el-select v-model="level" clearable placeholder="全部等级" style="width:140px" @change="page=1;load()"><el-option label="高" value="HIGH"/><el-option label="中" value="MEDIUM"/><el-option label="低" value="LOW"/></el-select>
      <el-select v-model="status" clearable placeholder="全部状态" style="width:140px" @change="page=1;load()"><el-option v-for="(label,value) in statusText" :key="value" :label="label" :value="value"/></el-select>
      <el-button @click="load">刷新云端日志</el-button>
    </div>
    <el-table v-loading="loading" :data="alarms" stripe empty-text="云端暂无告警日志">
      <el-table-column prop="id" label="ID" width="90"/>
      <el-table-column label="车辆" min-width="130"><template #default="s">{{s.row.plateNumber || `车辆 #${s.row.vehicleId ?? '--'}`}}</template></el-table-column>
      <el-table-column label="任务" width="130"><template #default="s">{{s.row.taskNo || s.row.taskId || '—'}}</template></el-table-column>
      <el-table-column prop="alarmType" label="告警类型" min-width="170"/>
      <el-table-column label="等级" width="110"><template #default="s"><el-tag :type="levelType(s.row.level)">{{s.row.level}}</el-tag></template></el-table-column>
      <el-table-column prop="message" label="说明" min-width="260"/>
      <el-table-column label="业务状态" width="120"><template #default="s">{{statusText[s.row.status] || s.row.status}}</template></el-table-column>
      <el-table-column label="物理状态" width="120"><template #default="s"><span class="status-pill" :class="String(s.row.conditionStatus || '').toLowerCase()">{{conditionText[s.row.conditionStatus] || s.row.conditionStatus || '—'}}</span></template></el-table-column>
      <el-table-column label="时间" min-width="180"><template #default="s">{{formatTime(s.row.createdAt || s.row.alarmTime)}}</template></el-table-column>
    </el-table>
    <div style="padding:16px;display:flex;justify-content:flex-end"><el-pagination v-model:current-page="page" v-model:page-size="pageSize" layout="total, prev, pager, next" :total="total" @current-change="load"/></div>
  </section>
</template>
