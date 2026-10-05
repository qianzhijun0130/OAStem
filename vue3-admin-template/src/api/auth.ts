import { request } from '@/utils/request'
import type { LoginRequest, LoginResponse, MenuVO, UserInfo } from './types'

/** 登录 */
export function loginApi(data: LoginRequest) {
  return request<LoginResponse>({ url: '/auth/login', method: 'post', data })
}

/** 获取当前登录用户的基本信息、角色与权限码 */
export function getUserInfoApi() {
  return request<UserInfo>({ url: '/auth/info', method: 'get' })
}

/** 获取当前用户的菜单树 */
export function getMenusApi() {
  return request<MenuVO[]>({ url: '/auth/menus', method: 'get' })
}

/** 登出（JWT 无状态，服务端仅清理上下文，前端需自行丢弃令牌） */
export function logoutApi() {
  return request<null>({ url: '/auth/logout', method: 'post' })
}
