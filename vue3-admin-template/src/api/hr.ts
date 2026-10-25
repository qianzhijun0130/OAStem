import { downloadFile } from '@/utils/download'
import { request } from '@/utils/request'
import type {
  AttendanceMonthVO,
  AttendancePageQuery,
  AttendanceVO,
  CertSaveRequest,
  ChangeVO,
  ClockRequest,
  ContractSaveRequest,
  EmployeeDetailVO,
  EmployeePageQuery,
  EmployeeProfileRequest,
  EmployeeVO,
  PageResult,
  PositionSaveRequest,
  PositionVO,
  ScheduleBatchRequest,
  ScheduleVO,
  ShiftSaveRequest,
  ShiftVO,
  TodayAttendanceVO,
  HrRequestSaveRequest,
  HrRequestVO,
  LeavePageQuery,
  LeavePolicySaveRequest,
  LeavePolicyVO,
  LeaveQuotaVO,
  LeaveSubmitRequest,
  LeaveVO,
  QuotaAdjustRequest,
  SalaryAdjustPayload,
  SalaryItemSaveRequest,
  SalaryItemVO,
  SalaryPageQuery,
  SalaryPayslipVO,
  SalaryProfileSaveRequest,
  SalaryProfileVO,
  SalaryRecordVO,
  SocialPolicySaveRequest,
  SocialPolicyVO,
} from './types'

/* ==================== 岗位 ==================== */

export function getPositionPageApi(params: {
  keyword?: string
  deptId?: number
  series?: string
  status?: number
  pageNo?: number
  pageSize?: number
}) {
  return request<PageResult<PositionVO>>({ url: '/hr/position/page', method: 'get', params })
}

export function listPositionOptionsApi(deptId?: number) {
  return request<PositionVO[]>({ url: '/hr/position/options', method: 'get', params: { deptId } })
}

export function savePositionApi(data: PositionSaveRequest) {
  return request<number>({ url: '/hr/position', method: 'post', data })
}

export function deletePositionApi(id: number) {
  return request<null>({ url: `/hr/position/${id}`, method: 'delete' })
}

export function updatePositionStatusApi(id: number, status: number) {
  return request<null>({ url: `/hr/position/${id}/status`, method: 'put', params: { status } })
}

/* ==================== 员工档案 ==================== */

export function getEmployeePageApi(params: EmployeePageQuery) {
  return request<PageResult<EmployeeVO>>({ url: '/hr/employee/page', method: 'get', params })
}

export function getEmployeeDetailApi(id: number) {
  return request<EmployeeDetailVO>({ url: `/hr/employee/${id}`, method: 'get' })
}

export function updateEmployeeProfileApi(id: number, data: EmployeeProfileRequest) {
  return request<null>({ url: `/hr/employee/${id}/profile`, method: 'post', data })
}

export function getEmployeeChangesApi(id: number) {
  return request<ChangeVO[]>({ url: `/hr/employee/${id}/changes`, method: 'get' })
}

export function saveContractApi(id: number, data: ContractSaveRequest) {
  return request<number>({ url: `/hr/employee/${id}/contract`, method: 'post', data })
}

export function deleteContractApi(id: number, contractId: number) {
  return request<null>({ url: `/hr/employee/${id}/contract/${contractId}`, method: 'delete' })
}

export function saveCertApi(id: number, data: CertSaveRequest) {
  return request<number>({ url: `/hr/employee/${id}/cert`, method: 'post', data })
}

export function deleteCertApi(id: number, certId: number) {
  return request<null>({ url: `/hr/employee/${id}/cert/${certId}`, method: 'delete' })
}

/** 花名册导出：返回 Blob，由页面触发下载 */
export function exportEmployeeApi(params: EmployeePageQuery) {
  return request<Blob>({
    url: '/hr/employee/export',
    method: 'get',
    params,
    responseType: 'blob',
  })
}

/* ==================== 考勤 ==================== */

export function listShiftsApi() {
  return request<ShiftVO[]>({ url: '/hr/attendance/shifts', method: 'get' })
}

export function saveShiftApi(data: ShiftSaveRequest) {
  return request<number>({ url: '/hr/attendance/shift', method: 'post', data })
}

