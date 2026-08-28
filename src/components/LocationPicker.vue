<script setup>
import { nextTick, onBeforeUnmount, ref } from 'vue'
import AMapLoader from '@amap/amap-jsapi-loader'

const props = defineProps({
  modelValue: Boolean,
  title: { type: String, default: '选择地点' },
  initialName: { type: String, default: '' },
  initialLongitude: { type: [Number, String], default: null },
  initialLatitude: { type: [Number, String], default: null },
  showCoordinates: { type: Boolean, default: true },
})
const emit = defineEmits(['update:modelValue', 'confirm'])

const mapContainer = ref(null)
const keyword = ref('')
const name = ref('')
const longitude = ref(null)
const latitude = ref(null)
const loading = ref(false)
const error = ref('')
let map = null
let marker = null
let geocoder = null

function close() {
  emit('update:modelValue', false)
}

function applyPoint(lng, lat, address = '') {
  longitude.value = Number(Number(lng).toFixed(6))
  latitude.value = Number(Number(lat).toFixed(6))
  if (address) name.value = address
  if (map) {
    if (!marker) marker = new window.AMap.Marker({ map })
    marker.setPosition([longitude.value, latitude.value])
    map.setCenter([longitude.value, latitude.value])
  }
}

function reverseGeocode(lng, lat) {
  if (!geocoder) return
  geocoder.getAddress([lng, lat], (status, result) => {
    if (status === 'complete' && result?.regeocode?.formattedAddress) name.value = result.regeocode.formattedAddress
  })
}

function searchLocation() {
  error.value = ''
  if (!keyword.value.trim()) {
    error.value = '请输入地名或详细地址'
    return
  }
  geocoder?.getLocation(keyword.value.trim(), (status, result) => {
    const point = result?.geocodes?.[0]
    if (status !== 'complete' || !point?.location) {
      error.value = '未找到该地点，请换一个更详细的地址'
      return
    }
    applyPoint(point.location.lng, point.location.lat, point.formattedAddress || keyword.value.trim())
  })
}

async function initializeMap() {
  loading.value = true
  error.value = ''
  name.value = props.initialName || ''
  keyword.value = props.initialName || ''
  longitude.value = props.initialLongitude
  latitude.value = props.initialLatitude
  try {
    await nextTick()
    const key = import.meta.env.VITE_AMAP_KEY
    const securityJsCode = import.meta.env.VITE_AMAP_SECURITY_CODE
    if (!key || !securityJsCode) throw new Error('未配置高德地图 Key 或 securityJsCode')
    window._AMapSecurityConfig = { securityJsCode }
    const AMap = await AMapLoader.load({
      key,
      version: '2.0',
      plugins: ['AMap.ToolBar', 'AMap.Geocoder'],
    })
    window.AMap = AMap
    if (map) map.destroy()
    const hasInitial = Number.isFinite(Number(longitude.value)) && Number.isFinite(Number(latitude.value))
    map = new AMap.Map(mapContainer.value, {
      zoom: hasInitial ? 15 : 11,
      center: hasInitial ? [Number(longitude.value), Number(latitude.value)] : [106.5516, 29.563],
    })
    map.addControl(new AMap.ToolBar())
    geocoder = new AMap.Geocoder()
    if (hasInitial) applyPoint(longitude.value, latitude.value, name.value)
    map.on('click', (event) => {
      applyPoint(event.lnglat.lng, event.lnglat.lat)
      reverseGeocode(event.lnglat.lng, event.lnglat.lat)
    })
  } catch (cause) {
    error.value = cause?.message || '高德地图加载失败'
  } finally {
    loading.value = false
  }
}

function confirm() {
  if (!name.value.trim() || !Number.isFinite(Number(longitude.value)) || !Number.isFinite(Number(latitude.value))) {
    error.value = '请先在地图上选择一个有效地点'
    return
  }
  emit('confirm', {
    name: name.value.trim(),
    longitude: Number(longitude.value),
    latitude: Number(latitude.value),
    coordinateSystem: 'GCJ-02',
  })
  close()
}

onBeforeUnmount(() => map?.destroy())
</script>

<template>
  <el-dialog :model-value="modelValue" :title="title" width="min(760px, 92vw)" destroy-on-close @opened="initializeMap" @close="close">
    <div class="location-search-row">
      <el-input v-model="keyword" placeholder="输入地名或详细地址" clearable @keyup.enter="searchLocation" />
      <el-button type="primary" @click="searchLocation">搜索</el-button>
    </div>
    <p class="location-picker-hint">可搜索地址，也可直接点击地图选点。坐标系为高德 GCJ-02。</p>
    <div v-loading="loading" ref="mapContainer" class="location-picker-map" />
    <div v-if="error" class="location-picker-error">{{ error }}</div>
    <div class="location-picker-result">
      <strong>{{ name || '尚未选择地点' }}</strong>
      <span v-if="showCoordinates && longitude !== null && latitude !== null">{{ longitude }}, {{ latitude }}</span>
    </div>
    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button type="primary" @click="confirm">确认地点</el-button>
    </template>
  </el-dialog>
</template>
