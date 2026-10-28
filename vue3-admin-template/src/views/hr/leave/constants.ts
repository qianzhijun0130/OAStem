/**
 * 请假页共享常量与小工具（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>四个 tab 与四个弹窗都要用到假别、单据状态两组字典，集中在这里避免多处复制；
 * 后端虽然也会返回 `leaveTypeLabel` / `leaveStatusLabel`，但列表在部分接口下不带，
 * 前端仍需能兜底翻译。</p>
 */

/** 假别字典：与后端 `oa_leave_quota.leave_type` 取值一致 */
export const LEAVE_TYPES = [
  { value: 1, label: '事假' },
  { value: 2, label: '病假' },
  { value: 3, label: '年假' },
  { value: 4, label: '婚假' },
  { value: 5, label: '产假' },
  { value: 6, label: '陪产假' },
  { value: 7, label: '丧假' },
  { value: 8, label: '调休' },
]

/** 请假单状态：0 草稿 / 1 审批中 / 2 已通过 / 3 已驳回 / 4 已撤销 */
export const LEAVE_STATUS = [
  { value: 0, label: '草稿', type: 'info' as const },
  { value: 1, label: '审批中', type: 'warning' as const },
  { value: 2, label: '已通过', type: 'success' as const },
  { value: 3, label: '已驳回', type: 'danger' as const },
  { value: 4, label: '已撤销', type: 'info' as const },
]

/** 除请假外的三类通用人事申请，共用 `oa_hr_request` 与同一个弹窗 */
export const BIZ_TABS = [
  { type: 1, name: 'overtime', label: '加班' },
  { type: 2, name: 'trip', label: '出差' },
  { type: 3, name: 'card', label: '补卡' },
]

/** 状态转标签文案 + el-tag 主题色，未知状态回落灰色 */
export function statusMeta(status?: number) {
  return LEAVE_STATUS.find((s) => s.value === status) || { label: '-', type: 'info' as const }
}

/** 假别码转中文，未知码回落 `-` */
export function leaveTypeLabel(type?: number) {
  return LEAVE_TYPES.find((t) => t.value === type)?.label || '-'
}
