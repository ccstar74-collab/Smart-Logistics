<script setup>
import PageHeader from '../components/PageHeader.vue'
const roles=[
  {name:'货主',desc:'查看自己的运输订单位置、轨迹、ETA、告警和智能问答',menus:'货物追踪 / 运输轨迹 / ETA / 告警 / 智能问答'},
  {name:'司机',desc:'执行运输任务、查看路线、上报状态并接收调度指令',menus:'我的任务 / 运输任务 / 状态上报 / 调度指令'},
  {name:'仓库管理员',desc:'管理仓库车辆、货物车辆绑定、货物信息录入与任务分配',menus:'车辆管理 / 货物车辆绑定 / 货物信息录入 / 任务分配'},
  {name:'调度员',desc:'全局查看车辆分布、处理告警并下发调度指令',menus:'车辆监控 / 告警管理 / 调度指令 / 数据统计'},
  {name:'系统管理员',desc:'管理用户角色、基础数据、告警日志与系统配置',menus:'用户管理 / 角色管理 / 车辆货物管理 / 系统设置'}
]
</script>
<template><PageHeader title="角色管理" subtitle="查看五类身份的功能边界与权限范围"/><section class="simple-card-grid" style="grid-template-columns:repeat(2,1fr)"><article v-for="r in roles" :key="r.name" class="panel simple-card"><h3>{{r.name}}</h3><p>{{r.desc}}</p><div class="section-note">{{r.menus}}</div><button class="mini" @click="alert('演示版：权限配置')">配置权限</button></article></section></template>
