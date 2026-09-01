<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '../components/PageHeader.vue'
import { api, extractList } from '../api/http'
import { useAuth } from '../stores/auth-session'

const { state } = useAuth()
const router = useRouter()
const loading=ref(false),saving=ref(false),dialogVisible=ref(false),tasks=ref([]),vehicles=ref([]),cargos=ref([]),owners=ref([]),currentTask=ref(null)
const page=ref(1),pageSize=ref(10),total=ref(0),status=ref('')
const form=reactive({ownerId:null,cargoId:null,vehicleId:null,startLocation:'',endLocation:'',planStartTime:[],planEndTime:''})
const optionId=item=>item?.id??item?.ownerId??item?.userId??item?.value
const ownerLabel=item=>item?.username??item?.account??item?.name??item?.label??`货主 #${optionId(item)}`
const statusText={WAITING:'待运输',TRANSPORTING:'运输中',COMPLETED:'已完成',ABNORMAL:'异常',CANCELLED:'已取消'}
const vehicleMap=computed(()=>Object.fromEntries(vehicles.value.map(v=>[v.id??v.vehicleId,v])))
const cargoMap=computed(()=>Object.fromEntries(cargos.value.map(c=>[c.id??c.cargoId,c])))
const availableVehicles=computed(()=>vehicles.value.filter(v=>v.status==='IDLE'))
const availableCargos=computed(()=>cargos.value.filter(c=>c.status==='WAITING'))
const title=computed(()=>state.currentUser.role==='DRIVER'?'运输任务':'任务分配')

async function load(){loading.value=true;try{const calls=[api.transportTasks.list({page:page.value,pageSize:pageSize.value,status:status.value})];if(state.currentUser.role==='DRIVER')calls.push(api.transportTasks.current().catch(()=>null));const [result,current]=await Promise.all(calls);tasks.value=extractList(result);total.value=Number(result?.total??tasks.value.length);if(state.currentUser.role==='DRIVER')currentTask.value=Array.isArray(current)?current[0]:(current?.record??current?.item??current)}catch(e){ElMessage.error(e.message)}finally{loading.value=false}}
async function loadOptions(){try{let vehicleResult,cargoResult,ownerResult;if(state.currentUser.role==='WAREHOUSE_MANAGER'){[vehicleResult,cargoResult,ownerResult]=await Promise.all([api.vehicles.available(),api.cargos.available().catch(()=>api.cargos.list({page:1,pageSize:100,status:'WAITING'})),api.owners.options()])}else if(state.currentUser.role==='DRIVER'){[vehicleResult,cargoResult]=await Promise.all([api.vehicles.list({page:1,pageSize:100}),api.cargos.list({page:1,pageSize:100})])}else return;vehicles.value=extractList(vehicleResult);cargos.value=extractList(cargoResult);owners.value=extractList(ownerResult)}catch(e){ElMessage.error(`加载关联车辆和货物失败：${e.message}`)}}
async function openCreate(){Object.assign(form,{ownerId:null,cargoId:null,vehicleId:null,startLocation:'',endLocation:'',planStartTime:[],planEndTime:''});await loadOptions();dialogVisible.value=true}
function iso(value){return value?new Date(value).toISOString():null}
async function save(){if(!form.ownerId||!form.cargoId||!form.vehicleId||!form.startLocation||!form.endLocation||form.planStartTime.length!==2)return ElMessage.warning('请完整选择货主、货物、车辆、起终点和计划时间');saving.value=true;try{await api.transportTasks.create({ownerId:form.ownerId,cargoId:form.cargoId,vehicleId:form.vehicleId,startLocation:form.startLocation,endLocation:form.endLocation,planStartTime:iso(form.planStartTime[0]),planEndTime:iso(form.planStartTime[1])});ElMessage.success('运输任务分配成功');dialogVisible.value=false;await load()}catch(e){ElMessage.error(e.message)}finally{saving.value=false}}
async function detail(row){try{const t=await api.transportTasks.get(row.id);await ElMessageBox.alert(`任务编号：${t.taskNo}<br>货物：${cargoMap.value[t.cargoId]?.name||'#'+t.cargoId}<br>车辆：${vehicleMap.value[t.vehicleId]?.plateNumber||'#'+t.vehicleId}<br>起点：${t.startLocation}<br>终点：${t.endLocation}<br>状态：${statusText[t.status]||t.status}`, '运输任务详情',{dangerouslyUseHTMLString:true})}catch(e){ElMessage.error(e.message)}}
async function changeStatus(row,next){const label=statusText[next]||next;try{await ElMessageBox.confirm(`确定将任务更新为“${label}”吗？`,'任务状态确认',{type:'warning'});await api.transportTasks.updateStatus(row.id,next);ElMessage.success(`任务已更新为${label}`);await Promise.all([load(),loadOptions()])}catch(e){if(e!=='cancel'&&e!=='close')ElMessage.error(e.message||'状态更新失败')}}
onMounted(()=>Promise.all([load(),loadOptions()]))
</script>

