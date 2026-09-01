<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '../components/PageHeader.vue'
import UiIcon from '../components/UiIcon.vue'
import { api, extractList } from '../api/http'
import { validateCargoForm } from '../utils/validation'

const loading=ref(false), saving=ref(false), cargos=ref([]), cargoTypes=ref([]), warehouses=ref([])
const form=reactive({cargoNo:'',name:'',cargoTypeId:null,warehouseId:null,weight:0,volume:0})
const waitingCargos=computed(()=>cargos.value.filter(c=>c.status==='WAITING'))
const statusText={WAITING:'待运输',TRANSPORTING:'运输中',COMPLETED:'已完成',ABNORMAL:'异常',CANCELLED:'已取消'}
const transportingCount=computed(()=>cargos.value.filter(c=>c.status==='TRANSPORTING').length)
const typeMap=computed(()=>Object.fromEntries(cargoTypes.value.map(x=>[Number(x.id),x])))
const warehouseMap=computed(()=>Object.fromEntries(warehouses.value.map(x=>[Number(x.id),x])))

async function load(){
  loading.value=true
  try{
    const [cargoResult,typeResult,warehouseResult]=await Promise.all([
      api.cargos.list({page:1,pageSize:100}),
      api.cargoTypes.list({page:1,pageSize:100}),
      api.warehouses.list({page:1,pageSize:100})
    ])
    cargos.value=extractList(cargoResult)
    cargoTypes.value=extractList(typeResult)
    warehouses.value=extractList(warehouseResult).filter(x=>!x.status||x.status==='ACTIVE')
    if(!form.cargoTypeId)form.cargoTypeId=cargoTypes.value[0]?.id??null
    if(!form.warehouseId)form.warehouseId=warehouses.value[0]?.id??null
  }catch(error){cargos.value=[];ElMessage.error(`入库数据加载失败：${error.message}`)}
  finally{loading.value=false}
}

async function createCargoType(){
  try{
    const {value}=await ElMessageBox.prompt('请输入新的货物种类名称','新增货物种类',{inputPlaceholder:'例如：苹果',inputValidator:v=>Boolean(v?.trim())||'名称不能为空'})
    const created=await api.cargoTypes.create({name:value.trim(),unit:'件',unitWeight:null,unitVolume:null,description:''})
    await load()
    form.cargoTypeId=created?.id??cargoTypes.value.find(x=>x.name===value.trim())?.id??form.cargoTypeId
    ElMessage.success('货物种类创建成功')
  }catch(error){if(error!=='cancel'&&error!=='close')ElMessage.error(error.message||'新增货物种类失败')}
}

async function submit(){
  const validationError=validateCargoForm(form)
  if(validationError)return ElMessage.warning(validationError)
  if(!form.cargoTypeId)return ElMessage.warning('请选择货物种类')
  if(!form.warehouseId)return ElMessage.warning('请选择入库仓库')
  saving.value=true
  try{
    await api.cargos.create({
      cargoNo:form.cargoNo.trim(),name:form.name.trim(),
      cargoTypeId:Number(form.cargoTypeId),warehouseId:Number(form.warehouseId),
      description:'',weight:Number(form.weight),volume:Number(form.volume)
    })
    ElMessage.success('货物已录入所选仓库')
    Object.assign(form,{cargoNo:'',name:'',weight:0,volume:0})
    await load()
  }catch(error){ElMessage.error(error.message||'入库失败')}
  finally{saving.value=false}
}
onMounted(load)
</script>

