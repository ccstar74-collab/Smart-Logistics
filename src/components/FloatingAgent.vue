<script setup>
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import UiIcon from './UiIcon.vue'

const router = useRouter()
const button = ref(null)
const position = reactive({ x: 0, y: 0 })
const dragging = ref(false)
let moved = false
let offsetX = 0
let offsetY = 0

function bounds() {
  const rect = button.value?.getBoundingClientRect()
  return { width: rect?.width || 150, height: rect?.height || 54, maxX: Math.max(12, window.innerWidth - (rect?.width || 150) - 12), maxY: Math.max(12, window.innerHeight - (rect?.height || 54) - 12) }
}
function clamp() {
  const area = bounds()
  position.x = Math.min(Math.max(12, position.x), area.maxX)
  position.y = Math.min(Math.max(12, position.y), area.maxY)
}
function restorePosition() {
  const area = bounds()
  try {
    const saved = JSON.parse(localStorage.getItem('floatingAgentPosition') || 'null')
    position.x = Number.isFinite(saved?.x) ? saved.x : area.maxX - 20
    position.y = Number.isFinite(saved?.y) ? saved.y : area.maxY - 20
  } catch { position.x = area.maxX - 20; position.y = area.maxY - 20 }
  clamp()
}
function pointerDown(event) {
  if (event.button !== undefined && event.button !== 0) return
  const rect = button.value.getBoundingClientRect()
  dragging.value = true; moved = false
  offsetX = event.clientX - rect.left; offsetY = event.clientY - rect.top
  button.value.setPointerCapture?.(event.pointerId)
}
function pointerMove(event) {
  if (!dragging.value) return
  moved = true
  position.x = event.clientX - offsetX; position.y = event.clientY - offsetY
  clamp()
}
function pointerUp(event) {
  if (!dragging.value) return
  dragging.value = false
  button.value.releasePointerCapture?.(event.pointerId)
  const area = bounds()
  position.x = position.x + area.width / 2 < window.innerWidth / 2 ? 12 : area.maxX
  clamp()
  localStorage.setItem('floatingAgentPosition', JSON.stringify({ x: position.x, y: position.y }))
  if (!moved) router.push('/agent')
}
onMounted(() => { restorePosition(); window.addEventListener('resize', clamp) })
onBeforeUnmount(() => window.removeEventListener('resize', clamp))
</script>

<template>
  <button ref="button" class="floating-agent global-floating-agent" :class="{ dragging }" :style="{ left: `${position.x}px`, top: `${position.y}px` }" aria-label="打开智能问答，可拖动" @pointerdown="pointerDown" @pointermove="pointerMove" @pointerup="pointerUp" @pointercancel="pointerUp">
    <UiIcon name="message" /><span>智能问答</span>
  </button>
</template>
