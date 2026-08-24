<script setup>
import { ref } from 'vue'
import data from '../mock/data.json'
import PageHeader from '../components/PageHeader.vue'
const status=ref('TRANSPORTING')
const note=ref('')
const history=ref([
  {time:'10:05',status:'运输中',note:'车辆已驶离仓库'},
  {time:'09:45',status:'已装货',note:'货物装载完成'}
])
function submit(){const text=status.value==='LOADED'?'已装货':status.value==='TRANSPORTING'?'运输中':'已送达';history.value.unshift({time:new Date().toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit',hour12:false}),status:text,note:note.value||'司机手动上报'});note.value='';alert('状态上报成功（演示）')}
</script>
<template>
  <PageHeader title="状态上报" subtitle="司机手动上报已装货、运输中、已送达等货物状态"/>
  <section class="driver-bottom">
    <article class="panel"><div class="panel-title"><div><h2>当前运输任务</h2><span>{{data.tasks[0].taskNo}}</span></div></div><div class="detail-lines task-card"><div><span>货物</span><strong>{{data.tasks[0].cargoName}}</strong></div><div><span>车辆</span><strong>{{data.tasks[0].plateNumber}}</strong></div><div><span>起点</span><strong>{{data.tasks[0].startLocation}}</strong></div><div><span>终点</span><strong>{{data.tasks[0].endLocation}}</strong></div></div></article>
    <article class="panel"><div class="panel-title"><div><h2>上报运输状态</h2><span>状态更新后可同步给货主</span></div></div><div class="binding-form"><label><span>状态</span><select v-model="status"><option value="LOADED">已装货</option><option value="TRANSPORTING">运输中</option><option value="DELIVERED">已送达</option></select></label><label><span>备注</span><textarea v-model="note" rows="4" placeholder="填写状态补充说明"></textarea></label><button class="primary" @click="submit">确认上报</button></div></article>
  </section>
  <section class="panel role-table-card"><div class="panel-title"><div><h2>状态历史记录</h2><span>最近上报记录</span></div></div><div class="table-wrap"><table><thead><tr><th>时间</th><th>状态</th><th>说明</th></tr></thead><tbody><tr v-for="(h,i) in history" :key="i"><td>{{h.time}}</td><td><span class="task-status">{{h.status}}</span></td><td>{{h.note}}</td></tr></tbody></table></div></section>
</template>
