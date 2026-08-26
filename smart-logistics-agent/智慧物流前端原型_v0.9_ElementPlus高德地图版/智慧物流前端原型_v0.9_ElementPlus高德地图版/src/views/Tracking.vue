<script setup>
import { computed, ref } from 'vue'
import data from '../mock/data.json'
import PageHeader from '../components/PageHeader.vue'
import AMapView from '../components/AMapView.vue'
import SimulationControls from '../components/SimulationControls.vue'
import { snapshots } from '../stores/realtime'
const selectedId=ref(snapshots[0]?.vehicle_id)
const selected=computed(()=>snapshots.find(v=>v.vehicle_id===selectedId.value)||snapshots[0])
function selectVehicle(v){selectedId.value=v.vehicle_id}
const task=computed(()=>data.tasks.find(t=>t.taskNo===selected.value?.display?.task_no))
function shortTime(iso){ if(!iso) return '--'; const d=new Date(iso); return Number.isNaN(d.getTime())?'--':d.toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit',second:'2-digit',hour12:false}) }
</script>
<template>
  <PageHeader title="实时追踪" subtitle="动态模拟车辆快照，观察车辆移动、速度变化、GPS时间戳与告警变化" />
  <SimulationControls />
  <section class="tracking-grid">
    <article class="panel">
      <div class="panel-title"><div><h2>车辆实时位置</h2><span>点击车辆切换快照</span></div><span class="badge">高德真实地图 · Mock 实时数据</span></div>
      <AMapView :selectedVehicleId="selected.vehicle_id" @select="selectVehicle" />
    </article>
    <article class="panel detail-card">
      <h2>{{selected.display.plate_number}}</h2>
      <p class="vehicle-id-line">{{selected.vehicle_id}}</p>
      <dl>
        <div><dt>在线状态</dt><dd>{{selected.online?'在线':'离线'}}</dd></div>
        <div><dt>运输状态</dt><dd>{{selected.transport_status}}</dd></div>
        <div><dt>速度</dt><dd>{{selected.gps.speed_kmh}} km/h</dd></div>
        <div><dt>航向</dt><dd>{{selected.gps.heading}}°</dd></div>
        <div><dt>纬度</dt><dd>{{selected.gps.lat}}</dd></div>
        <div><dt>经度</dt><dd>{{selected.gps.lon}}</dd></div>
        <div><dt>GPS 时间</dt><dd>{{shortTime(selected.gps.timestamp)}}</dd></div>
        <div><dt>当前任务</dt><dd>{{selected.display.task_no || '暂无'}}</dd></div>
        <div><dt>ETA</dt><dd>{{selected.display.eta}}</dd></div>
      </dl>
      <div v-if="selected.has_active_alert" class="tracking-alert"><strong>{{selected.latest_alert.alert_type}}</strong><p>{{selected.latest_alert.description}}</p></div>
    </article>
  </section>
</template>
