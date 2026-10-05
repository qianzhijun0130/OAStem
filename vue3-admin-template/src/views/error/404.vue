<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div class="error-page">
    <h1>404</h1>
    <p>抱歉，您访问的页面不存在或无权访问</p>
    <!-- 回根路径 `/`，由守卫按当前用户菜单决定落地页；不要硬编码 /dashboard -->
    <el-button type="primary" @click="router.push('/')">返回首页</el-button>
  </div>
</template>

<style scoped>
.error-page {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100vh;
  gap: 12px;
}

.error-page h1 {
  margin: 0;
  font-size: 72px;
  color: #409eff;
}

.error-page p {
  color: #606266;
}
</style>
