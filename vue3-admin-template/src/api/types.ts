/** 后端统一响应结构 */
export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
  timestamp: string
}

export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  token: string
  tokenType: string
  expiresIn: number
  username: string
  realName: string
}

export interface UserInfo {
  userId: number
  username: string
  realName: string
  nickname?: string
  avatar?: string
  deptId: number
  deptName?: string
  companyId: number
  position?: string
  workNumber?: string
  mobile?: string
  email?: string
  roles: string[]
  permissions: string[]
}

export interface DeptVO {
  id: number
  parentId: number
  deptName: string
  deptCode: string
  companyId: number
  leaderId?: number | null
  leaderName?: string | null
  deptType: number
  sort: number
  /** 工号前缀：该部门员工工号的字母前缀，留空时由系统按部门名称拼音首字母自动推导 */
  workNoPrefix?: string | null
  /** 0禁用 1启用 */
  status: number
  createTime?: string
  children: DeptVO[]
}

export interface DeptSaveRequest {
  id?: number | null
  deptName: string
  deptCode: string
  companyId?: number | null
  /** 工号前缀：留空由系统按部门名称自动生成；填写则以填写值为准 */
  workNoPrefix?: string | null
  parentId: number
  leaderId?: number | null
  deptType?: number
  sort?: number
  status?: number
}

export interface MenuVO {
  id: number
  parentId: number
  menuName: string
  menuCode: string
  /** 1目录 2菜单 3按钮 */
  menuType: number
  path?: string
  component?: string
  icon?: string
  sort: number
  visible: number
  /** 0禁用 1启用 */
  status: number
  children: MenuVO[]
}

/** 后端统一分页响应结构 */
export interface PageResult<T> {
  records: T[]
  total: number
  pageNo: number
  pageSize: number
  totalPages: number
}

/** 通用分页查询入参 */
export interface QueryPage {
  pageNo?: number
  pageSize?: number
}

export interface MenuSaveRequest {
  id?: number | null
  menuName: string
  menuCode: string
  /** 1目录 2菜单 3按钮 */
  menuType: number
  path?: string
  component?: string
  icon?: string
  parentId: number
  sort?: number
  /** 0隐藏 1显示 */
  visible?: number
  /** 0禁用 1启用 */
  status?: number
}

export interface RoleOptionVO {
  id: number
  roleName: string
  roleCode: string
}

export interface RoleVO {
  id: number
  roleName: string
  roleCode: string
  /** 1系统角色 2业务角色 */
  roleType?: number
  /** 1全部 2本部门 3仅本人 */
  dataScope?: number
  description?: string
  /** 0禁用 1启用 */
  status: number
  sort?: number
  createTime?: string
  userCount?: number
}

export interface RoleSaveRequest {
  id?: number | null
  roleName: string
  roleCode: string
  roleType?: number
  dataScope?: number
  description?: string
  status?: number
  sort?: number
}

export interface RolePageQuery extends QueryPage {
  roleName?: string
  status?: number
}

export interface UserVO {
  id: number
  username: string
  realName: string
  nickname?: string
  /** 1男 2女（业务上已取消「未知」，存量 0 值按男展示） */
  gender?: number
  mobile?: string
  email?: string
  companyId?: number
  deptId?: number
  deptName?: string
  position?: string
  workNumber?: string
  entryDate?: string
  roleIds?: number[]
  roleNames?: string
  /** 0禁用 1启用 2锁定 */
  status: number
  createTime?: string
}

export interface UserSaveRequest {
  id?: number | null
  username: string
  realName: string
  nickname?: string
  gender?: number
  mobile?: string
  email?: string
  /** 必填（后端校验），表单初始未选择时为 undefined */
  deptId?: number
  position?: string
  /** 留空时由后端按部门自动生成工号 */
  workNumber?: string
  entryDate?: string
  roleIds: number[]
  password?: string
  status?: number
}

export interface UserPageQuery extends QueryPage {
  deptId?: number
  roleId?: number
  status?: number
  keyword?: string
}

/** 批量导入的单行错误明细 */
export interface UserImportErrorVO {
  /** Excel 行号（含表头，从 1 开始） */
  row: number
  message: string
}

/** 批量导入结果：整批校验、全对才入库 */
export interface UserImportResultVO {
  imported: boolean
  totalRows: number
  successCount: number
  failCount: number
  errors: UserImportErrorVO[]
  message: string
}

// ==================== 第三阶段：流程引擎 / 审批中心 ====================

/** 流程模板 */
export interface FlowDefinitionVO {
  id: number
  flowCode: string
  flowName: string
  category?: string
  bpmnKey?: string
  deploymentId?: string
  /** 表单字段配置 JSON 字符串 */
  formFields?: string
  /** 节点审批人规则 JSON 字符串 */
  nodeConfig?: string
  status: number
  remark?: string
  createTime?: string
  updateTime?: string
}

/**
 * 可发起的流程模板选项（只读、最小字段集）。
 *
 * 与 {@link FlowDefinitionVO} 的区别：后者是**管理端**视图，含 deploymentId /
 * nodeConfig / remark 等内部信息，鉴权为 `system:flow:query`；本类型服务于
 * 「发起申请」页，鉴权为 `system:approval:apply`，面向全体员工。
 */
