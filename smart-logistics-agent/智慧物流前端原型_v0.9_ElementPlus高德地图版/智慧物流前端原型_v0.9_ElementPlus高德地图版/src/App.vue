<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuth } from './stores/auth'

const router = useRouter()
const { state, menus, users, switchUser } = useAuth()

const roleName = computed(() => ({
  OWNER: '货主',
  DRIVER: '司机',
  ADMIN: '管理员'
}[state.currentUser.role] || state.currentUser.role))

function chooseUser(user) {
  switchUser(user)
  const home = user.role === 'DRIVER' ? '/tasks' : '/dashboard'
  router.push(home)
}
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-icon">🚚</div>
        <div>
          <div class="brand-title">智慧物流</div>
          <div class="brand-sub">Smart Logistics</div>
        </div>
      </div>

      <nav class="menu">
        <RouterLink v-for="m in menus" :key="m[2]" :to="m[2]">
          <span>{{ m[0] }}</span>{{ m[1] }}
        </RouterLink>
      </nav>

      <button class="sidebar-footer user-switch-btn" @click="state.showSwitcher = true">
        <div class="avatar">{{ roleName.slice(0,1) }}</div>
        <div class="user-meta">
          <strong>{{ state.currentUser.name }}</strong>
          <span>{{ roleName }} · {{ state.currentUser.role }}</span>
        </div>
        <div class="switch-arrow">⇄</div>
      </button>
    </aside>

    <main class="content">
      <RouterView />
    </main>

    <div v-if="state.showSwitcher" class="modal-mask" @click.self="state.showSwitcher=false">
      <div class="role-modal">
        <div class="role-modal-head">
          <div>
            <h3>切换登录身份</h3>
            <p>演示模式：选择不同角色查看对应功能菜单</p>
          </div>
          <button class="close-btn" @click="state.showSwitcher=false">×</button>
        </div>

        <div class="role-list">
          <button
            v-for="u in users"
            :key="u.id"
            class="role-card"
            :class="{ current: state.currentUser.role === u.role }"
            @click="chooseUser(u)"
          >
            <div class="role-avatar">
              {{ u.role === 'OWNER' ? '货' : u.role === 'DRIVER' ? '司' : '管' }}
            </div>
            <div>
              <strong>{{ u.label }}</strong>
              <p>{{ u.name }}</p>
              <span>{{ u.username }}</span>
            </div>
            <b v-if="state.currentUser.role === u.role">当前</b>
          </button>
        </div>

        <div class="role-permission-note">
          <strong>角色功能差异：</strong>
          货主主要查看货物、任务、轨迹和异常；司机主要查看运输任务、实时位置和调度指令；管理员拥有完整管理与调度功能。
        </div>
      </div>
    </div>
  </div>
</template>
