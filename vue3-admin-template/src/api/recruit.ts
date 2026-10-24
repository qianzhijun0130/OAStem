import { request } from '@/utils/request'
import type { PageResult } from '@/components/list/types'

/* ================================================================== */
/* 招聘模块：需求 / 职位 / 候选人 / 面试 / Offer / 看板                      */
/* ================================================================== */

/* ----------------------------- 选项常量 ----------------------------- */

/** 候选人阶段：1待筛选 2初试 3复试 4终试 5待定 6已发offer 7已入职 8已淘汰 9已放弃 */
export const RECRUIT_STAGE_OPTIONS = [
  { value: 1, label: '待筛选' },
  { value: 2, label: '初试' },
  { value: 3, label: '复试' },
  { value: 4, label: '终试' },
  { value: 5, label: '待定' },
  { value: 6, label: '已发offer' },
  { value: 7, label: '已入职' },
  { value: 8, label: '已淘汰' },
  { value: 9, label: '已放弃' },
] as const

export const RECRUIT_SOURCE_OPTIONS = [
  { value: 1, label: '内推' },
  { value: 2, label: '招聘平台' },
  { value: 3, label: '猎头' },
  { value: 4, label: '校招' },
  { value: 5, label: '主动投递' },
] as const

export const RECRUIT_REJECT_TYPE_OPTIONS = [
  { value: 1, label: '技能不匹配' },
  { value: 2, label: '经验不足' },
  { value: 3, label: '学历不符' },
  { value: 4, label: '薪资不匹配' },
  { value: 5, label: '文化不匹配' },
  { value: 6, label: '候选人放弃' },
] as const

export const RECRUIT_REASON_TYPE_OPTIONS = [
  { value: 1, label: '新增' },
  { value: 2, label: '替补' },
  { value: 3, label: '储备' },
] as const

export const RECRUIT_URGENCY_OPTIONS = [
  { value: 1, label: '普通' },
  { value: 2, label: '紧急' },
] as const

export const RECRUIT_CHANNEL_OPTIONS = [
  { value: 1, label: '内推' },
  { value: 2, label: '官网' },
  { value: 3, label: '招聘平台' },
] as const

export const RECRUIT_POST_STATUS_OPTIONS = [
  { value: 1, label: '草稿' },
  { value: 2, label: '已发布' },
  { value: 3, label: '已下线' },
] as const

export const RECRUIT_INTERVIEW_STATUS_OPTIONS = [
  { value: 1, label: '待面试' },
  { value: 2, label: '已汇总' },
  { value: 3, label: '已取消' },
] as const

export const RECRUIT_INTERVIEW_WAY_OPTIONS = [
  { value: 1, label: '现场' },
  { value: 2, label: '电话' },
  { value: 3, label: '视频' },
] as const

export const RECRUIT_CONCLUSION_OPTIONS = [
  { value: 1, label: '通过' },
  { value: 2, label: '待定' },
  { value: 3, label: '不通过' },
] as const

export const RECRUIT_OFFER_STATUS_OPTIONS = [
  { value: 1, label: '待审批' },
  { value: 2, label: '已发出' },
  { value: 3, label: '已接受' },
  { value: 4, label: '已拒绝' },
  { value: 5, label: '已过期' },
  { value: 6, label: '已作废' },
] as const

export const RECRUIT_SEND_WAY_OPTIONS = [
  { value: 1, label: '邮件' },
  { value: 2, label: '短信' },
  { value: 3, label: '纸质' },
] as const

export const RECRUIT_DEMAND_STATUS_OPTIONS = [
  { value: 1, label: '待审批' },
  { value: 2, label: '招聘中' },
  { value: 3, label: '已暂停' },
  { value: 4, label: '已完成' },
  { value: 5, label: '已关闭' },
  { value: 6, label: '已驳回' },
] as const

export const RECRUIT_GENDER_OPTIONS = [
  { value: 1, label: '男' },
  { value: 2, label: '女' },
] as const

