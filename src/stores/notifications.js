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
  async function compensateUnread() {
    const result = await api.notifications.list({ page: 1, pageSize: 100, read: false })
    const unread = extractList(result).map(normalize)
    const merged = new Map(records.value.map(item => [String(item.id), item]))
    unread.forEach(item => merged.set(String(item.id), item))
    records.value = [...merged.values()].sort((a, b) => new Date(b.createdAt ?? 0) - new Date(a.createdAt ?? 0))
    await refreshUnreadCount()
  }
  async function markRead(id) {
    try {
      await api.notifications.markRead(id)
    } catch (cause) {
      if (!/HTTP 404/.test(cause?.message || '')) throw cause
    }
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
  function receiveNotification(item) {
    const normalized = normalize(item)
    if (normalized.id == null || records.value.some(record => String(record.id) === String(normalized.id))) return false
    records.value.unshift(normalized)
    if (!normalized.read) unreadTotal.value += 1
    return true
  }
  return { records, loading, error, unreadCount, load, compensateUnread, refreshUnreadCount, markRead, markAllRead, receiveNotification }
}
