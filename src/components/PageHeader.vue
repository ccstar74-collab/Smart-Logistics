<script setup>
defineProps({ title: String, subtitle: String })
</script>
<template>
  <header class="topbar">
    <div>
      <h1>{{ title }}</h1>
      <p>{{ subtitle }}</p>
    </div>
    <div class="top-actions">
      <button class="ghost" @click="location.reload()">刷新</button>
    </div>
  </header>
</template>
