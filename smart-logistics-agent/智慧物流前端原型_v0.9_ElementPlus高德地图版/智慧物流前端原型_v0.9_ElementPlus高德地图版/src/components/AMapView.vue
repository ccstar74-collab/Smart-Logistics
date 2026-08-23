<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import AMapLoader from '@amap/amap-jsapi-loader'
import { snapshots } from '../stores/realtime'

const props = defineProps({
  selectedVehicleId: { type: String, default: 'sim_000' },
  showTrack: { type: Boolean, default: true }
})
const emit = defineEmits(['select'])

const container = ref(null)
const mapReady = ref(false)
const mapError = ref('')
const selected = computed(() => snapshots.find(v => v.vehicle_id === props.selectedVehicleId) || snapshots[0])

let AMap = null
let map = null
let trackLine = null
let selectedRing = null
const vehicleMarkers = new Map()
const trackPoints = []

// 前端演示设施：使用 WGS84，正式项目可替换为后端仓库/配送点接口。
const facilities = [
  { id: 'WH001', name: '中心仓库 A', type: 'warehouse', lon: 106.55090, lat: 29.57330, radius: 180 },
  { id: 'WH002', name: '冷链仓库 C', type: 'warehouse', lon: 106.55318, lat: 29.57510, radius: 140 },
  { id: 'DP001', name: '配送点 B', type: 'delivery', lon: 106.55530, lat: 29.57462 },
  { id: 'DP002', name: '配送点 D', type: 'delivery', lon: 106.55420, lat: 29.57192 }
]

function outOfChina(lon, lat) {
  return lon < 72.004 || lon > 137.8347 || lat < 0.8293 || lat > 55.8271
}
function transformLat(x, y) {
  let ret = -100 + 2*x + 3*y + 0.2*y*y + 0.1*x*y + 0.2*Math.sqrt(Math.abs(x))
  ret += (20*Math.sin(6*x*Math.PI) + 20*Math.sin(2*x*Math.PI))*2/3
  ret += (20*Math.sin(y*Math.PI) + 40*Math.sin(y/3*Math.PI))*2/3
  ret += (160*Math.sin(y/12*Math.PI) + 320*Math.sin(y*Math.PI/30))*2/3
  return ret
}
function transformLon(x, y) {
  let ret = 300 + x + 2*y + 0.1*x*x + 0.1*x*y + 0.1*Math.sqrt(Math.abs(x))
  ret += (20*Math.sin(6*x*Math.PI) + 20*Math.sin(2*x*Math.PI))*2/3
  ret += (20*Math.sin(x*Math.PI) + 40*Math.sin(x/3*Math.PI))*2/3
  ret += (150*Math.sin(x/12*Math.PI) + 300*Math.sin(x/30*Math.PI))*2/3
  return ret
}
function wgs84ToGcj02(lon, lat) {
  if (outOfChina(lon, lat)) return [lon, lat]
  const a = 6378245.0
  const ee = 0.006693421622965943
  let dLat = transformLat(lon - 105, lat - 35)
  let dLon = transformLon(lon - 105, lat - 35)
  const radLat = lat / 180 * Math.PI
  let magic = Math.sin(radLat)
  magic = 1 - ee * magic * magic
  const sqrtMagic = Math.sqrt(magic)
  dLat = (dLat * 180) / ((a * (1-ee)) / (magic * sqrtMagic) * Math.PI)
  dLon = (dLon * 180) / (a / sqrtMagic * Math.cos(radLat) * Math.PI)
  return [lon + dLon, lat + dLat]
}

function vehicleContent(v) {
  const selectedClass = v.vehicle_id === props.selectedVehicleId ? ' amap-selected' : ''
  const alarmClass = v.has_active_alert ? ' amap-alarming' : ''
  const offlineClass = !v.online ? ' amap-offline' : ''
  const alert = v.has_active_alert ? '<i>!</i>' : ''
  return `<div class="amap-vehicle-marker${selectedClass}${alarmClass}${offlineClass}">
    <div class="amap-truck" style="transform:rotate(${Number(v.gps?.heading || 0)}deg)">▲</div>
    <span>${v.display?.plate_number || v.vehicle_id}</span>${alert}
  </div>`
}

function addFacilities() {
  facilities.forEach(f => {
    const pos = wgs84ToGcj02(f.lon, f.lat)
    const content = `<div class="amap-facility ${f.type}"><b>${f.type === 'warehouse' ? '仓' : '配'}</b><span>${f.name}</span></div>`
    const marker = new AMap.Marker({ position: pos, content, offset: new AMap.Pixel(-18, -18), zIndex: 120 })
    map.add(marker)
    if (f.radius) {
      const circle = new AMap.Circle({ center: pos, radius: f.radius, strokeWeight: 1.5, strokeOpacity: .8, fillOpacity: .10 })
      map.add(circle)
    }
  })
}

