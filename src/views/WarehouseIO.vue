<script setup>
import { ref } from 'vue'
import data from '../mock/data.json'
import PageHeader from '../components/PageHeader.vue'
const type=ref('IN'),cargo=ref(data.cargos[0].id),quantity=ref('')
function submit(){alert(type.value==='IN'?'入库登记成功（演示）':'出库登记成功（演示）');quantity.value=''}
</script>
<template>
  <PageHeader title="入库出库" subtitle="登记货物入库、出库和仓库流转记录"/>
  <section class="warehouse-main"><article class="panel"><div class="panel-title"><div><h2>新增出入库记录</h2><span>仓库操作登记</span></div></div><div class="binding-form"><label><span>操作类型</span><select v-model="type"><option value="IN">入库</option><option value="OUT">出库</option></select></label><label><span>货物</span><select v-model="cargo"><option v-for="c in data.cargos" :key="c.id" :value="c.id">{{c.cargoNo}} · {{c.name}}</option></select></label><label><span>数量/重量</span><input v-model="quantity" placeholder="例如：780 kg"/></label><button class="primary" @click="submit">确认登记</button></div></article><article class="panel role-table-card"><div class="panel-title"><div><h2>今日汇总</h2><span>仓库当日流转概览</span></div></div><div class="stats-grid" style="grid-template-columns:1fr 1fr;padding:16px"><article class="stat-card"><div class="stat-label">今日入库</div><div class="stat-value">12</div></article><article class="stat-card"><div class="stat-label">今日出库</div><div class="stat-value">15</div></article></div></article></section>
  <section class="panel role-table-card"><div class="panel-title"><div><h2>出入库记录</h2><span>最近仓库操作</span></div></div><div class="table-wrap"><table><thead><tr><th>类型</th><th>货物</th><th>数量</th><th>时间</th><th>操作人</th></tr></thead><tbody><tr v-for="r in data.warehouseRecords" :key="r.id"><td>{{r.type==='IN'?'入库':'出库'}}</td><td>{{r.cargo}}</td><td>{{r.quantity}}</td><td>{{r.time}}</td><td>{{r.operator}}</td></tr></tbody></table></div></section>
</template>
