<script setup>
defineProps({ title: String, subtitle: String })
</script>
<template>
  <header class="topbar">
    <div>
      <h1>{{ title }}</h1>
    </div>
  </header>
</template>