<template>
  <PageHeader title="货物信息录入" subtitle="创建具体 Cargo，并关联货物种类与实际入库仓库" />
  <section class="warehouse-main" v-loading="loading">
    <article class="panel">
      <div class="panel-title"><div><h2>新增货物入库</h2></div></div>
      <div class="binding-form">
        <label><span>货物编号</span><input v-model="form.cargoNo" placeholder="请输入唯一货物编号" /></label>
        <label><span>货物名称</span><input v-model="form.name" placeholder="请输入具体货物名称" /></label>
        <label><span>货物种类</span><span class="location-field"><select v-model.number="form.cargoTypeId"><option :value="null">请选择货物种类</option><option v-for="type in cargoTypes" :key="type.id" :value="type.id">{{type.name}}{{type.unit?`（${type.unit}）`:''}}</option></select><button type="button" class="mini" @click="createCargoType">新增种类</button></span></label>
        <label><span>入库仓库</span><select v-model.number="form.warehouseId"><option :value="null">请选择仓库</option><option v-for="warehouse in warehouses" :key="warehouse.id" :value="warehouse.id">{{warehouse.name}} · {{warehouse.address}}</option></select></label>
        <label><span>重量(kg)</span><el-input-number v-model="form.weight" :min="0" :precision="2" style="width:100%" /></label>
        <label><span>体积(m³)</span><el-input-number v-model="form.volume" :min="0" :precision="2" style="width:100%" /></label>
        <div class="api-scope-note">入库提交 cargoTypeId 与 warehouseId；不选择车辆，也不创建运输任务。</div>
        <button class="primary" :disabled="saving" @click="submit">{{saving?'正在入库…':'确认录入货物'}}</button>
      </div>
    </article>
    <article class="panel role-table-card warehouse-summary-card">
      <div class="panel-title"><div><h2>多仓货物汇总</h2></div><button class="mini" @click="load">刷新</button></div>
      <div class="stats-grid warehouse-io-stats">
        <article class="stat-card summary-stat"><span class="summary-icon"><UiIcon name="package" /></span><div><div class="stat-label">货物总数</div><div class="stat-value">{{cargos.length}}</div></div></article>
        <article class="stat-card summary-stat"><span class="summary-icon waiting"><UiIcon name="warehouse" /></span><div><div class="stat-label">待运输</div><div class="stat-value">{{waitingCargos.length}}</div></div></article>
        <article class="stat-card summary-stat"><span class="summary-icon transporting"><UiIcon name="truck" /></span><div><div class="stat-label">运输中</div><div class="stat-value">{{transportingCount}}</div></div></article>
      </div>
    </article>
  </section>
  <section class="panel role-table-card" v-loading="loading">
    <div class="panel-title"><div><h2>货物入库记录</h2></div></div>
    <div class="table-wrap"><table><thead><tr><th>货物编号</th><th>名称</th><th>货物种类</th><th>所在仓库</th><th>重量</th><th>体积</th><th>状态</th></tr></thead><tbody><tr v-for="cargo in cargos" :key="cargo.id"><td>{{cargo.cargoNo}}</td><td>{{cargo.name}}</td><td>{{cargo.cargoTypeName||typeMap[Number(cargo.cargoTypeId)]?.name||`#${cargo.cargoTypeId??'—'}`}}</td><td>{{cargo.warehouseName||warehouseMap[Number(cargo.warehouseId)]?.name||`#${cargo.warehouseId??'—'}`}}</td><td>{{cargo.weight}} 千克</td><td>{{cargo.volume}} 立方米</td><td>{{statusText[cargo.status] || cargo.status}}</td></tr><tr v-if="!cargos.length"><td colspan="7">暂无货物</td></tr></tbody></table></div>
  </section>
</template>

<style scoped>
.warehouse-main {
  align-items: start;
}

.warehouse-summary-card {
  align-self: start;
}

.warehouse-summary-card .warehouse-io-stats {
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.warehouse-summary-card .stat-card {
  min-height: 108px;
  padding: 18px;
}

.warehouse-summary-card .summary-stat {
  display: flex;
  align-items: center;
  gap: 14px;
  background: linear-gradient(145deg, #ffffff, #f7faff);
}

.summary-icon {
  display: grid;
  place-items: center;
  flex: 0 0 46px;
  width: 46px;
  height: 46px;
  border-radius: 14px;
  color: #3475df;
  background: #eaf2ff;
}

.summary-icon.waiting {
  color: #b7791f;
  background: #fff5df;
}

.summary-icon.transporting {
  color: #2f72d6;
  background: #e9f2ff;
}

.summary-icon :deep(.ui-icon) {
  width: 24px;
  height: 24px;
}

@media (max-width: 720px) {
  .warehouse-summary-card .warehouse-io-stats {
    grid-template-columns: 1fr;
  }
}
</style>
