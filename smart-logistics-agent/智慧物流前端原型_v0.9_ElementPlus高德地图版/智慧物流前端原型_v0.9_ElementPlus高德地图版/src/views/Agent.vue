<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import PageHeader from '../components/PageHeader.vue'
import { chatWithAgent, getAgentHealth } from '../api/agent'

const SESSION_KEY = 'smart-logistics-agent-session'
const suggestions = [
  '渝A10000现在在哪？',
  'sim_005的实时位置和状态是什么？',
  '显示所有车辆分布',
  '运输途中发生偏航应该怎么处理？'
]
const modeLabels = {
  model: '千问生成',
  tool: '实时工具',
  extractive: '知识检索',
  no_context: '无匹配知识',
  guardrail: '能力边界',
  welcome: '智能助手'
}

const input = ref('')
const sending = ref(false)
const health = ref(null)
const healthError = ref('')
const healthLoading = ref(false)
const chatBody = ref(null)

function createSessionId() {
  const id = globalThis.crypto?.randomUUID?.() || `web-${Date.now()}-${Math.random().toString(16).slice(2)}`
  localStorage.setItem(SESSION_KEY, id)
  return id
}

const sessionId = ref(localStorage.getItem(SESSION_KEY) || createSessionId())
const messages = ref([welcomeMessage()])

function welcomeMessage() {
  return {
    role: 'assistant',
    text: '你好，我是智慧物流智能助手。我可以结合物流知识库回答业务问题，也可以查询 CARLA 测试车辆的实时位置和运行状态。',
    mode: 'welcome'
  }
}

const serviceOnline = computed(() => health.value?.status === 'UP')
const modelText = computed(() => {
  if (!health.value) return '--'
  return health.value.modelEnabled ? `${health.value.modelName || '大模型'} 已启用` : '未启用（知识检索模式）'
})

function modeLabel(mode) {
  return modeLabels[mode] || mode || '智能助手'
}

function formatNumber(value, digits = 6) {
  const number = Number(value)
  return Number.isFinite(number) ? number.toFixed(digits) : '--'
}

function vehicleOf(message) {
  return message.toolData?.vehicle || null
}

function fleetOf(message) {
  return Array.isArray(message.toolData?.vehicles) ? message.toolData.vehicles : null
}

async function scrollToBottom() {
  await nextTick()
  if (chatBody.value) chatBody.value.scrollTop = chatBody.value.scrollHeight
}

async function refreshHealth() {
  healthLoading.value = true
  healthError.value = ''
  try {
    health.value = await getAgentHealth()
  } catch (error) {
    health.value = null
    healthError.value = error.message || '无法连接智能体服务'
  } finally {
    healthLoading.value = false
  }
}

async function send(question) {
  const content = (typeof question === 'string' ? question : input.value).trim()
  if (!content || sending.value) return

  messages.value.push({ role: 'user', text: content })
  input.value = ''
  sending.value = true
  await scrollToBottom()

  try {
    const response = await chatWithAgent(content, sessionId.value)
    messages.value.push({
      role: 'assistant',
      text: response.answer || '智能体没有返回文字内容。',
      mode: response.mode,
      sources: response.sources || [],
      toolData: response.toolData || null
    })
  } catch (error) {
    messages.value.push({
      role: 'assistant',
      text: `请求失败：${error.message || '无法连接智能体服务'}。请确认 Java 后端已在 8080 端口启动。`,
      mode: 'error',
      error: true
    })
    refreshHealth()
  } finally {
    sending.value = false
    await scrollToBottom()
  }
}

function handleEnter(event) {
  if (event.shiftKey) return
  event.preventDefault()
  send()
}

function newConversation() {
  sessionId.value = createSessionId()
  messages.value = [welcomeMessage()]
  input.value = ''
  nextTick(() => document.querySelector('.agent-textarea')?.focus())
}

onMounted(() => {
  refreshHealth()
  scrollToBottom()
})
</script>

