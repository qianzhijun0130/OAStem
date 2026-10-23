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
