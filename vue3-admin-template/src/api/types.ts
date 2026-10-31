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
  /** 流程图标（Lucide 图标名） */
  icon?: string
  bpmnKey?: string
  deploymentId?: string
  /** 表单字段配置 JSON 字符串 */
  formFields?: string
  /** 节点审批人规则 JSON 字符串 */
  nodeConfig?: string
  /** 网关分支条件配置 JSON 字符串（后端部署前注入 conditionExpression） */
  flowConfig?: string
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
  /** 流程图标（Lucide 图标名），发起页卡片展示 */
  icon?: string
  /** 表单字段配置 JSON 字符串，用于动态渲染表单 */
  formFields?: string
}

export interface FlowDefinitionSaveRequest {
  id?: number
  flowCode: string
  flowName: string
  category?: string
  /** 流程图标（Lucide 图标名） */
  icon?: string
  /** 设计器产出的 BPMN XML */
  bpmnXml?: string
  formFields?: string
  nodeConfig?: string
  /** 网关分支条件配置 JSON 字符串；空表示清空全部可视化条件 */
  flowConfig?: string
  status?: number
  remark?: string
}

/** 条件取值类型：决定比较值控件形态与后端字面量生成方式 */
export type ConditionValueType = 'number' | 'string' | 'date' | 'boolean'

/**
 * 条件组：可嵌套一层子组，实现「(A 且 B) 或 (C 且 D)」这类复合规则。
 *
 * <p>产品层限制 2 层（根组 + 子组），子组内不再允许嵌组；
 * 后端保存时有同样的兜底校验，防止接口直传绕过。</p>
 */
export interface ConditionGroup {
  /** 组内连接逻辑：and（缺省）/ or */
  logic?: string
  /** 本组直接持有的条件 */
  rules?: FlowConditionRule[]
  /** 子条件组（最多一层） */
  groups?: ConditionGroup[]
}

/**
 * 网关分支条件配置项（与后端 FlowConditionItem 一一对应）。
 *
 * <p>按「连线」配置：BPMN 2.0 的条件表达式挂在 sequenceFlow 上，
 * 网关只持有一个 default 属性指向兜底连线。</p>
 *
 * <p>两种配置模式（{@code mode}）互斥：
 * <ul>
 *   <li>{@code visual}：可视化条件组，由后端递归生成 JUEL 表达式；</li>
 *   <li>{@code raw}：手写 EL 表达式原文，仅对持有 {@code system:flow:condition-raw} 的角色开放。</li>
 * </ul>
 * 切换模式时另一侧的值应清空，避免"界面显示一套、实际生效另一套"。</p>
 */
export interface FlowConditionItem {
  /** 配置模式：visual=可视化（缺省），raw=手写表达式 */
  mode?: 'visual' | 'raw'
  /** BPMN sequenceFlow 的 id */
  flowId: string
  /** 源网关 id */
  gatewayId: string
  /** 源网关名称（冗余，便于展示） */
  gatewayName?: string
  /** 目标节点 id（冗余，便于展示） */
  targetNodeId?: string
  /** 目标节点名称（冗余，便于展示） */
  targetNodeName?: string
  /** 分支名称（业务语义，如「大额审批」，用于列表展示与流转日志） */
  name?: string
  /** 分支描述（配置说明，不参与运行） */
  description?: string
  /** 是否默认（兜底）分支 */
  isDefault?: boolean
  /** 可视化条件树（mode=visual 时生效） */
  condition?: ConditionGroup
  /** 手写 EL 表达式原文（mode=raw 时生效） */
  expression?: string
  /** @deprecated 存量扁平结构的组合方式；读取后自动升级为 condition */
  logic?: string
  /** @deprecated 存量扁平结构的条件列表；读取后自动升级为 condition.rules */
  conditions?: FlowConditionRule[]
}

/** 单条条件规则 */
export interface FlowConditionRule {
  /** 变量名（表单字段 fieldKey 或系统内置变量，发起时已平铺为流程变量） */
  field: string
  /** 运算符：eq ne gt ge lt le contains notContains empty notEmpty */
  op: string
  /** 值类型：number 裸值 / string 加单引号字面量 / date 按字典序比较 / boolean 布尔字面量 */
  valueType?: ConditionValueType
  /** 比较值；empty / notEmpty 运算符下无需值 */
  value?: string
}

/** 分支条件可选变量项 */
export interface FlowVariableItem {
  /** 变量名（直接写进 JUEL 表达式，必须是合法标识符） */
  key: string
  /** 展示名 */
  label: string
  /** 取值类型，决定比较值控件形态 */
  type: ConditionValueType
}

