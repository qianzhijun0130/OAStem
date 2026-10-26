/**
 * 审批人规则类型常量与下拉选项（FE-OPT-05 从 index.vue 抽出）。
 *
 * <p>父页面用它做保存/发布前的校验，`AssigneeTab` 用它渲染「规则」下拉——两处必须同一份，
 * 否则加一种规则时会出现「能选但校验不认」的割裂。</p>
 */

/** 审批人规则类型：0未配置 1指定人员 2指定角色 3部门主管 4申请人 5指定部门主管 */
export const ASSIGNEE_UNSET = 0
export const ASSIGNEE_USER = 1
export const ASSIGNEE_ROLE = 2
export const ASSIGNEE_LEADER = 3
/**
 * 申请人本人。
 *
 * <p>用于「提交后自己复核」「自我申报」这类环节。<b>已确认的副作用</b>：任务按候选人机制
 * 派发，申请人会在自己的待办里看到这条单子（即"自己审自己"），产品层面不做拦截。</p>
 */
export const ASSIGNEE_APPLICANT = 4
/** 指定部门主管：取某个固定部门（assigneeValue 为部门 id）的主管，与「发起人部门主管」区分 */
export const ASSIGNEE_DEPT_LEADER = 5

export interface AssigneeTypeOption {
  value: number
  label: string
}

/** 规则下拉选项（顺序即产品定义的 0→5） */
export const ASSIGNEE_TYPE_OPTIONS: AssigneeTypeOption[] = [
  { value: ASSIGNEE_UNSET, label: '未配置' },
  { value: ASSIGNEE_USER, label: '指定人员' },
  { value: ASSIGNEE_ROLE, label: '指定角色' },
  { value: ASSIGNEE_LEADER, label: '部门主管' },
  { value: ASSIGNEE_DEPT_LEADER, label: '指定部门主管' },
  { value: ASSIGNEE_APPLICANT, label: '申请人（本人）' },
]
