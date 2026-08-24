<script setup>
import { ref } from 'vue'
import PageHeader from '../components/PageHeader.vue'
const input=ref('')
const messages=ref([
  {role:'assistant', text:'你好，我是物流智能助手。当前为前端演示模式，可体验基础问答界面。'}
])
function send(){
  const q=input.value.trim()
  if(!q) return
  messages.value.push({role:'user',text:q})
  let reply='当前为 Mock 模式。后续此处将调用 /api/v1/agent/chat，并连接 MaxKB/RAG 知识库。'
  if(q.includes('偏航')) reply='路线偏航告警通常需要调度员查看异常位置，并根据情况下发路线调整指令。'
  if(q.includes('货物')) reply='货物状态可在“货物管理”和“运输任务”页面查看；实时位置由其绑定车辆决定。'
  messages.value.push({role:'assistant',text:reply})
  input.value=''
}
</script>
<template>
  <PageHeader title="智能问答" subtitle="物流知识问答与业务咨询入口" />
  <section class="chat panel">
    <div class="chat-body">
      <div v-for="(m,i) in messages" :key="i" class="bubble" :class="m.role">{{m.text}}</div>
    </div>
    <div class="chat-input"><input v-model="input" @keyup.enter="send" placeholder="请输入物流问题，例如：车辆偏航应该怎么办？" /><button class="primary" @click="send">发送</button></div>
  </section>
</template>