/**
 * 分支条件可用变量清单（对应后端 FlowVariableVO）。
 *
 * <p>分两组下发，下拉按组渲染：表单字段取自发环节主表单，系统变量与表单无关。</p>
 */
export interface FlowVariableVO {
  formFields: FlowVariableItem[]
  systemVars: FlowVariableItem[]
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
  /** 0未配置 1指定人员 2指定角色 3部门主管 4申请人本人 5指定部门主管 */
  assigneeType: number
  assigneeValue: string
  /**
   * 节点超时小时数（阶段四）。留空/0 表示回落全局 `approval.timeout.hours`（0=不启用）。
   *
   * <p>建任务时后端据此写 Flowable dueDate 与实例 `due_time`，列表逾期后显示「超时」标记。</p>
   */
  timeoutHours?: number
  /**
   * 到期自动动作（阶段四）：0/空 仅标记、1 提醒、2 自动驳回。
   *
   * <p>仅在 {@link timeoutHours} > 0 时有意义；由后端定时任务每分钟扫描执行。</p>
   */
  timeoutAction?: number
  /**
   * 节点类型（仅前端维护，用于校验与展示，**不提交给后端**）。
   *
   * <p>bpmn-js 中「用户任务」为 {@code bpmn:UserTask}、「创建任务」为 {@code bpmn:Task}，
   * 两者都要能被采集与配置审批人。</p>
   */
  nodeKind?: 'userTask' | 'createTask'
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
  /** 1草稿 2审批中 3已通过 4已驳回 5已撤销 6已终止 7已挂起 */
  status: number
  statusName?: string
  currentNode?: string
  /** 单据编号（系统生成） */
  billCode?: string
  /** 表单数据 JSON 字符串 */
  formData?: string
  createTime?: string
  /** 当前节点应处理截止时间（阶段四超时），未配置超时为空 */
  dueTime?: string
  /** 是否已逾期（审批中且 dueTime 已过），由后端计算 */
  overdue?: boolean
  /** 发起人所属部门 */
  deptName?: string
  /** 发起人所属公司 */
  companyName?: string
}

/** 流程实例（全量）查询入参 */
export interface ApprovalInstancePageQuery extends QueryPage {
  /** 标题模糊 */
  title?: string
  /** 发起人姓名模糊 */
  applicantName?: string
  /** 状态：1草稿 2审批中 3已通过 4已驳回 5已撤销 6已终止 */
  status?: number
  /** 流程编码精确 */
  flowCode?: string
  /** 发起时间起 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss */
  beginTime?: string
  /** 发起时间止 */
  endTime?: string
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
  /** 申请人所属部门（后端解析 oa_department） */
  deptName?: string
  /** 申请人所属公司（部门 company_id → oa_company） */
  companyName?: string
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
  /** 单据发起人所属部门 */
  deptName?: string
  /** 单据发起人所属公司 */
  companyName?: string
}

/** 抄送任务（被抄送人视角） */
export interface ApprovalCcVO {
  /** 抄送记录 id（标记已读用） */
  id: number
  instanceId: number
  title?: string
  flowName?: string
  applicantName?: string
  deptName?: string
  companyName?: string
  currentNode?: string
  status?: number
  statusName?: string
  /** 抄送人姓名 */
  ccByName?: string
  remark?: string
  /** 0 未读、1 已读 */
  readFlag?: number
  readTime?: string
  createTime?: string
}

/** 抄送人候选 */
export interface CcUserVO {
  id: number
  realName?: string
  username?: string
  deptName?: string
}