<template>
  <PageHeader :title="title" :subtitle="state.currentUser.role==='DRIVER'?'查看当前任务、路线与 ETA':'选择待运输货物和空闲车辆，创建真实运输任务'"/>
  <section class="panel page-panel">
    <div class="toolbar">
      <el-select v-model="status" placeholder="全部状态" clearable style="width:150px" @change="page=1;load()"><el-option v-for="(label,value) in statusText" :key="value" :label="label" :value="value"/></el-select>
      <el-button @click="load">刷新</el-button><el-button v-if="state.currentUser.role==='WAREHOUSE_MANAGER'" type="primary" @click="openCreate">+ 分配新任务</el-button>
    </div>
    <el-table v-loading="loading" :data="tasks" stripe empty-text="暂无运输任务">
      <el-table-column prop="taskNo" label="任务编号" min-width="200"/>
      <el-table-column label="货物" min-width="130"><template #default="s">{{cargoMap[s.row.cargoId]?.name||`货物 #${s.row.cargoId}`}}</template></el-table-column>
      <el-table-column label="车辆" min-width="130"><template #default="s">{{vehicleMap[s.row.vehicleId]?.plateNumber||`车辆 #${s.row.vehicleId}`}}</template></el-table-column>
      <el-table-column prop="startLocation" label="起点"/><el-table-column prop="endLocation" label="终点"/>
      <el-table-column label="ETA" min-width="150"><template #default="s">{{s.row.estimatedArrivalTime?.replace('T',' ').slice(0,16)||'待计算'}}</template></el-table-column>
      <el-table-column label="状态"><template #default="s"><el-tag>{{statusText[s.row.status]||s.row.status}}</el-tag></template></el-table-column>
      <el-table-column label="操作" min-width="180" fixed="right"><template #default="s"><el-button link type="primary" @click="detail(s.row)">详情</el-button><template v-if="state.currentUser.role==='DRIVER'"><el-button v-if="s.row.status==='WAITING'" link type="success" @click="changeStatus(s.row,'TRANSPORTING')">开始运输</el-button><el-button v-if="s.row.status==='TRANSPORTING'" link type="success" @click="router.push('/status-report')">到达确认</el-button></template></template></el-table-column>
    </el-table>
    <div style="padding:16px;display:flex;justify-content:flex-end"><el-pagination v-model:current-page="page" v-model:page-size="pageSize" layout="total, prev, pager, next" :total="total" @current-change="load"/></div>
  </section>
  <el-dialog v-model="dialogVisible" title="分配运输任务" width="560px">
    <el-form label-width="100px">
      <el-form-item label="收货货主" required><el-select v-model="form.ownerId" filterable style="width:100%" placeholder="创建任务时确定货主"><el-option v-for="owner in owners" :key="optionId(owner)" :value="optionId(owner)" :label="ownerLabel(owner)"/></el-select></el-form-item>
      <el-form-item label="待运输货物" required><el-select v-model="form.cargoId" filterable style="width:100%" placeholder="选择 WAITING 货物"><el-option v-for="c in availableCargos" :key="c.id" :value="c.id" :label="`${c.cargoNo} · ${c.name}`"/></el-select></el-form-item>
      <el-form-item label="空闲车辆" required><el-select v-model="form.vehicleId" filterable style="width:100%" placeholder="选择 IDLE 车辆"><el-option v-for="v in availableVehicles" :key="v.id" :value="v.id" :label="`${v.plateNumber} · ${v.type}`"/></el-select></el-form-item>
      <el-form-item label="起点" required><el-input v-model="form.startLocation" placeholder="例如：仓库A"/></el-form-item>
      <el-form-item label="终点" required><el-input v-model="form.endLocation" placeholder="例如：配送点B"/></el-form-item>
      <el-form-item label="计划时间" required><el-date-picker v-model="form.planStartTime" type="datetimerange" start-placeholder="计划开始" end-placeholder="计划结束" style="width:100%"/></el-form-item>
    </el-form>
    <template #footer><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">创建并分配</el-button></template>
  </el-dialog>
</template>
