<script setup>
import data from '../mock/data.json'
import PageHeader from '../components/PageHeader.vue'
</script>
<template><PageHeader title="告警日志" /><section class="panel page-panel role-table-card"><div class="table-wrap"><table><thead><tr><th>ID</th><th>车辆</th><th>告警类型</th><th>等级</th><th>说明</th><th>状态</th><th>时间</th></tr></thead><tbody><tr v-for="a in data.alarms" :key="a.id"><td>{{a.id}}</td><td>{{a.plateNumber}}</td><td>{{a.alarmType}}</td><td>{{a.level}}</td><td>{{a.message}}</td><td>{{a.status}}</td><td>{{a.createdAt.replace('T',' ').slice(0,19)}}</td></tr></tbody></table></div></section></template>
