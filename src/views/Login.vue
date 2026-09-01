<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuth } from '../stores/auth-session'
import UiIcon from '../components/UiIcon.vue'

const route = useRoute()
const router = useRouter()
const { login } = useAuth()

const roles = [
  { value: 'OWNER', label: '货主', icon: 'package' },
  { value: 'DRIVER', label: '司机', icon: 'truck' },
  { value: 'WAREHOUSE_MANAGER', label: '仓库管理员', icon: 'warehouse' },
  { value: 'DISPATCHER', label: '调度员', icon: 'route' },
  { value: 'ADMIN', label: '系统管理员', icon: 'shield' }
]

const demoAccounts = {
  OWNER: 'owner01',
  DRIVER: 'driver01',
  WAREHOUSE_MANAGER: 'warehouse01',
  DISPATCHER: 'frontend_wh_test',
  ADMIN: 'admin01'
}

const initialRole = roles.some(r => r.value === route.query.role) ? route.query.role : 'OWNER'
const form = ref({
  username: demoAccounts[initialRole],
  password: '',
  role: initialRole
})
const errorText = ref('')
const isRoleSwitch = computed(() => route.query.switch === '1')
const canRegister = computed(() => ['OWNER', 'DRIVER', 'WAREHOUSE_MANAGER'].includes(form.value.role))

function chooseRole(role) {
  form.value.role = role
  form.value.username = demoAccounts[role]
  form.value.password = ''
  errorText.value = ''
}

async function submit() {
  errorText.value = ''
  try {
    await login(form.value)
    router.replace('/dashboard')
  } catch (error) {
    errorText.value = error?.message || '登录失败，请检查账号信息'
  }
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-shell">
      <section class="auth-brand-panel">
        <div class="auth-brand-mark"><UiIcon name="route" /></div>
        <h1>智慧物流系统</h1>
        <p>智慧物流运输、调度、仓储与告警管理平台</p>
        <div class="auth-brand-lines">
          <span></span><span></span><span></span>
        </div>
      </section>

      <section class="auth-form-panel">
        <div class="auth-form-head">
          <h2>{{ isRoleSwitch ? '重新登录身份' : '登录' }}</h2>
        </div>

        <div class="auth-role-grid">
          <button
            v-for="r in roles"
            :key="r.value"
            class="auth-role-card"
            :class="{ active: form.role === r.value }"
            type="button"
            @click="chooseRole(r.value)"
          >
            <span class="auth-role-icon"><UiIcon :name="r.icon" /></span>
            <strong>{{ r.label }}</strong>
          </button>
        </div>

        <form class="auth-form" @submit.prevent="submit">
          <label>
            <span>账号</span>
            <input v-model="form.username" autocomplete="username" placeholder="请输入账号" />
          </label>
          <label>
            <span>密码</span>
            <input v-model="form.password" type="password" autocomplete="current-password" placeholder="请输入密码" />
          </label>
          <div v-if="errorText" class="auth-error">{{ errorText }}</div>
          <button class="auth-primary" type="submit">登录系统</button>
        </form>

        <div v-if="canRegister" class="auth-switch-link">
          <span>还没有该身份账号？</span>
          <RouterLink :to="{ path: '/register', query: { role: form.role } }">立即注册</RouterLink>
        </div>
        <div v-else class="auth-switch-link auth-fixed-account-note">
          <span>{{ form.role === 'DISPATCHER' ? '调度员' : '系统管理员' }}为系统预置账号，请使用已分配的账号登录</span>
        </div>
      </section>
    </div>
  </div>
</template>
