import { defineStore } from 'pinia'
import { getMenusApi, getUserInfoApi, loginApi, logoutApi } from '@/api/auth'
import type { LoginRequest, MenuVO, UserInfo } from '@/api/types'
import router from '@/router'
import { buildDynamicRoutes } from '@/router/dynamic'

const TOKEN_KEY = 'oa_token'

interface UserState {
  token: string
  userInfo: UserInfo | null
  menus: MenuVO[]
  /** 动态路由是否已注册，避免重复调用 addRoute */
  loaded: boolean
  /** 已注册的动态路由名称，登出时需要逐个移除 */
  dynamicRouteNames: string[]
}

export const useUserStore = defineStore('user', {
  state: (): UserState => ({
    token: localStorage.getItem(TOKEN_KEY) ?? '',
    userInfo: null,
    menus: [],
    loaded: false,
    dynamicRouteNames: [],
  }),

  getters: {
    roles: (state): string[] => state.userInfo?.roles ?? [],
    permissions: (state): string[] => state.userInfo?.permissions ?? [],
    displayName: (state): string => state.userInfo?.realName || state.userInfo?.username || '',
    isLogin: (state): boolean => Boolean(state.token),
  },

  actions: {
    async login(data: LoginRequest) {
      const res = await loginApi(data)
      this.token = res.token
      localStorage.setItem(TOKEN_KEY, res.token)
    },

    /**
     * 拉取用户信息与菜单，并注册动态路由。
     * 两个接口无依赖关系，用 Promise.all 并行请求以减少首屏等待。
     */
    async loadUserInfo() {
      const [userInfo, menus] = await Promise.all([getUserInfoApi(), getMenusApi()])
      this.userInfo = userInfo
      this.menus = menus

      const routes = buildDynamicRoutes(menus)
      for (const route of routes) {
        if (route.name) {
          this.dynamicRouteNames.push(String(route.name))
        }
        router.addRoute(route)
      }
      this.loaded = true
    },

    async logout() {
      try {
        await logoutApi()
      } catch {
        // 登出接口失败也要清理本地状态，不能把用户卡在系统中
      }
      this.resetState()
      router.push('/login')
    },

    /** 令牌失效时被请求拦截器调用，不发起登出请求（令牌已无效） */
    forceLogout() {
      this.resetState()
      router.push('/login')
    },

    resetState() {
      for (const name of this.dynamicRouteNames) {
        router.removeRoute(name)
      }
      this.dynamicRouteNames = []
      this.token = ''
      this.userInfo = null
      this.menus = []
      this.loaded = false
      localStorage.removeItem(TOKEN_KEY)
    },
  },
})
