import { request } from '@/utils/request'
import type { RoleVO, RoleOptionVO, RoleSaveRequest, RolePageQuery, PageResult } from './types'

/** 角色分页列表 */
export function getRolePageApi(params: RolePageQuery) {
  return request<PageResult<RoleVO>>({ url: '/system/role/page', method: 'get', params })
}

/** 角色下拉选项（用户表单等复用） */
export function getRoleOptionsApi() {
  return request<RoleOptionVO[]>({ url: '/system/role/options', method: 'get' })
}

/** 新增或更新角色：无 id 为新增 */
export function saveRoleApi(data: RoleSaveRequest) {
  return request<number>({ url: '/system/role', method: 'post', data })
}

/** 启用 / 禁用角色 */
export function updateRoleStatusApi(id: number, status: number) {
  return request<null>({ url: `/system/role/${id}/status`, method: 'put', params: { status } })
}

/** 删除角色 */
export function deleteRoleApi(id: number) {
  return request<null>({ url: `/system/role/${id}`, method: 'delete' })
}

/** 查询角色已分配的菜单ID集合 */
export function getRoleMenusApi(id: number) {
  return request<number[]>({ url: `/system/role/${id}/menus`, method: 'get' })
}

/** 分配角色菜单权限（全量覆盖） */
export function assignRoleMenusApi(id: number, menuIds: number[]) {
  return request<null>({ url: `/system/role/${id}/menus`, method: 'put', data: menuIds })
}

/**
 * 以源角色的菜单为基线，覆盖式克隆到目标角色。
 *
 * 系统没有角色继承机制，菜单必须逐角色独立勾选；克隆可避免漏勾基础菜单
 * （工作台、我的公告等）导致「权限给了但侧边栏缺节点」。语义为全量覆盖。
 */
export function cloneRoleMenusApi(id: number, sourceRoleId: number) {
  return request<null>({ url: `/system/role/${id}/menus/clone`, method: 'post', data: { sourceRoleId } })
}
