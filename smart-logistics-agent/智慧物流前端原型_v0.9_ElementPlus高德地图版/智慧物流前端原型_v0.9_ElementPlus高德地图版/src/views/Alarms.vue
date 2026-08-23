<script setup>
import { ref } from 'vue'
import data from '../mock/data.json'
import PageHeader from '../components/PageHeader.vue'
import { useAuth } from '../stores/auth'
const { state } = useAuth()
const alarms=ref(data.alarms.map(x=>({...x})))
const alarmText={ROUTE_DEVIATION:'路线偏离',ABNORMAL_STOP:'异常停留',ABNORMAL_OPEN:'异常开箱'}
const statusText={UNHANDLED:'未处理',PROCESSING:'处理中',RESOLVED:'已处理'}
function handle(a){ a.status='RESOLVED' }
</script>
<template>
  <PageHeader
    :title="state.currentUser.role==='OWNER' ? '异常消息' : '告警中心'"
    :subtitle="state.currentUser.role==='OWNER' ? '查看本人货物运输过程中的异常通知' : '运输异常、告警处理与历史记录'"
  />
  <section class="panel page-panel">
    <div class="table-wrap"><table><thead><tr><th>时间</th><th>车辆</th><th>类型</th><th>等级</th><th>说明</th><th>状态</th><th>操作</th></tr></thead>
    <tbody><tr v-for="a in alarms" :key="a.id"><td>{{a.createdAt.slice(11,16)}}</td><td>{{a.plateNumber}}</td><td>{{alarmText[a.alarmType]}}</td><td>{{a.level}}</td><td>{{a.message}}</td><td><span class="status-pill" :class="a.status.toLowerCase()">{{statusText[a.status]}}</span></td><td><button v-if="state.currentUser.role==='ADMIN'" class="mini" :disabled="a.status==='RESOLVED'" @click="handle(a)">标记已处理</button><span v-else class="muted-note">仅查看</span></td></tr></tbody></table></div>
  </section>
</template>
