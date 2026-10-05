<script setup lang="ts">
import { ref } from 'vue'
import Navbar from './components/Navbar.vue'
import Sidebar from './components/Sidebar.vue'

const collapsed = ref(false)
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
  height: var(--oa-header-height);
  padding: 0;
  background-color: #fff;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
}

.oa-layout__main {
  padding: 16px;
  background-color: #f0f2f5;
  overflow-y: auto;
}
</style>
