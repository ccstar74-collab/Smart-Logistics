<script setup>
import { computed, ref } from 'vue'
import data from '../mock/data.json'
import PageHeader from '../components/PageHeader.vue'
import AMapView from '../components/AMapView.vue'
import SimulationControls from '../components/SimulationControls.vue'
import { snapshots, summary } from '../stores/realtime'

const selectedVehicleId = ref(snapshots[0]?.vehicle_id)
const selectedVehicle = computed(() => snapshots.find(v => v.vehicle_id === selectedVehicleId.value) || snapshots[0])
const currentTask = computed(() => data.tasks.find(t => t.taskNo === selectedVehicle.value?.display?.task_no))
const activeSnapshots = computed(() => snapshots.filter(v => v.has_active_alert))
function selectVehicle(v){ selectedVehicleId.value = v.vehicle_id }

function shortTime(iso) {
  if (!iso) return '--'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return '--'
  return d.toLocaleTimeString('zh-CN', { hour:'2-digit', minute:'2-digit', second:'2-digit', hour12:false })
}
function sourceText(v) { return v?.latest_alert?.source === 'simulator' ? '模拟器' : (v?.latest_alert?.source || '--') }
</script>

<template>
  <PageHeader title="物流监控大屏" subtitle="动态模拟后端车辆实时快照：位置、速度、时间戳与告警都会实时变化" />
  <SimulationControls />

  <section class="stats-grid">
    <article class="stat-card"><div class="stat-label">实时快照车辆</div><div class="stat-value">{{summary.snapshot_count}}</div><div class="stat-foot">在线 {{summary.online_count}} 辆</div></article>
    <article class="stat-card"><div class="stat-label">运输相关车辆</div><div class="stat-value">{{summary.transporting_count}}</div><div class="stat-foot">已装货 / 运输中</div></article>
    <article class="stat-card alarm"><div class="stat-label">活动告警</div><div class="stat-value">{{summary.active_alert_count}}</div><div class="stat-foot">来源于 has_active_alert</div></article>
    <article class="stat-card"><div class="stat-label">当前车辆 ETA</div><div class="stat-value small">{{selectedVehicle?.display?.eta || '--'}}</div><div class="stat-foot">{{selectedVehicle?.vehicle_id}} · {{selectedVehicle?.transport_status}}</div></article>
  </section>

  <section class="main-grid">
    <article class="panel">
      <div class="panel-title">
        <div><h2>实时车辆分布</h2><span>重庆真实地图 + 后端快照字段对齐</span></div>
        <span class="badge">高德地图 · Mock 动态数据</span>
      </div>
      <AMapView :selectedVehicleId="selectedVehicle.vehicle_id" @select="selectVehicle" />
      <div class="vehicle-detail realtime-detail">
        <div><span>车辆 ID</span><strong>{{selectedVehicle.vehicle_id}}</strong></div>
        <div><span>在线状态</span><strong :class="selectedVehicle.online ? 'text-ok' : 'text-muted'">{{selectedVehicle.online ? '在线' : '离线'}}</strong></div>
        <div><span>运输状态</span><strong>{{selectedVehicle.transport_status}}</strong></div>
        <div><span>实时速度</span><strong>{{selectedVehicle.gps.speed_kmh}} km/h</strong></div>
        <div><span>航向角</span><strong>{{selectedVehicle.gps.heading}}°</strong></div>
        <div><span>GPS 更新时间</span><strong>{{shortTime(selectedVehicle.gps.timestamp)}}</strong></div>
      </div>
    </article>

    <article class="panel snapshot-panel">
      <div class="panel-title"><div><h2>车辆实时快照</h2><span>对应后端 MqttSubscriber 输出结构</span></div><span class="live-badge"><i></i>LIVE</span></div>
      <div class="snapshot-card">
        <div class="snapshot-head">
          <div><span class="vehicle-id">{{selectedVehicle.vehicle_id}}</span><strong>{{selectedVehicle.display.plate_number}}</strong></div>
          <span class="online-tag" :class="{off:!selectedVehicle.online}">{{selectedVehicle.online ? 'ONLINE' : 'OFFLINE'}}</span>
        </div>
        <div class="snapshot-grid">
          <div><span>纬度 lat</span><b>{{selectedVehicle.gps.lat}}</b></div>
          <div><span>经度 lon</span><b>{{selectedVehicle.gps.lon}}</b></div>
          <div><span>速度</span><b>{{selectedVehicle.gps.speed_kmh}} km/h</b></div>
          <div><span>航向</span><b>{{selectedVehicle.gps.heading}}°</b></div>
          <div><span>坐标系</span><b>{{selectedVehicle.gps.coordinate_system}}</b></div>
          <div><span>运输状态</span><b>{{selectedVehicle.transport_status}}</b></div>
        </div>
        <div class="snapshot-time">GPS timestamp：{{selectedVehicle.gps.timestamp}}</div>
      </div>

      <div class="current-alert" :class="{safe:!selectedVehicle.has_active_alert}">
        <div class="current-alert-title"><span>{{selectedVehicle.has_active_alert ? '⚠' : '✓'}}</span><strong>{{selectedVehicle.has_active_alert ? selectedVehicle.latest_alert.alert_type : '当前无活动告警'}}</strong></div>
        <template v-if="selectedVehicle.has_active_alert">
          <p>{{selectedVehicle.latest_alert.description}}</p>
          <div><span>时间 {{shortTime(selectedVehicle.latest_alert.timestamp)}}</span><span>来源 {{sourceText(selectedVehicle)}}</span></div>
        </template>
        <p v-else>车辆状态正常，has_active_alert = false</p>
      </div>
    </article>
  </section>

  <section class="bottom-grid realtime-bottom-grid">
    <article class="panel">
      <div class="panel-title"><div><h2>全部车辆快照</h2><span>前端已按后端字段命名，可直接替换为接口响应</span></div><span class="badge">{{snapshots.length}} 条</span></div>
      <div class="table-wrap"><table><thead><tr><th>车辆 ID</th><th>车牌</th><th>在线</th><th>运输状态</th><th>速度</th><th>经纬度</th><th>告警</th><th>更新时间</th></tr></thead>
      <tbody><tr v-for="v in snapshots" :key="v.vehicle_id" :class="{selectedRow:v.vehicle_id===selectedVehicle.vehicle_id}" @click="selectVehicle(v)">
        <td><strong>{{v.vehicle_id}}</strong></td><td>{{v.display.plate_number}}</td><td><span class="online-mini" :class="{off:!v.online}">{{v.online?'在线':'离线'}}</span></td><td>{{v.transport_status}}</td><td>{{v.gps.speed_kmh}} km/h</td><td>{{v.gps.lat.toFixed(6)}}, {{v.gps.lon.toFixed(6)}}</td><td><span v-if="v.has_active_alert" class="alert-mini">{{v.latest_alert.alert_type}}</span><span v-else class="safe-mini">正常</span></td><td>{{shortTime(v.gps.timestamp)}}</td>
      </tr></tbody></table></div>
    </article>

    <article class="panel">
      <div class="panel-title"><div><h2>活动告警</h2><span>由实时快照直接派生</span></div><RouterLink class="link-btn" to="/alarms">告警中心</RouterLink></div>
      <div class="alarm-list">
        <div v-for="v in activeSnapshots" :key="v.vehicle_id" class="alarm-item">
          <div class="alarm-icon">!</div>
          <div class="alarm-main"><strong>{{v.latest_alert.alert_type}}</strong><p>{{v.display.plate_number}} · {{v.latest_alert.description}}</p><span>{{shortTime(v.latest_alert.timestamp)}} · {{sourceText(v)}}</span></div>
          <span class="status-pill unhandled">活动中</span>
        </div>
        <div v-if="!activeSnapshots.length" class="empty-state">当前无活动告警</div>
      </div>
    </article>
  </section>
</template>
