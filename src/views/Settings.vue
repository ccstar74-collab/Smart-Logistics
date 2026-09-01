<script setup>
import { ref } from 'vue'
import PageHeader from '../components/PageHeader.vue'
const alarmPush=ref(true),mqtt=ref(true),audit=ref(true)
</script>
<template><PageHeader title="系统设置" subtitle="维护通知、系统参数与平台运行配置"/><section class="settings-grid"><article class="panel simple-card"><h3>通知设置</h3><div class="setting-row"><span>告警消息推送</span><el-switch v-model="alarmPush"/></div><div class="setting-row"><span>系统消息通知</span><el-switch v-model="audit"/></div></article><article class="panel simple-card"><h3>数据接入</h3><div class="setting-row"><span>物联网数据接入</span><el-switch v-model="mqtt"/></div><div class="setting-row"><span>高德地图服务</span><el-tag type="success">已配置</el-tag></div></article><article class="panel simple-card"><h3>告警参数</h3><div class="setting-row"><span>偏航阈值</span><strong>300 米</strong></div><div class="setting-row"><span>异常停留阈值</span><strong>10 分钟</strong></div></article><article class="panel simple-card"><h3>日志审计</h3><div class="setting-row"><span>操作日志</span><el-switch v-model="audit"/></div><div class="setting-row"><span>日志保留周期</span><strong>90 天</strong></div></article></section></template>
