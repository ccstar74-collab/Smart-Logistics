<script setup>
import { ref } from 'vue'
import data from '../mock/data.json'
import PageHeader from '../components/PageHeader.vue'
import AMapView from '../components/AMapView.vue'
import { snapshots } from '../stores/realtime'
const selectedId=ref(snapshots[0]?.vehicle_id)
function selectVehicle(v){selectedId.value=v.vehicle_id}
</script>
<template>
  <PageHeader title="运输轨迹" subtitle="查看货物历史运输路线、轨迹节点与时间信息"/>
  <section class="panel"><div class="panel-title"><div><h2>轨迹回放地图</h2><span>演示版使用实时轨迹线模拟历史回放效果</span></div></div><AMapView :selectedVehicleId="selectedId" :showTrack="true" @select="selectVehicle"/></section>
  <section class="panel role-table-card"><div class="panel-title"><div><h2>历史轨迹详情</h2><span>按任务查看轨迹与运输记录</span></div></div><div class="table-wrap"><table><thead><tr><th>任务编号</th><th>货物</th><th>起点</th><th>终点</th><th>计划开始</th><th>计划结束</th><th>状态</th></tr></thead><tbody><tr v-for="t in data.tasks" :key="t.id"><td>{{t.taskNo}}</td><td>{{t.cargoName}}</td><td>{{t.startLocation}}</td><td>{{t.endLocation}}</td><td>{{t.planStartTime.replace('T',' ').slice(0,16)}}</td><td>{{t.planEndTime.replace('T',' ').slice(0,16)}}</td><td><span class="task-status">{{t.status}}</span></td></tr></tbody></table></div></section>
</template>
