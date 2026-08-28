<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '../components/PageHeader.vue'
import { api, extractList } from '../api/http'
import { validateCargoForm } from '../utils/validation'

const loading = ref(false), saving = ref(false), cargos = ref([])
const form = reactive({ cargoNo: '', name: '', description: '', weight: 0, volume: 0 })
const waitingCargos = computed(() => cargos.value.filter(c => c.status === 'WAITING'))
const transportingCount = computed(() => cargos.value.filter(c => c.status === 'TRANSPORTING').length)

async function load() {
  loading.value = true
  try {
    const cargoResult = await api.cargos.list({ page: 1, pageSize: 100 })
    cargos.value = extractList(cargoResult)
  } catch (error) { ElMessage.error(`加载云端货物失败：${error.message}`) }
  finally { loading.value = false }
}
async function submit() {
  const validationError = validateCargoForm(form)
  if (validationError) return ElMessage.warning(validationError)
  saving.value = true
  try {
    if (!form.cargoNo.trim() || !form.name.trim()) return ElMessage.warning('请填写货物编号和名称')
    await api.cargos.create({ cargoNo: form.cargoNo.trim(), name: form.name.trim(), description: form.description.trim(), weight: form.weight, volume: form.volume })
    ElMessage.success('货物已录入云端数据库')
    Object.assign(form, { cargoNo: '', name: '', description: '', weight: 0, volume: 0 })
    await load()
  } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(error.message || '操作失败') }
  finally { saving.value = false }
}
onMounted(load)
</script>

<template>
  <PageHeader title="货物信息录入" subtitle="仅负责新增仓库库存；货主、车辆、司机和运输任务统一在货物出库页面确定" />
  <section class="warehouse-main" v-loading="loading">
    <article class="panel">
      <div class="panel-title"><div><h2>新增货物入库</h2><span>录入时允许暂不分配货主</span></div></div>
      <div class="binding-form">
        <label><span>货物编号</span><input v-model="form.cargoNo" placeholder="请输入唯一货物编号" /></label>
        <label><span>货物名称</span><input v-model="form.name" placeholder="请输入货物名称" /></label>
        <label><span>重量(kg)</span><el-input-number v-model="form.weight" :min="0" :precision="2" style="width:100%" /></label>
        <label><span>体积(m³)</span><el-input-number v-model="form.volume" :min="0" :precision="2" style="width:100%" /></label>
        <label><span>描述</span><textarea v-model="form.description" rows="3" placeholder="请输入货物描述（选填）"></textarea></label>
        <div class="api-scope-note">这里只创建库存货物，不选择货主、不分配车辆，也不创建运输任务。</div>
        <button class="primary" :disabled="saving" @click="submit">{{ saving ? '正在写入…' : '确认录入货物' }}</button>
      </div>
    </article>
    <article class="panel role-table-card">
      <div class="panel-title"><div><h2>云端货物汇总</h2></div><button class="mini" @click="load">刷新</button></div>
      <div class="stats-grid warehouse-io-stats">
        <article class="stat-card"><div class="stat-label">货物总数</div><div class="stat-value">{{ cargos.length }}</div></article>
        <article class="stat-card"><div class="stat-label">待运输</div><div class="stat-value">{{ waitingCargos.length }}</div></article>
        <article class="stat-card"><div class="stat-label">运输中</div><div class="stat-value">{{ transportingCount }}</div></article>
      </div>
    </article>
  </section>
  <section class="panel role-table-card" v-loading="loading">
    <div class="panel-title"><div><h2>货物录入记录</h2><span>货主归属将在出库创建任务时确定</span></div></div>
    <div class="table-wrap"><table><thead><tr><th>货物编号</th><th>名称</th><th>货主归属</th><th>重量</th><th>体积</th><th>状态</th></tr></thead><tbody><tr v-for="cargo in cargos" :key="cargo.id"><td>{{cargo.cargoNo}}</td><td>{{cargo.name}}</td><td>{{cargo.ownerName || cargo.ownerId || '未分配库存'}}</td><td>{{cargo.weight}} kg</td><td>{{cargo.volume}} m³</td><td>{{cargo.status}}</td></tr><tr v-if="!cargos.length"><td colspan="6">暂无云端货物</td></tr></tbody></table></div>
  </section>
</template>
