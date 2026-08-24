<script setup>
import { computed, ref } from 'vue'
import PageHeader from '../components/PageHeader.vue'
import AMapView from '../components/AMapView.vue'
import SimulationControls from '../components/SimulationControls.vue'
import { snapshots } from '../stores/realtime'
import { useAuth } from '../stores/auth'
const { state } = useAuth()
const selectedId=ref(snapshots[0]?.vehicle_id)
const selected=computed(()=>snapshots.find(v=>v.vehicle_id===selectedId.value)||snapshots[0])
const title=computed(()=>({OWNER:'货物追踪',DRIVER:'车辆位置',DISPATCHER:'车辆监控',ADMIN:'地图监控'}[state.currentUser.role]||'实时追踪'))
const subtitle=computed(()=>({OWNER:'查看货物绑定车辆的实时位置与运输状态',DRIVER:'查看当前任务路线和车辆实时位置',DISPATCHER:'统一查看所有车辆位置、速度、状态和异常',ADMIN:'系统级车辆与告警位置监控'}[state.currentUser.role]||'实时车辆追踪'))
function selectVehicle(v){selectedId.value=v.vehicle_id}
function shortTime(iso){if(!iso)return'--';const d=new Date(iso);return Number.isNaN(d.getTime())?'--':d.toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit',second:'2-digit',hour12:false})}
</script>
<template>
  <PageHeader :title="title" :subtitle="subtitle"/>
  <SimulationControls />
  <section class="tracking-grid">
    <article class="panel"><div class="panel-title"><div><h2>实时位置地图</h2><span>点击车辆切换追踪对象</span></div><span class="badge">高德真实地图 · Mock 实时数据</span></div><AMapView :selectedVehicleId="selected.vehicle_id" @select="selectVehicle"/></article>
    <article class="panel detail-card"><h2>{{selected.display.plate_number}}</h2><p class="vehicle-id-line">{{selected.vehicle_id}}</p><dl><div><dt>在线状态</dt><dd>{{selected.online?'在线':'离线'}}</dd></div><div><dt>运输状态</dt><dd>{{selected.transport_status}}</dd></div><div><dt>速度</dt><dd>{{selected.gps.speed_kmh}} km/h</dd></div><div><dt>航向</dt><dd>{{selected.gps.heading}}°</dd></div><div><dt>纬度</dt><dd>{{selected.gps.lat}}</dd></div><div><dt>经度</dt><dd>{{selected.gps.lon}}</dd></div><div><dt>GPS 时间</dt><dd>{{shortTime(selected.gps.timestamp)}}</dd></div><div><dt>当前任务</dt><dd>{{selected.display.task_no||'暂无'}}</dd></div><div><dt>ETA</dt><dd>{{selected.display.eta}}</dd></div></dl><div v-if="selected.has_active_alert" class="tracking-alert"><strong>{{selected.latest_alert.alert_type}}</strong><p>{{selected.latest_alert.description}}</p></div></article>
  </section>
</template>
