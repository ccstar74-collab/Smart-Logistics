<script setup>
defineProps({
  variant: { type: String, default: 'content' },
  rows: { type: Number, default: 4 },
})
</script>

<template>
  <div class="skeleton-screen" :class="`is-${variant}`" role="status" aria-live="polite" aria-busy="true">
    <span class="sr-only">页面内容加载中</span>
    <template v-if="variant === 'dashboard'">
      <div class="skeleton-stat-grid">
        <div v-for="index in 4" :key="index" class="skeleton-card skeleton-stat">
          <i class="skeleton-block skeleton-icon" />
          <div><i class="skeleton-block short" /><i class="skeleton-block value" /><i class="skeleton-block medium" /></div>
        </div>
      </div>
      <div class="skeleton-card skeleton-map"><i class="skeleton-block title" /><i class="skeleton-block map-fill" /></div>
      <div class="skeleton-table-grid"><div v-for="index in 2" :key="index" class="skeleton-card skeleton-table"><i class="skeleton-block title" /><i v-for="row in 3" :key="row" class="skeleton-block row" /></div></div>
    </template>
    <template v-else-if="variant === 'map'">
      <div class="skeleton-map-canvas">
        <i class="skeleton-block map-road road-a" /><i class="skeleton-block map-road road-b" />
        <i class="skeleton-block map-marker marker-a" /><i class="skeleton-block map-marker marker-b" />
      </div>
    </template>
    <template v-else>
      <i class="skeleton-block title" />
      <i v-for="row in rows" :key="row" class="skeleton-block row" />
    </template>
  </div>
</template>
