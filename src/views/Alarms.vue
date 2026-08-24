<script setup>
import { ref } from 'vue'
import data from '../mock/data.json'
import PageHeader from '../components/PageHeader.vue'
import { useAuth } from '../stores/auth'
const { state } = useAuth()
const alarms=ref(data.alarms.map(x=>({...x})))
const alarmText={ROUTE_DEVIATION:'路线偏离',ABNORMAL_STOP:'异常停留',ABNORMAL_OPEN:'异常开箱'}
const statusText={UNHANDLED:'未处理',PROCESSING:'处理中',RESOLVED:'已处理'}
function handle(a){a.status='RESOLVED'}
</script>
<template>
  <PageHeader :title="state.currentUser.role==='OWNER'?'告警通知':'告警管理'" :subtitle="state.currentUser.role==='OWNER'?'接收偏航、异常停留、异常开箱等运输异常通知':'查看告警详情、异常位置并跟踪处理状态'"/>
  <section class="panel page-panel"><div class="table-wrap"><table><thead><tr><th>时间</th><th>车辆</th><th>类型</th><th>等级</th><th>说明</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="a in alarms" :key="a.id"><td>{{a.createdAt.slice(11,16)}}</td><td>{{a.plateNumber}}</td><td>{{alarmText[a.alarmType]}}</td><td>{{a.level}}</td><td>{{a.message}}</td><td><span class="status-pill" :class="a.status.toLowerCase()">{{statusText[a.status]}}</span></td><td><button v-if="['ADMIN','DISPATCHER'].includes(state.currentUser.role)" class="mini" :disabled="a.status==='RESOLVED'" @click="handle(a)">标记已处理</button><span v-else class="muted-note">查看详情</span></td></tr></tbody></table></div></section>
</template>
