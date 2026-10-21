import { request } from '@/utils/request'
import type {
  ApprovalCcVO,
  ApprovalInstanceVO,
  ApprovalTodoVO,
  CcUserVO,
  ApprovalRecordVO,
  ApprovalDetailVO,
  ApprovalStartRequest,
  ApprovalCompleteRequest,
  ApprovalTransferRequest,
  ApprovalInstancePageQuery,
  QueryPage,
  PageResult,
} from './types'

/** 发起审批 */
export function startApprovalApi(data: ApprovalStartRequest) {
  return request<number>({ url: '/workflow/approval/start', method: 'post', data })
}

/** 保存草稿（不启动流程，可在「我的流程」继续提交） */
export function saveDraftApi(data: ApprovalStartRequest) {
  return request<number>({ url: '/workflow/approval/draft', method: 'post', data })
}

/** 更新草稿内容（仅本人、仅草稿） */
export function updateDraftApi(instanceId: number, data: ApprovalStartRequest) {
  return request<null>({ url: `/workflow/approval/draft/${instanceId}`, method: 'put', data })
}

/** 提交草稿 */
export function submitDraftApi(instanceId: number) {
  return request<null>({ url: `/workflow/approval/${instanceId}/submit`, method: 'post' })
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

/** 流程实例全量分页（管理员视角，多条件检索） */
export function getInstancesApi(params: ApprovalInstancePageQuery) {
  return request<PageResult<ApprovalInstanceVO>>({ url: '/workflow/approval/page', method: 'get', params })
}

/** 强制终止审批中的流程实例 */
export function terminateInstanceApi(instanceId: number) {
  return request<null>({
    url: '/workflow/approval/terminate',
    method: 'post',
    params: { instanceId },
  })
}

/** 挂起审批中的流程实例（任务暂时退出待办） */
export function suspendInstanceApi(instanceId: number) {
  return request<null>({
    url: '/workflow/approval/suspend',
    method: 'post',
    params: { instanceId },
  })
}

/** 恢复已挂起的流程实例（任务重新回到待办） */
export function resumeInstanceApi(instanceId: number) {
  return request<null>({
    url: '/workflow/approval/resume',
    method: 'post',
    params: { instanceId },
  })
}

/** 抄送审批单给若干人（仅告知，不产生待办） */
export function ccInstanceApi(data: { instanceId: number; userIds: number[]; remark?: string }) {
  return request<null>({ url: '/workflow/approval/cc', method: 'post', data })
}

/** 我的抄送任务分页（readFlag 为空表示全部，0 未读、1 已读） */
export function getCcPageApi(params: { readFlag?: number; pageNo?: number; pageSize?: number }) {
  return request<PageResult<ApprovalCcVO>>({
    url: '/workflow/approval/cc/page',
    method: 'get',
    params,
  })
}

/** 抄送人候选（仅启用用户） */
export function getCcUsersApi() {
  return request<CcUserVO[]>({ url: '/workflow/approval/cc/users', method: 'get' })
}

/** 标记单条抄送已读 */
export function readCcApi(ccId: number) {
  return request<null>({ url: '/workflow/approval/cc/read', method: 'post', params: { ccId } })
}

/** 我的抄送全部标记已读 */
export function readAllCcApi() {
  return request<null>({ url: '/workflow/approval/cc/read-all', method: 'post' })
}

/** 删除流程实例（逻辑删除） */
export function deleteInstanceApi(id: number) {
  return request<null>({ url: `/workflow/approval/${id}`, method: 'delete' })
}
