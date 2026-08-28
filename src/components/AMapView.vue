<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import AMapLoader from '@amap/amap-jsapi-loader'

const props = defineProps({
  selectedVehicleId: { type: String, default: '' },
  showTrack: { type: Boolean, default: true },
  vehicleIds: { type: Array, default: null },
  plannedRoute: { type: Array, default: () => [] },
  plannedRouteCoordinateSystem: { type: String, default: 'WGS84' },
  actualTrack: { type: Array, default: () => [] },
  routeStartLabel: { type: String, default: '' },
  routeEndLabel: { type: String, default: '' },
  facilityIds: { type: Array, default: null },
  destinationGuides: { type: Array, default: () => [] },
  taskRoutes: { type: Array, default: () => [] },
  taskRoutesCoordinateSystem: { type: String, default: 'WGS84' },
  selectedTaskId: { type: [String, Number], default: null },
  externalVehicles: { type: Array, default: null },
  showFacilities: { type: Boolean, default: true },
  focusSelected: { type: Boolean, default: false }
})
const emit = defineEmits(['select', 'select-task', 'hover-task', 'leave-task'])

const container = ref(null)
const mapReady = ref(false)
const mapError = ref('')
const visibleSnapshots = computed(() => Array.isArray(props.externalVehicles) ? props.externalVehicles : [])
const selected = computed(() => visibleSnapshots.value.find(v => v.vehicle_id === props.selectedVehicleId) || visibleSnapshots.value[0] || null)

let AMap = null
let map = null
let trackLine = null
let plannedLine = null
let actualHistoryLine = null
let routeStartMarker = null
let routeEndMarker = null
const vehicleMarkers = new Map()
const facilityMarkers = new Map()
const destinationGuideLines = new Map()
const taskLines = new Map()
const taskEndpointMarkers = []
const trackPoints = []

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[char])
}

function hasValidGps(vehicle) {
  const lon = Number(vehicle?.gps?.lon), lat = Number(vehicle?.gps?.lat)
  return Number.isFinite(lon) && Number.isFinite(lat) && Math.abs(lon) <= 180 && Math.abs(lat) <= 90 && !(lon === 0 && lat === 0)
}

function mapPointOf(vehicle) {
  const lon = Number(vehicle?.gps?.lon), lat = Number(vehicle?.gps?.lat)
  return String(vehicle?.gps?.coordinate_system).toUpperCase() === 'GCJ02' ? [lon, lat] : wgs84ToGcj02(lon, lat)
}

// 前端演示设施：使用 WGS84，正式项目可替换为后端仓库/配送点接口。
const facilities = []

function nearestFacilityId(point) {
  if (!Array.isArray(point) || point.length < 2) return null
  const [lon, lat] = point.map(Number)
  let best = null, bestDistance = Infinity
  facilities.forEach(f => {
    const distance = Math.hypot(f.lon - lon, f.lat - lat)
    if (distance < bestDistance) { bestDistance = distance; best = f.id }
  })
  return bestDistance <= 0.0006 ? best : null
}
const routeEndpointFacilityIds = computed(() => props.plannedRoute.length < 2 ? new Set() : new Set([
  nearestFacilityId(props.plannedRoute[0]),
  nearestFacilityId(props.plannedRoute[props.plannedRoute.length - 1])
].filter(Boolean)))
const visibleFacilities = computed(() => {
  if (!props.showFacilities) return []
  const scoped = Array.isArray(props.facilityIds) ? facilities.filter(f => props.facilityIds.includes(f.id)) : facilities
  return props.plannedRoute.length >= 2 ? scoped.filter(f => !routeEndpointFacilityIds.value.has(f.id)) : scoped
})

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
  const alarmClass = v.has_active_alert && !props.taskRoutes.length ? ' amap-alarming' : ''
  const alert = v.has_active_alert && !props.taskRoutes.length ? '<i>!</i>' : ''
  const truckSvg = `<svg viewBox="0 0 64 42" aria-hidden="true">
    <rect x="4" y="7" width="36" height="24" rx="3" fill="currentColor"/>
    <path d="M40 14h10l9 9v8H40z" fill="currentColor"/>
    <rect x="45" y="17" width="7" height="6" rx="1" fill="#fff" opacity=".92"/>
    <rect x="8" y="11" width="28" height="3" rx="1.5" fill="#fff" opacity=".20"/>
    <circle cx="16" cy="33" r="5" fill="#fff" stroke="currentColor" stroke-width="3"/>
    <circle cx="49" cy="33" r="5" fill="#fff" stroke="currentColor" stroke-width="3"/>
  </svg>`
  return `<div class="amap-vehicle-marker${selectedClass}${alarmClass}">
    <div class="amap-truck">${truckSvg}</div>
    <span>${v.display?.plate_number || v.vehicle_id}</span>${alert}
  </div>`
}

