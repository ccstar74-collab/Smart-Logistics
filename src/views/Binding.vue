<script setup>
import { ref } from 'vue'
import data from '../mock/data.json'
import PageHeader from '../components/PageHeader.vue'
const cargoId=ref(data.cargos[0].id),vehicleId=ref(data.vehicles[0].vehicleId),driver=ref(data.vehicles[0].driverName),remark=ref('')
function bind(){alert('绑定成功（演示）：货物与车辆关系已建立');remark.value=''}
</script>
<template>
  <PageHeader title="货物车辆绑定" subtitle="将货物、运输车辆和司机建立关联，支持后续实时追踪"/>
  <section class="warehouse-main">
    <article class="panel"><div class="panel-title"><div><h2>新建绑定关系</h2><span>选择货物、车辆与司机</span></div></div><div class="binding-form"><label><span>选择货物</span><select v-model="cargoId"><option v-for="c in data.cargos" :key="c.id" :value="c.id">{{c.cargoNo}} · {{c.name}}</option></select></label><label><span>选择车辆</span><select v-model="vehicleId"><option v-for="v in data.vehicles" :key="v.vehicleId" :value="v.vehicleId">{{v.plateNumber}} · {{v.type}}</option></select></label><label><span>选择司机</span><select v-model="driver"><option v-for="v in data.vehicles" :key="v.vehicleId" :value="v.driverName">{{v.driverName}}</option></select></label><label><span>备注</span><textarea v-model="remark" rows="4" placeholder="请输入备注（选填）"></textarea></label><button class="primary" @click="bind">绑定</button></div></article>
    <article class="panel role-table-card"><div class="panel-title"><div><h2>待绑定货物</h2><span>等待车辆分配</span></div></div><div class="table-wrap"><table><thead><tr><th>货物编号</th><th>名称</th><th>重量</th><th>状态</th></tr></thead><tbody><tr v-for="c in data.cargos" :key="c.id"><td>{{c.cargoNo}}</td><td>{{c.name}}</td><td>{{c.weight}} kg</td><td>{{c.status}}</td></tr></tbody></table></div></article>
  </section>
</template>
