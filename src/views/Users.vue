<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '../components/PageHeader.vue'
import { api, extractList } from '../api/http'

const loading = ref(false), activeRole = ref('ALL'), users = ref([])
const roleLabel = { OWNER: '货主', DRIVER: '司机', WAREHOUSE_MANAGER: '仓库管理员', DISPATCHER: '调度员', ADMIN: '系统管理员' }
const roleFilters = Object.entries({ ALL: '全部', ...roleLabel }).map(([value, label]) => ({ value, label }))
const optionId = item => item.id ?? item.ownerId ?? item.driverId ?? item.userId
const optionAccount = item => item.username ?? item.account ?? item.userName ?? '--'
function normalize(item, role) { return { ...item, id: `${role}-${optionId(item)}`, backendId: optionId(item), account: optionAccount(item), name: item.name ?? item.realName ?? item.ownerName ?? item.driverName ?? item.companyName ?? '--', role, status: item.status ?? 'ENABLED' } }
const filteredUsers = computed(() => activeRole.value === 'ALL' ? users.value : users.value.filter(u => u.role === activeRole.value))
const roleCount = role => role === 'ALL' ? users.value.length : users.value.filter(u => u.role === role).length

async function load() {
  loading.value = true
  try {
    const [ownersResult, driversResult, me] = await Promise.all([api.owners.options(), api.drivers.options(), api.me()])
    const rows = [...extractList(ownersResult).map(item => normalize(item, 'OWNER')), ...extractList(driversResult).map(item => normalize(item, 'DRIVER'))]
    if (me?.id != null && !rows.some(row => row.role === me.role && Number(row.backendId) === Number(me.id))) rows.push(normalize(me, me.role))
    users.value = rows
  } catch (error) { ElMessage.error(`云端用户数据加载失败：${error.message}`) }
  finally { loading.value = false }
}
onMounted(load)
</script>

<template>
  <PageHeader title="用户管理" subtitle="货主、司机和当前管理员信息来自云端 API" />
  <section class="panel page-panel account-admin-panel" v-loading="loading">
    <div class="account-role-tabs"><button v-for="role in roleFilters" :key="role.value" :class="{ active: activeRole === role.value }" @click="activeRole = role.value"><span>{{ role.label }}</span><b>{{ roleCount(role.value) }}</b></button><button class="mini" @click="load">刷新云端数据</button></div>
    <div class="api-scope-note">当前后端只开放货主、司机选项和当前用户接口；全量仓库管理员、调度员、管理员列表及账号启停需后端新增管理接口。</div>
    <div class="table-wrap"><table><thead><tr><th>ID</th><th>用户</th><th>账号</th><th>身份</th><th>手机</th><th>状态</th><th>数据来源</th></tr></thead><tbody><tr v-for="user in filteredUsers" :key="user.id"><td>{{ user.backendId }}</td><td>{{ user.name }}</td><td>{{ user.account }}</td><td><span class="role-pill">{{ roleLabel[user.role] || user.role }}</span></td><td>{{ user.phone || '--' }}</td><td><span class="task-status">{{ user.status }}</span></td><td>云端数据库</td></tr><tr v-if="!filteredUsers.length"><td colspan="7">当前接口未返回该角色账号</td></tr></tbody></table></div>
  </section>
</template>
