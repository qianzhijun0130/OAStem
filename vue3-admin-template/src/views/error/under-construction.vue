<script setup lang="ts">
/**
 * 占位页：后端已配置菜单但前端页面尚未实现时展示。
 * 避免动态路由指向不存在的组件导致白屏或构建报错。
 */
</script>

<template>
  <el-card shadow="never">
    <el-empty description="该功能模块正在建设中，将在后续阶段交付" />
  </el-card>
</template>