export function deleteShiftApi(id: number) {
  return request<null>({ url: `/hr/attendance/shift/${id}`, method: 'delete' })
}

export function updateShiftStatusApi(id: number, status: number) {
  return request<null>({ url: `/hr/attendance/shift/${id}/status`, method: 'put', params: { status } })
}

export function batchScheduleApi(data: ScheduleBatchRequest) {
  return request<number>({ url: '/hr/attendance/schedule/batch', method: 'post', data })
}

export function listScheduleApi(params: { deptId?: number; month?: string }) {
  return request<ScheduleVO[]>({ url: '/hr/attendance/schedule', method: 'get', params })
}

export function getTodayAttendanceApi() {
  return request<TodayAttendanceVO>({ url: '/hr/attendance/today', method: 'get' })
}

export function clockApi(data: ClockRequest) {
  return request<null>({ url: '/hr/attendance/clock', method: 'post', data })
}

export function getAttendancePageApi(params: AttendancePageQuery) {
  return request<PageResult<AttendanceVO>>({ url: '/hr/attendance/page', method: 'get', params })
}

export function getMyAttendanceApi(month?: string) {
  return request<AttendanceVO[]>({ url: '/hr/attendance/my', method: 'get', params: { month } })
}

export function getAttendanceMonthApi(params: {
  month?: string
  deptId?: number
  pageNo?: number
  pageSize?: number
}) {
  return request<PageResult<AttendanceMonthVO>>({ url: '/hr/attendance/month', method: 'get', params })
}

export function rebuildAttendanceApi(month: string) {
  return request<number>({ url: '/hr/attendance/rebuild', method: 'post', params: { month } })
}

export function lockAttendanceMonthApi(month: string) {
  return request<null>({ url: '/hr/attendance/lock', method: 'post', params: { month } })
}

export function unlockAttendanceMonthApi(month: string, reason: string) {
  return request<null>({ url: '/hr/attendance/unlock', method: 'post', params: { month, reason } })
}

/* ==================== 假勤：额度与请假 ==================== */

export function getLeaveQuotaApi(params: { year?: number; deptId?: number }) {
  return request<LeaveQuotaVO[]>({ url: '/hr/leave/quota', method: 'get', params })
}

export function getMyLeaveQuotaApi(year?: number) {
  return request<LeaveQuotaVO[]>({ url: '/hr/leave/quota/my', method: 'get', params: { year } })
}

export function adjustLeaveQuotaApi(data: QuotaAdjustRequest) {
  return request<null>({ url: '/hr/leave/quota/adjust', method: 'post', data })
}

export function submitLeaveApi(data: LeaveSubmitRequest) {
  return request<number>({ url: '/hr/leave', method: 'post', data })
}

export function cancelLeaveApi(id: number) {
  return request<null>({ url: `/hr/leave/${id}/cancel`, method: 'post' })
}

/** 销假（提前返岗）：登记实际返岗日期，差额额度由后端退回 */
export function returnLeaveApi(id: number, returnDate: string, remark?: string) {
  return request<null>({
    url: `/hr/leave/${id}/return`,
    method: 'put',
    params: { returnDate, remark },
  })
}

export function getLeavePageApi(params: LeavePageQuery) {
  return request<PageResult<LeaveVO>>({ url: '/hr/leave/page', method: 'get', params })
}

export function getMyLeaveApi(leaveStatus?: number) {
  return request<LeaveVO[]>({ url: '/hr/leave/my', method: 'get', params: { leaveStatus } })
}

/* ==================== 假勤：假别政策与请假单导出 ==================== */

export function getLeavePolicyListApi(params: { year?: number; city?: string }) {
  return request<LeavePolicyVO[]>({ url: '/hr/leave/policy', method: 'get', params })
}

/** 假别下拉项：后端只返回可配置政策的假别（4 婚假 / 5 产假 / 6 陪产假 / 7 丧假） */
export function getLeavePolicyTypesApi() {
  return request<{ code: number; label: string }[]>({ url: '/hr/leave/policy/types', method: 'get' })
}

export function saveLeavePolicyApi(data: LeavePolicySaveRequest) {
  return request<number>({ url: '/hr/leave/policy', method: 'post', data })
}

export function deleteLeavePolicyApi(id: number) {
  return request<null>({ url: `/hr/leave/policy/${id}`, method: 'delete' })
}