<template>
  <PageHeader title="智能问答" subtitle="物流知识问答、车辆实时查询与业务咨询入口" />

  <div class="agent-layout">
    <section class="agent-chat panel">
      <header class="agent-chat-header">
        <div>
          <h3>智慧物流助手</h3>
          <p>知识库 + 通义千问 + CARLA 实时车辆数据</p>
        </div>
        <button class="agent-secondary-button" type="button" @click="newConversation">新对话</button>
      </header>

      <div ref="chatBody" class="agent-chat-body">
        <article
          v-for="(message, index) in messages"
          :key="index"
          class="agent-message-row"
          :class="message.role"
        >
          <div class="agent-avatar">{{ message.role === 'user' ? '我' : '智' }}</div>
          <div class="agent-message-content">
            <div class="agent-message-meta">
              <span>{{ message.role === 'user' ? '当前用户' : '物流智能体' }}</span>
              <span v-if="message.mode" class="agent-mode" :class="{ error: message.error }">
                {{ modeLabel(message.mode) }}
              </span>
            </div>
            <div class="agent-bubble" :class="{ error: message.error }">{{ message.text }}</div>

            <div v-if="vehicleOf(message)" class="vehicle-card">
              <div class="vehicle-card-title">
                <strong>{{ vehicleOf(message).plateNumber || vehicleOf(message).deviceCode || '车辆' }}</strong>
                <span :class="vehicleOf(message).online ? 'online' : 'offline'">
                  {{ vehicleOf(message).online ? '在线' : '离线' }}
                </span>
              </div>
              <div class="vehicle-grid">
                <div><label>设备编号</label><span>{{ vehicleOf(message).deviceCode || '--' }}</span></div>
                <div><label>车辆 ID</label><span>{{ vehicleOf(message).vehicleId ?? '--' }}</span></div>
                <div><label>经度</label><span>{{ formatNumber(vehicleOf(message).longitude) }}</span></div>
                <div><label>纬度</label><span>{{ formatNumber(vehicleOf(message).latitude) }}</span></div>
                <div><label>运行状态</label><span>{{ vehicleOf(message).status || '--' }}</span></div>
                <div><label>速度</label><span>{{ formatNumber(vehicleOf(message).speed, 1) }} km/h</span></div>
                <div><label>方向角</label><span>{{ formatNumber(vehicleOf(message).direction, 1) }}°</span></div>
                <div><label>最近节点</label><span>{{ vehicleOf(message).nearestLocation?.name || '--' }}</span></div>
                <div class="wide"><label>数据时间</label><span>{{ vehicleOf(message).recordedAt || '--' }}</span></div>
              </div>
            </div>

            <div v-if="fleetOf(message)" class="fleet-card">
              已获取 <strong>{{ fleetOf(message).length }}</strong> 辆车辆的最新位置数据，可继续输入车牌号或设备编号查询详情。
            </div>

            <div v-if="message.sources?.length" class="source-list">
              <span>参考来源</span>
              <span v-for="(source, sourceIndex) in message.sources" :key="sourceIndex" class="source-chip">
                {{ source.title || source.name || source.source || `知识片段 ${sourceIndex + 1}` }}
              </span>
            </div>
          </div>
        </article>

        <article v-if="sending" class="agent-message-row assistant">
          <div class="agent-avatar">智</div>
          <div class="agent-message-content">
            <div class="agent-message-meta"><span>物流智能体</span></div>
            <div class="agent-bubble thinking"><i></i><i></i><i></i><span>正在思考并查询数据</span></div>
          </div>
        </article>
      </div>

      <footer class="agent-composer">
        <textarea
          v-model="input"
          class="agent-textarea"
          rows="2"
          :disabled="sending"
          placeholder="输入问题，例如：渝A10000现在在哪？（Enter 发送，Shift+Enter 换行）"
          @keydown.enter="handleEnter"
        ></textarea>
        <button class="agent-send-button" type="button" :disabled="sending || !input.trim()" @click="send()">
          {{ sending ? '发送中' : '发送' }}
        </button>
      </footer>
    </section>

    <aside class="agent-side">
      <section class="panel agent-status-card">
        <div class="side-title-row">
          <h3>服务状态</h3>
          <button type="button" class="text-button" :disabled="healthLoading" @click="refreshHealth">刷新</button>
        </div>
        <div class="status-main">
          <span class="status-dot" :class="serviceOnline ? 'online' : 'offline'"></span>
          <strong>{{ healthLoading ? '检测中' : serviceOnline ? '智能体在线' : '智能体离线' }}</strong>
        </div>
        <p v-if="healthError" class="health-error">{{ healthError }}</p>
        <dl class="status-list">
          <div><dt>模型</dt><dd>{{ modelText }}</dd></div>
          <div><dt>知识片段</dt><dd>{{ health?.knowledgeChunks ?? '--' }}</dd></div>
          <div><dt>实时数据</dt><dd>{{ health?.realtimeDataEnabled ? '已启用' : '未启用' }}</dd></div>
          <div><dt>测试车辆</dt><dd>{{ health?.realtimeVehicleCount ?? '--' }} 辆</dd></div>
        </dl>
      </section>

      <section class="panel agent-suggestions">
        <h3>你可以这样问</h3>
        <button v-for="suggestion in suggestions" :key="suggestion" type="button" :disabled="sending" @click="send(suggestion)">
          {{ suggestion }}
        </button>
      </section>

      <section class="panel agent-tip">
        <h3>能力说明</h3>
        <p>车辆实时查询支持车牌号、设备编号和车辆 ID。业务问答会结合本地知识库，并在已配置模型时调用通义千问生成答案。</p>
        <p>模型密钥仅配置在 Java 后端，不会传给浏览器。</p>
      </section>
    </aside>
  </div>