function routeEndpointContent(type, locationName) {
  const roleText = type === 'start' ? '起' : '终'
  return `<div class="route-location-marker ${type}"><i class="route-location-dot"></i><span class="route-location-label"><b>${roleText}</b><em>${escapeHtml(locationName)}</em></span></div>`
}
function endpointFallbackLabel(point) {
  return facilities.find(f => f.id === nearestFacilityId(point))?.name || '地点'
}
function facilityContent(f) {
  return `<div class="amap-facility ${f.type}"><i class="amap-facility-dot"></i><span>${escapeHtml(f.name)}</span></div>`
}
function refreshFacilities() {
  if (!mapReady.value || !map) return
  const visibleIds = new Set(visibleFacilities.value.map(f => f.id))
  facilityMarkers.forEach((marker, id) => {
    if (!visibleIds.has(id)) { map.remove(marker); facilityMarkers.delete(id) }
  })
  visibleFacilities.value.forEach(f => {
    const pos = wgs84ToGcj02(f.lon, f.lat)
    let marker = facilityMarkers.get(f.id)
    if (!marker) {
      marker = new AMap.Marker({ position: pos, content: facilityContent(f), offset: new AMap.Pixel(-4, -4), zIndex: 120 })
      facilityMarkers.set(f.id, marker)
      map.add(marker)
    } else {
      marker.setPosition(pos)
      marker.setContent(facilityContent(f))
    }
  })
}

function ensureVehicleMarker(v) {
  if (!hasValidGps(v)) return null
  let marker = vehicleMarkers.get(v.vehicle_id)
  const pos = mapPointOf(v)
  if (!marker) {
    marker = new AMap.Marker({ position: pos, content: vehicleContent(v), offset: new AMap.Pixel(-28, -18), zIndex: 200 })
    marker.on('click', () => {
      emit('select', v)
      if (v.task_id != null) emit('select-task', v.task_id)
    })
    vehicleMarkers.set(v.vehicle_id, marker)
    map.add(marker)
  } else {
    marker.setPosition(pos)
    marker.setContent(vehicleContent(v))
  }
  return marker
}

function updateSelectedVisual() {
  if (!map || !AMap || !hasValidGps(selected.value)) return
  const v = selected.value
  const pos = mapPointOf(v)

  vehicleMarkers.forEach((marker, id) => {
    const item = visibleSnapshots.value.find(v => v.vehicle_id === id)
    if (item) marker.setContent(vehicleContent(item))
  })

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
  const visibleIds = new Set(visibleSnapshots.value.filter(hasValidGps).map(v => v.vehicle_id))
  vehicleMarkers.forEach((marker, id) => {
    if (!visibleIds.has(id)) { map.remove(marker); vehicleMarkers.delete(id) }
  })
  visibleSnapshots.value.forEach(ensureVehicleMarker)
  updateSelectedVisual()
  refreshDestinationGuides()
}

function refreshDestinationGuides() {
  if (!mapReady.value || !map || !AMap) return
  const activeKeys = new Set()
  const palette = ['#2f75e8','#18a57a','#d8902f','#7b61d1','#d05252','#3b8fa8']
  props.destinationGuides.forEach((guide, index) => {
    const vehicle = visibleSnapshots.value.find(v => String(v.vehicle_id) === String(guide?.vehicleId))
    const facility = facilities.find(f => f.id === guide?.facilityId)
    if (!hasValidGps(vehicle) || !facility) return
    const key = `${guide.vehicleId}:${guide.facilityId}`
    activeKeys.add(key)
    const path = [wgs84ToGcj02(Number(vehicle.gps.lon), Number(vehicle.gps.lat)), wgs84ToGcj02(facility.lon, facility.lat)]
    let line = destinationGuideLines.get(key)
    if (!line) {
      line = new AMap.Polyline({ path, strokeColor: guide.color || palette[index % palette.length], strokeWeight: 2, strokeOpacity: .62, strokeStyle: 'dashed', showDir: true, zIndex: 105 })
      destinationGuideLines.set(key, line)
      map.add(line)
    } else line.setPath(path)
  })
  destinationGuideLines.forEach((line, key) => {
    if (!activeKeys.has(key)) { map.remove(line); destinationGuideLines.delete(key) }
  })
}

