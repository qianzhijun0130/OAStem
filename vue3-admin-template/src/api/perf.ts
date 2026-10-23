import { request } from '@/utils/request'
import type {
  PageResult,
  PerfAppealHandleRequest,
  PerfAppealRequest,
  PerfAppealVO,
  PerfCalibrateRequest,
  PerfCycleSaveRequest,
  PerfCycleVO,
  PerfDistributionVO,
  PerfIndicatorSaveRequest,
  PerfIndicatorVO,
  PerfPlanSaveRequest,
  PerfPlanVO,
  PerfScoreRequest,
  PerfSheetDetailVO,
  PerfSheetPageQuery,
  PerfSheetVO,
  PerfTargetSaveRequest,
} from './types'

/* ==================== 方案 ==================== */

export function getPerfPlanPageApi(params: {
  keyword?: string
  status?: number
  pageNo?: number
  pageSize?: number
}) {
  return request<PageResult<PerfPlanVO>>({ url: '/hr/perf/plan/page', method: 'get', params })
}

export function getPerfPlanApi(id: number) {
  return request<PerfPlanVO>({ url: `/hr/perf/plan/${id}`, method: 'get' })
}

export function savePerfPlanApi(data: PerfPlanSaveRequest) {
  return request<number>({ url: '/hr/perf/plan', method: 'post', data })
}

export function deletePerfPlanApi(id: number) {
  return request<null>({ url: `/hr/perf/plan/${id}`, method: 'delete' })
}

export function listPerfPlanOptionsApi() {
  return request<PerfPlanVO[]>({ url: '/hr/perf/plan/options', method: 'get' })
}

/* ==================== 指标库 ==================== */

export function getPerfIndicatorPageApi(params: {
  keyword?: string
  type?: number
  pageNo?: number
  pageSize?: number
}) {
  return request<PageResult<PerfIndicatorVO>>({ url: '/hr/perf/indicator/page', method: 'get', params })
}

export function savePerfIndicatorApi(data: PerfIndicatorSaveRequest) {
  return request<number>({ url: '/hr/perf/indicator', method: 'post', data })
}

export function deletePerfIndicatorApi(id: number) {
  return request<null>({ url: `/hr/perf/indicator/${id}`, method: 'delete' })
}

export function listPerfIndicatorOptionsApi() {
  return request<PerfIndicatorVO[]>({ url: '/hr/perf/indicator/options', method: 'get' })
}

/* ==================== 周期 ==================== */

export function getPerfCyclePageApi(params: { status?: number; pageNo?: number; pageSize?: number }) {
  return request<PageResult<PerfCycleVO>>({ url: '/hr/perf/cycle/page', method: 'get', params })
}

export function getPerfCycleApi(id: number) {
  return request<PerfCycleVO>({ url: `/hr/perf/cycle/${id}`, method: 'get' })
}

export function savePerfCycleApi(data: PerfCycleSaveRequest) {
  return request<number>({ url: '/hr/perf/cycle', method: 'post', data })
}

export function generatePerfSheetsApi(id: number) {
  return request<number>({ url: `/hr/perf/cycle/${id}/generate`, method: 'post' })
}

export function advancePerfCycleApi(id: number) {
  return request<number>({ url: `/hr/perf/cycle/${id}/advance`, method: 'post' })
}

export function archivePerfCycleApi(id: number) {
  return request<number>({ url: `/hr/perf/cycle/${id}/archive`, method: 'post' })
}

export function deletePerfCycleApi(id: number) {
  return request<null>({ url: `/hr/perf/cycle/${id}`, method: 'delete' })
}

/* ==================== 考核表 ==================== */

export function getMyPerfSheetApi(cycleId: number) {
  return request<PerfSheetDetailVO>({ url: '/hr/perf/sheet/my', method: 'get', params: { cycleId } })
}

export function getMyPerfHistoryApi(params: { pageNo?: number; pageSize?: number }) {
  return request<PageResult<PerfSheetVO>>({ url: '/hr/perf/my/page', method: 'get', params })
}

export function savePerfTargetApi(data: PerfTargetSaveRequest) {
  return request<null>({ url: '/hr/perf/target/my', method: 'post', data })
}

export function approvePerfTargetApi(sheetId: number, pass: boolean, opinion?: string) {
  return request<null>({
    url: `/hr/perf/target/${sheetId}/approve`,
    method: 'post',
    params: { pass, opinion },
  })
}

export function submitPerfSelfApi(data: PerfScoreRequest) {
  return request<null>({ url: '/hr/perf/score/self', method: 'post', data })
}

export function submitPerfManagerApi(data: PerfScoreRequest) {
  return request<null>({ url: '/hr/perf/score/manager', method: 'post', data })
}

export function calibratePerfApi(data: PerfCalibrateRequest) {
  return request<null>({ url: '/hr/perf/score/calibrate', method: 'post', data })
}

export function confirmPerfApi(sheetId: number) {
  return request<null>({ url: `/hr/perf/score/${sheetId}/confirm`, method: 'post' })
}

export function appealPerfApi(data: PerfAppealRequest) {
  return request<null>({ url: '/hr/perf/appeal', method: 'post', data })
}

export function handlePerfAppealApi(data: PerfAppealHandleRequest) {
  return request<null>({ url: '/hr/perf/appeal/handle', method: 'post', data })
}

export function listPendingAppealsApi(cycleId?: number) {
  return request<PerfAppealVO[]>({ url: '/hr/perf/appeal/pending', method: 'get', params: { cycleId } })
}

export function getPerfSheetPageApi(params: PerfSheetPageQuery) {
  return request<PageResult<PerfSheetVO>>({ url: '/hr/perf/sheet/page', method: 'get', params })
}

export function getPerfTeamPageApi(params: PerfSheetPageQuery) {
  return request<PageResult<PerfSheetVO>>({ url: '/hr/perf/team/page', method: 'get', params })
}

export function getPerfSheetApi(id: number) {
  return request<PerfSheetDetailVO>({ url: `/hr/perf/sheet/${id}`, method: 'get' })
}

export function getPerfDistributionApi(cycleId: number) {
  return request<PerfDistributionVO[]>({ url: '/hr/perf/distribution', method: 'get', params: { cycleId } })
}

export function exportPerfResultApi(cycleId: number) {
  return request<Blob>({
    url: '/hr/perf/export',
    method: 'get',
    params: { cycleId },
    responseType: 'blob',
  })
}
