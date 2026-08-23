<script setup>
import { computed, ref } from 'vue'
import data from '../mock/data.json'
import PageHeader from '../components/PageHeader.vue'
import { useAuth } from '../stores/auth'
const { state } = useAuth()
const keyword=ref('')
const statusText={WAITING:'待运输',TRANSPORTING:'运输中',COMPLETED:'已完成',ABNORMAL:'异常'}
const list=computed(()=>data.cargos.filter(c=>!keyword.value || c.name.includes(keyword.value)||c.cargoNo.includes(keyword.value)||c.ownerName.includes(keyword.value)))
</script>
<template>
  <PageHeader
    :title="state.currentUser.role==='OWNER' ? '我的货物' : state.currentUser.role==='DRIVER' ? '货物状态上报' : '货物管理'"
    :subtitle="state.currentUser.role==='OWNER' ? '查看本人货物及运输状态' : state.currentUser.role==='DRIVER' ? '查看任务货物并模拟上报运输状态' : '货物信息、货主与运输状态管理'"
  />
  <section class="panel page-panel">
    <div class="toolbar"><input v-model="keyword" placeholder="搜索货物编号/名称/货主" /><button v-if="state.currentUser.role==='ADMIN'" class="primary" @click="alert('演示版：新增货物')">+ 新增货物</button></div>
    <div class="table-wrap"><table><thead><tr><th>货物编号</th><th>名称</th><th>货主</th><th>重量(kg)</th><th>体积(m³)</th><th>状态</th><th>操作</th></tr></thead>
    <tbody><tr v-for="c in list" :key="c.id"><td>{{c.cargoNo}}</td><td>{{c.name}}</td><td>{{c.ownerName}}</td><td>{{c.weight}}</td><td>{{c.volume}}</td><td><span class="task-status">{{statusText[c.status]}}</span></td><td><button class="mini" @click="alert('货物详情：'+c.name)">详情</button></td></tr></tbody></table></div>
  </section>
</template>
