import { request } from '@/utils/request'
import type { DashboardOverviewVO } from './types'

/**
 * 工作台首屏聚合数据。
 *
 * 卡片统计、图表与最新公告一次返回，避免首屏串行发起多个请求。
 */
export function getDashboardOverviewApi() {
  return request<DashboardOverviewVO>({ url: '/dashboard/overview', method: 'get' })
}
