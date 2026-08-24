<script setup>
import data from '../mock/data.json'
import PageHeader from '../components/PageHeader.vue'
</script>
<template>
  <PageHeader title="预计到达时间" subtitle="集中查看在途货物 ETA 估算与运输进度"/>
  <section class="stats-grid">
    <article class="stat-card"><div class="stat-label">运输中任务</div><div class="stat-value">2</div><div class="stat-foot">当前在途</div></article>
    <article class="stat-card"><div class="stat-label">平均进度</div><div class="stat-value">60%</div><div class="stat-foot">在途任务平均值</div></article>
    <article class="stat-card"><div class="stat-label">准时到达</div><div class="stat-value">96%</div><div class="stat-foot">历史统计演示</div></article>
    <article class="stat-card"><div class="stat-label">异常延误</div><div class="stat-value">1</div><div class="stat-foot">需要关注</div></article>
  </section>
  <section class="panel page-panel"><div class="table-wrap"><table><thead><tr><th>运单号</th><th>货物</th><th>车辆</th><th>目的地</th><th>预计到达时间</th><th>运输进度</th><th>状态</th></tr></thead><tbody><tr v-for="t in data.tasks" :key="t.id"><td>{{t.taskNo}}</td><td>{{t.cargoName}}</td><td>{{t.plateNumber}}</td><td>{{t.endLocation}}</td><td>{{t.estimatedArrivalTime.replace('T',' ').slice(0,16)}}</td><td>{{t.progress}}%</td><td><span class="task-status">{{t.status==='COMPLETED'?'已到达':'运输中'}}</span></td></tr></tbody></table></div></section>
</template>
