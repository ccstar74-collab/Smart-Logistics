<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '../components/PageHeader.vue'
import { api, extractList } from '../api/http'
import { useRoute } from 'vue-router'

const route=useRoute()
const loading=ref(false),saving=ref(false),cargos=ref([]),availableCargos=ref([]),vehicles=ref([]),tasks=ref([]),drivers=ref([]),owners=ref([])
const form=reactive({ownerId:null,cargoId:null,vehicleId:null,startLocation:'',startLongitude:null,startLatitude:null,endLocation:'',endLongitude:null,endLatitude:null,planTime:[]})
const idOf=(item,type)=>item?.id??item?.[`${type}Id`]??item?.userId??item?.value
const accountOf=item=>item?.username??item?.account??item?.userName??item?.label??''
const labelOf=(item,type)=>{if(!item)return`未知${type}`;const account=accountOf(item),name=item.name??item.realName??item.ownerName??item.driverName??item.companyName??'';return[account,name].filter(Boolean).join(' · ')||`${type} #${idOf(item,type==='司机'?'driver':'owner')}`}
const cargoMap=computed(()=>Object.fromEntries(cargos.value.map(c=>[Number(c.id),c])))
const vehicleMap=computed(()=>Object.fromEntries(vehicles.value.map(v=>[Number(v.id),v])))
const ownerMap=computed(()=>Object.fromEntries(owners.value.map(o=>[Number(idOf(o,'owner')),o])))
const driverMap=computed(()=>Object.fromEntries(drivers.value.map(d=>[Number(idOf(d,'driver')),d])))
const activeCargoIds=computed(()=>new Set(tasks.value.filter(t=>['WAITING','TRANSPORTING'].includes(t.status)).map(t=>Number(t.cargoId))))
const waitingCargos=computed(()=>availableCargos.value.filter(c=>(c.status==null||c.status==='WAITING')&&!activeCargoIds.value.has(Number(c.id))))
const idleVehicles=computed(()=>vehicles.value.filter(v=>v.status==='IDLE'))
const selectedVehicle=computed(()=>vehicleMap.value[Number(form.vehicleId)])
const selectedDriver=computed(()=>driverMap.value[Number(selectedVehicle.value?.driverId)])
const statusText={WAITING:'待运输',TRANSPORTING:'运输中',COMPLETED:'已完成',ABNORMAL:'异常',CANCELLED:'已取消'}
const statusClass=status=>`binding-status status-${String(status||'').toLowerCase()}`
const dateText=value=>value?String(value).replace('T',' ').slice(0,16):'待计算'

async function load(){loading.value=true;try{const [cargoResult,availableResult,vehicleResult,taskResult,driverResult,ownerResult]=await Promise.all([api.cargos.list({page:1,pageSize:100}),api.cargos.available().catch(()=>api.cargos.list({page:1,pageSize:100,status:'WAITING'})),api.vehicles.list({page:1,pageSize:100}),api.transportTasks.list({page:1,pageSize:100}),api.drivers.options(),api.owners.options()]);cargos.value=extractList(cargoResult);availableCargos.value=extractList(availableResult);vehicles.value=extractList(vehicleResult);tasks.value=extractList(taskResult);drivers.value=extractList(driverResult);owners.value=extractList(ownerResult);const queryOwner=Number(route.query.ownerId),queryCargo=Number(route.query.cargoId);if(Number.isFinite(queryOwner)&&owners.value.some(o=>Number(idOf(o,'owner'))===queryOwner))form.ownerId=queryOwner;else if(!owners.value.some(o=>Number(idOf(o,'owner'))===Number(form.ownerId)))form.ownerId=idOf(owners.value[0],'owner')??null;if(Number.isFinite(queryCargo)&&waitingCargos.value.some(c=>Number(c.id)===queryCargo))form.cargoId=queryCargo;else if(!waitingCargos.value.some(c=>Number(c.id)===Number(form.cargoId)))form.cargoId=waitingCargos.value[0]?.id??null;if(!idleVehicles.value.some(v=>Number(v.id)===Number(form.vehicleId)))form.vehicleId=idleVehicles.value[0]?.id??null}catch(error){ElMessage.error(`统一调度数据加载失败：${error.message}`)}finally{loading.value=false}}
const iso=value=>value?new Date(value).toISOString():null
async function bind(){const cargo=availableCargos.value.find(c=>Number(c.id)===Number(form.cargoId));if(!cargo)return ElMessage.warning('请选择可出库货物');if(!form.ownerId)return ElMessage.warning('请选择收货货主');if(!selectedDriver.value)return ElMessage.warning('所选车辆尚未绑定真实司机账号');if(!form.startLocation.trim()||!form.endLocation.trim()||form.planTime.length!==2)return ElMessage.warning('请填写起点、终点及计划时间');saving.value=true;try{const body={cargoId:form.cargoId,ownerId:form.ownerId,vehicleId:form.vehicleId,startLocation:form.startLocation.trim(),endLocation:form.endLocation.trim(),planStartTime:iso(form.planTime[0]),planEndTime:iso(form.planTime[1])};if([form.startLongitude,form.startLatitude,form.endLongitude,form.endLatitude].every(v=>Number.isFinite(Number(v))))Object.assign(body,{startLongitude:Number(form.startLongitude),startLatitude:Number(form.startLatitude),endLongitude:Number(form.endLongitude),endLatitude:Number(form.endLatitude)});await api.transportTasks.create(body);ElMessage.success('出库绑定成功，货主将在任务创建成功后由后端绑定');Object.assign(form,{cargoId:null,vehicleId:null,startLocation:'',startLongitude:null,startLatitude:null,endLocation:'',endLongitude:null,endLatitude:null,planTime:[]});await load()}catch(error){ElMessage.error(error.message)}finally{saving.value=false}}
onMounted(load)
</script>

