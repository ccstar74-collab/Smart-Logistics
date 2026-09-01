<script setup>
import { nextTick, onMounted, ref } from 'vue'
import PageHeader from '../components/PageHeader.vue'
import AMapView from '../components/AMapView.vue'
import { askAgent, getAgentHealth, getOrCreateAgentSessionId } from '../api/agent'
const input = ref(''), sending = ref(false), status = ref('checking')
const chatBody = ref(null)
const latestVehicleLocation = ref(null)
const mapDialogVisible = ref(false)
const mapDialogLocation = ref(null)
const messages = ref([{ role: 'assistant', text: '你好，我是云端物流智能助手，可以回答物流知识问题，也可以查询车辆、货物、运输任务和告警。' }])
let sessionId = getOrCreateAgentSessionId()
function normalizeVehicleLocation(result) {
  const data = result?.data?.location ? result.data : result?.vehicleLocation ?? result?.location ?? result?.data
  const location = data?.location ?? data?.gps ?? data
  const longitude = Number(location?.longitude ?? location?.lon ?? location?.lng)
  const latitude = Number(location?.latitude ?? location?.lat)
  if (!Number.isFinite(longitude) || !Number.isFinite(latitude) || (longitude === 0 && latitude === 0)) return null
  const vehicle = data?.vehicle ?? result?.vehicle ?? {}
  return {
    vehicle_id: String(vehicle.id ?? vehicle.vehicleId ?? data?.vehicleId ?? location?.vehicleId ?? 'agent-location'),
    online: location?.online ?? data?.online ?? true,
    gps: {
      lon: longitude,
      lat: latitude,
      speed_kmh: Number(location?.speed ?? location?.speed_kmh ?? 0),
      heading: Number(location?.direction ?? location?.heading ?? 0),
      timestamp: location?.collectedAt ?? location?.timestamp ?? location?.collectTime
    },
    display: {
      plate_number: vehicle.plateNumber ?? vehicle.plate_number ?? data?.plateNumber ?? data?.plate_number ?? '查询车辆'
    }
  }
}
async function refreshHealth() {
  status.value = 'checking'
  try { const health = await getAgentHealth(); status.value = health.status !== 'UP' ? 'offline' : !health.modelEnabled ? 'model-disabled' : !health.businessDataEnabled ? 'business-disabled' : 'online' }
  catch { status.value = 'offline' }
}
async function send() {
  const question = input.value.trim()
  if (!question || sending.value) return
  messages.value.push({ role: 'user', text: question }); input.value = ''; sending.value = true
  await scrollToBottom()
  try {
    const result = await askAgent(question, sessionId)
    const sources = [...new Set((result.sources || []).map(item => item?.source).filter(Boolean))]
    const location = normalizeVehicleLocation(result)
    if (location) latestVehicleLocation.value = location
    messages.value.push({ role: 'assistant', text: result.answer || '智能体未返回回答', mode: result.mode, sources, location })
  } catch (error) { messages.value.push({ role: 'system', text: error instanceof Error ? error.message : '智能体连接失败' }) }
  finally { sending.value = false; await scrollToBottom() }
}
function newConversation() { sessionId = getOrCreateAgentSessionId(true); latestVehicleLocation.value = null; messages.value = [{ role: 'assistant', text: '已开启新对话，请问有什么可以帮你？' }] }
function openLocationMap(location) { mapDialogLocation.value = location; mapDialogVisible.value = true }
async function scrollToBottom() { await nextTick(); if (chatBody.value) chatBody.value.scrollTop = chatBody.value.scrollHeight }
function boldSegments(text) {
  const value = String(text ?? '')
  const segments = []
  const pattern = /\*\*([\s\S]+?)\*\*/g
  let cursor = 0, match
  while ((match = pattern.exec(value))) {
    if (match.index > cursor) segments.push({ text: value.slice(cursor, match.index), bold: false })
    segments.push({ text: match[1], bold: true })
    cursor = pattern.lastIndex
  }
  if (cursor < value.length) segments.push({ text: value.slice(cursor), bold: false })
  return segments.length ? segments : [{ text: value, bold: false }]
}
onMounted(refreshHealth)
</script>
<template>
  <div class="agent-view">
    <PageHeader title="智能问答" subtitle="已接入云端 Java 智能体、通义千问和真实物流业务数据" />
    <section class="chat panel">
    <div class="agent-toolbar"><span :class="['agent-dot', status]"></span><span>{{ status === 'online' ? '智能体在线' : status === 'checking' ? '正在检查服务' : status === 'business-disabled' ? '智能体在线，业务数据未连接' : status === 'model-disabled' ? '智能体在线，模型未启用' : '智能体离线' }}</span><button class="mini" @click="refreshHealth">检查状态</button><button class="mini" @click="newConversation">新对话</button></div>
    <div ref="chatBody" class="chat-body" aria-live="polite"><div v-for="(message,index) in messages" :key="index" :class="['bubble', message.role]"><div class="message-rich-text"><template v-for="(segment,segmentIndex) in boldSegments(message.text)" :key="segmentIndex"><strong v-if="segment.bold">{{ segment.text }}</strong><span v-else>{{ segment.text }}</span></template></div><small v-if="message.mode">回答模式：{{ message.mode }}</small><small v-if="message.sources?.length">来源：{{ message.sources.join('、') }}</small><button v-if="message.location" type="button" class="message-map-button" @click="openLocationMap(message.location)">查看 {{message.location.display.plate_number}} 的地图位置</button></div><div v-if="sending" class="bubble assistant">智能体正在思考……</div></div>
    <form class="chat-input" @submit.prevent="send"><input v-model="input" maxlength="2000" placeholder="例如：查询目前空闲车辆，或运输途中发生偏航应该怎么处理？" /><button class="primary" type="submit" :disabled="sending || !input.trim()">{{ sending ? '发送中' : '发送' }}</button></form>
    </section>
    <el-dialog v-model="mapDialogVisible" class="agent-map-dialog" width="min(1280px, 96vw)" append-to-body destroy-on-close align-center>
      <template #header><div class="agent-map-dialog-title"><strong>车辆实时位置</strong><span v-if="mapDialogLocation">{{mapDialogLocation.display.plate_number}} · {{mapDialogLocation.gps.lat}}, {{mapDialogLocation.gps.lon}}</span></div></template>
      <AMapView v-if="mapDialogLocation && mapDialogVisible" :selectedVehicleId="mapDialogLocation.vehicle_id" :externalVehicles="[mapDialogLocation]" :showTrack="false" :showFacilities="false" focus-selected />
    </el-dialog>
  </div>
</template>
