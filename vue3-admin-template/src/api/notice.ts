import { request } from '@/utils/request'
import type {
  MyNoticeDetailVO,
  MyNoticePageQuery,
  MyNoticeVO,
  NoticeDetailVO,
  NoticePageQuery,
  NoticeSaveRequest,
  NoticeVO,
  PageResult,
} from './types'

/* ========== 公告管理（发布者视角，需按钮权限） ========== */

export function getNoticePageApi(params: NoticePageQuery) {
  return request<PageResult<NoticeVO>>({ url: '/notice/page', method: 'get', params })
}

export function getNoticeDetailApi(id: number) {
  return request<NoticeDetailVO>({ url: `/notice/${id}`, method: 'get' })
}

export function saveNoticeApi(data: NoticeSaveRequest) {
  return request<number>({ url: '/notice', method: 'post', data })
}

export function publishNoticeApi(id: number) {
  return request<null>({ url: `/notice/${id}/publish`, method: 'put' })
}

export function offlineNoticeApi(id: number) {
  return request<null>({ url: `/notice/${id}/offline`, method: 'put' })
}

export function updateNoticeTopApi(id: number, topFlag: number) {
  return request<null>({ url: `/notice/${id}/top`, method: 'put', params: { topFlag } })
}

export function deleteNoticeApi(id: number) {
  return request<null>({ url: `/notice/${id}`, method: 'delete' })
}

/* ========== 我的公告（读者视角，可见性由后端按发布范围判定） ========== */

export function getMyNoticePageApi(params: MyNoticePageQuery) {
  return request<PageResult<MyNoticeVO>>({ url: '/notice/my/page', method: 'get', params })
}

/**
 * 打开详情即自动标记已读并累加阅读数，因此不要在「仅预览」的场景调用。
 */
export function getMyNoticeDetailApi(id: number) {
  return request<MyNoticeDetailVO>({ url: `/notice/my/${id}`, method: 'get' })
}

export function getMyNoticeUnreadCountApi() {
  return request<number>({ url: '/notice/my/unread-count', method: 'get' })
}

export function getLatestNoticeApi(limit = 5) {
  return request<MyNoticeVO[]>({ url: '/notice/my/latest', method: 'get', params: { limit } })
}
