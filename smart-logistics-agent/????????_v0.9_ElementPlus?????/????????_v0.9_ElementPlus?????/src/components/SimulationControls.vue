<script setup>
import { running, simSpeed, tickCount, startSimulation, pauseSimulation, resetSimulation, stepSimulation, setSimulationSpeed } from '../stores/realtime'
</script>
<template>
  <div class="simulation-controls">
    <div class="simulation-status">
      <span class="sim-dot" :class="{active:running}"></span>
      <div><strong>{{running ? '动态数据推送中' : '动态数据已暂停'}}</strong><small>已更新 {{tickCount}} 次 · 模拟 WebSocket/MQTT 实时快照</small></div>
    </div>
    <div class="simulation-actions">
      <button v-if="!running" class="sim-btn primary" @click="startSimulation">▶ 开始模拟</button>
      <button v-else class="sim-btn" @click="pauseSimulation">Ⅱ 暂停</button>
      <button class="sim-btn" @click="stepSimulation">单步更新</button>
      <label class="speed-select">速度
        <select :value="simSpeed" @change="setSimulationSpeed($event.target.value)">
          <option value="0.5">0.5×</option><option value="1">1×</option><option value="2">2×</option><option value="4">4×</option>
        </select>
      </label>
      <button class="sim-btn ghost" @click="resetSimulation">重置</button>
    </div>
  </div>
</template>
