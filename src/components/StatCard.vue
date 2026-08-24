<script setup>
import UiIcon from './UiIcon.vue'

defineProps({
  label: String,
  value: [String, Number],
  foot: String,
  icon: { type: String, default: 'box' },
  tone: { type: String, default: 'blue' },
  alarm: Boolean
})
</script>

<template>
  <article class="stat-card enriched-stat-card" :class="[{ alarm }, `tone-${tone}`]">
    <div class="stat-icon-wrap"><UiIcon :name="icon" /></div>
    <div class="stat-card-copy">
      <div class="stat-label">{{ label }}</div>
      <div class="stat-value">{{ value }}</div>
      <div class="stat-foot">{{ foot }}</div>
    </div>
  </article>
</template>
