import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import Layout from '@/layout/index.vue'

/**
 * 静态路由：不依赖用户权限，任何情况下都可访问。
 * 业务路由由后端菜单在登录后动态注册，见 router/dynamic.ts
 */
export const constantRoutes: RouteRecordRaw[] = [
  {
    /**
     * 根路径占位路由。
     *
     * <p>这里必须把 `/` **声明**出来，哪怕它自己没有固定落地页。</p>
     *
     * <p>原因：`/` 的真实目标取决于当前用户有权访问的第一个菜单（不同角色不同），
     * 由 permission.ts 的全局守卫按菜单树动态决定，无法在路由表里写死。
     * 但如果不声明，`/` 就会落到 router/dynamic.ts 注册的兜底路由
     * `/:pathMatch(.*)*` 上——而兜底路由带的是**路由级 `redirect: '/404'`**。
     * 路由级 redirect 在全局守卫**之前**执行，于是守卫拿到的 `to.path` 已经是 `/404`，
     * 里面那段 `to.path === '/'` 的分支永远不可达，表现为「登录成功后直接进 404」。</p>
     *
     * <p>组件用空渲染函数：守卫一定会把 `/` 重定向到真实落地页（或菜单为空时到 `/404`），
     * 正常情况下该组件不会被渲染。</p>
     */
    path: '/',
    name: 'Root',
    component: { render: () => null },
    meta: { title: '首页', hidden: true },
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', hidden: true },
  },
  {
    /**
     * 个人中心走静态路由而不是后端菜单。
     *
     * <p>原因：后端菜单树只下发 visible=1 的记录（见 MenuServiceImpl.getMenuTreeByUserId），
     * 想做成「侧边栏不显示、但顶栏能进」就必须让菜单可见性同时承担路由注册的职责，
     * 语义会变得含糊。个人中心对所有登录用户开放、也不需要按钮权限，
     * 放在这里与 /404 同级别最直接。</p>
     */
    path: '/profile',
    name: 'Profile',
    component: Layout,
    meta: { title: '个人中心', hidden: true },
    children: [
      {
        path: '',
        name: 'ProfilePage',
        component: () => import('@/views/profile/index.vue'),
        meta: { title: '个人中心' },
      },
    ],
  },
  {
    path: '/404',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '页面不存在', hidden: true },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes: constantRoutes,
  scrollBehavior: () => ({ top: 0 }),
})

export default router
