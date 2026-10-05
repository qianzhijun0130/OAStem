import { request } from '@/utils/request'
import type {
  ApprovalInstanceVO,
  ApprovalTodoVO,
  ApprovalRecordVO,
  ApprovalDetailVO,
  ApprovalStartRequest,
  ApprovalCompleteRequest,
  ApprovalTransferRequest,
  QueryPage,
  PageResult,
} from './types'

/** 发起审批 */
export function startApprovalApi(data: ApprovalStartRequest) {
  return request<number>({ url: '/workflow/approval/start', method: 'post', data })
}

/** 我的待办（Flowable 当前候选人任务） */
export function getTodoApi(params: QueryPage) {
  return request<PageResult<ApprovalTodoVO>>({ url: '/workflow/approval/todo', method: 'get', params })
}

/** 我的已办（本人处理过的审批动作） */
export function getDoneApi(params: QueryPage) {
  return request<PageResult<ApprovalRecordVO>>({ url: '/workflow/approval/done', method: 'get', params })
}

/** 我的发起 */
export function getMineApi(params: QueryPage & { status?: number }) {
  return request<PageResult<ApprovalInstanceVO>>({ url: '/workflow/approval/mine', method: 'get', params })
}

/** 同意 / 驳回 */
export function completeApprovalApi(data: ApprovalCompleteRequest) {
  return request<null>({ url: '/workflow/approval/complete', method: 'post', data })
}

/** 撤销申请（仅发起人，且审批中） */
export function withdrawApprovalApi(instanceId: number) {
  return request<null>({
    url: '/workflow/approval/withdraw',
    method: 'post',
    params: { instanceId },
  })
}

/** 转办给他人 */
export function transferApprovalApi(data: ApprovalTransferRequest) {
  return request<null>({ url: '/workflow/approval/transfer', method: 'post', data })
}

/** 审批详情（含流转时间轴） */
export function getApprovalDetailApi(id: number) {
  return request<ApprovalDetailVO>({ url: `/workflow/approval/${id}/detail`, method: 'get' })
}