</template>

<style scoped>
.agent-layout { display: grid; grid-template-columns: minmax(0, 1fr) 320px; gap: 18px; min-height: calc(100vh - 150px); }
.agent-chat { display: flex; flex-direction: column; min-height: 680px; overflow: hidden; padding: 0; }
.agent-chat-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 18px 22px; border-bottom: 1px solid #e8edf5; }
.agent-chat-header h3, .agent-side h3 { margin: 0; color: #1d2b45; font-size: 16px; }
.agent-chat-header p { margin: 5px 0 0; color: #8792a7; font-size: 12px; }
.agent-secondary-button { border: 1px solid #cbd7e8; border-radius: 7px; background: #fff; color: #3d5f91; padding: 8px 14px; cursor: pointer; }
.agent-chat-body { flex: 1; overflow-y: auto; padding: 24px; background: #f7f9fc; }
.agent-message-row { display: flex; align-items: flex-start; gap: 10px; margin-bottom: 22px; }
.agent-message-row.user { flex-direction: row-reverse; }
.agent-avatar { display: grid; place-items: center; width: 34px; height: 34px; flex: 0 0 34px; border-radius: 10px; background: linear-gradient(135deg, #2f6fda, #4b8bf0); color: #fff; font-size: 13px; font-weight: 700; box-shadow: 0 4px 12px rgba(47, 111, 218, .2); }
.agent-message-row.user .agent-avatar { background: #74839a; }
.agent-message-content { max-width: min(78%, 780px); }
.agent-message-meta { display: flex; align-items: center; gap: 8px; margin: 0 4px 6px; color: #8a95a8; font-size: 11px; }
.agent-message-row.user .agent-message-meta { justify-content: flex-end; }
.agent-mode { padding: 2px 7px; border-radius: 10px; background: #e8f1ff; color: #2f6fda; }
.agent-mode.error { background: #fff0f0; color: #cf3d3d; }
.agent-bubble { padding: 12px 15px; border: 1px solid #e3e9f2; border-radius: 4px 14px 14px 14px; background: #fff; color: #26364f; line-height: 1.75; white-space: pre-wrap; word-break: break-word; box-shadow: 0 3px 10px rgba(27, 54, 93, .05); }
.agent-message-row.user .agent-bubble { border: none; border-radius: 14px 4px 14px 14px; background: #2f6fda; color: #fff; }
.agent-bubble.error { border-color: #ffd4d4; background: #fff8f8; color: #a93636; }
.vehicle-card, .fleet-card { margin-top: 10px; border: 1px solid #d9e5f5; border-radius: 10px; background: #fff; overflow: hidden; }
.vehicle-card-title { display: flex; align-items: center; justify-content: space-between; padding: 11px 14px; background: #edf5ff; color: #244f87; }
.vehicle-card-title span { padding: 2px 8px; border-radius: 10px; font-size: 11px; }
.vehicle-card-title .online { background: #dff6e8; color: #1c8b50; }
.vehicle-card-title .offline { background: #f0f2f5; color: #7b8798; }
.vehicle-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); padding: 8px 14px 12px; }
.vehicle-grid > div { display: flex; justify-content: space-between; gap: 8px; padding: 7px 4px; border-bottom: 1px dashed #edf0f5; font-size: 12px; }
.vehicle-grid .wide { grid-column: 1 / -1; }
.vehicle-grid label { color: #8490a3; }
.vehicle-grid span { color: #26364f; text-align: right; }
.fleet-card { padding: 12px 14px; color: #42536c; line-height: 1.6; }
.source-list { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; margin-top: 9px; color: #8a95a8; font-size: 11px; }
.source-chip { padding: 3px 8px; border-radius: 10px; background: #eef2f7; color: #66758b; }
.thinking { display: flex; align-items: center; gap: 5px; color: #718096; }
.thinking i { width: 6px; height: 6px; border-radius: 50%; background: #2f6fda; animation: pulse 1.2s infinite ease-in-out; }
.thinking i:nth-child(2) { animation-delay: .15s; }.thinking i:nth-child(3) { animation-delay: .3s; }.thinking span { margin-left: 5px; }
@keyframes pulse { 0%, 80%, 100% { opacity: .25; transform: scale(.8); } 40% { opacity: 1; transform: scale(1); } }
.agent-composer { display: flex; align-items: flex-end; gap: 12px; padding: 16px 18px; border-top: 1px solid #e8edf5; background: #fff; }
.agent-textarea { flex: 1; min-height: 46px; max-height: 140px; resize: vertical; border: 1px solid #ccd7e7; border-radius: 8px; padding: 11px 13px; outline: none; color: #26364f; font: inherit; line-height: 1.5; }
.agent-textarea:focus { border-color: #2f6fda; box-shadow: 0 0 0 3px rgba(47,111,218,.1); }
.agent-send-button { height: 44px; min-width: 78px; border: none; border-radius: 8px; background: #2f6fda; color: #fff; cursor: pointer; }
.agent-send-button:disabled { cursor: not-allowed; opacity: .5; }
.agent-side { display: flex; flex-direction: column; gap: 18px; }
.agent-side .panel { padding: 18px; }
.side-title-row { display: flex; align-items: center; justify-content: space-between; }
.text-button { border: none; background: transparent; color: #2f6fda; cursor: pointer; }
.status-main { display: flex; align-items: center; gap: 9px; margin: 18px 0; color: #33445e; }
.status-dot { width: 9px; height: 9px; border-radius: 50%; }.status-dot.online { background: #24b36b; box-shadow: 0 0 0 4px #def6e9; }.status-dot.offline { background: #d64c4c; box-shadow: 0 0 0 4px #ffe6e6; }
.health-error { margin: -5px 0 12px; color: #c34747; font-size: 12px; line-height: 1.5; }
.status-list { margin: 0; }
.status-list div { display: flex; justify-content: space-between; gap: 12px; padding: 9px 0; border-top: 1px solid #edf0f5; font-size: 12px; }
.status-list dt { color: #8792a7; }.status-list dd { margin: 0; color: #33445e; text-align: right; }
.agent-suggestions { display: flex; flex-direction: column; gap: 9px; }
.agent-suggestions h3 { margin-bottom: 5px; }
.agent-suggestions button { border: 1px solid #dee6f1; border-radius: 7px; background: #f8faff; color: #3c5577; padding: 9px 10px; text-align: left; line-height: 1.4; cursor: pointer; }
.agent-suggestions button:hover { border-color: #8fb4eb; background: #f0f6ff; color: #2f6fda; }
.agent-tip p { margin: 12px 0 0; color: #68778d; font-size: 12px; line-height: 1.7; }
@media (max-width: 1300px) { .agent-layout { grid-template-columns: 1fr; }.agent-side { display: grid; grid-template-columns: repeat(3, 1fr); }.agent-chat { min-height: 620px; } }
@media (max-width: 820px) { .agent-side { grid-template-columns: 1fr; }.agent-chat-body { padding: 16px 12px; }.agent-message-content { max-width: 86%; }.vehicle-grid { grid-template-columns: 1fr; }.vehicle-grid .wide { grid-column: auto; }.agent-composer { padding: 12px; }.agent-send-button { min-width: 64px; } }
</style>
