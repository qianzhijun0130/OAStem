<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import type { MenuVO } from '@/api/types'

defineProps<{ collapsed: boolean }>()

const route = useRoute()
const userStore = useUserStore()

/**
 * 侧边栏只渲染目录（1）与菜单（2），按钮（3）不参与菜单展示。
 * 菜单树目前最多两级（目录 → 菜单），因此无需递归组件。
 */
const menuTree = computed(() => userStore.menus)

const activePath = computed(() => route.path)

function childrenOf(menu: MenuVO): MenuVO[] {
  return (menu.children ?? []).filter((child) => child.menuType === 2 && Boolean(child.path))
}
</script>

<template>
  <el-menu
    :default-active="activePath"
    :collapse="collapsed"
    :collapse-transition="false"
    router
    unique-opened
    background-color="#304156"
    text-color="#bfcbd9"
    active-text-color="#409eff"
  >
    <template v-for="menu in menuTree" :key="menu.id">
      <el-menu-item v-if="menu.menuType === 2 && menu.path" :index="menu.path">
        <el-icon v-if="menu.icon">
          <component :is="menu.icon" />
        </el-icon>
        <template #title>{{ menu.menuName }}</template>
      </el-menu-item>

      <el-sub-menu v-else-if="menu.menuType === 1 && childrenOf(menu).length > 0" :index="`dir-${menu.id}`">
        <template #title>
          <el-icon v-if="menu.icon">
            <component :is="menu.icon" />
          </el-icon>
          <span>{{ menu.menuName }}</span>
        </template>
        <el-menu-item v-for="child in childrenOf(menu)" :key="child.id" :index="child.path">
          <el-icon v-if="child.icon">
            <component :is="child.icon" />
          </el-icon>
          <template #title>{{ child.menuName }}</template>
        </el-menu-item>
      </el-sub-menu>
    </template>
  </el-menu>
</template>

<style scoped>
.el-menu {
  border-right: none;
}
</style>
