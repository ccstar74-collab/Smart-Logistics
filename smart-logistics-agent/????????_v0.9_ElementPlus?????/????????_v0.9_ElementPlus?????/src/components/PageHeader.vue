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
      <span class="online-dot"></span><span>模拟数据在线</span>
      <button class="ghost" @click="location.reload()">刷新</button>
    </div>
  </header>
</template>