function refreshPlannedRoute() {
  if (!mapReady.value || !map || !AMap) return
  if (plannedLine) { map.remove(plannedLine); plannedLine = null }
  if (routeStartMarker) { map.remove(routeStartMarker); routeStartMarker = null }
  if (routeEndMarker) { map.remove(routeEndMarker); routeEndMarker = null }
  if (props.plannedRoute.length < 2) return
  const path = props.plannedRoute.map(([lon, lat]) => props.plannedRouteCoordinateSystem === 'GCJ02'
    ? [Number(lon), Number(lat)]
    : wgs84ToGcj02(Number(lon), Number(lat)))
  plannedLine = new AMap.Polyline({ path, strokeColor:'#2468e5', strokeWeight:6, strokeOpacity:.9, lineJoin:'round', lineCap:'round', showDir:true, zIndex:115 })
  const startName = props.routeStartLabel || endpointFallbackLabel(props.plannedRoute[0])
  const endName = props.routeEndLabel || endpointFallbackLabel(props.plannedRoute[props.plannedRoute.length - 1])
  routeStartMarker = new AMap.Marker({ position:path[0], content:routeEndpointContent('start', startName), offset:new AMap.Pixel(-4,-4), zIndex:215 })
  routeEndMarker = new AMap.Marker({ position:path[path.length-1], content:routeEndpointContent('end', endName), offset:new AMap.Pixel(-4,-4), zIndex:215 })
  map.add([plannedLine, routeStartMarker, routeEndMarker])
  map.setFitView([plannedLine, ...vehicleMarkers.values()], false, [55,55,55,55], 16)
}

function refreshActualTrack() {
  if (!mapReady.value || !map || !AMap) return
  if (actualHistoryLine) { map.remove(actualHistoryLine); actualHistoryLine = null }
  const path = props.actualTrack
    .map(([lon, lat]) => wgs84ToGcj02(Number(lon), Number(lat)))
    .filter(([lon, lat]) => Number.isFinite(lon) && Number.isFinite(lat))
  if (path.length < 2) return
  actualHistoryLine = new AMap.Polyline({ path, strokeColor:'#16a37a', strokeWeight:5, strokeOpacity:.9, lineJoin:'round', lineCap:'round', showDir:true, zIndex:125 })
  map.add(actualHistoryLine)
  const overlays = [actualHistoryLine, ...(plannedLine ? [plannedLine] : []), ...vehicleMarkers.values()]
  map.setFitView(overlays, false, [55,55,55,55], 16)
}

function taskRoutePointOf(point) {
  const [lon, lat] = point.map(Number)
  return props.taskRoutesCoordinateSystem === 'GCJ02' ? [lon, lat] : wgs84ToGcj02(lon, lat)
}

function taskRouteLineOptions(selected) {
  return selected
    ? { strokeColor: '#2468e5', strokeWeight: 8, strokeOpacity: 1, zIndex: 130 }
    : { strokeColor: '#aebbd0', strokeWeight: 5, strokeOpacity: 0.45, zIndex: 111 }
}

function refreshTaskRoutes(fitView = true) {
  if (!mapReady.value || !map || !AMap) return
  taskLines.forEach(line => map.remove(line))
  taskLines.clear()
  if (taskEndpointMarkers.length) map.remove(taskEndpointMarkers.splice(0))
  props.taskRoutes.forEach(route => {
    if (!Array.isArray(route.path) || route.path.length < 2) return
    const selected = String(route.id) === String(props.selectedTaskId)
    const path = route.path.map(taskRoutePointOf)
    const line = new AMap.Polyline({ path, ...taskRouteLineOptions(selected), lineJoin: 'round', lineCap: 'round', showDir: true, cursor: 'pointer' })
    line.on('click', () => emit('select-task', route.id))
    line.on('mousemove', event => {
      line.setOptions({ strokeWeight: 9, strokeOpacity: 1, zIndex: 140 })
      const pixel = map.lngLatToContainer(event.lnglat)
      const size = map.getSize()
      emit('hover-task', { id: route.id, x: Math.min(pixel.x, size.width - 390), y: Math.max(150, pixel.y) })
    })
    line.on('mouseout', () => {
      line.setOptions(taskRouteLineOptions(String(route.id) === String(props.selectedTaskId)))
      emit('leave-task', route.id)
    })
    taskLines.set(String(route.id), line)
    map.add(line)
    const startLabel = escapeHtml(route.startLabel || '发货地')
    const endLabel = escapeHtml(route.endLabel || '收货地')
    const endpointZIndex = selected ? 212 : 208
    const endpointDim = selected ? '' : ' style="opacity:.45"'
    const start = new AMap.Marker({ position: path[0], content: `<div class="task-route-point start"${endpointDim}><b>发</b><span>${startLabel}</span></div>`, offset: new AMap.Pixel(-13,-15), zIndex: endpointZIndex })
    const end = new AMap.Marker({ position: path[path.length-1], content: `<div class="task-route-point receive"${endpointDim}><b>收</b><span>${endLabel}</span></div>`, offset: new AMap.Pixel(-13,-15), zIndex: endpointZIndex })
    taskEndpointMarkers.push(start, end)
    map.add([start, end])
  })
  if (fitView) {
    const overlays = [...taskLines.values(), ...vehicleMarkers.values()]
    if (overlays.length) map.setFitView(overlays, false, [55, 55, 55, 55], 16)
  }
}

