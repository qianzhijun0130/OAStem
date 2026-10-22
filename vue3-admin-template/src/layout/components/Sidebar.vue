<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import AppIcon from '@/components/icons/AppIcon.vue'
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
  <div class="oa-sidebar">
    <!-- 品牌区：渐变 logo mark + 名称，收起时只留 mark -->
    <div class="oa-sidebar__brand" :class="{ 'is-collapsed': collapsed }">
      <span class="oa-sidebar__mark">OA</span>
      <span v-show="!collapsed" class="oa-sidebar__brand-name">企业办公管理系统</span>
    </div>

    <!-- 菜单区独立滚动：菜单项多时不会把品牌区顶走 -->
    <nav class="oa-sidebar__nav">
      <el-menu
        class="oa-sidebar__menu"
        :default-active="activePath"
        :collapse="collapsed"
        :collapse-transition="false"
        router
        unique-opened
      >
        <template v-for="menu in menuTree" :key="menu.id">
          <el-menu-item v-if="menu.menuType === 2 && menu.path" :index="menu.path">
            <el-icon v-if="menu.icon">
              <AppIcon :name="menu.icon" :size="18" />
            </el-icon>
            <template #title>
              <span class="oa-sidebar__label">{{ menu.menuName }}</span>
            </template>
          </el-menu-item>

          <el-sub-menu v-else-if="menu.menuType === 1 && childrenOf(menu).length > 0" :index="`dir-${menu.id}`">
            <template #title>
              <el-icon v-if="menu.icon">
                <AppIcon :name="menu.icon" :size="18" />
              </el-icon>
              <span class="oa-sidebar__label">{{ menu.menuName }}</span>
            </template>
            <el-menu-item v-for="child in childrenOf(menu)" :key="child.id" :index="child.path">
              <el-icon v-if="child.icon">
                <AppIcon :name="child.icon" :size="17" />
              </el-icon>
              <template #title>
                <span class="oa-sidebar__label">{{ child.menuName }}</span>
              </template>
            </el-menu-item>
          </el-sub-menu>
        </template>
      </el-menu>
    </nav>
  </div>
</template>

<style scoped>
/* ============================================================
 * 侧边栏：深色纵向渐变 + 圆角胶囊式菜单项
 * 层级靠「亮度」区分 —— 默认 #9fb0c6，hover 提亮，激活项白字 + 蓝色渐变底
 * ============================================================ */
.oa-sidebar {
  display: flex;
  flex-direction: column;
  height: 100%;
}

