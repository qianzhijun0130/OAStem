<script setup lang="ts">
import { computed, ref } from 'vue'
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

/** 用户下拉是否展开：仅用于把右侧箭头做 180° 翻转 */
const userMenuOpen = ref(false)

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

const displayName = computed(() => userStore.displayName || '用户')
const initial = computed(() => displayName.value.charAt(0).toUpperCase())
const deptName = computed(() => userStore.userInfo?.deptName ?? '')

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
  <header class="oa-navbar">
    <!-- 左：收起/展开 + 面包屑 -->
    <div class="oa-navbar__left">
      <button
        type="button"
        class="oa-navbar__toggle"
        :title="collapsed ? '展开侧边栏' : '收起侧边栏'"
        :aria-label="collapsed ? '展开侧边栏' : '收起侧边栏'"
        @click="emit('toggle')"
      >
        <AppIcon :name="collapsed ? 'PanelLeftOpen' : 'PanelLeftClose'" :size="18" />
      </button>

      <span class="oa-navbar__split" aria-hidden="true"></span>

      <nav class="oa-navbar__crumb" aria-label="面包屑">
        <template v-for="(crumb, index) in crumbs" :key="`${crumb.name}-${index}`">
          <span class="oa-navbar__crumb-item">
            <AppIcon v-if="index === 0" name="House" :size="15" class="oa-navbar__crumb-home" />
            <router-link
              v-if="index < crumbs.length - 1 && crumb.path"
              :to="crumb.path"
              class="oa-navbar__crumb-link"
            >
              {{ crumb.name }}
            </router-link>
            <span
              v-else
              class="oa-navbar__crumb-text"
              :class="{ 'is-current': index === crumbs.length - 1 }"
            >
              {{ crumb.name }}
            </span>
          </span>
          <AppIcon
            v-if="index < crumbs.length - 1"
            name="ChevronRight"
            :size="13"
            class="oa-navbar__crumb-sep"
          />
        </template>
      </nav>
    </div>

    <!-- 右：用户信息 + 下拉菜单 -->
    <div class="oa-navbar__right">
      <el-dropdown trigger="click" placement="bottom-end" @visible-change="userMenuOpen = $event">
        <div class="oa-navbar__user" :class="{ 'is-open': userMenuOpen }">
          <el-avatar :size="32" :src="userStore.userInfo?.avatar" class="oa-navbar__avatar">
            {{ initial }}
          </el-avatar>
          <span class="oa-navbar__meta">
            <span class="oa-navbar__name">{{ displayName }}</span>
            <span v-if="deptName" class="oa-navbar__dept">{{ deptName }}</span>
          </span>
          <AppIcon name="ChevronDown" :size="15" class="oa-navbar__caret" />
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="goProfile">
              <span class="oa-navbar__menu-item">
                <AppIcon name="UserRound" :size="16" />
                个人中心
              </span>
            </el-dropdown-item>
            <el-dropdown-item divided @click="handleLogout">
              <span class="oa-navbar__menu-item is-danger">
                <AppIcon name="LogOut" :size="16" />
                退出登录
              </span>
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<style scoped>
/* ============================================================
 * Header：左侧「折叠键 + 面包屑」，右侧「用户胶囊」
 * 视觉基调 —— 白底 + 1px 描边（不用厚投影），靠间距与字重分层
 * ============================================================ */
