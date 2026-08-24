<script setup>
import { computed } from 'vue'
import data from '../mock/data.json'
import PageHeader from '../components/PageHeader.vue'
import { useAuth } from '../stores/auth'
const { state } = useAuth()
const list=computed(()=>data.notifications.filter(n=>n.role==='ALL'||n.role===state.currentUser.role))
</script>
<template>
  <PageHeader title="消息中心" subtitle="接收系统通知、调度消息与业务提醒"/>
  <section class="panel page-panel"><div class="command-cards"><div v-for="n in list" :key="n.id" class="command-card"><div class="command-card-head"><strong>{{n.title}}</strong><span>{{n.time}}</span></div><p>{{n.content}}</p></div></div></section>
</template>
