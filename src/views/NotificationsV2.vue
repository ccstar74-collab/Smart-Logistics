<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import DataState from '../components/DataState.vue'
import PageHeader from '../components/PageHeader.vue'
import { useNotifications } from '../stores/notifications'

const { records, loading, error, markRead, markAllRead, load } = useNotifications()
const router = useRouter()
const filter = ref('all')
const readingId = ref(null)
const unreadAmount = computed(() => records.value.filter(item => !item.read).length)
const readAmount = computed(() => records.value.length - unreadAmount.value)
const visibleRecords = computed(() => filter.value === 'unread' ? records.value.filter(item => !item.read) : filter.value === 'read' ? records.value.filter(item => item.read) : records.value)
const filters = computed(() => [
  { value: 'all', label: '全部', count: records.value.length },
  { value: 'unread', label: '未读', count: unreadAmount.value },
  { value: 'read', label: '已读', count: readAmount.value },
])
const dateText = value => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—'

async function readOne(item) {
  if (readingId.value != null) return
  readingId.value = item.id
  try {
    if (!item.read) await markRead(item.id)
    if (item.targetPath) router.push(item.targetPath)
  } catch (cause) { ElMessage.error(cause.message) }
  finally { readingId.value = null }
}
async function readAll() {
  try { await markAllRead(); ElMessage.success('全部通知已标记为已读') }
  catch (cause) { ElMessage.error(cause.message) }
}
onMounted(load)
</script>

<template>
  <PageHeader title="消息中心" subtitle="接收系统通知、调度消息与业务提醒" />
  <section class="panel page-panel notification-panel">
    <div class="panel-title notification-titlebar">
      <div><h2>通知列表</h2><span>未读通知以蓝色突出显示，点击后会原位切换为已读</span></div>
      <el-button :disabled="!unreadAmount" @click="readAll">全部已读</el-button>
    </div>
    <div class="notification-toolbar" role="tablist" aria-label="通知状态筛选">
      <button v-for="item in filters" :key="item.value" type="button" class="notification-filter" :class="{ active: filter === item.value }" :aria-selected="filter === item.value" role="tab" @click="filter = item.value">{{ item.label }} <span>{{ item.count }}</span></button>
    </div>
    <DataState :loading="loading" :error="error" :empty="!visibleRecords.length" :empty-text="filter === 'unread' ? '暂无未读通知' : filter === 'read' ? '暂无已读通知' : '暂无通知'" @retry="load">
      <div class="command-cards notification-list">
        <button v-for="item in visibleRecords" :key="item.id" type="button" class="command-card notification-item" :class="{ unread: !item.read, read: item.read, processing: readingId === item.id }" @click="readOne(item)">
          <div class="notification-state-rail" aria-hidden="true" />
          <div class="notification-copy">
            <div class="command-card-head"><strong>{{ item.title }}</strong><span>{{ dateText(item.createdAt) }}</span></div>
            <p>{{ item.content }}</p><small>{{ item.targetPath ? '点击查看详情' : '通知记录' }}</small>
          </div>
          <span class="notification-read-state" :class="{ unread: !item.read }"><i v-if="!item.read" />{{ item.read ? '已读' : '未读' }}</span>
        </button>
      </div>
    </DataState>
  </section>
</template>