.oa-navbar {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  height: 100%;
  padding: 0 20px;
  background-color: var(--oa-header-bg, #fff);
}

/* 底边左侧一小段品牌色渐变，压住 1px 灰线的单调，又不至于抢内容 */
.oa-navbar::after {
  position: absolute;
  right: 0;
  bottom: -1px;
  left: 0;
  height: 1px;
  pointer-events: none;
  content: '';
  background-image: linear-gradient(90deg, var(--oa-brand, #409eff) 0%, rgba(64, 158, 255, 0) 34%);
  opacity: 0.65;
}

/* ---------- 左区 ---------- */
.oa-navbar__left {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

/* 折叠键做成 34×34 的圆角芯片：既有明确的点击热区，hover 时才有反馈 */
.oa-navbar__toggle {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  padding: 0;
  color: #5b6474;
  cursor: pointer;
  background-color: transparent;
  border: none;
  border-radius: 10px;
  transition: background-color 0.18s, color 0.18s, transform 0.18s;
}

.oa-navbar__toggle:hover {
  color: var(--oa-brand, #409eff);
  background-color: #f2f6fd;
}

.oa-navbar__toggle:active {
  transform: scale(0.92);
}

.oa-navbar__toggle:focus-visible {
  outline: 2px solid rgba(64, 158, 255, 0.4);
  outline-offset: 1px;
}

/* 折叠键与面包屑之间的竖向分隔线，避免两个元素“粘”在一起 */
.oa-navbar__split {
  flex-shrink: 0;
  width: 1px;
  height: 16px;
  background-color: #e8ecf3;
}

/* ---------- 面包屑 ---------- */
.oa-navbar__crumb {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
}

.oa-navbar__crumb-item {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  gap: 5px;
  min-width: 0;
}

.oa-navbar__crumb-home {
  flex-shrink: 0;
  color: #aab3c2;
}

/* 上级：弱化的中性色，hover 才点亮品牌色——把视觉重量留给末项 */
.oa-navbar__crumb-link {
  font-size: 13px;
  color: #8a94a5;
  text-decoration: none;
  border-radius: 4px;
  transition: color 0.18s;
}

.oa-navbar__crumb-link:hover {
  color: var(--oa-brand, #409eff);
}

.oa-navbar__crumb-link:focus-visible {
  outline: 2px solid rgba(64, 158, 255, 0.4);
  outline-offset: 2px;
}

.oa-navbar__crumb-text {
  font-size: 13px;
  color: #8a94a5;
}

/* 当前页：唯一加粗变深的节点，一眼定位「我在哪」 */
.oa-navbar__crumb-text.is-current {
  font-size: 14px;
  font-weight: 600;
  color: #1f2a37;
}

.oa-navbar__crumb-sep {
  flex-shrink: 0;
  color: #c9d0da;
}

/* ---------- 右区 ---------- */
.oa-navbar__right {
  flex-shrink: 0;
}

/* 用户区整体是一个可点的胶囊，hover 才铺底色，不再是裸文字 */
.oa-navbar__user {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 4px 8px 4px 4px;
  cursor: pointer;
  border-radius: 999px;
  outline: none;
  transition: background-color 0.18s;
}

.oa-navbar__user:hover,
.oa-navbar__user.is-open {
  background-color: #f4f7fc;
}

/* 头像：品牌色渐变 + 双层描边（白色内圈 + 淡蓝外圈），小尺寸下也有质感 */
.oa-navbar__avatar {
  flex-shrink: 0;
  font-size: 14px;
  font-weight: 600;
  color: #fff;
  background-image: linear-gradient(135deg, #7fadff 0%, #3d7bfd 100%);
  box-shadow: 0 0 0 2px #fff, 0 0 0 3.5px rgba(64, 158, 255, 0.16);
}

/* 姓名/部门两行堆叠，比原来「姓名 + 部门标签」更紧凑，也不抢面包屑的视觉 */
.oa-navbar__meta {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
  line-height: 1.25;
}

.oa-navbar__name {
  max-width: 120px;
  overflow: hidden;
  font-size: 13.5px;
  font-weight: 600;
  color: #1f2a37;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.oa-navbar__dept {
  max-width: 120px;
  overflow: hidden;
  font-size: 11.5px;
  color: #98a2b3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.oa-navbar__caret {
  flex-shrink: 0;
  color: #9aa4b5;
  transition: transform 0.22s, color 0.18s;
}

.oa-navbar__user.is-open .oa-navbar__caret {
  color: var(--oa-brand, #409eff);
  transform: rotate(180deg);
}

/* ---------- 下拉项 ---------- */
.oa-navbar__menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.oa-navbar__menu-item.is-danger {
  color: var(--el-color-danger, #f56c6c);
}

/* ---------- 响应式：先丢部门，再丢姓名，面包屑保持可用 ---------- */
@media (max-width: 1024px) {
  .oa-navbar {
    padding: 0 14px;
  }

  .oa-navbar__dept {
    display: none;
  }
}

@media (max-width: 640px) {
  .oa-navbar__name,
  .oa-navbar__split {
    display: none;
  }

  .oa-navbar__user {
    padding: 4px;
  }
}
</style>
