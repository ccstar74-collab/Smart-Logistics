<script setup>
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import data from '../mock/data.json'
import PageHeader from '../components/PageHeader.vue'
const keyword = ref('')
const status = ref('')
const statusText={IDLE:'空闲',TRANSPORTING:'运输中',MAINTENANCE:'维修',DISABLED:'停用'}
const list = computed(()=>data.vehicles.filter(v =>
  (!keyword.value || v.plateNumber.includes(keyword.value) || v.driverName.includes(keyword.value)) &&
  (!status.value || v.status===status.value)
))
function addVehicle(){ ElMessage.info('演示版：新增车辆表单可在下一阶段接入') }
function showDetail(vehicle){
  ElMessageBox.alert(
    `车型：${vehicle.type}<br>载重：${vehicle.capacity} kg<br>司机：${vehicle.driverName}<br>当前速度：${vehicle.speed} km/h`,
    `${vehicle.plateNumber} · 车辆详情`,
    { dangerouslyUseHTMLString: true, confirmButtonText: '知道了' }
  )
}
</script>
<template>
  <PageHeader title="车辆管理" subtitle="车辆信息、司机与运行状态管理" />
  <section class="panel page-panel">
    <div class="toolbar">
      <el-input v-model="keyword" clearable placeholder="搜索车牌或司机" style="width: 240px" />
      <el-select v-model="status" placeholder="全部状态" clearable style="width: 150px">
        <el-option label="运输中" value="TRANSPORTING" />
        <el-option label="空闲" value="IDLE" />
      </el-select>
      <el-button type="primary" @click="addVehicle">+ 新增车辆</el-button>
    </div>
    <el-table :data="list" stripe empty-text="暂无车辆数据" style="width: 100%">
      <el-table-column prop="vehicleId" label="ID" width="80" />
      <el-table-column prop="plateNumber" label="车牌" min-width="120"><template #default="scope"><strong>{{scope.row.plateNumber}}</strong></template></el-table-column>
      <el-table-column prop="type" label="车型" min-width="120" />
      <el-table-column prop="capacity" label="载重(kg)" min-width="110" />
      <el-table-column prop="driverName" label="司机" min-width="100" />
      <el-table-column prop="speed" label="速度(km/h)" min-width="110" />
      <el-table-column label="状态" min-width="100"><template #default="scope"><el-tag :type="scope.row.status==='TRANSPORTING' ? 'primary' : 'success'" effect="light">{{statusText[scope.row.status]}}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="90" fixed="right"><template #default="scope"><el-button link type="primary" @click="showDetail(scope.row)">详情</el-button></template></el-table-column>
    </el-table>
  </section>
</template>
