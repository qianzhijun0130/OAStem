import { request } from '@/utils/request'
import { downloadFile } from '@/utils/download'
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
  PerfImportResult,
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

/**
 * 绩效结果导出。
 *
 * <p>下载动作（含「后端返回 JSON 报错时提示而不是存成假 xlsx」）统一收敛在
 * {@link downloadFile}。不要返回 Blob 让页面 createObjectURL：
 * 响应拦截器对 `responseType: 'blob'` 返回完整 {@code AxiosResponse}，
 * 页面 `as Blob` 强转会在运行期拿到对象而非 Blob，点了没反应。</p>
 */
export function exportPerfResultApi(cycleId: number) {
  return downloadFile('/hr/perf/export', { cycleId }, '绩效结果.xlsx')
}

/* ==================== 结果导入（BIZ-OPT-10：历史迁移） ==================== */

/**
 * 下载导入模板。
 *
 * <p>模板按周期生成（每个方案的指标不同），所以必须带 cycleId；
 * 文件名由后端命名（含周期名），这里给一个兜底名。</p>
 */
export function downloadPerfImportTemplateApi(cycleId: number) {
  return downloadFile('/hr/perf/import/template', { cycleId }, '绩效结果导入模板.xlsx')
}

/**
 * 导入历史绩效结果。
 *
 * <p>超时放宽到 120s：历史迁移一次几十到几百人，默认 10s 不够。
 * 返回值里的 imported/updated/failed 才是成败依据——坏行不阻断整批。</p>
 */
export function importPerfResultsApi(cycleId: number, file: File) {
  const form = new FormData()
  form.append('file', file)
  return request<PerfImportResult>({
    url: '/hr/perf/import',
    method: 'post',
    params: { cycleId },
    data: form,
    timeout: 120000,
  })
}
