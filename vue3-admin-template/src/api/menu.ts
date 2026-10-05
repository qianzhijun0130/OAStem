import { request } from '@/utils/request'
import type { MenuVO, MenuSaveRequest } from './types'

/** 全量菜单树（含目录/菜单/按钮），用于菜单管理与角色权限分配 */
export function getMenuTreeApi() {
  return request<MenuVO[]>({ url: '/system/menu/tree', method: 'get' })
}

/** 新增或更新菜单：无 id 为新增 */
export function saveMenuApi(data: MenuSaveRequest) {
  return request<number>({ url: '/system/menu', method: 'post', data })
}

/** 删除菜单（存在子节点时后端拒绝） */
export function deleteMenuApi(id: number) {
  return request<null>({ url: `/system/menu/${id}`, method: 'delete' })
}

/** 启用 / 禁用菜单 */
export function updateMenuStatusApi(id: number, status: number) {
  return request<null>({ url: `/system/menu/${id}/status`, method: 'put', params: { status } })
}
