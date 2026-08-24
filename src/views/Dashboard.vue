<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import data from '../mock/data.json'
import AMapView from '../components/AMapView.vue'
import StatCard from '../components/StatCard.vue'
import UiIcon from '../components/UiIcon.vue'
import { snapshots, summary } from '../stores/realtime'
import { useAuth } from '../stores/auth'

const router = useRouter()
const { state } = useAuth()
const role = computed(() => state.currentUser.role)
const selectedVehicleId = ref(snapshots[0]?.vehicle_id)
const selectedVehicle = computed(() => snapshots.find(v => v.vehicle_id === selectedVehicleId.value) || snapshots[0])
const activeSnapshots = computed(() => snapshots.filter(v => v.has_active_alert))
const driverVehicle = computed(() => snapshots.find(v => v.display?.driver_name === state.currentUser.name) || snapshots[0])
const driverTask = computed(() => data.tasks.find(t => t.taskNo === driverVehicle.value?.display?.task_no) || data.tasks[0])
const driverCommands = computed(() => data.commands.filter(c => c.taskId === driverTask.value?.id || c.vehicleId === driverTask.value?.vehicleId))
const driverRoute = computed(() => {
  const vehicle = driverVehicle.value
  if (!vehicle?.gps) return []
  const current = [Number(vehicle.gps.lon), Number(vehicle.gps.lat)]
  return [
    [106.55090, 29.57330],
    [106.55145, 29.57352],
    current,
    [106.55365, 29.57408],
    [106.55435, 29.57438],
    [106.55530, 29.57462]
  ]
})
const driverStatus = ref('TRANSPORTING')
const bindingCargo = ref(data.cargos[1]?.id || data.cargos[0]?.id)
const bindingVehicle = ref(data.vehicles.find(v => v.status === 'IDLE')?.vehicleId || data.vehicles[0]?.vehicleId)
const bindingDriver = ref('王师傅')

const dashboardTitle = computed(() => ({
  OWNER:'运输概览', DRIVER:'我的任务', WAREHOUSE:'仓储作业',
  DISPATCHER:'调度总览', ADMIN:'系统总览'
}[role.value]))

const dashboardSubtitle = computed(() => ({
  OWNER:'实时掌握货物位置、到达时间与运输异常',
  DRIVER:'查看当前任务、车辆路线并及时上报运输状态',
  WAREHOUSE:'管理车辆与货物绑定，衔接出入库和任务分配',
  DISPATCHER:'查看车辆全局分布，处理告警并下发调度指令',
  ADMIN:'查看平台关键数据、车辆状态与系统告警记录'
}[role.value]))

function selectVehicle(v){ selectedVehicleId.value = v.vehicle_id }
function shortTime(iso){
  if(!iso) return '--'
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? '--' : d.toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit',hour12:false})
}
function submitBinding(){ alert('演示版：已完成货物、车辆与司机绑定') }
function setDriverStatus(v){ driverStatus.value=v; alert(`演示版：状态已上报为${v==='LOADED'?'已装货':v==='TRANSPORTING'?'运输中':'已送达'}`) }
</script>

