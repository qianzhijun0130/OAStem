import { request } from '@/utils/request'
import type { DeptSaveRequest, DeptVO } from './types'

/** 部门树（可按名称/状态过滤，后端会保留命中节点的祖先链） */
export function getDeptTreeApi(params?: { deptName?: string; status?: number }) {
  return request<DeptVO[]>({ url: '/system/dept/tree', method: 'get', params })
}

/** 新增或更新部门：无 id 为新增 */
export function saveDeptApi(data: DeptSaveRequest) {
  return request<number>({ url: '/system/dept', method: 'post', data })
}

/** 删除部门（存在下级或部门下有用户时后端会拒绝） */
export function deleteDeptApi(id: number) {
  return request<null>({ url: `/system/dept/${id}`, method: 'delete' })
}

/** 启用 / 禁用部门 */
export function updateDeptStatusApi(id: number, status: number) {
  return request<null>({ url: `/system/dept/${id}/status`, method: 'put', params: { status } })
}
