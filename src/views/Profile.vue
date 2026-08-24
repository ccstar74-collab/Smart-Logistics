<script setup>
import PageHeader from '../components/PageHeader.vue'
import { useAuth } from '../stores/auth'
const { state, roleLabel } = useAuth()
</script>
<template><PageHeader title="个人中心" subtitle="查看当前账号信息与基础设置"/><section class="panel simple-card"><h3>{{state.currentUser.name}}</h3><div class="detail-lines"><div><span>账号</span><strong>{{state.currentUser.username}}</strong></div><div><span>身份</span><strong>{{roleLabel}}</strong></div><div><span>账号状态</span><strong>正常</strong></div><div><span>最近登录</span><strong>当前会话</strong></div></div><div style="margin-top:18px"><button class="primary" @click="alert('演示版：保存个人设置')">保存设置</button></div></section></template>
