<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '../components/PageHeader.vue'
import { api, extractList } from '../api/http'
const loading = ref(false), tasks = ref([]), cargos = ref([]), vehicles = ref([])
const cargoMap = computed(() => Object.fromEntries(cargos.value.map(item => [item.id, item])))
const vehicleMap = computed(() => Object.fromEntries(vehicles.value.map(item => [item.id, item])))
async function load() {
  loading.value = true
  try {
    const [taskResult, cargoResult, vehicleResult] = await Promise.all([api.transportTasks.list({ page: 1, pageSize: 100 }), api.cargos.list({ page: 1, pageSize: 100 }), api.vehicles.list({ page: 1, pageSize: 100 })])
    tasks.value = extractList(taskResult); cargos.value = extractList(cargoResult); vehicles.value = extractList(vehicleResult)
  } catch (error) { ElMessage.error(error.message) }
  finally { loading.value = false }
}
function dateText(value) { return value ? String(value).replace('T', ' ').slice(0, 16) : '—' }
onMounted(load)
</script>
<template>
  <PageHeader title="绑定记录" subtitle="运输任务即货物与车辆的云端绑定记录" />
  <section class="panel page-panel role-table-card" v-loading="loading">
    <div class="toolbar"><button class="mini" @click="load">刷新云端记录</button></div>
    <div class="table-wrap"><table><thead><tr><th>任务编号</th><th>货物</th><th>货物编号</th><th>车牌号</th><th>司机</th><th>计划开始</th><th>状态</th></tr></thead><tbody><tr v-for="task in tasks" :key="task.id"><td>{{ task.taskNo }}</td><td>{{ cargoMap[task.cargoId]?.name || `货物 #${task.cargoId}` }}</td><td>{{ cargoMap[task.cargoId]?.cargoNo || '—' }}</td><td>{{ vehicleMap[task.vehicleId]?.plateNumber || `车辆 #${task.vehicleId}` }}</td><td>{{ vehicleMap[task.vehicleId]?.driverId ? `司机 #${vehicleMap[task.vehicleId].driverId}` : '未绑定司机' }}</td><td>{{ dateText(task.planStartTime) }}</td><td>{{ task.status }}</td></tr><tr v-if="!tasks.length"><td colspan="7">暂无云端绑定记录</td></tr></tbody></table></div>
  </section>
</template>
