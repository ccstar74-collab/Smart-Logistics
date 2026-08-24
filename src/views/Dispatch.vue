<script setup>
import { ref } from 'vue'
import data from '../mock/data.json'
import PageHeader from '../components/PageHeader.vue'
import { useAuth } from '../stores/auth'
const { state } = useAuth()
const commands=ref(data.commands.map(x=>({...x})))
const vehicleId=ref(1), content=ref('')
function send(){if(!content.value.trim())return alert('请输入指令内容');const v=data.vehicles.find(x=>x.vehicleId===Number(vehicleId.value));commands.value.unshift({id:Date.now(),taskId:v.taskId,vehicleId:v.vehicleId,plateNumber:v.plateNumber,toUser:v.driverName,commandType:'CUSTOM',content:content.value,status:'PENDING',sentAt:new Date().toISOString()});content.value=''}
</script>
<template>
  <PageHeader :title="state.currentUser.role==='DRIVER'?'调度指令':'调度指令下发'" :subtitle="state.currentUser.role==='DRIVER'?'接收并查看调度中心下发的路线调整与业务指令':'向指定车辆或司机下发路线调整、联系司机等指令'"/>
  <section class="dispatch-grid">
    <article v-if="state.currentUser.role==='DISPATCHER'" class="panel form-card"><h2>下发新指令</h2><label>目标车辆<select v-model="vehicleId"><option v-for="v in data.vehicles" :key="v.vehicleId" :value="v.vehicleId">{{v.plateNumber}} · {{v.driverName}}</option></select></label><label>指令内容<textarea v-model="content" rows="5" placeholder="例如：切换至备用路线B"></textarea></label><button class="primary" @click="send">发送指令</button></article>
    <article class="panel page-panel"><div class="panel-title"><div><h2>{{state.currentUser.role==='DRIVER'?'收到的调度指令':'指令记录'}}</h2><span>查看指令内容与执行状态</span></div></div><div class="command-list"><div v-for="c in commands" :key="c.id" class="command-row"><div><strong>{{c.plateNumber}} · {{c.toUser}}</strong><p>{{c.content}}</p></div><span class="task-status">{{c.status}}</span></div></div></article>
  </section>
</template>