function focusOnSelectedTask() {
  if (!mapReady.value || !map || !AMap) return
  const line = taskLines.get(String(props.selectedTaskId))
  if (!line) return
  const overlays = [line]
  visibleSnapshots.value.forEach(item => {
    if (String(item.task_id) === String(props.selectedTaskId)) {
      const marker = vehicleMarkers.get(item.vehicle_id)
      if (marker) overlays.push(marker)
    }
  })
  map.setFitView(overlays, false, [55, 55, 55, 55], 16)
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
    mapReady.value = true
    refreshFacilities()
    refreshVehicles()
    refreshDestinationGuides()
    refreshPlannedRoute()
    refreshActualTrack()
    refreshTaskRoutes()
  } catch (e) {
    console.error(e)
    mapError.value = `高德地图加载失败：${e?.message || '请检查 Key、安全密钥和网络'}`
  }
})

watch(() => props.vehicleIds, refreshVehicles, { deep: true })
watch(() => props.externalVehicles, refreshVehicles, { deep: true })
watch(() => props.facilityIds, refreshFacilities, { deep: true })
watch(() => props.destinationGuides, refreshDestinationGuides, { deep: true })
watch(() => props.plannedRoute, () => { refreshFacilities(); refreshPlannedRoute() }, { deep: true })
watch(() => props.plannedRouteCoordinateSystem, refreshPlannedRoute)
watch(() => props.actualTrack, refreshActualTrack, { deep: true })
watch(() => props.routeStartLabel, refreshPlannedRoute)
watch(() => props.routeEndLabel, refreshPlannedRoute)
let lastTaskRouteKeys = ''
watch(() => props.taskRoutes, () => {
  const keys = props.taskRoutes.map(route => String(route.id)).sort().join(',')
  const routeSetChanged = keys !== lastTaskRouteKeys
  lastTaskRouteKeys = keys
  refreshTaskRoutes(routeSetChanged)
}, { deep: true })
watch(() => props.taskRoutesCoordinateSystem, () => refreshTaskRoutes(true))
watch(() => props.selectedTaskId, () => {
  refreshTaskRoutes(false)
  focusOnSelectedTask()
})
watch(() => props.selectedVehicleId, () => {
  trackPoints.splice(0)
  if (trackLine) trackLine.setPath([])
  refreshVehicles()
  if (props.focusSelected && hasValidGps(selected.value)) {
    map?.panTo(mapPointOf(selected.value))
  }
})

onBeforeUnmount(() => {
  vehicleMarkers.clear()
  facilityMarkers.clear()
  destinationGuideLines.clear()
  taskLines.clear()
  taskEndpointMarkers.splice(0)
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
      <span>高德 JS API · 车辆位置追踪</span>
    </div>
    <div v-if="mapError" class="amap-config-mask">
      <div>
        <strong>地图暂未加载</strong>
        <p>{{ mapError }}</p>
        <code>VITE_AMAP_KEY=你的Key<br>VITE_AMAP_SECURITY_CODE=你的securityJsCode</code>
      </div>
    </div>
    <div class="amap-legend-custom" :class="{ 'task-route-legend': taskRoutes.length }">
      <template v-if="taskRoutes.length"><span><i class="vehicle"></i>车辆</span><span><i class="multi-route selected"></i>选中订单路线</span><span><i class="multi-route dim"></i>其他订单路线</span><span class="legend-route-point start"><b>发</b>发货地</span><span class="legend-route-point receive"><b>收</b>收货地</span></template>
      <template v-else>
        <span class="legend-item"><i class="legend-truck" aria-hidden="true"><svg viewBox="0 0 68 44"><rect x="3" y="7" width="39" height="25" rx="4"/><path d="M42 15h11l11 10v7H42z"/><circle cx="16" cy="34" r="5"/><circle cx="53" cy="34" r="5"/></svg></i><b>车辆</b></span>
        <span class="legend-item"><i class="legend-warehouse"></i><b>仓库</b></span>
        <span class="legend-item"><i class="legend-delivery"></i><b>配送点</b></span>
        <span class="legend-item"><i class="legend-track"></i><b>轨迹</b></span>
      </template>
    </div>
  </div>
</template>