function ensureVehicleMarker(v) {
  let marker = vehicleMarkers.get(v.vehicle_id)
  const pos = wgs84ToGcj02(Number(v.gps.lon), Number(v.gps.lat))
  if (!marker) {
    marker = new AMap.Marker({ position: pos, content: vehicleContent(v), offset: new AMap.Pixel(-20, -20), zIndex: 200 })
    marker.on('click', () => emit('select', v))
    vehicleMarkers.set(v.vehicle_id, marker)
    map.add(marker)
  } else {
    marker.setPosition(pos)
    marker.setContent(vehicleContent(v))
  }
  return marker
}

function updateSelectedVisual() {
  if (!map || !AMap || !selected.value) return
  const v = selected.value
  const pos = wgs84ToGcj02(Number(v.gps.lon), Number(v.gps.lat))

  vehicleMarkers.forEach((marker, id) => {
    const item = snapshots.find(v => v.vehicle_id === id)
    if (item) marker.setContent(vehicleContent(item))
  })

  if (!selectedRing) {
    selectedRing = new AMap.Circle({ center: pos, radius: 45, strokeWeight: 2, strokeOpacity: .85, fillOpacity: .05, zIndex: 150 })
    map.add(selectedRing)
  } else {
    selectedRing.setCenter(pos)
  }

  if (props.showTrack) {
    const last = trackPoints[trackPoints.length - 1]
    if (!last || Math.abs(last[0]-pos[0]) + Math.abs(last[1]-pos[1]) > 0.000001) {
      trackPoints.push(pos)
      if (trackPoints.length > 45) trackPoints.shift()
    }
    if (!trackLine) {
      trackLine = new AMap.Polyline({ path: [...trackPoints], strokeWeight: 5, strokeOpacity: .8, showDir: true, zIndex: 110 })
      map.add(trackLine)
    } else {
      trackLine.setPath([...trackPoints])
    }
  }
}

function refreshVehicles() {
  if (!mapReady.value || !map) return
  snapshots.forEach(ensureVehicleMarker)
  updateSelectedVisual()
}

onMounted(async () => {
  const key = import.meta.env.VITE_AMAP_KEY
  const securityJsCode = import.meta.env.VITE_AMAP_SECURITY_CODE
  if (!key || !securityJsCode) {
    mapError.value = '尚未配置高德 Key。请复制 .env.example 为 .env.local，并填写 VITE_AMAP_KEY 与 VITE_AMAP_SECURITY_CODE。'
    return
  }
  try {
    window._AMapSecurityConfig = { securityJsCode }
    AMap = await AMapLoader.load({
      key,
      version: '2.0',
      plugins: ['AMap.Scale', 'AMap.ToolBar']
    })
    const center = wgs84ToGcj02(106.552515, 29.573811)
    map = new AMap.Map(container.value, {
      viewMode: '3D',
      zoom: 15,
      pitch: 42,
      rotation: 0,
      center,
      mapStyle: 'amap://styles/normal',
      showBuildingBlock: true
    })
    map.addControl(new AMap.Scale())
    map.addControl(new AMap.ToolBar({ position: 'RT' }))
    addFacilities()
    mapReady.value = true
    refreshVehicles()
  } catch (e) {
    console.error(e)
    mapError.value = `高德地图加载失败：${e?.message || '请检查 Key、安全密钥和网络'}`
  }
})

watch(snapshots, refreshVehicles, { deep: true })
watch(() => props.selectedVehicleId, () => {
  trackPoints.splice(0)
  if (trackLine) trackLine.setPath([])
  refreshVehicles()
})

onBeforeUnmount(() => {
  vehicleMarkers.clear()
  if (map) map.destroy()
  map = null
  AMap = null
})
</script>

<template>
  <div class="amap-shell">
    <div ref="container" class="amap-container"></div>
    <div class="amap-info-chip">
      <strong>重庆真实城市地图</strong>
      <span>高德 JS API · 动态车辆快照演示</span>
    </div>
    <div v-if="mapError" class="amap-config-mask">
      <div>
        <strong>地图暂未加载</strong>
        <p>{{ mapError }}</p>
        <code>VITE_AMAP_KEY=你的Key<br>VITE_AMAP_SECURITY_CODE=你的securityJsCode</code>
      </div>
    </div>
    <div class="amap-legend-custom">
      <span><i class="vehicle"></i>车辆</span>
      <span><i class="warehouse"></i>仓库 / 电子围栏</span>
      <span><i class="delivery"></i>配送点</span>
      <span><i class="track"></i>选中车辆轨迹</span>
    </div>
  </div>
</template>