/** 请假单导出：后端直接返回 xlsx 流（非 Result 包装），下载与文件名解析复用 downloadFile */
export function exportLeaveApi(params: {
  month?: string
  leaveType?: number
  leaveStatus?: number
  deptId?: number
  userId?: number
  keyword?: string
}) {
  return downloadFile('/hr/leave/export', { ...params }, '请假单.xlsx')
}

/* ==================== 假勤：加班 / 出差 / 补卡 ==================== */

export function submitHrRequestApi(data: HrRequestSaveRequest) {
  return request<number>({ url: '/hr/request', method: 'post', data })
}

export function cancelHrRequestApi(bizType: number, id: number) {
  return request<null>({ url: `/hr/request/${bizType}/${id}/cancel`, method: 'post' })
}

export function getHrRequestPageApi(params: {
  bizType: number
  month?: string
  deptId?: number
  status?: number
  keyword?: string
  pageNo?: number
  pageSize?: number
}) {
  return request<PageResult<HrRequestVO>>({ url: '/hr/request/page', method: 'get', params })
}

/* ==================== 考勤月度汇总导出 ==================== */

export function exportAttendanceMonthApi(params: { month: string; deptId?: number }) {
  return request<Blob>({
    url: '/hr/attendance/month/export',
    method: 'get',
    params,
    responseType: 'blob',
  })
}

/* ==================== 薪酬 ==================== */

export function listSalaryItemsApi() {
  return request<SalaryItemVO[]>({ url: '/hr/salary/items', method: 'get' })
}

export function saveSalaryItemApi(data: SalaryItemSaveRequest) {
  return request<number>({ url: '/hr/salary/items', method: 'post', data })
}

export function deleteSalaryItemApi(id: number) {
  return request<null>({ url: `/hr/salary/items/${id}`, method: 'delete' })
}

export function listSocialPoliciesApi(year?: number) {
  return request<SocialPolicyVO[]>({ url: '/hr/salary/policies', method: 'get', params: { year } })
}

export function saveSocialPolicyApi(data: SocialPolicySaveRequest) {
  return request<number>({ url: '/hr/salary/policies', method: 'post', data })
}

export function deleteSocialPolicyApi(id: number) {
  return request<null>({ url: `/hr/salary/policies/${id}`, method: 'delete' })
}

export function getSalaryProfilePageApi(params: {
  keyword?: string
  deptId?: number
  pageNo?: number
  pageSize?: number
}) {
  return request<PageResult<SalaryProfileVO>>({ url: '/hr/salary/profiles', method: 'get', params })
}

export function saveSalaryProfileApi(data: SalaryProfileSaveRequest) {
  return request<number>({ url: '/hr/salary/profiles', method: 'post', data })
}

export function getSalaryProfileHistoryApi(userId: number) {
  return request<SalaryProfileVO[]>({ url: `/hr/salary/profiles/${userId}/history`, method: 'get' })
}

export function calcSalaryApi(params: { month: string; deptId?: number }) {
  return request<number>({ url: '/hr/salary/calc', method: 'post', params })
}

export function getSalaryRecordPageApi(params: SalaryPageQuery) {
  return request<PageResult<SalaryRecordVO>>({ url: '/hr/salary/records', method: 'get', params })
}

export function getSalaryRecordDetailApi(id: number) {
  return request<SalaryPayslipVO>({ url: `/hr/salary/records/${id}`, method: 'get' })
}

export function adjustSalaryApi(id: number, data: SalaryAdjustPayload) {
  return request<null>({ url: `/hr/salary/records/${id}/adjust`, method: 'put', data })
}

export function reviewSalaryApi(params: { month: string; deptId?: number }) {
  return request<number>({ url: '/hr/salary/review', method: 'post', params })
}

export function lockSalaryApi(params: { month: string; deptId?: number }) {
  return request<number>({ url: '/hr/salary/lock', method: 'post', params })
}

export function paySalaryApi(params: { month: string; deptId?: number }) {
  return request<number>({ url: '/hr/salary/pay', method: 'post', params })
}

export function getMyPayslipsApi(year?: number) {
  return request<SalaryPayslipVO[]>({ url: '/hr/salary/my', method: 'get', params: { year } })
}