<template>
  <header class="topbar dashboard-heading">
    <div><h1>{{ dashboardTitle }}</h1><p>{{ dashboardSubtitle }}</p></div>
    <div class="top-actions live-state-pill"><span class="online-dot"></span><span>模拟数据在线</span></div>
  </header>

  <!-- 货主首页 -->
  <template v-if="role==='OWNER'">
    <section class="stats-grid">
      <StatCard label="在途订单" value="8" foot="正在运输中的货物" icon="truck" tone="blue" />
      <StatCard label="正常运输" value="12" foot="当前状态正常" icon="check" tone="green" />
      <StatCard label="告警中" :value="summary.active_alert_count" foot="偏航 / 停留 / 开箱" icon="alert" tone="red" alarm />
      <StatCard label="已完成" value="20" foot="历史完成订单" icon="package" tone="violet" />
    </section>
    <article class="panel home-map-panel">
      <div class="panel-title"><div><h2>货物追踪地图</h2><span>查看绑定车辆的实时位置和运输状态</span></div><RouterLink class="link-btn" to="/tracking">查看追踪</RouterLink></div>
      <AMapView :selectedVehicleId="selectedVehicle.vehicle_id" @select="selectVehicle" />
    </article>
    <section class="owner-dashboard-grid">
      <article class="panel role-table-card">
        <div class="panel-title"><div><h2>预计到达时间（ETA）</h2><span>当前在途任务到达预测</span></div><RouterLink class="link-btn" to="/eta">查看全部</RouterLink></div>
        <div class="table-wrap"><table><thead><tr><th>运单号</th><th>目的地</th><th>ETA</th><th>状态</th></tr></thead><tbody>
          <tr v-for="t in data.tasks" :key="t.id"><td>{{t.taskNo}}</td><td>{{t.endLocation}}</td><td>{{t.estimatedArrivalTime.slice(11,16)}}</td><td><span class="task-status">{{t.status==='COMPLETED'?'已到达':'运输中'}}</span></td></tr>
        </tbody></table></div>
      </article>
      <article class="panel role-table-card">
        <div class="panel-title"><div><h2>近期告警</h2><span>本人货物运输过程中的异常通知</span></div><RouterLink class="link-btn" to="/alarms">查看全部</RouterLink></div>
        <div class="table-wrap"><table><thead><tr><th>运单号</th><th>告警类型</th><th>车辆</th><th>时间</th></tr></thead><tbody>
          <tr v-for="a in data.alarms" :key="a.id"><td>{{data.tasks.find(t=>t.id===a.taskId)?.taskNo || '--'}}</td><td>{{a.message}}</td><td>{{a.plateNumber}}</td><td>{{a.createdAt.slice(11,16)}}</td></tr>
        </tbody></table></div>
      </article>
    </section>
    <button class="floating-agent" @click="router.push('/agent')"><UiIcon name="message" /> 智能问答</button>
  </template>

  <!-- 司机首页 -->
  <template v-else-if="role==='DRIVER'">
    <section class="driver-top">
      <article class="panel task-card">
        <div class="panel-title"><div><h2>当前任务</h2><span>司机当前执行中的运输任务</span></div><span class="badge">运输中</span></div>
        <dl>
          <div><dt>运单号</dt><dd>{{driverTask.taskNo}}</dd></div><div><dt>起点</dt><dd>{{driverTask.startLocation}}</dd></div>
          <div><dt>终点</dt><dd>{{driverTask.endLocation}}</dd></div><div><dt>货物</dt><dd>{{driverTask.cargoName}}</dd></div>
          <div><dt>车辆</dt><dd>{{driverTask.plateNumber}}</dd></div><div><dt>预计到达</dt><dd>{{driverTask.estimatedArrivalTime.slice(11,16)}}</dd></div>
        </dl>
      </article>
      <article class="panel home-map-panel">
        <div class="panel-title"><div><h2>路线与车辆位置</h2><span>当前任务路线及车辆实时位置</span></div></div>
        <AMapView :selectedVehicleId="driverVehicle.vehicle_id" :vehicleIds="[driverVehicle.vehicle_id]" :plannedRoute="driverRoute" @select="selectVehicle" />
      </article>
    </section>
    <section class="driver-bottom">
      <article class="panel">
        <div class="panel-title"><div><h2>状态上报</h2><span>手动更新货物运输状态</span></div></div>
        <div class="status-choice-grid">
          <button class="status-choice" :class="{current:driverStatus==='LOADED'}" @click="setDriverStatus('LOADED')"><span class="status-choice-icon"><UiIcon name="package" /></span><strong>已装货</strong><span>货物已装车</span></button>
          <button class="status-choice" :class="{current:driverStatus==='TRANSPORTING'}" @click="setDriverStatus('TRANSPORTING')"><span class="status-choice-icon"><UiIcon name="truck" /></span><strong>运输中</strong><span>运输途中</span></button>
          <button class="status-choice" :class="{current:driverStatus==='DELIVERED'}" @click="setDriverStatus('DELIVERED')"><span class="status-choice-icon"><UiIcon name="check" /></span><strong>已送达</strong><span>完成签收</span></button>
        </div>
      </article>
      <article class="panel">
        <div class="panel-title"><div><h2>调度指令</h2><span>来自调度中心的路线与任务通知</span></div><RouterLink class="link-btn" to="/dispatch">查看全部</RouterLink></div>
        <div class="command-cards"><div v-for="c in driverCommands" :key="c.id" class="command-card"><div class="command-card-head"><strong>调度中心</strong><span>{{shortTime(c.sentAt)}}</span></div><p>{{c.content}}</p></div><div v-if="!driverCommands.length" class="empty-command">暂无调度指令</div></div>
      </article>
    </section>
  </template>

  <!-- 仓库管理员首页 -->
  <template v-else-if="role==='WAREHOUSE'">
    <section class="stats-grid">
      <StatCard label="在线车辆" value="45" foot="仓库可调度车辆" icon="truck" tone="blue" />
      <StatCard label="待绑定车辆" value="8" foot="等待任务分配" icon="route" tone="amber" />
      <StatCard label="今日入库" value="12" foot="今日入库货物" icon="download" tone="green" />
      <StatCard label="今日出库" value="15" foot="今日出库货物" icon="upload" tone="violet" />
    </section>
    <section class="warehouse-main">
      <article class="panel role-table-card">
        <div class="panel-title"><div><h2>车辆列表</h2><span>查看车辆状态与所属司机</span></div><RouterLink class="link-btn" to="/vehicles">车辆管理</RouterLink></div>
        <div class="table-wrap"><table><thead><tr><th>车牌号</th><th>车型</th><th>状态</th><th>所属司机</th></tr></thead><tbody><tr v-for="v in data.vehicles" :key="v.vehicleId"><td>{{v.plateNumber}}</td><td>{{v.type}}</td><td><span class="task-status">{{v.status==='TRANSPORTING'?'运输中':'空闲'}}</span></td><td>{{v.driverName}}</td></tr></tbody></table></div>
      </article>
      <article class="panel">
        <div class="panel-title"><div><h2>货物车辆绑定</h2><span>绑定货物、车辆与司机</span></div></div>
        <div class="binding-form">
          <label><span>选择货物</span><select v-model="bindingCargo"><option v-for="c in data.cargos" :key="c.id" :value="c.id">{{c.cargoNo}} · {{c.name}}</option></select></label>
          <label><span>选择车辆</span><select v-model="bindingVehicle"><option v-for="v in data.vehicles" :key="v.vehicleId" :value="v.vehicleId">{{v.plateNumber}} · {{v.type}}</option></select></label>
          <label><span>选择司机</span><select v-model="bindingDriver"><option v-for="v in data.vehicles" :key="v.vehicleId" :value="v.driverName">{{v.driverName}}</option></select></label>
          <label><span>备注</span><textarea rows="3" placeholder="请输入备注（选填）"></textarea></label><button class="primary" @click="submitBinding">绑定</button>
        </div>
      </article>
    </section>
    <article class="panel role-table-card">
      <div class="panel-title"><div><h2>最近绑定记录</h2><span>货物与运输车辆关联记录</span></div><RouterLink class="link-btn" to="/binding-records">查看更多</RouterLink></div>
      <div class="table-wrap"><table><thead><tr><th>货物</th><th>车牌号</th><th>司机</th><th>绑定人</th><th>绑定时间</th></tr></thead><tbody><tr v-for="r in data.warehouseBindings" :key="r.id"><td>{{r.cargo}}（{{r.cargoNo}}）</td><td>{{r.plateNumber}}</td><td>{{r.driver}}</td><td>{{r.operator}}</td><td>{{r.time}}</td></tr></tbody></table></div>
    </article>
  </template>

  <!-- 调度员首页 -->
  <template v-else-if="role==='DISPATCHER'">
    <section class="stats-grid">
      <StatCard label="在线车辆" value="128" foot="实时车辆总数" icon="truck" tone="blue" />
      <StatCard label="告警车辆" value="5" foot="需优先处理" icon="alert" tone="red" alarm />
      <StatCard label="今日告警" value="18" foot="今日累计告警" icon="bell" tone="amber" />
      <StatCard label="任务车辆" value="156" foot="当前运输任务" icon="task" tone="violet" />
    </section>
    <article class="panel home-map-panel">
      <div class="panel-title"><div><h2>车辆分布地图</h2><span>全局实时车辆分布与告警位置</span></div><RouterLink class="link-btn" to="/tracking">车辆监控</RouterLink></div>
      <AMapView :selectedVehicleId="selectedVehicle.vehicle_id" @select="selectVehicle" />
    </article>
    <section class="dispatcher-bottom">
      <article class="panel role-table-card">
        <div class="panel-title"><div><h2>待处理告警</h2><span>运输异常处理队列</span></div><RouterLink class="link-btn" to="/alarms">查看全部</RouterLink></div>
        <div class="table-wrap"><table><thead><tr><th>告警类型</th><th>车辆</th><th>说明</th><th>时间</th><th>级别</th></tr></thead><tbody><tr v-for="a in data.alarms" :key="a.id"><td>{{a.alarmType}}</td><td>{{a.plateNumber}}</td><td>{{a.message}}</td><td>{{a.createdAt.slice(11,16)}}</td><td>{{a.level}}</td></tr></tbody></table></div>
      </article>
      <article class="panel"><div class="panel-title"><div><h2>快捷操作</h2><span>调度员常用业务入口</span></div></div><div class="quick-actions">
        <button class="quick-action" @click="router.push('/dispatch')"><span class="quick-action-icon"><UiIcon name="route" /></span><span class="quick-action-copy"><strong>下发调度指令</strong><span>创建并下发新的调度任务</span></span><span class="quick-action-arrow">→</span></button>
        <button class="quick-action" @click="router.push('/notifications')"><span class="quick-action-icon"><UiIcon name="message" /></span><span class="quick-action-copy"><strong>批量消息</strong><span>向车辆或司机发送消息</span></span><span class="quick-action-arrow">→</span></button>
        <button class="quick-action" @click="router.push('/stats')"><span class="quick-action-icon"><UiIcon name="chart" /></span><span class="quick-action-copy"><strong>数据统计</strong><span>查看运输与告警统计</span></span><span class="quick-action-arrow">→</span></button>
      </div></article>
    </section>
  </template>

  <!-- 系统管理员首页 -->
  <template v-else>
    <section class="stats-grid">
      <StatCard label="当日用户数" value="256" foot="当日活跃用户" icon="users" tone="blue" />
      <StatCard label="当日车辆数" value="128" foot="系统车辆总量" icon="vehicle" tone="green" />
      <StatCard label="在线车辆数" value="96" foot="实时在线车辆" icon="truck" tone="blue" />
      <StatCard label="当日告警量" value="8" foot="今日异常记录" icon="alert" tone="amber" alarm />
    </section>
    <article class="panel home-map-panel">
      <div class="panel-title"><div><h2>车辆与告警分布地图</h2><span>系统级车辆追踪与告警位置监控</span></div></div>
      <AMapView :selectedVehicleId="selectedVehicle.vehicle_id" @select="selectVehicle" />
    </article>
    <section class="admin-bottom"><article class="panel role-table-card"><div class="panel-title"><div><h2>告警日志</h2><span>系统历史告警记录</span></div><RouterLink class="link-btn" to="/alarm-logs">查看全部</RouterLink></div><div class="table-wrap"><table><thead><tr><th>告警类型</th><th>车辆</th><th>位置/说明</th><th>时间</th><th>级别</th><th>状态</th></tr></thead><tbody><tr v-for="a in data.alarms" :key="a.id"><td>{{a.alarmType}}</td><td>{{a.plateNumber}}</td><td>{{a.message}}</td><td>{{a.createdAt.replace('T',' ').slice(0,16)}}</td><td>{{a.level}}</td><td>{{a.status}}</td></tr></tbody></table></div></article></section>
  </template>
</template>
