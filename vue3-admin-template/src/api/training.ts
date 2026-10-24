import { request } from '@/utils/request'
import type { PageResult } from '@/components/list/types'

/* ================================================================== */
/* 培训模块：课程 / 期次 / 报名 / 成绩 / 我的培训 / 统计                      */
/* ================================================================== */

/* ----------------------------- 选项常量 ----------------------------- */

export const TRAIN_TYPE_OPTIONS = [
  { value: 1, label: '新员工入职' },
  { value: 2, label: '岗位技能' },
  { value: 3, label: '管理能力' },
  { value: 4, label: '合规安全' },
  { value: 5, label: '通用素养' },
] as const

export const TRAIN_TEACHER_TYPE_OPTIONS = [
  { value: 1, label: '内部讲师' },
  { value: 2, label: '外部讲师' },
] as const

export const TRAIN_REQUIRED_OPTIONS = [
  { value: 1, label: '必修' },
  { value: 0, label: '选修' },
] as const

export const TRAIN_COURSE_STATUS_OPTIONS = [
  { value: 1, label: '启用' },
  { value: 2, label: '停用' },
] as const

export const TRAIN_SESSION_STATUS_OPTIONS = [
  { value: 1, label: '报名中' },
  { value: 2, label: '进行中' },
  { value: 3, label: '已结束' },
  { value: 4, label: '已取消' },
] as const

export const TRAIN_CHECKIN_WAY_OPTIONS = [
  { value: 1, label: '二维码' },
  { value: 2, label: '手工点名' },
] as const

export const TRAIN_ENROLL_STATUS_OPTIONS = [
  { value: 1, label: '已报名' },
  { value: 2, label: '候补' },
  { value: 3, label: '已取消' },
  { value: 4, label: '已签到' },
  { value: 5, label: '缺勤' },
  { value: 6, label: '已完成' },
  { value: 7, label: '爽约' },
] as const

export const TRAIN_ENROLL_WAY_OPTIONS = [
  { value: 1, label: '自助报名' },
  { value: 2, label: 'HR指派' },
] as const

export const TRAIN_ASSESS_WAY_OPTIONS = [
  { value: 1, label: '考试' },
  { value: 2, label: '作业' },
  { value: 3, label: '讲师评估' },
  { value: 4, label: '免考' },
] as const

/* ----------------------------- 类型定义 ----------------------------- */

export interface TrainCourseVO {
  id: number
  courseCode: string
  courseName: string
  trainType?: number
  trainTypeLabel?: string
  teacherType?: number
  teacherTypeLabel?: string
  teacherId?: number | null
  teacherName?: string
  teacherOrg?: string
  hours?: number
  deptIds?: string
  series?: string
  required?: number
  requiredLabel?: string
  passScore?: number
  fileId?: number | null
  cost?: number
  status?: number
  statusLabel?: string
  remark?: string
  sessionCount?: number
  completedCount?: number
}

export interface TrainCourseSaveRequest {
  id?: number | null
  courseCode: string
  courseName: string
  trainType?: number
  teacherType?: number
  teacherId?: number | null
  teacherName?: string
  teacherOrg?: string
  hours?: number
  deptIds?: string
  series?: string
  required?: number
  passScore?: number
  fileId?: number | null
  cost?: number
  status?: number
  remark?: string
}

export interface TrainSessionVO {
  id: number
  courseId?: number
  courseCode?: string
  courseName?: string
  trainType?: number
  trainTypeLabel?: string
  required?: number
  requiredLabel?: string
  hours?: number
  sessionName: string
  startTime?: string
  endTime?: string
  location?: string
  onlineLink?: string
  capacity?: number
  enrolledCount?: number
  waitCount?: number
  enrollDeadline?: string
  status?: number
  statusLabel?: string
  checkinWay?: number
  remark?: string
  remainQuota?: number
  enrollClosed?: boolean
  started?: boolean
  enrollable?: boolean
  myEnrollStatus?: number | null
  createTime?: string
}

export interface TrainSessionSaveRequest {
  id?: number | null
  courseId?: number
  sessionName: string
  startTime?: string
  endTime?: string
  location?: string
  onlineLink?: string
  capacity?: number
  enrollDeadline?: string
  checkinWay?: number
  remark?: string
}

