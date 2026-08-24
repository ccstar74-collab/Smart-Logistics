<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuth } from './stores/auth'
import UiIcon from './components/UiIcon.vue'

const router = useRouter()
const { state, menus, users, switchUser, roleLabel } = useAuth()

const roleMeta = {
  OWNER: { icon: 'package', desc: '查看货物位置、运输轨迹、ETA 与异常告警' },
  DRIVER: { icon: 'truck', desc: '执行运输任务、查看路线并上报运输状态' },
  WAREHOUSE: { icon: 'warehouse', desc: '管理车辆、货物绑定、出入库与任务分配' },
  DISPATCHER: { icon: 'route', desc: '查看车辆分布、处理告警并下发调度指令' },
  ADMIN: { icon: 'shield', desc: '管理用户与业务数据、查看告警日志和系统配置' }
}

const currentInitial = computed(() => state.currentUser.name?.slice(0, 1) || roleLabel.value?.slice(0, 1) || '用')

function chooseUser(user) {
  switchUser(user)
  router.push('/dashboard')
}
</script>

<template>
  <div class="role-app-shell">
    <header class="global-header">
      <div class="brand-title">智慧物流平台</div>
      <div class="global-header-right">
        <button class="header-icon-btn" title="消息通知">
          <UiIcon name="bell" />
          <span class="notify-count">8</span>
        </button>
        <button class="header-user" @click="state.showSwitcher = true" title="切换身份">
          <span class="header-avatar">{{ currentInitial }}</span>
          <span class="header-user-copy">
            <strong>{{ state.currentUser.name }}</strong>
            <small>{{ roleLabel }}</small>
          </span>
          <span class="header-chevron">⌄</span>
        </button>
      </div>
    </header>

    <aside class="dark-sidebar">
      <nav class="menu role-menu">
        <RouterLink v-for="m in menus" :key="m[2]" :to="m[2]">
          <span class="menu-icon">{{ m[0] }}</span><span>{{ m[1] }}</span>
        </RouterLink>
      </nav>
      <button class="sidebar-account-card" @click="state.showSwitcher = true">
        <span class="sidebar-account-avatar">{{ currentInitial }}</span>
        <span class="sidebar-account-copy">
          <strong>{{ state.currentUser.name }}</strong>
          <small>{{ roleLabel }}</small>
        </span>
        <UiIcon name="switch" />
      </button>
    </aside>

    <main class="role-content">
      <RouterView />
    </main>

    <div v-if="state.showSwitcher" class="modal-mask role-switch-mask" @click.self="state.showSwitcher=false">
      <div class="role-modal role-switch-modal">
        <div class="role-modal-head role-switch-head">
          <div>
            <span class="role-switch-kicker">DEMO ROLE</span>
            <h3>选择演示身份</h3>
            <p>当前用于原型演示；正式接入登录后，将根据账号权限自动进入对应工作台。</p>
          </div>
          <button class="close-btn" @click="state.showSwitcher=false">×</button>
        </div>

        <div class="role-list five-role-list">
          <button
            v-for="u in users"
            :key="u.id"
            class="role-card role-select-card"
            :class="{ current: state.currentUser.role === u.role }"
            @click="chooseUser(u)"
          >
            <span class="role-avatar role-icon-avatar">
              <UiIcon :name="roleMeta[u.role]?.icon || 'user'" />
            </span>
            <span class="role-card-main">
              <span class="role-card-title-row">
                <strong>{{ u.label }}</strong>
                <em v-if="state.currentUser.role === u.role">当前身份</em>
              </span>
              <p>{{ u.name }}</p>
              <span class="role-card-desc">{{ roleMeta[u.role]?.desc }}</span>
            </span>
            <span class="role-card-arrow">→</span>
          </button>
        </div>

        <div class="role-switch-footer">
          <UiIcon name="shield" />
          <span>后续可直接与登录 / 注册和角色权限体系衔接，无需改变现有业务页面结构。</span>
        </div>
      </div>
    </div>
  </div>
</template>