/* ---------- 品牌区 ---------- */
.oa-sidebar__brand {
  display: flex;
  flex-shrink: 0;
  gap: 10px;
  align-items: center;
  height: var(--oa-header-height);
  padding: 0 16px;
  overflow: hidden;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.oa-sidebar__brand.is-collapsed {
  justify-content: center;
  padding: 0;
}

/* logo mark：品牌渐变方块 + 字标。刻意不用菜单图标，避免与「流程管理」等图标撞脸 */
.oa-sidebar__mark {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  font-size: 12px;
  font-weight: 700;
  color: #fff;
  letter-spacing: 0.2px;
  background-image: linear-gradient(135deg, #6aa8ff 0%, #2f6ff5 100%);
  border-radius: 9px;
  box-shadow: 0 4px 10px rgba(47, 111, 245, 0.35);
}

.oa-sidebar__brand-name {
  overflow: hidden;
  font-size: 15px;
  font-weight: 600;
  color: #fff;
  letter-spacing: 0.4px;
  white-space: nowrap;
}

/* ---------- 菜单容器（独立滚动） ---------- */
.oa-sidebar__nav {
  flex: 1;
  min-height: 0;
  padding: 10px 8px 14px;
  overflow-x: hidden;
  overflow-y: auto;
  scrollbar-color: rgba(255, 255, 255, 0.18) transparent;
  scrollbar-width: thin;
}

.oa-sidebar__nav::-webkit-scrollbar {
  width: 6px;
}

.oa-sidebar__nav::-webkit-scrollbar-track {
  background: transparent;
}

.oa-sidebar__nav::-webkit-scrollbar-thumb {
  background-color: rgba(255, 255, 255, 0.16);
  border-radius: 3px;
}

.oa-sidebar__nav::-webkit-scrollbar-thumb:hover {
  background-color: rgba(255, 255, 255, 0.3);
}

/* ---------- 菜单：用 EP 的 CSS 变量接管配色，替代原来写死的 3 个 props ---------- */
.oa-sidebar__menu {
  --el-menu-bg-color: transparent;
  --el-menu-text-color: var(--oa-sidebar-text, #9fb0c6);
  --el-menu-hover-text-color: #e9f0fa;
  --el-menu-hover-bg-color: rgba(255, 255, 255, 0.055);
  --el-menu-active-color: #fff;
  --el-menu-item-height: 44px;
  --el-menu-sub-item-height: 40px;
  --el-menu-base-level-padding: 14px;
  --el-menu-level-padding: 18px;
  --el-menu-icon-width: 20px;
  border-right: none;
  background: transparent;
}

.oa-sidebar__label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 一级项：圆角 + 上下留白，形成「一项一颗」的胶囊观感 */
.oa-sidebar__menu :deep(.el-menu-item),
.oa-sidebar__menu :deep(.el-sub-menu__title) {
  height: 44px;
  margin: 2px 0;
  border-radius: 9px;
  font-size: 14px;
  transition: background-color 0.18s, color 0.18s;
}

/* 图标与文字之间留出比 EP 默认(5px)更舒展的间距 */
.oa-sidebar__menu :deep(.el-menu-item > .el-icon),
.oa-sidebar__menu :deep(.el-sub-menu__title > .el-icon) {
  margin-right: 10px;
  font-size: 18px;
}

/* 子菜单箭头：弱化，给标题让位 */
.oa-sidebar__menu :deep(.el-sub-menu__icon-arrow) {
  right: 12px;
  color: #71839a;
  transition: transform 0.22s, color 0.18s;
}

.oa-sidebar__menu :deep(.el-sub-menu.is-opened > .el-sub-menu__title .el-sub-menu__icon-arrow) {
  color: #9fb0c6;
}

/* 有子项处于激活态的分组标题：提亮但不加蓝，避免和激活项抢注意力 */
.oa-sidebar__menu :deep(.el-sub-menu.is-active > .el-sub-menu__title) {
  color: #e9f0fa;
}

/* 激活项：蓝色渐变底 + 左侧 3px 高光条，取代原来只有蓝字的弱提示 */
.oa-sidebar__menu :deep(.el-menu-item.is-active) {
  position: relative;
  font-weight: 600;
  color: #fff;
  background-image: linear-gradient(90deg, rgba(64, 158, 255, 0.32) 0%, rgba(64, 158, 255, 0.08) 100%);
}

.oa-sidebar__menu :deep(.el-menu-item.is-active)::before {
  position: absolute;
  top: 50%;
  left: 0;
  width: 3px;
  height: 18px;
  content: '';
  background-image: linear-gradient(180deg, #8ec2ff 0%, #3d7bfd 100%);
  border-radius: 0 3px 3px 0;
  transform: translateY(-50%);
}

.oa-sidebar__menu :deep(.el-menu-item.is-active:hover) {
  background-image: linear-gradient(90deg, rgba(64, 158, 255, 0.38) 0%, rgba(64, 158, 255, 0.12) 100%);
}

/* 子菜单展开区：一块更深的凹陷面板，把「子级」和内层空间说清楚 */
.oa-sidebar__menu :deep(.el-menu--inline) {
  padding: 4px;
  margin: 2px 0 4px;
  background-color: rgba(0, 0, 0, 0.18);
  border-radius: 10px;
}

/* 横向不留边距：激活项的左侧高光条要贴着胶囊左缘，留边距会变成一条飘在外面的细线 */
.oa-sidebar__menu :deep(.el-menu--inline .el-menu-item) {
  height: 40px;
  margin: 1px 0;
  border-radius: 7px;
  font-size: 13.5px;
}

/* ---------- 收起态 ---------- */
/* 收起后 EP 把菜单宽算成 icon-width + padding*2 = 48px，正好铺满 64px 侧栏的内边距 */
.oa-sidebar__menu.el-menu--collapse :deep(.el-menu-item),
.oa-sidebar__menu.el-menu--collapse :deep(.el-sub-menu__title) {
  justify-content: center;
  margin: 4px 0;
}

/* 收起态改用整块圆角高亮：这个尺寸下左侧高光条会显得挤 */
.oa-sidebar__menu.el-menu--collapse :deep(.el-menu-item.is-active)::before {
  display: none;
}

.oa-sidebar__menu.el-menu--collapse :deep(.el-menu-item.is-active) {
  background-image: linear-gradient(135deg, rgba(64, 158, 255, 0.42) 0%, rgba(64, 158, 255, 0.2) 100%);
}

/* 收起后子项不可见，必须由父级图标承担「我在这组里」的提示，否则用户完全失去位置感 */
.oa-sidebar__menu.el-menu--collapse :deep(.el-sub-menu.is-active > .el-sub-menu__title) {
  color: #fff;
  background-image: linear-gradient(135deg, rgba(64, 158, 255, 0.42) 0%, rgba(64, 158, 255, 0.2) 100%);
}

.oa-sidebar__menu.el-menu--collapse :deep(.el-menu-item > .el-icon),
.oa-sidebar__menu.el-menu--collapse :deep(.el-sub-menu__title > .el-icon) {
  margin-right: 0;
}
</style>