export interface TrainEnrollVO {
  id: number
  sessionId?: number
  sessionName?: string
  courseId?: number
  courseName?: string
  trainType?: number
  required?: number
  requiredLabel?: string
  hours?: number
  userId?: number
  userName?: string
  deptName?: string
  enrollWay?: number
  enrollWayLabel?: string
  status?: number
  statusLabel?: string
  assignReason?: string
  checkinTime?: string
  realHours?: number
  score?: number
  assessWay?: number
  passed?: number
  satisfied?: number
  certFileId?: number | null
  cancelTime?: string
  remark?: string
  createTime?: string
}

export interface TrainEnrollBatchRequest {
  sessionId?: number
  userIds?: number[]
  reason?: string
}

export interface TrainScoreRequest {
  enrollId?: number
  score?: number
  assessWay?: number
  satisfied?: number
  certFileId?: number | null
  remark?: string
}

export interface TrainStatisticCourseStat {
  courseId?: number
  courseName?: string
  required?: number
  enrollCount?: number
  completedCount?: number
  attendRate?: number
  avgHours?: number
  satisfaction?: number
}

export interface TrainStatisticDeptStat {
  deptId?: number
  deptName?: string
  enrollCount?: number
  completedCount?: number
  totalHours?: number
}

export interface TrainStatisticVO {
  avgHours?: number
  totalHours?: number
  staffCount?: number
  requiredFinishRate?: number
  requiredShouldCount?: number
  requiredDoneCount?: number
  satisfaction?: number
  totalCost?: number
  courseStats?: TrainStatisticCourseStat[]
  deptStats?: TrainStatisticDeptStat[]
}

/* ----------------------------- 接口函数 ----------------------------- */

type CoursePageParams = {
  keyword?: string
  type?: number
  required?: number
  pageNo?: number
  pageSize?: number
}

export function getCoursePageApi(params: CoursePageParams) {
  return request<PageResult<TrainCourseVO>>({ url: '/hr/train/course/page', method: 'get', params })
}

export function getCourseDetailApi(id: number) {
  return request<TrainCourseVO>({ url: `/hr/train/course/${id}`, method: 'get' })
}

export function saveCourseApi(data: TrainCourseSaveRequest) {
  return request<number>({ url: '/hr/train/course', method: 'post', data })
}

export function deleteCourseApi(id: number) {
  return request<null>({ url: `/hr/train/course/${id}`, method: 'delete' })
}

type SessionPageParams = {
  keyword?: string
  courseId?: number
  status?: number
  pageNo?: number
  pageSize?: number
}

export function getSessionPageApi(params: SessionPageParams) {
  return request<PageResult<TrainSessionVO>>({ url: '/hr/train/session/page', method: 'get', params })
}

export function getSessionDetailApi(id: number) {
  return request<TrainSessionVO>({ url: `/hr/train/session/${id}`, method: 'get' })
}

export function saveSessionApi(data: TrainSessionSaveRequest) {
  return request<number>({ url: '/hr/train/session', method: 'post', data })
}

export function cancelSessionApi(id: number) {
  return request<null>({ url: `/hr/train/session/${id}/cancel`, method: 'post' })
}

export function listEnrollsApi(sessionId: number) {
  return request<TrainEnrollVO[]>({ url: `/hr/train/session/${sessionId}/enrolls`, method: 'get' })
}

export function enrollSessionApi(sessionId: number) {
  return request<TrainEnrollVO>({ url: `/hr/train/session/${sessionId}/enroll`, method: 'post' })
}

export function enrollBatchApi(data: TrainEnrollBatchRequest) {
  return request<number>({ url: '/hr/train/session/enroll/batch', method: 'post', data })
}

export function cancelEnrollApi(id: number) {
  return request<null>({ url: `/hr/train/enroll/${id}/cancel`, method: 'post' })
}

export function checkinEnrollApi(id: number) {
  return request<null>({ url: `/hr/train/enroll/${id}/checkin`, method: 'post' })
}

export function scoreEnrollApi(data: TrainScoreRequest) {
  return request<null>({ url: '/hr/train/enroll/score', method: 'post', data })
}

type MyTrainPageParams = {
  status?: number
  pageNo?: number
  pageSize?: number
}

export function getMyTrainPageApi(params: MyTrainPageParams) {
  return request<PageResult<TrainEnrollVO>>({ url: '/hr/train/my/page', method: 'get', params })
}

export function getMyAvailableApi() {
  return request<TrainSessionVO[]>({ url: '/hr/train/my/available', method: 'get' })
}

export function getStatisticsApi(year?: string) {
  return request<TrainStatisticVO>({ url: '/hr/train/statistics', method: 'get', params: { year } })
}
