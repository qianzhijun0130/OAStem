<script setup lang="ts">
import { computed } from 'vue'
import { ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import AppIcon from '@/components/icons/AppIcon.vue'
import type { MenuVO } from '@/api/types'

defineProps<{ collapsed: boolean }>()
const emit = defineEmits<{ toggle: [] }>()

const userStore = useUserStore()
const router = useRouter()
const route = useRoute()

interface Crumb {
  name: string
  /** 有 path 且不是最后一项时才可点击 */
  path?: string
}

/**
 * 在菜单树里按路由 path 找节点，返回「从根到该节点」的整条链。
 *
 * <p>面包屑的数据源必须是菜单树而不是路由表：目录（menuType=1）本身不生成路由，
 * 但它恰恰是面包屑里的「顶级菜单」那一层，只从路由表拼是拿不到的。</p>
 */
function findChain(list: MenuVO[], target: string, trail: MenuVO[] = []): MenuVO[] | null {
  for (const menu of list) {
    const chain = [...trail, menu]
    if (menu.path === target) {
      return chain
    }
    if (menu.children?.length) {
      const hit = findChain(menu.children, target, chain)
      if (hit) {
        return hit
      }
    }
  }
  return null
}

/** 摊平菜单树，每项带「从根到自身」的链 */
function flatten(list: MenuVO[], trail: MenuVO[] = []): MenuVO[][] {
  const out: MenuVO[][] = []
  for (const menu of list) {
    const chain = [...trail, menu]
    out.push(chain)
    if (menu.children?.length) {
      out.push(...flatten(menu.children, chain))
    }
  }
  return out
}

/**
 * 当前页对应的菜单链。
 *
 * <p>先按 `route.path` 精确匹配；匹配不到再退回「最长前缀」——隐藏路由（如
 * `/workflow/apply/:flowCode` 填单页）不在菜单里，但它的父级菜单在，
 * 靠前缀才能挂到正确的顶级菜单下。</p>
 */
const menuChain = computed<MenuVO[]>(() => {
  const path = route.path
  const exact = findChain(userStore.menus, path)
  if (exact) {
    return exact
  }
  const prefixHits = flatten(userStore.menus)
    .filter((chain) => {
      const menuPath = chain[chain.length - 1].path
      return menuPath && path.startsWith(`${menuPath}/`)
    })
    .sort((a, b) => (b[b.length - 1].path?.length ?? 0) - (a[a.length - 1].path?.length ?? 0))
  return prefixHits[0] ?? []
})

/** 面包屑：工作台 > 顶级菜单 > 子菜单（目录不可点，末项不可点） */
const crumbs = computed<Crumb[]>(() => {
  const items: Crumb[] = [{ name: '工作台', path: '/dashboard' }]

  for (const menu of menuChain.value) {
    // 按钮（menuType=3）不进面包屑：它不是一个「页面」
    if (menu.menuType === 3) {
      continue
    }
    items.push({
      name: menu.menuName,
      // 目录（menuType=1）有 path 但没有注册路由，给了会点出 404，只作纯文本展示
      path: menu.menuType === 2 ? menu.path ?? undefined : undefined,
    })
  }

  // 隐藏子页（如填单页）：菜单链只到父级，补一个当前页名
  const last = menuChain.value[menuChain.value.length - 1]
  const title = route.meta.title as string | undefined
  if (title && last?.path !== route.path) {
    items.push({ name: title })
  }

  // 去掉相邻重名（历史上存在「表单设计」目录下再挂「表单设计」页的结构）
  const deduped = items.filter((item, i) => i === 0 || item.name !== items[i - 1].name)
  return deduped.length ? deduped : [{ name: '工作台' }]
})

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  await userStore.logout()
}

function goProfile() {
  router.push('/profile')
}
</script>

<template>
  <div class="oa-navbar">
    <div class="oa-navbar__left">
      <el-icon class="oa-navbar__toggle" :size="20" @click="emit('toggle')">
        <AppIcon :name="collapsed ? 'PanelLeftOpen' : 'PanelLeftClose'" :size="20" />
      </el-icon>
      <el-breadcrumb class="oa-navbar__crumb" separator="/">
        <el-breadcrumb-item
          v-for="(crumb, index) in crumbs"
          :key="`${crumb.name}-${index}`"
          :to="index < crumbs.length - 1 && crumb.path ? { path: crumb.path } : undefined"
        >
          <span :class="{ 'oa-navbar__crumb-current': index === crumbs.length - 1 }">
            {{ crumb.name }}
          </span>
        </el-breadcrumb-item>
      </el-breadcrumb>
    </div>

    <div class="oa-navbar__right">
      <el-dropdown trigger="click">
        <span class="oa-navbar__user">
          <el-avatar :size="28" :src="userStore.userInfo?.avatar">
            {{ userStore.displayName.charAt(0) }}
          </el-avatar>
          <span class="oa-navbar__name">{{ userStore.displayName }}</span>
          <el-tag v-if="userStore.userInfo?.deptName" size="small" type="info">
            {{ userStore.userInfo.deptName }}
          </el-tag>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="goProfile">个人中心</el-dropdown-item>
            <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<style scoped>
.oa-navbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 100%;
  padding: 0 16px;
}

.oa-navbar__left {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.oa-navbar__toggle {
  cursor: pointer;
  color: #303133;
  flex-shrink: 0;
}

/* 面包屑可能很长（顶级菜单 + 子菜单名都不短），窄屏时整体省略而不是撑破 Header */
.oa-navbar__crumb {
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

/* 末项加粗表示「当前所在页面」，与可点的上级区分开 */
.oa-navbar__crumb-current {
  font-weight: 600;
  color: #303133;
}

.oa-navbar__right {
  flex-shrink: 0;
}

.oa-navbar__user {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  outline: none;
}

.oa-navbar__name {
  font-size: 14px;
  color: #303133;
}
</style>
