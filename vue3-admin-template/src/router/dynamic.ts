import type { RouteRecordRaw } from 'vue-router'
import Layout from '@/layout/index.vue'
import type { MenuVO } from '@/api/types'

/**
 * 扫描 views 目录下的所有页面组件，用于把后端返回的 component 字符串映射为真实组件。
 * 路径需与后端 oa_menu.component 字段保持一致（如 system/user/index）。
 */
const viewModules = import.meta.glob('/src/views/**/*.vue')

/**
 * 页面尚未实现时的占位组件。
 *
 * 后端可以先配置好菜单（第二阶段、第三阶段的页面），前端不会因组件缺失而白屏。
 */
const UnderConstruction = () => import('@/views/error/under-construction.vue')

function resolveComponent(component?: string) {
  if (!component) {
    return UnderConstruction
  }
  const key = `/src/views/${component}.vue`
  const loader = viewModules[key]
  return loader ? (loader as () => Promise<unknown>) : UnderConstruction
}

/**
 * 把后端菜单树转换为前端路由。
 *
 * <p>采用「Layout + 单页」两级结构：每个菜单项注册为顶层路由，其下挂一个
 * path 为空串的子路由承载真实页面。这样侧边栏在页面切换时不会重新渲染，
 * 也避免了父子路径拼接产生的歧义。</p>
 *
 * <p>只有 menuType=2（菜单）会生成路由；1（目录）与 3（按钮）不生成，
 * 目录仅用于侧边栏分组，按钮仅用于权限判断。</p>
 */
export function buildDynamicRoutes(menus: MenuVO[]): RouteRecordRaw[] {
  const routes: RouteRecordRaw[] = []

  const walk = (list: MenuVO[]) => {
    for (const menu of list) {
      if (menu.menuType === 2 && menu.path) {
        routes.push({
          path: menu.path,
          name: menu.menuCode,
          component: Layout,
          redirect: undefined,
          meta: { title: menu.menuName, icon: menu.icon, code: menu.menuCode },
          children: [
            {
              path: '',
              name: `${menu.menuCode}__page`,
              component: resolveComponent(menu.component),
              meta: { title: menu.menuName, icon: menu.icon, code: menu.menuCode },
            },
          ],
        })
      }
      if (menu.children && menu.children.length > 0) {
        walk(menu.children)
      }
    }
  }

  walk(menus)

  // 兜底路由必须放在最后，否则会在动态路由生效前就把未知路径导向 404。
  // 必须带 name：登出时 resetState 依赖 name 移除路由，否则每次登录都会重复注册一条。
  //
  // ⚠️ 这里用的是「路由级 redirect」，它在全局守卫(permission.ts)**之前**执行。
  // 因此任何「需要在守卫里分支处理」的路径都必须先在 router/index.ts 的
  // constantRoutes 里显式声明（如 `/`），否则会被本兜底路由抢先匹配并改写成 /404，
  // 守卫拿到的 to.path 已经是 /404，分支永远不可达。
  routes.push({
    path: '/:pathMatch(.*)*',
    name: 'CatchAll',
    redirect: '/404',
    meta: { hidden: true },
  })

  return routes
}
