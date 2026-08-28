<script setup>
import { onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import DataState from '../components/DataState.vue'
import PageHeader from '../components/PageHeader.vue'
import { useNotifications } from '../stores/notifications'

const { records, loading, error, markRead, markAllRead, load } = useNotifications()
const dateText = value => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—'
async function readOne(id) { try { await markRead(id) } catch (cause) { ElMessage.error(cause.message) } }
async function readAll() { try { await markAllRead(); ElMessage.success('全部通知已标记为已读') } catch (cause) { ElMessage.error(cause.message) } }
onMounted(load)
</script>

<template>
  <PageHeader title="消息中心" subtitle="接收系统通知、调度消息与业务提醒" />
  <section class="panel page-panel">
    <div class="panel-title"><div><h2>通知列表</h2><span>未读通知会以蓝色标记</span></div><el-button :disabled="!records.some(item => !item.read)" @click="readAll">全部已读</el-button></div>
    <DataState :loading="loading" :error="error" :empty="!records.length" empty-text="暂无通知" @retry="load">
      <div class="command-cards"><button v-for="item in records" :key="item.id" class="command-card notification-item" :class="{ unread: !item.read }" @click="readOne(item.id)"><div class="command-card-head"><strong><i v-if="!item.read" class="notification-unread-dot" />{{ item.title }}</strong><span>{{ dateText(item.createdAt) }}</span></div><p>{{ item.content }}</p><small>{{ item.read ? '已读' : '点击标记已读' }}</small></button></div>
    </DataState>
  </section>
</template>