export interface FlowOptionVO {
  id: number
  flowCode: string
  flowName: string
  category?: string
  /** 表单字段配置 JSON 字符串，用于动态渲染表单 */
  formFields?: string
}

export interface FlowDefinitionSaveRequest {
  id?: number
  flowCode: string
  flowName: string
  category?: string
  /** 设计器产出的 BPMN XML */
  bpmnXml?: string
  formFields?: string
  nodeConfig?: string
  status?: number
  remark?: string
}

/** 导入 BPMN XML 生成流程模板：flowCode / flowName 留空时取 BPMN 的 process id / name */
export interface FlowDefinitionImportRequest {
  bpmnXml: string
  flowCode?: string
  flowName?: string
  category?: string
  remark?: string
}

export interface FlowDefinitionPageQuery extends QueryPage {
  flowName?: string
  /** 精确匹配流程编码（审批详情按 flowCode 反查模板） */
  flowCode?: string
  status?: number
  /** 分类精确匹配，取值来自 flow_category 字典 */
  category?: string
}

/** 节点审批人规则项（node_config 的元素） */
export interface NodeConfigItem {
  nodeId: string
  nodeName: string
  /** 0未配置 1指定人员 2指定角色 3部门主管 */
  assigneeType: number
  assigneeValue: string
}

/** 表单字段配置（驱动发起申请的动态表单） */
export interface FormFieldItem {
  field: string
  label: string
  type: 'text' | 'textarea' | 'number' | 'date' | 'select'
  required?: boolean
  options?: string[]
}

/** 审批单 */
export interface ApprovalInstanceVO {
  id: number
  flowCode: string
  flowName: string
  title: string
  applicantId: number
  applicantName?: string
  /** 1草稿 2审批中 3已通过 4已驳回 5已撤销 */
  status: number
  statusName?: string
  currentNode?: string
  /** 表单数据 JSON 字符串 */
  formData?: string
  createTime?: string
}

/** 待办任务 */
export interface ApprovalTodoVO {
  taskId: string
  instanceId: number
  title: string
  flowName?: string
  applicantName?: string
  currentNode?: string
  createTime?: string
}

/** 审批流转记录（时间轴 / 已办） */
export interface ApprovalRecordVO {
  instanceId: number
  title?: string
  flowName?: string
  nodeName?: string
  handlerName?: string
  action?: string
  actionName?: string
  opinion?: string
  createTime?: string
}

/** 审批详情 */
export interface ApprovalDetailVO {
  instance: ApprovalInstanceVO
  records: ApprovalRecordVO[]
  currentTask?: ApprovalTodoVO
}

export interface ApprovalStartRequest {
  flowCode: string
  title: string
  /** 表单数据 JSON 字符串 */
  formData: string
}

export interface ApprovalCompleteRequest {
  taskId: string
  /** approve 同意 / reject 驳回 */
  action: string
  opinion?: string
}

export interface ApprovalTransferRequest {
  taskId: string
  targetUserId: number
}

/* ========== 第四阶段：工作台 ========== */

/** 图表通用数据项：分类名 + 数值 */
export interface ChartItemVO {
  label: string
  total: number
}

export interface LatestNoticeVO {
  id: number
  noticeTitle: string
  noticeType: number
  noticeLevel: number
  /** 1置顶 0普通 */
  topFlag: number
  publishTime?: string
  publisherName?: string
}

export interface DashboardOverviewVO {
  userCount: number
  deptCount: number
  todoCount: number
  myApplyCount: number
  monthlyTrend: ChartItemVO[]
  typeDistribution: ChartItemVO[]
  latestNotices: LatestNoticeVO[]
}

/* ========== 第四阶段：个人中心 ========== */

export interface ProfileVO {
  id: number
  username: string
  realName: string
  nickname?: string
  avatar?: string
  /** 1男 2女（业务上已取消「未知」） */
  gender?: number
  mobile?: string
  email?: string
  workNumber?: string
  position?: string
  entryDate?: string
  deptId?: number
  deptName?: string
  roles: string[]
  lastLoginTime?: string
  lastLoginIp?: string
}

export interface ProfileUpdateRequest {
  nickname?: string
  gender?: number
  mobile?: string
  email?: string
}

export interface PasswordChangeRequest {
  oldPassword: string
  newPassword: string
}

/* ========== 第四阶段：文件上传 ========== */

export interface FileUploadVO {
  id: number
  fileName: string
  /** 可直接用于 img / 附件链接的相对地址 */
  url: string
  fileSize: number
  fileType?: string
}

/* ========== 第四阶段：数据字典 ========== */

export interface DictTypeVO {
  id: number
  dictName: string
  dictType: string
  /** 0禁用 1启用 */
  status: number
  remark?: string
  createTime?: string
  updateTime?: string
}

export interface DictTypeSaveRequest {
  id?: number | null
  dictName: string
  dictType: string
  status?: number
  remark?: string
}

