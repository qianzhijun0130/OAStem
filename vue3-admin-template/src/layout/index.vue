<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import Navbar from './components/Navbar.vue'
import Sidebar from './components/Sidebar.vue'

const collapsed = ref(false)

// 响应式骨架：窄屏（<768px，如平板/手机）自动收起侧边栏，避免挤占内容区；
// 宽屏恢复展开。手动点击汉堡键仍可临时切换，下次 resize 会按视口重新校准。
function syncCollapseByWidth() {
  collapsed.value = window.innerWidth < 768
}

let resizeHandler: (() => void) | null = null
onMounted(() => {
  syncCollapseByWidth()
  resizeHandler = () => syncCollapseByWidth()
  window.addEventListener('resize', resizeHandler)
})
onBeforeUnmount(() => {
  if (resizeHandler) window.removeEventListener('resize', resizeHandler)
})
</script>

<template>
  <el-container class="oa-layout">
    <el-aside class="oa-layout__aside" :width="collapsed ? '64px' : '210px'">
      <Sidebar :collapsed="collapsed" />
    </el-aside>

    <el-container>
      <el-header class="oa-layout__header">
        <Navbar :collapsed="collapsed" @toggle="collapsed = !collapsed" />
      </el-header>
      <el-main class="oa-layout__main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.oa-layout {
  height: 100%;
}

.oa-layout__aside {
  /* 深色纵向渐变替代原来的纯色块：上深下浅，与白底 Header 形成干净的明暗分界 */
  background-color: var(--oa-sidebar-bg);
  background-image: var(--oa-sidebar-grad);
  transition: width 0.28s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
}

.oa-layout__header {
  position: relative;
  z-index: 10;
  height: var(--oa-header-height);
  padding: 0;
  background-color: var(--oa-header-bg);
  /* 用 1px 描边替代原来的厚投影：与内容区分离但不发灰，且不压住下方卡片 */
  border-bottom: 1px solid var(--oa-header-border);
}

.oa-layout__main {
  padding: 16px;
  background-color: #f0f2f5;
  overflow-y: auto;
}
</style>