/** 是/否（授权、人才库等） */
export const RECRUIT_YESNO_OPTIONS = [
  { value: 1, label: '是' },
  { value: 0, label: '否' },
] as const

/* ----------------------------- 类型定义 ----------------------------- */

export interface RecruitDemandVO {
  id: number
  demandCode: string
  deptId?: number | null
  deptName?: string
  positionId?: number | null
  positionName?: string
  headCount?: number
  urgency?: number
  urgencyLabel?: string
  expectDate?: string
  education?: string
  experience?: string
  skillRequire?: string
  salaryMin?: number
  salaryMax?: number
  reasonType?: number
  reasonTypeLabel?: string
  reason?: string
  status?: number
  statusLabel?: string
  hrId?: number | null
  hrName?: string
  approveTime?: string
  finishTime?: string
  quotaTotal?: number
  quotaUsed?: number
  quotaGap?: number
  overQuota?: number
  cycleDays?: number
  hiredCount?: number
  remark?: string
  createTime?: string
}

export interface RecruitDemandSaveRequest {
  id?: number | null
  deptId?: number
  positionId?: number
  headCount?: number
  urgency?: number
  expectDate?: string
  education?: string
  experience?: string
  skillRequire?: string
  salaryMin?: number
  salaryMax?: number
  reasonType?: number
  reason?: string
  hrId?: number | null
  remark?: string
}

export interface RecruitPostVO {
  id: number
  demandId?: number | null
  demandCode?: string
  postName: string
  city?: string
  salaryMin?: number
  salaryMax?: number
  headCount?: number
  channel?: number
  channelLabel?: string
  status?: number
  statusLabel?: string
  hrId?: number | null
  hrName?: string
  publishTime?: string
  candidateCount?: number
  hiredCount?: number
  remark?: string
  createTime?: string
}

export interface RecruitPostSaveRequest {
  id?: number | null
  demandId?: number | null
  postName: string
  city?: string
  salaryMin?: number
  salaryMax?: number
  headCount?: number
  channel?: number
  status?: number
  hrId?: number | null
  remark?: string
}

export interface RecruitCandidateVO {
  id: number
  name: string
  mobile?: string
  mobileMasked?: string
  email?: string
  gender?: number
  genderLabel?: string
  birthDate?: string
  postId?: number | null
  postName?: string
  demandId?: number | null
  demandCode?: string
  resumeFileId?: number | null
  resumeFileName?: string
  source?: number
  sourceLabel?: string
  referrerId?: number | null
  referrerName?: string
  stage?: number
  stageLabel?: string
  rejectType?: number
  rejectReason?: string
  expectSalary?: number
  currentSalary?: number
  education?: string
  experience?: string
  authorized?: number
  talentPool?: number
  poolExpireDate?: string
  userId?: number | null
  remark?: string
  createTime?: string
  interviewCount?: number
}

export interface RecruitCandidateSaveRequest {
  id?: number | null
  name: string
  mobile: string
  email?: string
  gender?: number
  birthDate?: string
  postId?: number | null
  demandId?: number | null
  resumeFileId?: number | null
  source?: number
  referrerId?: number | null
  expectSalary?: number
  currentSalary?: number
  education?: string
  experience?: string
  authorized?: number
  talentPool?: number
  remark?: string
}

export interface RecruitFeedbackVO {
  id: number
  interviewId?: number
  interviewerId?: number
  interviewerName?: string
  professional?: number
  communication?: number
  logicScore?: number
  cultureMatch?: number
  conclusion?: number
  conclusionLabel?: string
  comment?: string
  createTime?: string
}

export interface RecruitInterviewVO {
  id: number
  candidateId?: number
  candidateName?: string
  candidateMobile?: string
  postId?: number | null
  postName?: string
  roundNo?: number
  interviewTime?: string
  way?: number
  wayLabel?: string
  location?: string
  interviewerIds?: string
  interviewerNames?: string[]
  status?: number
  statusLabel?: string
  conclusion?: number
  conclusionLabel?: string
  summary?: string
  continueReason?: string
  feedbackCount?: number
  interviewerCount?: number
  allSubmitted?: boolean
  feedbacks?: RecruitFeedbackVO[]
  remark?: string
  createTime?: string
}

