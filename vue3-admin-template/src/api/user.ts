import { request } from '@/utils/request'
import { downloadBlobUrl } from '@/utils/download'
import type {
  UserVO,
  UserSaveRequest,
  UserPageQuery,
  UserImportResultVO,
  PageResult,
} from './types'

/** 批量导入模板的文件名，下载失败时用作兜底 */
const IMPORT_TEMPLATE_NAME = '员工批量导入模板.xlsx'

/** 员工（用户）分页列表 */
export function getUserPageApi(params: UserPageQuery) {
  return request<PageResult<UserVO>>({ url: '/system/user/page', method: 'get', params })
}

/**
 * 候选用户列表（启用态），供审批转办 / 审批人 / 通知接收人选择。
 *
 * <p>不依赖 system:user:*（该权限仅 HR+超管持有），避免非 HR 角色在转办 / 指定接收人时 403。
 * 返回数组而非分页对象——候选清单不需要分页，也避开硬编码 pageSize 截断。</p>
 */
export function getUserCandidatesApi() {
  return request<UserVO[]>({ url: '/system/user/candidates', method: 'get' })
}

/** 新增或更新员工：无 id 为新增 */
export function saveUserApi(data: UserSaveRequest) {
  return request<number>({ url: '/system/user', method: 'post', data })
}

/** 启用 / 禁用 / 锁定员工 */
export function updateUserStatusApi(id: number, status: number) {
  return request<null>({ url: `/system/user/${id}/status`, method: 'put', params: { status } })
}

/** 重置密码为系统默认初始密码 */
export function resetUserPwdApi(id: number) {
  return request<null>({ url: `/system/user/${id}/reset-pwd`, method: 'put' })
}

/** 删除员工 */
export function deleteUserApi(id: number) {
  return request<null>({ url: `/system/user/${id}`, method: 'delete' })
}

/** 下载批量导入模板（含「填写说明」工作表） */
export function downloadUserImportTemplateApi() {
  return downloadBlobUrl('/system/user/import-template', IMPORT_TEMPLATE_NAME)
}

/**
 * 批量导入员工。
 *
 * <p>刻意不手工设置 Content-Type：multipart 的 boundary 必须由浏览器生成，
 * 手写会缺 boundary 导致后端解析不出文件。</p>
 *
 * <p>导入要逐行校验再批量写库，默认 10s 超时对大文件不够，这里放宽到 60s。</p>
 */
export function importUsersApi(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request<UserImportResultVO>({
    url: '/system/user/import',
    method: 'post',
    data: formData,
    timeout: 60_000,
  })
}
