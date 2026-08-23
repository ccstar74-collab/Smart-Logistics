<script setup>
import { ref } from 'vue'
import data from '../mock/data.json'
import PageHeader from '../components/PageHeader.vue'
const commands=ref(data.commands.map(x=>({...x})))
const vehicleId=ref(1)
const content=ref('')
function send(){
  if(!content.value.trim()) return alert('请输入指令内容')
  const v=data.vehicles.find(x=>x.vehicleId===Number(vehicleId.value))
  commands.value.unshift({
    id:Date.now(), taskId:v.taskId, vehicleId:v.vehicleId, plateNumber:v.plateNumber,
    toUser:v.driverName, commandType:'CUSTOM', content:content.value, status:'PENDING',
    sentAt:new Date().toISOString()
  })
  content.value=''
}
</script>
<template>
  <PageHeader title="调度指令" subtitle="向指定车辆/司机下发调度指令" />
  <section class="dispatch-grid">
    <article class="panel form-card">
      <h2>下发新指令</h2>
      <label>目标车辆<select v-model="vehicleId"><option v-for="v in data.vehicles" :key="v.vehicleId" :value="v.vehicleId">{{v.plateNumber}} · {{v.driverName}}</option></select></label>
      <label>指令内容<textarea v-model="content" rows="5" placeholder="例如：切换至备用路线B"></textarea></label>
      <button class="primary" @click="send">发送指令</button>
    </article>
    <article class="panel page-panel">
      <div class="panel-title"><div><h2>指令记录</h2><span>模拟前端状态</span></div></div>
      <div class="command-list">
        <div v-for="c in commands" :key="c.id" class="command-row">
          <div><strong>{{c.plateNumber}} · {{c.toUser}}</strong><p>{{c.content}}</p></div>
          <span class="task-status">{{c.status}}</span>
        </div>
      </div>
    </article>
  </section>
</template>
