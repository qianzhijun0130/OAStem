import { request } from '@/utils/request'
import { downloadFile } from '@/utils/download'
import type {
  LoginLogPageQuery,
  LoginLogVO,
  OperationLogPageQuery,
  OperationLogVO,
  PageResult,
} from './types'

/* ========== 登录日志 ========== */

export function getLoginLogPageApi(params: LoginLogPageQuery) {
  return request<PageResult<LoginLogVO>>({ url: '/system/login-log/page', method: 'get', params })
}

export function exportLoginLogApi(params: LoginLogPageQuery) {
  return downloadFile('/system/login-log/export', { ...params }, '登录日志.xlsx')
}

/**
 * 清理Login日志。
 *
 * @param before 清理截止时间（不含）；all=true 时后端忽略此值
 * @param all    是否全量清理。仅超级管理员可用；本次操作自身的审计日志不会被清除
 */
export function cleanLoginLogApi(before: string | undefined, all = false) {
  return request<number>({
    url: '/system/login-log/clean',
    method: 'delete',
    params: all ? { all: true } : { before },
  })
}

/* ========== 操作日志 ========== */

export function getOperationLogPageApi(params: OperationLogPageQuery) {
  return request<PageResult<OperationLogVO>>({ url: '/system/operation-log/page', method: 'get', params })
}

export function exportOperationLogApi(params: OperationLogPageQuery) {
  return downloadFile('/system/operation-log/export', { ...params }, '操作日志.xlsx')
}

/**
 * 清理Operation日志。
 *
 * @param before 清理截止时间（不含）；all=true 时后端忽略此值
 * @param all    是否全量清理。仅超级管理员可用；本次操作自身的审计日志不会被清除
 */
export function cleanOperationLogApi(before: string | undefined, all = false) {
  return request<number>({
    url: '/system/operation-log/clean',
    method: 'delete',
    params: all ? { all: true } : { before },
  })
}
