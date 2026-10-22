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
      <div class="oa-layout__logo">
        <span v-if="!collapsed">OA 管理系统</span>
        <span v-else>OA</span>
      </div>
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
  background-color: var(--oa-sidebar-bg);
  transition: width 0.28s;
  overflow: hidden;
}

.oa-layout__logo {
  height: var(--oa-header-height);
  line-height: var(--oa-header-height);
  text-align: center;
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  background-color: #2b3649;
  overflow: hidden;
  white-space: nowrap;
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
