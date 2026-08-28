<script setup>
import { computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import FloatingAgent from './components/FloatingAgent.vue'
import UiIcon from './components/UiIcon.vue'
import { useAuth } from './stores/auth-session'
import { useNotifications } from './stores/notifications'

const route = useRoute()
const router = useRouter()
const { state, menus, roleLabel, logout } = useAuth()
const { unreadCount, refreshUnreadCount } = useNotifications()
const isPublicRoute = computed(() => route.meta.public === true)
const currentInitial = computed(() => state.currentUser?.name?.slice(0, 1) || roleLabel.value?.slice(0, 1) || '用')

function handleLogout() {
  logout()
  router.replace('/login')
}
watch(
  () => [route.path, state.currentUser?.id, Boolean(localStorage.getItem('accessToken'))],
  ([path, _userId, hasToken]) => {
    // 登录/注册页不应请求需要认证的消息接口；只有真实登录后才刷新未读数。
    if (!hasToken || path === '/login' || path === '/register') return
    refreshUnreadCount()
  },
  { immediate: true }
)
</script>

<template>
  <RouterView v-if="isPublicRoute" />
  <div v-else class="role-app-shell">
    <aside class="dark-sidebar">
      <button class="sidebar-brand" @click="router.push('/dashboard')">智慧物流系统</button>
      <nav class="menu role-menu"><RouterLink v-for="item in menus" :key="item[2]" :to="item[2]"><span class="menu-icon">{{ item[0] }}</span><span>{{ item[1] }}</span></RouterLink></nav>
      <div class="sidebar-bottom-zone">
        <button class="sidebar-notification-card" @click="router.push('/notifications')"><span class="sidebar-notification-icon"><UiIcon name="bell" /></span><span class="sidebar-notification-copy"><strong>消息通知</strong><small>{{ unreadCount ? `${unreadCount} 条待查看` : '暂无待查看消息' }}</small></span><span v-if="unreadCount" class="sidebar-notification-count">{{ unreadCount }}</span></button>
        <button class="sidebar-account-card" @click="state.showSwitcher = true"><span class="sidebar-account-avatar">{{ currentInitial }}</span><span class="sidebar-account-copy"><strong>{{ state.currentUser?.name }}</strong><small>{{ roleLabel }}</small></span><UiIcon name="user" /></button>
      </div>
    </aside>
    <FloatingAgent v-if="state.currentUser?.role !== 'ADMIN'" />
    <main class="role-content"><RouterView /></main>
    <div v-if="state.showSwitcher" class="modal-mask role-switch-mask" @click.self="state.showSwitcher = false"><div class="role-modal account-modal"><div class="role-modal-head role-switch-head"><h3>当前账号</h3><button class="close-btn" @click="state.showSwitcher = false">×</button></div><div class="account-modal-body"><span class="account-modal-avatar">{{ currentInitial }}</span><div><strong>{{ state.currentUser?.name }}</strong><span>{{ roleLabel }}</span><small>账号：{{ state.currentUser?.username || state.currentUser?.account || '—' }}</small></div></div><div class="account-modal-actions"><button class="account-profile-btn" @click="state.showSwitcher = false; router.push('/profile')"><UiIcon name="user" />个人中心</button><button class="logout-link" @click="handleLogout">退出登录</button></div></div></div>
  </div>
</template>
