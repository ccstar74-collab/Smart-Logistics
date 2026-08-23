import { computed, reactive, ref } from 'vue'
import data from '../mock/data.json'

const original = JSON.parse(JSON.stringify(data.vehicleSnapshots || []))
export const snapshots = reactive(JSON.parse(JSON.stringify(original)))
export const running = ref(false)
export const tickCount = ref(0)
export const simSpeed = ref(1)
let timer = null

// v0.8：直接模拟 WGS84 GPS 点。高德地图组件负责 WGS84 -> GCJ-02 显示转换。
// 这些点是“前端演示路线”，目的是在真实城市底图上验证动态数据表现，后续可直接换成后端 GPS 快照。
const gpsRoutes = {
  sim_000: [
    [29.573811,106.552515],[29.573900,106.552720],[29.574020,106.552950],[29.574150,106.553160],
    [29.574290,106.553390],[29.574420,106.553620],[29.574500,106.553870],[29.574460,106.554100],
    [29.574330,106.554310],[29.574160,106.554490],[29.573980,106.554650],[29.573790,106.554760]
  ],
  sim_001: [
    [29.574126,106.553082],[29.573980,106.553120],[29.573820,106.553170],[29.573650,106.553240],
    [29.573470,106.553320],[29.573300,106.553420],[29.573120,106.553540],[29.572950,106.553700],
    [29.572800,106.553880],[29.572680,106.554070],[29.572580,106.554260],[29.572500,106.554470]
  ],
  sim_002: [
    [29.572965,106.551870],[29.573050,106.552060],[29.573130,106.552270],[29.573220,106.552480],
    [29.573300,106.552700],[29.573370,106.552930],[29.573420,106.553170],[29.573430,106.553420],
    [29.573410,106.553680],[29.573360,106.553940],[29.573290,106.554190],[29.573200,106.554430]
  ],
  sim_003: [
    [29.573298,106.550944],[29.573380,106.551090],[29.573470,106.551240],[29.573570,106.551390],
    [29.573680,106.551540],[29.573800,106.551690],[29.573930,106.551850],[29.574070,106.552020]
  ]
}
const routeIndex = reactive({ sim_000:0, sim_001:0, sim_002:0, sim_003:0 })

export const summary = computed(() => ({
  snapshot_count: snapshots.length,
  online_count: snapshots.filter(v => v.online).length,
  transporting_count: snapshots.filter(v => ['已装货','运输中'].includes(v.transport_status)).length,
  active_alert_count: snapshots.filter(v => v.has_active_alert).length
}))

function headingBetween(lat1, lon1, lat2, lon2) {
  const y = Math.sin((lon2-lon1)*Math.PI/180) * Math.cos(lat2*Math.PI/180)
  const x = Math.cos(lat1*Math.PI/180)*Math.sin(lat2*Math.PI/180) -
    Math.sin(lat1*Math.PI/180)*Math.cos(lat2*Math.PI/180)*Math.cos((lon2-lon1)*Math.PI/180)
  return (Math.atan2(y,x)*180/Math.PI + 360) % 360
}

function updateVehicle(v, idx) {
  const now = new Date().toISOString()
  if (!v.online) return

  const route = gpsRoutes[v.vehicle_id] || []
  if (route.length) {
    const oldLat = Number(v.gps.lat)
    const oldLon = Number(v.gps.lon)
    routeIndex[v.vehicle_id] = (routeIndex[v.vehicle_id] + 1) % route.length
    const [lat, lon] = route[routeIndex[v.vehicle_id]]
    v.gps.lat = Number(lat.toFixed(6))
    v.gps.lon = Number(lon.toFixed(6))
    v.gps.heading = Number(headingBetween(oldLat, oldLon, lat, lon).toFixed(1))
  }

  const phase = tickCount.value + idx * 2
  const base = idx === 0 ? 28 : idx === 1 ? 35 : idx === 2 ? 42 : 20
  v.gps.speed_kmh = Number(Math.max(0, base + Math.sin(phase/2.2)*7 + (Math.random()-0.5)*2).toFixed(1))
  v.gps.timestamp = now
  v.status_timestamp = now
  v.display.route_progress = Math.min(99, (v.display.route_progress || 0) + 1)

  // 动态 ETA 只是前端演示：随着路线推进逐步提前。
  if (v.display?.eta && v.display.eta !== '--') {
    const mins = Math.max(4, 24 - Math.floor((v.display.route_progress || 0) / 5))
    const eta = new Date(Date.now() + mins * 60 * 1000)
    v.display.eta = eta.toLocaleTimeString('zh-CN', { hour:'2-digit', minute:'2-digit', hour12:false })
  }

  // sim_000：周期性模拟“异常停留 -> 自动恢复”。
  if (v.vehicle_id === 'sim_000') {
    const cycle = tickCount.value % 24
    if (cycle >= 12 && cycle < 18) {
      v.gps.speed_kmh = 0
      v.has_active_alert = true
      v.latest_alert = { alert_type:'异常停留', description:'车辆连续低速/静止超过模拟阈值', timestamp:now, source:'simulator' }
    } else if (cycle === 18) {
      v.has_active_alert = false
      v.latest_alert = null
    }
  }

  // sim_002：周期性模拟路线偏离告警。
  if (v.vehicle_id === 'sim_002') {
    const cycle = tickCount.value % 30
    if (cycle === 8) {
      v.has_active_alert = true
      v.latest_alert = { alert_type:'路线偏离', description:'车辆偏离计划路线约 260 米（动态演示）', timestamp:now, source:'simulator' }
    }
    if (cycle === 16) {
      v.has_active_alert = false
      v.latest_alert = null
    }
  }
}

export function stepSimulation() {
  tickCount.value += 1
  snapshots.forEach(updateVehicle)
}
function restartTimer() {
  if (timer) clearInterval(timer)
  if (!running.value) return
  timer = setInterval(stepSimulation, Math.max(250, 1000 / simSpeed.value))
}
export function startSimulation() { running.value = true; restartTimer() }
export function pauseSimulation() { running.value = false; if (timer) clearInterval(timer); timer = null }
export function setSimulationSpeed(value) { simSpeed.value = Number(value) || 1; restartTimer() }
export function resetSimulation() {
  pauseSimulation()
  tickCount.value = 0
  snapshots.splice(0, snapshots.length, ...JSON.parse(JSON.stringify(original)))
  Object.keys(routeIndex).forEach(k => { routeIndex[k] = 0 })
}
