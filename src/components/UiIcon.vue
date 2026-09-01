<script setup>
defineProps({
  name: { type: String, default: 'box' }
})
</script>

<template>
  <svg class="ui-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
    <template v-if="name==='truck'">
      <path d="M3 6h11v10H3z"/><path d="M14 10h4l3 3v3h-7z"/><circle cx="7" cy="18" r="2"/><circle cx="18" cy="18" r="2"/>
    </template>
    <template v-else-if="name==='check'">
      <circle cx="12" cy="12" r="9"/><path d="m8 12 2.5 2.5L16.5 9"/>
    </template>
    <template v-else-if="name==='alert'">
      <path d="M12 3 2.8 19h18.4z"/><path d="M12 9v4"/><path d="M12 16h.01"/>
    </template>
    <template v-else-if="name==='package' || name==='box'">
      <path d="m4 7 8-4 8 4-8 4z"/><path d="M4 7v10l8 4 8-4V7"/><path d="M12 11v10"/>
    </template>
    <template v-else-if="name==='users'">
      <circle cx="9" cy="8" r="3"/><path d="M3.5 19c.5-4 2.5-6 5.5-6s5 2 5.5 6"/><circle cx="17" cy="9" r="2.2"/><path d="M15.5 14c3.2-.2 5 1.5 5.3 4.5"/>
    </template>
    <template v-else-if="name==='vehicle'">
      <path d="M5 8 6.5 5h11L19 8"/><rect x="3" y="8" width="18" height="8" rx="2"/><path d="M6 16v2M18 16v2M7 12h.01M17 12h.01"/>
    </template>
    <template v-else-if="name==='warehouse'">
      <path d="m3 10 9-6 9 6"/><path d="M5 9v11h14V9"/><path d="M9 20v-6h6v6"/>
    </template>
    <template v-else-if="name==='upload'">
      <path d="M12 16V4"/><path d="m8 8 4-4 4 4"/><path d="M5 14v5h14v-5"/>
    </template>
    <template v-else-if="name==='download'">
      <path d="M12 4v12"/><path d="m8 12 4 4 4-4"/><path d="M5 19h14"/>
    </template>
    <template v-else-if="name==='task'">
      <rect x="5" y="4" width="14" height="17" rx="2"/><path d="M9 4.5h6"/><path d="m8 10 1.5 1.5L12 9"/><path d="M13 11h3M8 16h8"/>
    </template>
    <template v-else-if="name==='route'">
      <circle cx="6" cy="17" r="2"/><circle cx="18" cy="7" r="2"/><path d="M8 17h3c3 0 2-6 5-6h2"/>
    </template>
    <template v-else-if="name==='shield'">
      <path d="M12 3 5 6v5c0 4.8 2.8 8 7 10 4.2-2 7-5.2 7-10V6z"/><path d="m9.5 12 1.7 1.7 3.6-4"/>
    </template>
    <template v-else-if="name==='bell'">
      <path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9"/><path d="M10 21h4"/>
    </template>
    <template v-else-if="name==='switch'">
      <path d="M7 7h11l-3-3"/><path d="m18 7-3 3"/><path d="M17 17H6l3 3"/><path d="m6 17 3-3"/>
    </template>
    <template v-else-if="name==='message'">
      <path d="M4 5h16v12H8l-4 3z"/><path d="M8 10h.01M12 10h.01M16 10h.01"/>
    </template>
    <template v-else-if="name==='chart'">
      <path d="M4 20V10M10 20V4M16 20v-7M22 20H2"/>
    </template>
    <template v-else-if="name==='command'">
      <path d="M5 4h9l5 5v11H5z"/><path d="M14 4v5h5"/><path d="M8 13h7M8 17h5"/><path d="m15.5 14.5 3 2-3 2"/>
    </template>
    <template v-else-if="name==='broadcast'">
      <path d="m4 13 11-5v8L4 11z"/><path d="M7 13v5h4l-1.5-5"/><path d="M18 9.5c1.5 1.3 1.5 3.7 0 5M20.5 7c3 2.8 3 7.2 0 10"/>
    </template>
    <template v-else-if="name==='analytics'">
      <path d="M4 20V5M4 20h16"/><path d="m7 15 4-4 3 2 5-6"/><circle cx="7" cy="15" r="1"/><circle cx="11" cy="11" r="1"/><circle cx="14" cy="13" r="1"/><circle cx="19" cy="7" r="1"/>
    </template>
    <template v-else-if="name==='user'">
      <circle cx="12" cy="8" r="4"/><path d="M4.5 21c.7-5 3.2-7.5 7.5-7.5s6.8 2.5 7.5 7.5"/>
    </template>
    <template v-else>
      <rect x="4" y="4" width="16" height="16" rx="4"/><path d="M8 12h8"/>
    </template>
  </svg>
</template>
