<script setup>
import { computed, onMounted, ref } from 'vue'
import data from '../mock/data.json'
import { snapshots } from '../stores/realtime'

const props = defineProps({
  selectedVehicleId: { type: String, default: 'sim_000' },
  showTrack: { type: Boolean, default: true }
})
const emit = defineEmits(['select'])
const selected = computed(() => snapshots.find(v => v.vehicle_id === props.selectedVehicleId) || snapshots[0])
const mapData = ref(null)
const mapError = ref('')

onMounted(async () => {
  try {
    const response = await fetch('/data/town10hd-map.json')
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    mapData.value = await response.json()
  } catch (e) {
    mapError.value = 'Town10HD 地图数据加载失败'
    console.error(e)
  }
})

function pointStyle(x, y) {
  return { left: `${x / 10}%`, top: `${y / 6.5}%` }
}
function roadPath(points) {
  if (!points?.length) return ''
  return points.map((p, i) => `${i === 0 ? 'M' : 'L'}${p[0]},${p[1]}`).join(' ')
}
const primaryRoads = computed(() => mapData.value?.roads.filter(r => r.junction === '-1') || [])
const junctionRoads = computed(() => mapData.value?.roads.filter(r => r.junction !== '-1') || [])
function poiSide(f) { return f.x > 500 ? 'left' : 'right' }
function headingStyle(v) { return { transform: `rotate(${v.gps?.heading || 0}deg)` } }
</script>

<template>
  <div class="map carla-map">
    <div class="map-info-chip">
      <strong>CARLA · Town10HD</strong>
      <span>OpenDRIVE 路网 · 前端模拟实时快照</span>
    </div>

    <div v-if="!mapData && !mapError" class="map-loading">正在加载 Town10HD 道路数据…</div>
    <div v-else-if="mapError" class="map-loading error">{{ mapError }}</div>

    <svg v-if="mapData" class="carla-road-layer" viewBox="0 0 1000 650" preserveAspectRatio="xMidYMid meet">
      <rect x="0" y="0" width="1000" height="650" class="map-ground" />
      <g class="road-shadow-layer"><path v-for="road in primaryRoads" :key="'shadow-'+road.id" :d="roadPath(road.displayPoints)" /></g>
      <g class="road-base-layer"><path v-for="road in primaryRoads" :key="'base-'+road.id" :d="roadPath(road.displayPoints)" /></g>
      <g class="road-center-layer"><path v-for="road in primaryRoads" :key="'center-'+road.id" :d="roadPath(road.displayPoints)" /></g>
      <g class="junction-guide-layer"><path v-for="road in junctionRoads" :key="'junction-'+road.id" :d="roadPath(road.displayPoints)" /></g>
      <g v-if="showTrack && mapData.demoRoute" class="route-highlight-layer"><path :d="roadPath(mapData.demoRoute)" /></g>
    </svg>

    <template v-if="mapData">
      <div v-for="f in mapData.facilities" :key="f.name" class="map-poi" :class="[f.type, poiSide(f)]" :style="pointStyle(f.x, f.y)">
        <span class="poi-anchor"><span class="poi-anchor-core">{{ f.type === 'warehouse' ? '仓' : '配' }}</span></span>
        <span class="poi-connector"></span>
        <span class="poi-card"><b>{{ f.name }}</b><small>{{ f.type === 'warehouse' ? '物流仓库' : '配送节点' }}</small></span>
      </div>

      <button
        v-for="v in snapshots"
        :key="v.vehicle_id"
        class="vehicle-dot carla-vehicle"
        :class="{ selected: selected?.vehicle_id===v.vehicle_id, offline:!v.online, alarming:v.has_active_alert }"
        :style="pointStyle(v.display.map_x, v.display.map_y)"
        :title="`${v.display.plate_number} · ${v.transport_status}`"
        @click="emit('select', v)"
      >
        <span class="heading-arrow" :style="headingStyle(v)">↑</span>
        <span class="truck-icon">🚚</span>
        <span class="vehicle-label">{{ v.display.plate_number }}</span>
        <i v-if="v.has_active_alert" class="alert-pulse">!</i>
      </button>
    </template>

    <div class="map-legend">
      <span><i class="legend-road"></i>主路</span>
      <span><i class="legend-route"></i>运输路线</span>
      <span><i class="legend-warehouse"></i>仓库</span>
      <span><i class="legend-delivery"></i>配送点</span>
      <span><i class="legend-alert"></i>异常车辆</span>
    </div>
  </div>
</template>