export interface DictTypePageQuery {
  pageNo?: number
  pageSize?: number
  dictName?: string
  dictType?: string
  status?: number
}

export interface DictDataVO {
  id: number
  dictType: string
  itemLabel: string
  itemValue: string
  sort: number
  /** 0禁用 1启用 */
  status: number
  /** 前端标签样式：el-tag 的 type */
  cssClass?: string
  /** 1为该类型默认项 */
  isDefault: number
  remark?: string
  createTime?: string
  updateTime?: string
}

export interface DictDataSaveRequest {
  id?: number | null
  dictType: string
  itemLabel: string
  itemValue: string
  sort?: number
  status?: number
  cssClass?: string
  isDefault?: number
  remark?: string
}

export interface DictDataPageQuery {
  pageNo?: number
  pageSize?: number
  dictType?: string
  itemLabel?: string
  status?: number
}

/* ========== 公告通知 ========== */

export interface NoticeAttachmentVO {
  id: number
  fileName: string
  /** 受鉴权保护的下载地址 */
  url: string
  fileSize?: number
}

export interface NoticeVO {
  id: number
  noticeTitle: string
  /** 1通知 2公告 3新闻 */
  noticeType: number
  /** 1普通 2重要 3紧急 */
  noticeLevel: number
  /** 0草稿 1已发布 2已下架 */
  noticeStatus: number
  /** 0否 1是 */
  topFlag: number
  /** 1全部 2指定部门 3指定人员 */
  publishScope: number
  attachmentIds?: string
  publishTime?: string
  publisherId?: number
  publisherName?: string
  viewCount?: number
  readCount?: number
  createTime?: string
}

export interface NoticeDetailVO {
  id: number
  noticeTitle: string
  noticeType: number
  noticeLevel: number
  noticeStatus: number
  topFlag: number
  publishScope: number
  content: string
  scopeDeptIds: number[]
  scopeUserIds: number[]
  attachmentIds: number[]
  attachments: NoticeAttachmentVO[]
  publishTime?: string
  publisherId?: number
  publisherName?: string
  viewCount?: number
  readCount?: number
  createTime?: string
  updateTime?: string
  /** 计划发布时间（ISO 字符串，后端 LocalDateTime）：到点自动发布；为空表示不启用定时发布 */
  scheduledPublishTime?: string | null
  /** 自动下架时间：到点自动下架；为空表示永不下架 */
  expireTime?: string | null
}

export interface NoticeSaveRequest {
  id?: number | null
  noticeTitle: string
  noticeType: number
  noticeLevel: number
  content: string
  publishScope: number
  scopeDeptIds?: number[]
  scopeUserIds?: number[]
  attachmentIds?: number[]
  topFlag?: number
  /** 计划发布时间（ISO 字符串，后端 LocalDateTime）：到点自动发布；为空表示不启用定时发布 */
  scheduledPublishTime?: string | null
  /** 自动下架时间：到点自动下架；为空表示永不下架 */
  expireTime?: string | null
}

export interface NoticePageQuery {
  pageNo?: number
  pageSize?: number
  noticeTitle?: string
  noticeType?: number
  noticeLevel?: number
  noticeStatus?: number
}

export interface MyNoticeVO {
  id: number
  noticeTitle: string
  noticeType: number
  noticeLevel: number
  topFlag: number
  publishTime?: string
  publisherId?: number
  publisherName?: string
  /** 0未读 1已读 */
  readFlag: number
  /** 0无附件 1有附件 */
  hasAttachment: number
}

export interface MyNoticeDetailVO extends MyNoticeVO {
  content: string
  viewCount?: number
  attachments: NoticeAttachmentVO[]
}

export interface MyNoticePageQuery {
  pageNo?: number
  pageSize?: number
  noticeTitle?: string
  noticeType?: number
  /** 0只看未读 1只看已读 */
  readFlag?: number
}

/* ========== 日志（登录日志 / 操作日志） ========== */

export interface LoginLogVO {
  id: number
  userId?: number
  username?: string
  /** 1账号密码登录 */
  loginType: number
  ipAddress?: string
  location?: string
  browser?: string
  os?: string
  /** 1成功 0失败 */
  loginStatus: number
  failReason?: string
  createTime?: string
}

export interface LoginLogPageQuery {
  pageNo?: number
  pageSize?: number
  /** 按操作人精确筛选；只能在后端算出的可见范围内收窄，越权时返回空集 */
  userId?: number
  username?: string
  loginStatus?: number
  ipAddress?: string
  startTime?: string
  endTime?: string
}

export interface OperationLogVO {
  id: number
  userId?: number
  username?: string
  module?: string
  operation?: string
  method?: string
  requestParams?: string
  responseResult?: string
  ipAddress?: string
  location?: string
  costTime?: number
  /** 1成功 0失败 */
  operationStatus: number
  errorMsg?: string
  createTime?: string
}

export interface OperationLogPageQuery {
  pageNo?: number
  pageSize?: number
  /** 按操作人精确筛选；只能在后端算出的可见范围内收窄，越权时返回空集 */
  userId?: number
  username?: string
  module?: string
  operationStatus?: number
  startTime?: string
  endTime?: string
}
