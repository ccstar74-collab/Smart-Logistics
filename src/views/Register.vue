<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuth } from '../stores/auth-session'
import UiIcon from '../components/UiIcon.vue'

const route = useRoute()
const router = useRouter()
const { register } = useAuth()

const roles = [
  { value: 'OWNER', label: '货主', icon: 'package' },
  { value: 'DRIVER', label: '司机', icon: 'truck' },
  { value: 'WAREHOUSE_MANAGER', label: '仓库管理员', icon: 'warehouse' }
]
const initialRole = roles.some(r => r.value === route.query.role) ? route.query.role : 'OWNER'

const form = ref({
  name: '',
  username: '',
  password: '',
  confirmPassword: '',
  role: initialRole
})
const errorText = ref('')

async function submit() {
  errorText.value = ''
  if (form.value.password !== form.value.confirmPassword) {
    errorText.value = '两次输入的密码不一致'
    return
  }
  try {
    await register(form.value)
    router.replace('/dashboard')
  } catch (error) {
    errorText.value = error?.message || '注册失败，请检查填写内容'
  }
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-shell register-shell">
      <section class="auth-brand-panel">
        <div class="auth-brand-mark"><UiIcon name="warehouse" /></div>
        <h1>智慧物流系统</h1>
        <p>货主、司机与仓库管理员可创建业务账号</p>
        <div class="auth-brand-lines">
          <span></span><span></span><span></span>
        </div>
      </section>

      <section class="auth-form-panel">
        <div class="auth-form-head">
          <h2>注册</h2>
          <p>调度员和系统管理员使用系统预置账号，不开放自行注册</p>
        </div>

        <div class="auth-role-grid">
          <button
            v-for="r in roles"
            :key="r.value"
            class="auth-role-card"
            :class="{ active: form.role === r.value }"
            type="button"
            @click="form.role = r.value"
          >
            <span class="auth-role-icon"><UiIcon :name="r.icon" /></span>
            <strong>{{ r.label }}</strong>
          </button>
        </div>

        <form class="auth-form register-form" @submit.prevent="submit">
          <label>
            <span>姓名</span>
            <input v-model="form.name" placeholder="请输入姓名" />
          </label>
          <label>
            <span>账号</span>
            <input v-model="form.username" autocomplete="username" placeholder="请输入账号" />
          </label>
          <label>
            <span>密码</span>
            <input v-model="form.password" type="password" autocomplete="new-password" placeholder="请输入密码" />
          </label>
          <label>
            <span>确认密码</span>
            <input v-model="form.confirmPassword" type="password" autocomplete="new-password" placeholder="请再次输入密码" />
          </label>
          <div v-if="errorText" class="auth-error register-error">{{ errorText }}</div>
          <button class="auth-primary" type="submit">注册并进入系统</button>
        </form>

        <div class="auth-switch-link">
          <span>已有账号？</span>
          <RouterLink :to="{ path: '/login', query: { role: form.role } }">返回登录</RouterLink>
        </div>
      </section>
    </div>
  </div>
</template>
