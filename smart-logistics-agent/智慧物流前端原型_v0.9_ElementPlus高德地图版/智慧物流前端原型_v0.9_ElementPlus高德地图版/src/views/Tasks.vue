<script setup>
import data from '../mock/data.json'
import PageHeader from '../components/PageHeader.vue'
import { useAuth } from '../stores/auth'
const { state } = useAuth()
const statusText={WAITING:'待分配',TRANSPORTING:'运输中',COMPLETED:'已完成',ABNORMAL:'异常',CANCELLED:'已取消'}
</script>
<template>
  <PageHeader
    :title="state.currentUser.role==='DRIVER' ? '我的运输任务' : state.currentUser.role==='OWNER' ? '我的运输进度' : '运输任务'"
    :subtitle="state.currentUser.role==='DRIVER' ? '查看待执行任务、路线和 ETA' : state.currentUser.role==='OWNER' ? '查看货物对应运输任务与预计到达时间' : '运输任务、车辆绑定、路线与 ETA'"
  />
  <section class="panel page-panel">
    <div v-if="state.currentUser.role==='ADMIN'" class="toolbar"><button class="primary" @click="alert('演示版：创建运输任务')">+ 创建任务</button></div>
    <div class="table-wrap"><table><thead><tr><th>任务编号</th><th>货物</th><th>车辆</th><th>起点</th><th>终点</th><th>ETA</th><th>进度</th><th>状态</th></tr></thead>
    <tbody><tr v-for="t in data.tasks" :key="t.id"><td>{{t.taskNo}}</td><td>{{t.cargoName}}</td><td>{{t.plateNumber}}</td><td>{{t.startLocation}}</td><td>{{t.endLocation}}</td><td>{{t.estimatedArrivalTime.slice(11,16)}}</td><td>{{t.progress}}%</td><td><span class="task-status">{{statusText[t.status]}}</span></td></tr></tbody></table></div>
  </section>
</template>
