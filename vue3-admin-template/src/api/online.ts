import { request } from '@/utils/request'
import type { OnlineUserVO, PageResult } from './types'

/**
 * 在线用户分页。
 *
 * <p>数据来自 Redis 的会话快照（不是数据库），分页由后端在内存里完成——
 * 前端仍按普通分页接口使用，无需关心数据来源。</p>
 */
export function getOnlineUserPageApi(params: {
  keyword?: string
  pageNo?: number
  pageSize?: number
}) {
  return request<PageResult<OnlineUserVO>>({ url: '/system/online/page', method: 'get', params })
}

/** 强制下线：让该用户「此刻之前签发」的令牌立即失效 */
export function kickOnlineUserApi(userId: number) {
  return request<null>({ url: `/system/online/${userId}/kick`, method: 'post' })
}
