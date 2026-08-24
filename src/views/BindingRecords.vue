<script setup>
import data from '../mock/data.json'
import PageHeader from '../components/PageHeader.vue'
</script>
<template><PageHeader title="绑定记录" subtitle="查看历史货物、车辆与司机绑定关系"/><section class="panel page-panel role-table-card"><div class="table-wrap"><table><thead><tr><th>货物</th><th>货物编号</th><th>车牌号</th><th>司机</th><th>绑定人</th><th>绑定时间</th><th>操作</th></tr></thead><tbody><tr v-for="r in data.warehouseBindings" :key="r.id"><td>{{r.cargo}}</td><td>{{r.cargoNo}}</td><td>{{r.plateNumber}}</td><td>{{r.driver}}</td><td>{{r.operator}}</td><td>{{r.time}}</td><td><button class="mini" @click="alert('演示版：解除绑定')">解除绑定</button></td></tr></tbody></table></div></section></template>
