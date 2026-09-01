<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <main class="not-found-page">
    <div class="not-found-code">404</div>
    <h1>页面不存在</h1>
    <p>访问地址可能已变更，或者您没有可用的导航入口。</p>
    <el-button type="primary" @click="router.push('/')">返回首页</el-button>
  </main>
</template>