export interface RecruitInterviewSaveRequest {
  id?: number | null
  candidateId?: number
  postId?: number | null
  roundNo?: number
  interviewTime?: string
  way?: number
  location?: string
  interviewerIds?: number[]
  remark?: string
}

export interface RecruitFeedbackRequest {
  interviewId?: number
  professional?: number
  communication?: number
  logicScore?: number
  cultureMatch?: number
  conclusion?: number
  comment?: string
}

export interface RecruitOfferVO {
  id: number
  offerNo?: string
  candidateId?: number
  candidateName?: string
  candidateMobile?: string
  postId?: number | null
  postName?: string
  positionId?: number | null
  positionName?: string
  salary?: number
  probationMonths?: number
  reportDate?: string
  expireDate?: string
  remainDays?: number
  expired?: number
  status?: number
  statusLabel?: string
  sendWay?: number
  sendWayLabel?: string
  sendTime?: string
  acceptTime?: string
  refuseReason?: string
  userId?: number | null
  convertLog?: string
  remark?: string
  createTime?: string
}

export interface RecruitOfferSaveRequest {
  candidateId?: number
  postId?: number | null
  positionId?: number | null
  positionName?: string
  salary?: number
  probationMonths?: number
  reportDate?: string
  expireDate?: string
  sendWay?: number
  remark?: string
}

export interface RecruitStageRequest {
  stage?: number
  rejectType?: number
  rejectReason?: string
  rollback?: boolean
  reason?: string
}

export interface RecruitConvertRequest {
  offerId?: number
  reuseMode?: number
  targetUserId?: number | null
  workNumber?: string
  remark?: string
}

export interface RecruitBoardStageCount {
  stage?: number
  label?: string
  count?: number
  convertRate?: number
}

export interface RecruitBoardChannelStat {
  source?: number
  label?: string
  resumeCount?: number
  interviewCount?: number
  hireCount?: number
  interviewRate?: number
  hireRate?: number
}

export interface RecruitBoardRejectStat {
  rejectType?: number
  label?: string
  count?: number
}

export interface RecruitBoardDeptStat {
  deptId?: number
  deptName?: string
  demandCount?: number
  headCount?: number
  hiredCount?: number
}

export interface RecruitBoardVO {
  postingCount?: number
  demandHeadCount?: number
  hiredCount?: number
  avgCycleDays?: number
  stageFunnel?: RecruitBoardStageCount[]
  channelStats?: RecruitBoardChannelStat[]
  rejectStats?: RecruitBoardRejectStat[]
  deptStats?: RecruitBoardDeptStat[]
}

/* ----------------------------- 接口函数 ----------------------------- */

type DemandPageParams = {
  keyword?: string
  status?: number
  deptId?: number
  pageNo?: number
  pageSize?: number
}

export function getDemandPageApi(params: DemandPageParams) {
  return request<PageResult<RecruitDemandVO>>({ url: '/hr/recruit/demand/page', method: 'get', params })
}

export function getDemandDetailApi(id: number) {
  return request<RecruitDemandVO>({ url: `/hr/recruit/demand/${id}`, method: 'get' })
}

export function saveDemandApi(data: RecruitDemandSaveRequest) {
  return request<number>({ url: '/hr/recruit/demand', method: 'post', data })
}

export function closeDemandApi(id: number) {
  return request<null>({ url: `/hr/recruit/demand/${id}/close`, method: 'post' })
}

export function approveDemandApi(id: number) {
  return request<null>({ url: `/hr/recruit/demand/${id}/approve`, method: 'post' })
}

type PostPageParams = {
  keyword?: string
  status?: number
  demandId?: number
  pageNo?: number
  pageSize?: number
}

