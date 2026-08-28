<script setup>
defineProps({
  loading: Boolean,
  error: { type: String, default: '' },
  empty: Boolean,
  loadingText: { type: String, default: '数据加载中…' },
  emptyText: { type: String, default: '暂无数据' },
})

defineEmits(['retry'])
</script>

<template>
  <div v-if="loading" class="unified-data-state" role="status">
    <span class="state-spinner" />{{ loadingText }}
  </div>
  <div v-else-if="error" class="unified-data-state is-error" role="alert">
    <span>{{ error }}</span>
    <button type="button" class="state-retry" @click="$emit('retry')">重试</button>
  </div>
  <div v-else-if="empty" class="unified-data-state is-empty">{{ emptyText }}</div>
  <slot v-else />
</template>
