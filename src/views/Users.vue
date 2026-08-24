<script setup>
import { ref } from 'vue'
import data from '../mock/data.json'
import PageHeader from '../components/PageHeader.vue'
const users=ref(data.systemUsers.map(x=>({...x})))
function toggle(u){u.status=u.status==='启用'?'停用':'启用'}
</script>
<template><PageHeader title="用户管理" subtitle="管理平台用户账号、所属角色与启停状态"/><section class="panel page-panel"><div class="toolbar"><button class="primary" @click="alert('演示版：新增用户')">+ 新增用户</button></div><div class="table-wrap"><table><thead><tr><th>用户</th><th>账号</th><th>角色</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="u in users" :key="u.id"><td>{{u.name}}</td><td>{{u.account}}</td><td>{{u.role}}</td><td><span class="task-status">{{u.status}}</span></td><td><button class="mini" @click="toggle(u)">{{u.status==='启用'?'停用':'启用'}}</button></td></tr></tbody></table></div></section></template>