export function getPostPageApi(params: PostPageParams) {
  return request<PageResult<RecruitPostVO>>({ url: '/hr/recruit/post/page', method: 'get', params })
}

export function savePostApi(data: RecruitPostSaveRequest) {
  return request<number>({ url: '/hr/recruit/post', method: 'post', data })
}

export function deletePostApi(id: number) {
  return request<null>({ url: `/hr/recruit/post/${id}`, method: 'delete' })
}

type CandidatePageParams = {
  keyword?: string
  stage?: number
  source?: number
  postId?: number
  pageNo?: number
  pageSize?: number
}

export function getCandidatePageApi(params: CandidatePageParams) {
  return request<PageResult<RecruitCandidateVO>>({ url: '/hr/recruit/candidate/page', method: 'get', params })
}

export function getCandidateDetailApi(id: number) {
  return request<RecruitCandidateVO>({ url: `/hr/recruit/candidate/${id}`, method: 'get' })
}

export function saveCandidateApi(data: RecruitCandidateSaveRequest) {
  return request<number>({ url: '/hr/recruit/candidate', method: 'post', data })
}

export function changeCandidateStageApi(id: number, data: RecruitStageRequest) {
  return request<null>({ url: `/hr/recruit/candidate/${id}/stage`, method: 'post', data })
}

export function findDuplicateApi(mobile: string, name?: string) {
  return request<RecruitCandidateVO>({ url: '/hr/recruit/candidate/duplicate', method: 'get', params: { mobile, name } })
}

type InterviewPageParams = {
  candidateId?: number
  status?: number
  pageNo?: number
  pageSize?: number
}

export function getInterviewPageApi(params: InterviewPageParams) {
  return request<PageResult<RecruitInterviewVO>>({ url: '/hr/recruit/interview/page', method: 'get', params })
}

export function getInterviewDetailApi(id: number) {
  return request<RecruitInterviewVO>({ url: `/hr/recruit/interview/${id}`, method: 'get' })
}

export function saveInterviewApi(data: RecruitInterviewSaveRequest) {
  return request<number>({ url: '/hr/recruit/interview', method: 'post', data })
}

export function submitFeedbackApi(data: RecruitFeedbackRequest) {
  return request<null>({ url: '/hr/recruit/interview/feedback', method: 'post', data })
}

export function summarizeInterviewApi(id: number, conclusion: number, summary?: string, continueReason?: string) {
  return request<null>({
    url: `/hr/recruit/interview/${id}/summarize`,
    method: 'post',
    params: { conclusion, summary, continueReason },
  })
}

type OfferPageParams = {
  status?: number
  candidateId?: number
  pageNo?: number
  pageSize?: number
}

export function getOfferPageApi(params: OfferPageParams) {
  return request<PageResult<RecruitOfferVO>>({ url: '/hr/recruit/offer/page', method: 'get', params })
}

export function getOfferDetailApi(id: number) {
  return request<RecruitOfferVO>({ url: `/hr/recruit/offer/${id}`, method: 'get' })
}

export function saveOfferApi(data: RecruitOfferSaveRequest) {
  return request<number>({ url: '/hr/recruit/offer', method: 'post', data })
}

export function sendOfferApi(id: number, sendWay?: number) {
  return request<null>({ url: `/hr/recruit/offer/${id}/send`, method: 'post', params: { sendWay } })
}

export function acceptOfferApi(id: number) {
  return request<null>({ url: `/hr/recruit/offer/${id}/accept`, method: 'post' })
}

export function refuseOfferApi(id: number, reason?: string) {
  return request<null>({ url: `/hr/recruit/offer/${id}/refuse`, method: 'post', params: { reason } })
}

export function convertOfferApi(data: RecruitConvertRequest) {
  return request<number>({ url: '/hr/recruit/offer/convert', method: 'post', data })
}

export function getBoardApi(deptId?: number) {
  return request<RecruitBoardVO>({ url: '/hr/recruit/board', method: 'get', params: { deptId } })
}