/** 审批详情 */
export interface ApprovalDetailVO {
  instance: ApprovalInstanceVO
  records: ApprovalRecordVO[]
  currentTask?: ApprovalTodoVO
  /**
   * 当前用户是否可对当前节点审批（同意 / 驳回）。
   *
   * <p>与 {@code currentTask} 解耦：{@code currentTask} 仅表示「流程停在哪个节点」（申请人 / 实例总览
   * 用户也能看到），而此字段才表示「我能不能审批」。为真条件：当前节点存在且当前用户是候选人或超管。
   * 前端据此显示审批区，避免非候选人点了同意/驳回却报 3104。</p>
   */
  canApprove?: boolean
  /**
   * 发起环节主表单的 FormCreate 规则 JSON。
   *
   * <p>所有审批节点复用这一份表单，审批人打开待办时用它只读渲染申请人填写的内容；
   * 未绑定表单时为 undefined，前端降级为键值对展示。</p>
   */
  formRuleJson?: string
  /**
   * 当前节点允许编辑的字段标识集合。
   *
   * <p>未配置字段权限时为空数组 —— 全部只读（与后端「默认只读」约定一致）。
   * 销假场景下该节点会把「销假日期」等字段设为可编辑，员工填写后随审批提交。</p>
   */
  editableProps?: string[]
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
  /**
   * 本节点可编辑字段的补丁。
   *
   * <p>销假节点填写销假信息时提交；后端只合并被授权可编辑的字段，其余静默忽略。</p>
   */
  formDataPatch?: Record<string, unknown>
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

/* ========== 在线用户（会话快照，数据源为 Redis） ========== */

export interface OnlineUserVO {
  userId: number
  username: string
  realName?: string
  clientIp?: string
  /** 浏览器 UA 摘要（后端已截断） */
  userAgent?: string
  loginTime?: string
  expireTime?: string
  /** 已在线分钟数（后端算好，避免前后端各算一套） */
  onlineMinutes: number
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

/* ---------------- 低代码表单（FormCreate） ---------------- */

/**
 * FormCreate 规则项。
 *
 * <p>对应 FormCreate option 数组的单元素：`type` 决定渲染控件，`prop` 是数据字段名，
 * `rule` 承载校验（`required` / `min` / `max` / `pattern` / 自定义 `validator`）。</p>
 */
export interface FormRuleItem {
  /**
   * FormCreate 内置组件类型名。
   *
   * <p>⚠️ 必须用 FormCreate 的类型名，不能用 Element Plus 组件短名：
   * {@code inputNumber}（不是 number）、{@code datePicker}（不是 date）、
   * {@code timePicker}（不是 time）。写错时 FormCreate 不报错，只是静默不渲染该控件。</p>
   */
  type: string
  prop: string
  /**
   * 字段标签。
   *
   * <p>FormCreate 3.x 的标签字段名是 {@code title}；{@code label} 会被
   * {@code FormRender} 自动映射为 {@code title}（存量数据兼容用）。</p>
   */
  label?: string
  title?: string
  value?: unknown
  valueType?: string
  options?: unknown[]
  rule?: {
    required?: boolean
    message?: string
    min?: number
    max?: number
    pattern?: string
    trigger?: string
    validator?: unknown
  }
  [key: string]: unknown
}

/** 表单字段清单项（后端从规则派生，供预校验与详情页表头使用） */
export interface FormFieldSchemaItem {
  prop: string
  label: string
  type: string
  required: boolean
}

export interface FormVO {
  id: number
  formCode: string
  formName: string
  category?: string
  /** FormCreate 规则 JSON 字符串 */
  ruleJson?: string
  /** 字段清单 JSON 字符串 */
  fieldSchema?: string
  status: number
  remark?: string
  createTime?: string
  updateTime?: string
  /** 被多少个流程节点引用；>0 时删除会被后端拒绝 */
  bindCount?: number
}

export interface FormSaveRequest {
  /** 带 id 为更新，不带为新增 */
  id?: number
  formCode: string
  formName: string
  category?: string
  /** FormCreate 规则 JSON 字符串（由设计器产出） */
  ruleJson: string
  remark?: string
  status?: number
}

export interface FormPageQuery {
  keyword?: string
  status?: number
  pageNo?: number
  pageSize?: number
}

/** 流程节点↔表单 绑定项 */
export interface NodeFormVO {
  /** 绑定记录主键，解除绑定时按此调用删除接口 */
  id: number
  flowDefId: number
  flowCode?: string
  flowName?: string
  nodeKey: string
  nodeName?: string
  formId: number
  formCode?: string
  formName?: string
  /** FormCreate 规则 JSON 字符串 */
  ruleJson?: string
  isPrimary?: number
  sort?: number
}

export interface NodeFormBindRequest {
  flowDefId: number
  /** 发起环节统一用 init */
  nodeKey?: string
  nodeName?: string
  formId: number
  isPrimary?: number
}

/* ---------------- 节点表单字段权限 ---------------- */

/** 字段权限：1只读 2可编辑 3隐藏 */
export type FieldPermission = 1 | 2 | 3

export const FIELD_PERMISSION_OPTIONS: Array<{ value: FieldPermission; label: string }> = [
  { value: 1, label: '只读' },
  { value: 2, label: '可编辑' },
  { value: 3, label: '隐藏' },
]

export interface NodeFieldPermissionItem {
  prop: string
  label: string
  permission: FieldPermission
  /** 是否为显式配置；false 表示走默认只读 */
  configured?: boolean
}

export interface NodeFieldPermissionVO {
  flowDefId: number
  nodeKey: string
  nodeName?: string
  formId: number
  formCode?: string
  formName?: string
  /** 表单的全部字段（未配置的以只读补齐） */
  fields: NodeFieldPermissionItem[]
}

export interface NodeFieldPermissionSaveRequest {
  flowDefId: number
  nodeKey: string
  nodeName?: string
  formId: number
  fields: Array<{ prop: string; fieldLabel?: string; permission: FieldPermission }>
}

/* ========== 人力资源：岗位 / 员工档案 / 考勤 ========== */

export interface PositionVO {
  id: number
  positionCode: string
  positionName: string
  companyId: number
  deptId: number
  deptName?: string
  series: string
  levelCode?: string
  headcount: number
  onDutyCount: number
  overHeadcount: boolean
  description?: string
  status: number
  sort: number
  createTime?: string
}

export interface PositionSaveRequest {
  id?: number
  positionCode: string
  positionName: string
  deptId?: number
  series?: string
  levelCode?: string
  headcount?: number
  description?: string
  status?: number
  sort?: number
}

/**
 * 建档完整度看板（BIZ-OPT-09）。
 *
 * <p>维度缺失不会让功能立刻报错，而是几个月后集中爆发（社保缺号、年假算不出、
 * 合同到期无提醒、发工资缺卡号）。所以这张卡片存在的意义是「把隐患变成清单」。</p>
 */
export interface EmployeeCompletenessVO {
  /** 在职员工总数（分母） */
  totalOnDuty: number
  cards: CompletenessCard[]
}

export interface CompletenessCard {
  /** 维度编码，点击卡片查清单时原样回传 */
  dimension: string
  label: string
  count: number
  /** 占在职人数百分比（0~100，一位小数） */
  percent: number
}

export interface CompletenessItemVO {
  userId: number
  workNumber?: string
  realName?: string
  deptName?: string
  employmentStatus?: number
  employmentStatusLabel?: string
  entryDate?: string
  directLeaderName?: string
}

export interface EmployeeVO {
  id: number
  username: string
  realName: string
  gender?: number
  mobile?: string
  email?: string
  companyId?: number
  deptId?: number
  deptName?: string
  positionId?: number
  positionName?: string
  position?: string
  workNumber?: string
  entryDate?: string
  /** 司龄，如「2.3 年」 */
  seniority?: string
  employmentStatus: number
  employmentStatusLabel?: string
  directLeaderName?: string
  status: number
  createTime?: string
}

export interface EmployeePageQuery {
  keyword?: string
  deptId?: number
  positionId?: number
  employmentStatus?: number
  entryDateStart?: string
  entryDateEnd?: string
  pageNo?: number
  pageSize?: number
}

export interface ContractVO {
  id: number
  contractNo: string
  contractType: number
  contractTypeLabel?: string
  startDate: string
  endDate?: string
  signCount: number
  signDate?: string
  fileIds?: string
  status: number
  daysToExpire?: number
}

export interface CertVO {
  id: number
  certType: number
  certTypeLabel?: string
  certName: string
  certNo?: string
  issueOrg?: string
  issueDate?: string
  expireDate?: string
  fileIds?: string
  daysToExpire?: number
}

export interface ChangeVO {
  id: number
  changeType: number
  changeTypeLabel?: string
  effectiveDate: string
  beforeJson?: string
  afterJson?: string
  instanceId?: number
  remark?: string
  operatorName?: string
  createTime?: string
}

export interface EmployeeDetailVO {
  id: number
  username: string
  realName: string
  nickname?: string
  avatar?: string
  gender?: number
  mobile?: string
  email?: string
  companyId?: number
  deptId?: number
  deptName?: string
  positionId?: number
  positionName?: string
  position?: string
  workNumber?: string
  entryDate?: string
  seniority?: string
  employmentStatus: number
  employmentStatusLabel?: string
  directLeaderId?: number
  directLeaderName?: string
  status: number
  lastLoginTime?: string
  createTime?: string
  idCard?: string
  birthDate?: string
  maritalStatus?: number
  nativePlace?: string
  address?: string
  emergencyName?: string
  emergencyPhone?: string
  workStartDate?: string
  probationEndDate?: string
  bankName?: string
  bankAccount?: string
  socialNo?: string
  fundNo?: string
  contracts: ContractVO[]
  certs: CertVO[]
  changes: ChangeVO[]
  canViewSensitive: boolean
}

export interface EmployeeProfileRequest {
  employmentStatus?: number
  changeRemark?: string
  positionId?: number
  directLeaderId?: number
  workStartDate?: string
  probationEndDate?: string
  idCard?: string
  birthDate?: string
  maritalStatus?: number
  nativePlace?: string
  address?: string
  emergencyName?: string
  emergencyPhone?: string
  mobile?: string
  email?: string
  bankName?: string
  bankAccount?: string
  socialNo?: string
  fundNo?: string
}

export interface ContractSaveRequest {
  id?: number
  contractNo: string
  contractType?: number
  startDate: string
  endDate?: string
  signDate?: string
  fileIds?: string
  remark?: string
  renew?: boolean
}

export interface CertSaveRequest {
  id?: number
  certType: number
  certName: string
  certNo?: string
  issueOrg?: string
  issueDate?: string
  expireDate?: string
  fileIds?: string
  remark?: string
}

export interface ShiftVO {
  id: number
  shiftCode: string
  shiftName: string
  workStart: string
  workEnd: string
  restMinutes: number
  lateGrace: number
  earlyGrace: number
  flexible: number
  freeClock: number
  status: number
  standardHours?: string
  scheduleCount?: number
}

export interface ShiftSaveRequest {
  id?: number
  shiftCode: string
  shiftName: string
  workStart: string
  workEnd: string
  restMinutes?: number
  lateGrace?: number
  earlyGrace?: number
  flexible?: number
  freeClock?: number
  status?: number
}

export interface ScheduleBatchRequest {
  deptId: number
  month: string
  shiftId: number
  includeWeekend?: number
  overwrite?: number
}

export interface ScheduleVO {
  id: number
  userId: number
  realName?: string
  workNumber?: string
  scheduleDate: string
  shiftId?: number
  shiftName?: string
  dayType: number
  settled: number
}

export interface AttendanceVO {
  id: number
  userId: number
  realName?: string
  workNumber?: string
  deptId?: number
  deptName?: string
  attendanceDate: string
  checkInTime?: string
  checkOutTime?: string
  /** 上班打卡来源：1 网页打卡 / 2 考勤机 / 3 补卡 */
  checkInSource?: number
  /** 下班打卡来源：1 网页打卡 / 2 考勤机 / 3 补卡 */
  checkOutSource?: number
  checkInLocation?: string
  checkOutLocation?: string
  workDuration?: number
  attendanceStatus: number
  attendanceStatusLabel?: string
  lateMinutes?: number
  earlyMinutes?: number
  cardMissing?: number
  shiftName?: string
  settled?: number
  remark?: string
}

export interface AttendancePageQuery {
  month?: string
  deptId?: number
  userId?: number
  attendanceStatus?: number
  keyword?: string
  pageNo?: number
  pageSize?: number
}

/** 考勤机数据导入结果：判断成败看 imported/failed，不看接口是否报错 */
export interface MachineImportResult {
  /** 读取到的数据行数（不含表头、不含空行） */
  total: number
  /** 实际写入或更新的「人天」条数 */
  imported: number
  /** 与已有考勤机记录一致而跳过（重复上传同一文件时全部落这里） */
  duplicate: number
  /** 校验不通过的行数 */
  failed: number
  errors: MachineImportRow[]
  /** 已跳过但不算失败（如该侧已有网页打卡/补卡，保留原值） */
  warnings: MachineImportRow[]
}

export interface MachineImportRow {
  /** Excel 行号，从 1 开始且含表头，便于直接在文件里定位 */
  row: number
  message: string
}

/** 漏卡待处理项 */
export interface MissingCardVO {
  attendanceId: number
  userId: number
  workNumber?: string
  realName?: string
  deptId?: number
  deptName?: string
  attendanceDate: string
  /** 1 缺上班卡 / 2 缺下班卡 / 3 两侧都缺 */
  cardMissing: number
  missingLabel?: string
  /** 缺卡方向明细：[1] / [2] / [1,2] */
  missingDirections: number[]
  /** 已有审批中或已通过补卡单的方向，前端据此置灰按钮 */
  pendingDirections: number[]
  attendanceStatus?: number
  attendanceStatusLabel?: string
}

export interface AttendanceMonthVO {
  id: number
  userId: number
  realName?: string
  workNumber?: string
  deptId?: number
  deptName?: string
  month: string
  shouldDays: number
  actualDays: number
  lateCount: number
  earlyCount: number
  absentDays: number
  missingCount: number
  tripDays: number
  /** 假别明细原文（JSON），后端已按假别汇总 */
  leaveDaysJson?: string
  /** 假别明细展示文本，如「年假 2 天 · 病假 1 天」 */
  leaveDaysText?: string
  /** 加班时数原文（JSON，按工作日/休息日/节假日分档） */
  overtimeJson?: string
  overtimeText?: string
  status: number
  lockTime?: string
}

export interface TodayAttendanceVO {
  date: string
  scheduled: boolean
  shiftName?: string
  workStart?: string
  workEnd?: string
  checkInTime?: string
  checkOutTime?: string
  attendanceStatus?: number
  attendanceStatusLabel?: string
  lateMinutes?: number
  earlyMinutes?: number
  workedMinutes?: number
  canClockIn: boolean
  canClockOut: boolean
}

export interface ClockRequest {
  direction: 1 | 2
  location?: string
}

/* ========== 人力资源：假勤（额度 / 请假 / 加班 / 出差 / 补卡） ========== */

export interface LeaveQuotaVO {
  id: number
  userId: number
  realName?: string
  workNumber?: string
  deptId?: number
  deptName?: string
  leaveType: number
  leaveTypeLabel?: string
  year: number
  totalDays: number
  carryOverDays: number
  usedDays: number
  lockedDays: number
  availableDays: number
  expireDate?: string
  remark?: string
}

export interface QuotaAdjustRequest {
  userId: number
  leaveType: number
  year: number
  totalDays: number
  carryOverDays?: number
  remark: string
}

export interface LeaveVO {
  id: number
  billCode?: string
  userId: number
  realName?: string
  workNumber?: string
  deptId?: number
  deptName?: string
  leaveType: number
  leaveTypeLabel?: string
  startTime: string
  endTime: string
  leaveDays: number
  actualDays?: number
  returnDate?: string
  reason?: string
  attachmentIds?: string
  instanceId?: number
  leaveStatus: number
  leaveStatusLabel?: string
  cancelStatus: number
  canCancel: boolean
  createTime?: string
}

export interface LeavePageQuery {
  month?: string
  userId?: number
  deptId?: number
  leaveStatus?: number
  leaveType?: number
  keyword?: string
  pageNo?: number
  pageSize?: number
}

export interface LeaveSubmitRequest {
  leaveType: number
  startTime: string
  endTime: string
  leaveDays?: number
  reason?: string
  attachmentIds?: string
}

/** 假别政策：某城市某假别的法定天数（按年生效） */
export interface LeavePolicyVO {
  id?: number
  city: string
  leaveType: number
  leaveTypeLabel?: string
  days: number
  effectiveYear: number
  remark?: string
  updateTime?: string
}

/** 假别政策保存请求：id 为空视为新增，有值视为修改 */
export interface LeavePolicySaveRequest {
  id?: number
  city: string
  leaveType: number
  days: number
  effectiveYear: number
  remark?: string
}

export interface HrRequestVO {
  id: number
  billCode?: string
  bizType: number
  bizTypeLabel?: string
  userId: number
  realName?: string
  workNumber?: string
  deptName?: string
  bizDate?: string
  startDate?: string
  endDate?: string
  startTime?: string
  endTime?: string
  destination?: string
  hours?: number
  days?: number
  budget?: number
  overtimeType?: number
  compensateType?: number
  direction?: number
  reason?: string
  instanceId?: number
  status: number
  statusLabel?: string
  canCancel: boolean
  createTime?: string
}

export interface HrRequestSaveRequest {
  bizType: number
  overtimeType?: number
  overtimeDate?: string
  compensateType?: number
  startTime?: string
  endTime?: string
  hours?: number
  destination?: string
  startDate?: string
  endDate?: string
  budget?: number
  cardDate?: string
  direction?: number
  cardTime?: string
  reason?: string
}

/* ========== 人力资源：薪酬 ========== */

export interface SalaryItemVO {
  id: number
  itemCode: string
  itemName: string
  itemType: number
  includeSocial: number
  includeTax: number
  editable: number
  sort: number
  status: number
  remark?: string
}

export interface SalaryItemSaveRequest {
  id?: number
  itemCode: string
  itemName: string
  itemType?: number
  includeSocial?: number
  includeTax?: number
  editable?: number
  sort?: number
  status?: number
  remark?: string
}

export interface SocialPolicyVO {
  id: number
  city: string
  policyYear: number
  baseMin: number
  baseMax: number
  fundBaseMin: number
  fundBaseMax: number
  pensionPersonal: number
  pensionCompany: number
  medicalPersonal: number
  medicalCompany: number
  unemploymentPersonal: number
  unemploymentCompany: number
  injuryCompany: number
  maternityCompany: number
  fundPersonal: number
  fundCompany: number
  personalRateTotal: number
  remark?: string
}

export type SocialPolicySaveRequest = Omit<SocialPolicyVO, 'id' | 'personalRateTotal'> & { id?: number }

export interface SalaryProfileVO {
  id: number
  userId: number
  realName?: string
  workNumber?: string
  deptId?: number
  deptName?: string
  effectiveDate: string
  baseSalary: number
  postSalary: number
  perfSalary: number
  allowance: number
  standardSalary: number
  socialBase?: number
  fundBase?: number
  city?: string
  specialDeduct: number
  changeReason?: string
  createTime?: string
}

export interface SalaryProfileSaveRequest {
  userId: number
  effectiveDate: string
  baseSalary?: number
  postSalary?: number
  perfSalary?: number
  allowance?: number
  socialBase?: number
  fundBase?: number
  city?: string
  specialDeduct?: number
  changeReason?: string
}

export interface SalaryRecordVO {
  id: number
  userId: number
  realName?: string
  workNumber?: string
  deptId?: number
  deptName?: string
  month: string
  grossPay: number
  /** 在职天数折算系数（1 或空表示未折算） */
  prorationFactor?: number
  /** 折算后标准工资 */
  proratedStandard?: number
  /** 本年度是否有前单位收入（需年度汇算合并个税） */
  priorEmployerIncome?: boolean
  /** 换单位提示文案 */
  priorEmployerTip?: string
  attendanceDeduct: number
  socialPersonal: number
  fundPersonal: number
  tax: number
  otherDeduct: number
  netPay: number
  companyCost: number
  status: number
  statusLabel: string
  calcTime?: string
  payTime?: string
}

export interface SalaryItemLineVO {
  itemCode: string
  itemName: string
  itemType: number
  amount: number
}

export interface SalaryPayslipVO {
  id: number
  userId: number
  realName?: string
  workNumber?: string
  deptName?: string
  month: string
  baseSalary: number
  postSalary: number
  perfSalary: number
  allowance: number
  overtimePay: number
  otherIncome: number
  grossPay: number
  attendanceDeduct: number
  socialPersonal: number
  fundPersonal: number
  tax: number
  otherDeduct: number
  netPay: number
  socialCompany: number
  fundCompany: number
  socialBase: number
  cumulativeTaxable: number
  specialDeduct: number
  status: number
  statusLabel: string
  remark?: string
  calcTime?: string
  payTime?: string
  items: SalaryItemLineVO[]
}

/** 手工调整：两个金额都可只传一个，未传的保持不变 */
export interface SalaryAdjustPayload {
  otherIncome?: number
  otherDeduct?: number
  remark: string
}

export interface SalaryPageQuery {
  month?: string
  deptId?: number
  status?: number
  keyword?: string
  pageNo?: number
  pageSize?: number
}

/* ========== 人力资源：绩效 ========== */

export interface PerfGradeVO {
  gradeCode: string
  gradeName: string
  minScore: number
  maxScore: number
  coefficient: number
  sort?: number
}

export interface PerfPlanIndicatorVO {
  indicatorId: number
  indicatorCode?: string
  indicatorName?: string
  indicatorType?: number
  unit?: string
  weight: number
  sort?: number
}

export interface PerfPlanVO {
  id: number
  planCode: string
  planName: string
  cycleType: number
  cycleTypeLabel?: string
  scoreMode: number
  deptIds?: string
  deptNames?: string
  series?: string
  forceDistribution: number
  distributionLimit: number
  status: number
  statusLabel?: string
  remark?: string
  grades?: PerfGradeVO[]
  indicators?: PerfPlanIndicatorVO[]
  weightTotal?: number
  createTime?: string
}

export interface PerfPlanSaveRequest {
  id?: number
  planCode: string
  planName: string
  cycleType?: number
  scoreMode?: number
  deptIds?: string
  series?: string
  forceDistribution?: number
  distributionLimit?: number
  status?: number
  remark?: string
  grades?: PerfGradeVO[]
  indicators?: PerfPlanIndicatorVO[]
}

export interface PerfIndicatorVO {
  id: number
  indicatorCode: string
  indicatorName: string
  definition?: string
  indicatorType: number
  indicatorTypeLabel?: string
  unit?: string
  scoreStandard?: string
  series?: string
  status: number
  remark?: string
}

export interface PerfIndicatorSaveRequest {
  id?: number
  indicatorCode: string
  indicatorName: string
  definition?: string
  indicatorType?: number
  unit?: string
  scoreStandard?: string
  series?: string
  status?: number
  remark?: string
}

export interface PerfCycleVO {
  id: number
  cycleCode: string
  cycleName: string
  planId: number
  planName?: string
  startDate: string
  endDate: string
  stage: number
  stageLabel?: string
  targetDeadline?: string
  selfDeadline?: string
  scoreDeadline?: string
  calibrateDeadline?: string
  confirmDeadline?: string
  status: number
  sheetCount: number
  targetDone: number
  selfDone: number
  managerDone: number
  confirmDone: number
  remark?: string
}

export interface PerfCycleSaveRequest {
  id?: number
  cycleCode: string
  cycleName: string
  planId: number
  startDate: string
  endDate: string
  targetDeadline?: string
  selfDeadline?: string
  scoreDeadline?: string
  calibrateDeadline?: string
  confirmDeadline?: string
  remark?: string
}

export interface PerfSheetItemVO {
  id: number
  indicatorId?: number
  indicatorCode: string
  indicatorName: string
  indicatorType: number
  weight: number
  targetValue?: string
  measureStandard?: string
  selfScore?: number
  selfComment?: string
  managerScore?: number
  managerComment?: string
  finalScore?: number
  weightedScore?: number
}

export interface PerfAppealVO {
  id: number
  sheetId: number
  realName?: string
  reason: string
  status: number
  statusLabel?: string
  handleOpinion?: string
  handlerName?: string
  handleTime?: string
  beforeScore?: number
  afterScore?: number
  beforeGrade?: string
  afterGrade?: string
  createTime?: string
}

export interface PerfSheetVO {
  id: number
  cycleId: number
  cycleCode?: string
  cycleName?: string
  userId: number
  realName?: string
  workNumber?: string
  deptName?: string
  positionName?: string
  leaderName?: string
  targetStatus: number
  targetStatusLabel?: string
  selfStatus: number
  managerStatus: number
  calibrateStatus: number
  confirmStatus: number
  totalScore?: number
  gradeCode?: string
  coefficient?: number
  selfMissing: number
  hrScored: number
  /** 结果来源：1 系统评分 / 2 结果导入（历史迁移） */
  source?: number
  appealStatus?: number
  remark?: string
}

/** 绩效结果导入结果：成败看 imported/failed，不看接口是否报错 */
export interface PerfImportResult {
  /** 读取到的数据行数 */
  total: number
  /** 成功写入的人数 */
  imported: number
  /** 覆盖已有考核表的条数（其余为新建） */
  updated: number
  /** 校验不通过的行数 */
  failed: number
  errors: PerfImportRow[]
}

export interface PerfImportRow {
  /** Excel 行号（从 1 开始、含表头） */
  row: number
  message: string
}

export interface PerfSheetDetailVO extends PerfSheetVO {
  stage: number
  stageLabel?: string
  gradeName?: string
  selfTotal?: number
  managerTotal?: number
  managerComment?: string
  items: PerfSheetItemVO[]
  appeal?: PerfAppealVO
}

export interface PerfSheetPageQuery {
  cycleId?: number
  deptId?: number
  stage?: string
  keyword?: string
  pageNo?: number
  pageSize?: number
}

export interface PerfDistributionVO {
  dimension: string
  total: number
  countA: number
  countB: number
  countC: number
  countD: number
  avgScore: number
  aRatio: number
  overLimit: number
}

/** 目标设定入参 */
export interface PerfTargetSaveRequest {
  sheetId: number
  items: { itemId: number; targetValue?: string; measureStandard?: string }[]
}

/** 评分入参（自评 / 上级评分共用） */
export interface PerfScoreRequest {
  sheetId: number
  items: { itemId: number; score?: number; comment?: string }[]
  comment?: string
}

export interface PerfCalibrateRequest {
  sheetId: number
  score?: number
  gradeCode?: string
  reason: string
}

export interface PerfAppealRequest {
  sheetId: number
  reason: string
}

export interface PerfAppealHandleRequest {
  appealId: number
  status: number
  opinion?: string
  score?: number
}

/** 节点↔表单绑定（oa_flow_node_form 的业务视图；父级 nodeFormBinds 的元素结构） */
export interface NodeFormBindRow {
  id: number
  nodeKey: string
  nodeName: string
  formId: number
  formName: string
  formCode: string
  isPrimary: number
}