<template>
  <PageHeader title="货物出库" subtitle="一次完成货主、货物、车辆、司机、起终点绑定并创建运输任务" />
  <section class="warehouse-main" v-loading="loading">
    <article class="panel"><div class="panel-title"><div><h2>办理货物出库</h2><span>提交成功后生成运输任务并正式绑定货主</span></div></div><div class="binding-form">
      <label><span>绑定货主</span><select v-model.number="form.ownerId"><option :value="null">请选择货主（创建任务时正式绑定）</option><option v-for="owner in owners" :key="idOf(owner,'owner')" :value="idOf(owner,'owner')">{{labelOf(owner,'货主')}}</option></select></label>
      <label><span>出库货物</span><select v-model.number="form.cargoId"><option :value="null">请选择可出库库存</option><option v-for="cargo in waitingCargos" :key="cargo.id" :value="cargo.id">{{cargo.cargoNo}} · {{cargo.name}}</option></select></label>
      <label><span>运输车辆</span><select v-model.number="form.vehicleId"><option :value="null">请选择空闲车辆</option><option v-for="vehicle in idleVehicles" :key="vehicle.id" :value="vehicle.id">{{vehicle.plateNumber}} · {{vehicle.type}}</option></select></label>
      <label><span>绑定司机</span><input :value="selectedDriver?labelOf(selectedDriver,'司机'):'所选车辆未绑定司机，无法出库'" disabled /></label>
      <label><span>运输起点</span><input v-model="form.startLocation" placeholder="地点名称或详细地址，例如：重庆北站" /></label>
      <label><span>起点坐标</span><span class="coordinate-inputs"><el-input-number v-model="form.startLongitude" :precision="6" :controls="false" placeholder="经度"/><el-input-number v-model="form.startLatitude" :precision="6" :controls="false" placeholder="纬度"/></span></label>
      <label><span>运输终点</span><input v-model="form.endLocation" placeholder="地点名称或详细地址，例如：重庆西站" /></label>
      <label><span>终点坐标</span><span class="coordinate-inputs"><el-input-number v-model="form.endLongitude" :precision="6" :controls="false" placeholder="经度"/><el-input-number v-model="form.endLatitude" :precision="6" :controls="false" placeholder="纬度"/></span></label>
      <label><span>计划时间</span><el-date-picker v-model="form.planTime" type="datetimerange" start-placeholder="计划开始" end-placeholder="计划结束" style="width:100%" /></label>
      <button class="primary" :disabled="saving||!form.cargoId||!form.vehicleId" @click="bind">{{saving?'正在写入…':'确认出库并创建任务'}}</button>
    </div></article>
    <article class="panel role-table-card"><div class="panel-title"><div><h2>可出库货物</h2><span>未进入活跃任务，共 {{waitingCargos.length}} 条</span></div><button class="mini" @click="load">刷新</button></div><div class="table-wrap"><table><thead><tr><th>货物编号</th><th>名称</th><th>当前货主</th><th>重量</th><th>状态</th></tr></thead><tbody><tr v-for="cargo in waitingCargos" :key="cargo.id"><td>{{cargo.cargoNo}}</td><td>{{cargo.name}}</td><td>{{cargo.ownerId==null?'未分配库存':labelOf(ownerMap[Number(cargo.ownerId)],'货主')}}</td><td>{{cargo.weight}} kg</td><td><span :class="statusClass(cargo.status)">{{statusText[cargo.status]||cargo.status}}</span></td></tr><tr v-if="!waitingCargos.length"><td colspan="5">暂无可出库货物</td></tr></tbody></table></div></article>
  </section>
  <section class="panel role-table-card" v-loading="loading"><div class="panel-title"><div><h2>出库记录与任务状态</h2><span>数据来自云端运输任务</span></div></div><div class="table-wrap"><table><thead><tr><th>任务编号</th><th>货物</th><th>货主账号</th><th>车牌</th><th>司机账号</th><th>ETA</th><th>状态</th></tr></thead><tbody><tr v-for="task in tasks" :key="task.id"><td>{{task.taskNo}}</td><td>{{cargoMap[Number(task.cargoId)]?.name||`#${task.cargoId}`}}</td><td>{{labelOf(ownerMap[Number(task.ownerId??cargoMap[Number(task.cargoId)]?.ownerId)],'货主')}}</td><td>{{vehicleMap[Number(task.vehicleId)]?.plateNumber||`#${task.vehicleId}`}}</td><td>{{labelOf(driverMap[Number(vehicleMap[Number(task.vehicleId)]?.driverId)],'司机')}}</td><td>{{dateText(task.estimatedArrivalTime||task.planEndTime)}}</td><td><span :class="statusClass(task.status)">{{statusText[task.status]||task.status}}</span></td></tr><tr v-if="!tasks.length"><td colspan="7">暂无出库任务记录</td></tr></tbody></table></div></section>
</template>
