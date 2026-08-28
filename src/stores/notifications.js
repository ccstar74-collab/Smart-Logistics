import { computed, ref } from 'vue'
import { api, extractList } from '../api/http'

const records = ref([])
const loading = ref(false)
const error = ref('')
const unreadTotal = ref(0)

export function useNotifications() {
  const unreadCount = computed(() => unreadTotal.value)
  const normalize = item => ({ ...item, id: item.id ?? item.notificationId ?? item.notification_id, title: item.title ?? item.type ?? '系统通知', content: item.content ?? item.message ?? '', read: Boolean(item.read ?? item.isRead ?? item.is_read), createdAt: item.createdAt ?? item.created_at ?? item.time })
  async function load(params = {}) {
    loading.value = true
    error.value = ''
    try {
      const result = await api.notifications.list({ page: 1, pageSize: 100, ...params })
      records.value = extractList(result).map(normalize)
      unreadTotal.value = records.value.filter(item => !item.read).length
    } catch (cause) {
      error.value = cause.message
    } finally {
      loading.value = false
    }
  }
  async function refreshUnreadCount() {
    try {
      const result = await api.notifications.unreadCount()
      unreadTotal.value = Number(result?.count ?? result?.unreadCount ?? result ?? 0)
    } catch {
      unreadTotal.value = records.value.filter(item => !item.read).length
    }
  }
  async function markRead(id) {
    await api.notifications.markRead(id)
    const item = records.value.find(record => String(record.id) === String(id))
    if (item && !item.read) {
      item.read = true
      unreadTotal.value = Math.max(0, unreadTotal.value - 1)
    }
  }
  async function markAllRead() {
    await api.notifications.markAllRead()
    records.value.forEach(item => { item.read = true })
    unreadTotal.value = 0
  }
  return { records, loading, error, unreadCount, load, refreshUnreadCount, markRead, markAllRead }
}
