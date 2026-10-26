<script setup lang="ts">
/**
 * 流程模板管理：列表 + bpmn-js 可视化设计器 + 节点审批人配置 + 动态表单字段配置。
 */
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules, type UploadFile } from 'element-plus'
import BpmnModeler from '@/components/bpmn/BpmnModeler.vue'
import type { GatewayFlowInfo } from '@/components/bpmn/BpmnModeler.vue'
import { useUserStore } from '@/stores/user'
import {
  deleteFlowApi,
  getFlowDetailApi,
  getFlowBpmnApi,
  getFlowPageApi,
  getFlowVariablesApi,
  importFlowApi,
  saveFlowApi,
  updateFlowStatusApi,
} from '@/api/flow'
import AppIcon from '@/components/icons/AppIcon.vue'
import { LUCIDE_ICON_NAMES } from '@/utils/icons'
import { getRoleOptionsApi } from '@/api/role'
import { getDeptTreeApi } from '@/api/dept'
import { getDictDataByTypeApi } from '@/api/dict'
import { getUserCandidatesApi } from '@/api/user'
import {
  bindNodeFormApi,
  getFormOptionsApi,
  getNodeFieldPermissionsApi,
  getNodeFormsApi,
  saveNodeFieldPermissionsApi,
  unbindNodeFormApi,
} from '@/api/form'
import type {
  ConditionGroup,
  DeptVO,
  DictDataVO,
  FlowConditionItem,
  FlowConditionRule,
  FlowDefinitionPageQuery,
  FlowDefinitionSaveRequest,
  FlowDefinitionVO,
  FlowVariableVO,
  FormFieldItem,
  NodeConfigItem,
  NodeFieldPermissionItem,
  RoleOptionVO,
  UserVO,
} from '@/api/types'
import { logAsyncError } from '@/utils/asyncGuard'
import BranchConditionEditor from '../components/BranchConditionEditor.vue'
import DefinitionTable from './components/DefinitionTable.vue'

/** 审批人规则类型：0未配置 1指定人员 2指定角色 3部门主管 4申请人 5指定部门主管 */
const ASSIGNEE_UNSET = 0
const ASSIGNEE_USER = 1
const ASSIGNEE_ROLE = 2
const ASSIGNEE_LEADER = 3
/**
 * 申请人本人。
 *
 * <p>用于「提交后自己复核」「自我申报」这类环节。<b>已确认的副作用</b>：任务按候选人机制
 * 派发，申请人会在自己的待办里看到这条单子（即"自己审自己"），产品层面不做拦截。</p>
 */
const ASSIGNEE_APPLICANT = 4
/** 指定部门主管：取某个固定部门（assigneeValue 为部门 id）的主管，与「发起人部门主管」区分 */
const ASSIGNEE_DEPT_LEADER = 5

/** 条件原文编辑权限码（不建菜单：超管经角色自动放行，其余角色默认无此码） */
const CONDITION_RAW_PERM_CODE = 'system:flow:condition-raw'

/** 超级管理员角色：跳过一切前端权限校验（与 v-permission 指令一致） */
const SUPER_ROLE = 'SUPER_ADMIN'

/**
 * 条件运算符选项（与后端 BpmnXmlNormalizer.SUPPORTED_OPS 一致）。
 *
 * <p>empty / notEmpty 不需要比较值；contains 仅支持文本类型。</p>
 */
const CONDITION_OP_OPTIONS: Array<{ value: string; label: string }> = [
  { value: 'eq', label: '等于' },
  { value: 'ne', label: '不等于' },
  { value: 'gt', label: '大于' },
  { value: 'ge', label: '大于等于' },
  { value: 'lt', label: '小于' },
  { value: 'le', label: '小于等于' },
  { value: 'contains', label: '包含' },
  { value: 'notContains', label: '不包含' },
  { value: 'empty', label: '为空' },
  { value: 'notEmpty', label: '不为空' },
]

/** 不需要比较值的运算符 */
const VALUELESS_OPS = new Set(['empty', 'notEmpty'])

/** 条件变量名必须是合法标识符（与后端 IDENTIFIER_PATTERN 一致） */
const IDENTIFIER_PATTERN = /^[A-Za-z_$][A-Za-z0-9_$]*$/

/** BPMN 2.0 标准命名空间（前端预解析用） */
const BPMN_NS = 'http://www.omg.org/spec/BPMN/20100524/MODEL'

/** 设计器新建画布的默认流程标识（process_1 之类），不宜直接当作业务编码使用 */
const DEFAULT_PROCESS_ID = /^[Pp]rocess_?\d*$/

/**
 * 字段权限选项（本地常量而非从 types.ts 导入值）。
 *
 * <p>{@code <script setup>} 下模板只能访问顶层绑定；此处显式声明为本地常量，
 * 既保证模板可见，也避免为一个下拉项在 API 层导出运行时代码。</p>
 */
const FIELD_PERMISSION_OPTIONS: Array<{ value: number; label: string }> = [
  { value: 1, label: '只读' },
  { value: 2, label: '可编辑' },
  { value: 3, label: '隐藏' },
]

const ASSIGNEE_TYPE_OPTIONS = [
  { value: ASSIGNEE_UNSET, label: '未配置' },
  { value: ASSIGNEE_USER, label: '指定人员' },
  { value: ASSIGNEE_ROLE, label: '指定角色' },
  { value: ASSIGNEE_LEADER, label: '部门主管' },
  { value: ASSIGNEE_DEPT_LEADER, label: '指定部门主管' },
  { value: ASSIGNEE_APPLICANT, label: '申请人（本人）' },
]
const loading = ref(false)
const list = ref<FlowDefinitionVO[]>([])
const total = ref(0)
const query = reactive<FlowDefinitionPageQuery>({ pageNo: 1, pageSize: 10 })

/** 用户/角色/部门下拉数据（审批人配置用） */
const userOptions = ref<UserVO[]>([])
const roleOptions = ref<RoleOptionVO[]>([])
/** 部门树：「指定部门主管」策略选择固定部门的来源 */
const deptOptions = ref<DeptVO[]>([])
/** 流程分类下拉数据：取自 flow_category 字典（`itemValue` 与 oa_flow_definition.category 同值） */
const categoryOptions = ref<DictDataVO[]>([])

// ---------------- 设计器抽屉 ----------------
const designerVisible = ref(false)
const submitting = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const modelerRef = ref<InstanceType<typeof BpmnModeler>>()
const currentXml = ref('')

const form = reactive<FlowDefinitionSaveRequest>({
  flowCode: '',
  flowName: '',
  category: '',
  icon: '',
  status: 1,
  remark: '',
})

/** 节点审批人配置：key = 节点 id */
const nodeConfig = ref<NodeConfigItem[]>([])
/**
 * 动态表单字段配置（流程级）。
 *
 * <p><b>UI 已移除</b>：右侧面板的「申请表单字段」区块被「表单绑定」标签页取代 ——
 * 表单改为复用 {@code oa_form} 的 FormCreate 规则，字段清单由后端从规则派生，
 * 不再手工维护。</p>
 *
 * <p>但此变量**保留**：加载时回填、保存时原样回传。若一并删除，编辑保存会把历史
 * 模板的 {@code form_fields} 写成空数组，导致老数据丢失。彻底清空应等确认
 * 「存量 form_fields 已全部迁移到 oa_form」之后再做。</p>
 */
const formFields = ref<FormFieldItem[]>([])

/** 节点↔表单绑定（node_form_binds）：节点键 → 绑定项 */
const nodeFormBinds = ref<Array<{ id: number; nodeKey: string; nodeName: string; formId: number; formName: string; formCode: string; isPrimary: number }>>([])
/** 可选表单（已发布/启用的表单定义） */
const formOptions = ref<Array<{ id: number; formCode: string; formName: string }>>([])
/** 右侧面板当前标签页 */
const activeSideTab = ref('node')

/** 发起环节的节点键，与后端 /primary-form 的约定一致 */
const INIT_NODE_KEY = 'init'

/* ---------------- 分支条件配置 ----------------
 *
 * 数据流：画布（bpmn-js）↔ 结构化配置（flowConditions ↔ 后端 flow_config）。
 * 保存时后端把结构化条件注入 BPMN（只覆盖配置中出现的连线）；
 * 因此「删除条件 / 改为默认分支」必须同步清掉画布上的残留表达式，
 * 否则上次部署注入的表达式会在默认分支上继续生效。
 */

/** 属性面板宿主（第五标签页「流程属性」内的容器） */
const panelHost = ref<HTMLElement>()

/** 画布上的排他网关及其出线 */
const gatewayFlows = ref<GatewayFlowInfo[]>([])

/** 网关分支条件配置（对应后端 flow_config，保存时序列化提交） */
const flowConditions = ref<FlowConditionItem[]>([])

/** 当前选中网关 */
const condGatewayId = ref('')

/** 正在编辑条件的出线配置（null 表示编辑区收起） */
const editingFlow = ref<FlowConditionItem | null>(null)

/**
 * 分支条件可选变量（表单字段 + 系统内置变量）。
 *
 * <p>来自后端 {@code GET /flow-definition/{id}/variables}，后端保存时用同一份清单校验
 * 变量名合法性，因此这里选不到的变量后端也会拒，不会出现「能配但保存失败」。</p>
 */
const flowVariables = ref<FlowVariableVO>({ formFields: [], systemVars: [] })

const userStore = useUserStore()

/** 条件原文编辑权：超管经角色自动放行，其他角色需显式授权码 */
const canEditConditionRaw = computed(() =>
  userStore.roles.includes(SUPER_ROLE) || userStore.permissions.includes(CONDITION_RAW_PERM_CODE),
)

/** 当前网关的出线清单 */
const currentGatewayFlows = computed(
  () => gatewayFlows.value.find((g) => g.gatewayId === condGatewayId.value)?.flows ?? [],
)

/** 某条出线的条件配置项 */
function condOf(flowId: string): FlowConditionItem | undefined {
  return flowConditions.value.find((item) => item.flowId === flowId)
}

/** 某条出线是否为默认分支（本次配置优先，其次画布回显的 default 属性） */
function isDefaultFlow(flowId: string): boolean {
  if (condOf(flowId)?.isDefault) {
    return true
  }
  return gatewayFlows.value.find((g) => g.gatewayId === condGatewayId.value)?.defaultFlowId === flowId
}

/** 条件摘要（表格里的一行文案） */
function summarizeCondition(item: FlowConditionItem): string {
  if (item.mode === 'raw') {
    return item.expression || '（手写表达式未填写）'
  }
  const group = item.condition
  if (!group) {
    return ''
  }
  return describeGroup(group)
}

/** 递归描述条件组：子组带括号，与生成的表达式形态保持一致 */
function describeGroup(group: ConditionGroup): string {
  const joiner = group.logic === 'or' ? ' 或 ' : ' 且 '
  const parts: string[] = []
  for (const rule of group.rules ?? []) {
    const op = CONDITION_OP_OPTIONS.find((o) => o.value === rule.op)?.label ?? rule.op
    parts.push(VALUELESS_OPS.has(rule.op) ? `${rule.field} ${op}` : `${rule.field} ${op} ${rule.value}`)
  }
  for (const sub of group.groups ?? []) {
    const text = describeGroup(sub)
    if (text) {
      parts.push(`(${text})`)
    }
  }
  return parts.join(joiner)
}

/** 表格用：某条出线的条件摘要，无配置返回空串 */
function condSummaryOf(flowId: string): string {
  const item = condOf(flowId)
  return item ? summarizeCondition(item) : ''
}

/**
 * 归一化一条回显配置：补齐默认值，并把存量扁平结构升级为条件树。
 *
 * <p><b>为什么要升级</b>：早期版本存的是 {@code logic + conditions}（单层），
 * 现在用 {@code condition} 条件树（支持嵌套）。这里在读取时统一转换成新结构，
 * 历史模板无需人工迁移，保存后自然写回新格式。</p>
 */
function normalizeConditionItem(item: FlowConditionItem): FlowConditionItem {
  const mode = item.mode === 'raw' ? 'raw' : 'visual'
  const condition: ConditionGroup = item.condition
    ? {
        logic: item.condition.logic === 'or' ? 'or' : 'and',
        rules: (item.condition.rules ?? []).map((rule) => ({ ...rule })),
        groups: (item.condition.groups ?? []).map((sub) => ({
          logic: sub.logic === 'or' ? 'or' : 'and',
          rules: (sub.rules ?? []).map((rule) => ({ ...rule })),
          groups: [],
        })),
      }
    : {
        logic: item.logic === 'or' ? 'or' : 'and',
        rules: (item.conditions ?? []).map((rule) => ({ ...rule })),
        groups: [],
      }
  return {
    ...item,
    mode,
    isDefault: Boolean(item.isDefault),
    condition,
  }
}

/** 打开设计器时载入已保存的分支条件配置 */
function loadFlowConditions(row: FlowDefinitionVO) {
  flowConditions.value = parseJson<FlowConditionItem[]>(row.flowConfig, []).map(normalizeConditionItem)
  condGatewayId.value = ''
  editingFlow.value = null
}

/** 从画布重新采集网关出线（绘制、同步节点、保存前调用） */
function refreshGatewayFlows() {
  gatewayFlows.value = modelerRef.value?.getGatewayFlows() ?? []
  if (condGatewayId.value && !gatewayFlows.value.some((g) => g.gatewayId === condGatewayId.value)) {
    condGatewayId.value = ''
  }
}

/** 切换网关：收起编辑区，避免把上一条出线的配置误写到新网关 */
function onCondGatewayChange() {
  editingFlow.value = null
}

/**
 * 载入分支条件可选变量（表单字段 + 系统内置变量）。
 *
 * <p>依赖「已绑定发起环节主表单」：未绑定时后端只返回系统变量，此时仍可配条件，
 * 只是引用不到业务字段——界面上会自然表现为「申请表单字段」分组为空。</p>
 */
async function loadFlowVariables() {
  flowVariables.value = { formFields: [], systemVars: [] }
  if (!editingId.value) {
    return
  }
  try {
    flowVariables.value = await getFlowVariablesApi(editingId.value)
  } catch (error) {
    logAsyncError('加载分支条件变量清单失败', error)
  }
}

/** 打开某条出线的条件编辑区（无配置则给一条空条件起步） */
function editFlowCondition(flow: { flowId: string; gatewayId: string; targetNodeId: string; targetNodeName: string }) {
  const existing = condOf(flow.flowId)
  if (existing) {
    editingFlow.value = normalizeConditionItem(existing)
    return
  }
  const gateway = gatewayFlows.value.find((g) => g.gatewayId === flow.gatewayId)
  editingFlow.value = {
    mode: 'visual',
    flowId: flow.flowId,
    gatewayId: flow.gatewayId,
    gatewayName: gateway?.gatewayName ?? flow.gatewayId,
    targetNodeId: flow.targetNodeId,
    targetNodeName: flow.targetNodeName,
    isDefault: false,
    condition: {
      logic: 'and',
      rules: [{ field: '', op: 'eq', valueType: 'string', value: '' }],
      groups: [],
    },
  }
}

/** 收集条件树（含子组）里的全部规则，便于统一校验 */
function collectRules(group: ConditionGroup | undefined): FlowConditionRule[] {
  if (!group) {
    return []
  }
  const out: FlowConditionRule[] = [...(group.rules ?? [])]
  for (const sub of group.groups ?? []) {
    out.push(...collectRules(sub))
  }
  return out
}

/** 提交编辑区：校验后写回 flowConditions */
function commitEditingFlowCondition() {
  const item = editingFlow.value
  if (!item) {
    return
  }
  if (item.mode === 'raw') {
    if (!item.expression?.trim()) {
      ElMessage.warning('手写表达式模式下必须填写表达式')
      return
    }
  } else {
    const rules = collectRules(item.condition)
    if (rules.length === 0) {
      ElMessage.warning('至少配置一条条件；若想设为默认分支请用「设为默认」')
      return
    }
    for (const rule of rules) {
      if (!IDENTIFIER_PATTERN.test(rule.field)) {
        ElMessage.warning(`字段「${rule.field || '空'}」不是合法标识符（字母/下划线开头，仅含字母数字下划线）`)
        return
      }
      if (!CONDITION_OP_OPTIONS.some((op) => op.value === rule.op)) {
        ElMessage.warning(`运算符「${rule.op}」不受支持`)
        return
      }
      if (!VALUELESS_OPS.has(rule.op)) {
        if (rule.value === '' || rule.value === null || rule.value === undefined) {
          ElMessage.warning(`条件「${rule.field}」缺少比较值`)
          return
        }
        if (rule.valueType === 'number' && !/^-?\d+(\.\d+)?$/.test(String(rule.value))) {
          ElMessage.warning(`条件「${rule.field}」选择了数值类型，但比较值「${rule.value}」不是数值`)
          return
        }
      }
    }
  }
  // 有条件就不是默认分支：二者语义互斥（默认分支不配条件）
  const committed: FlowConditionItem = { ...item, isDefault: false }
  const index = flowConditions.value.findIndex((i) => i.flowId === committed.flowId)
  if (index >= 0) {
    flowConditions.value.splice(index, 1, committed)
  } else {
    flowConditions.value.push(committed)
  }
  editingFlow.value = null
}

/**
 * 把某条出线设为默认分支。
 *
 * <p>默认分支不配条件（条件都不满足时才走它），故同时清空该出线的条件配置与
 * 画布上残留的表达式；同网关的其它默认标记一并取消（一个网关只能有一条默认）。</p>
 */
function setFlowAsDefault(flow: { flowId: string; gatewayId: string; targetNodeName: string }) {
  for (const item of flowConditions.value) {
    if (item.gatewayId === flow.gatewayId) {
      item.isDefault = item.flowId === flow.flowId
      if (item.isDefault) {
        // 默认分支不配条件：清空条件树与手写表达式，避免「条件 + default」并存
        item.condition = { logic: 'and', rules: [], groups: [] }
        item.expression = ''
      }
    }
  }
  if (!condOf(flow.flowId)) {
    const gateway = gatewayFlows.value.find((g) => g.gatewayId === flow.gatewayId)
    flowConditions.value.push({
      mode: 'visual',
      flowId: flow.flowId,
      gatewayId: flow.gatewayId,
      gatewayName: gateway?.gatewayName ?? flow.gatewayId,
      targetNodeId: currentGatewayFlows.value.find((f) => f.flowId === flow.flowId)?.targetNodeId ?? '',
      targetNodeName: flow.targetNodeName,
      isDefault: true,
      condition: { logic: 'and', rules: [], groups: [] },
    })
  }
  // 画布上的旧表达式必须清掉：后端注入只覆盖配置出现的连线，不清就会「默认 + 条件」并存
  modelerRef.value?.clearFlowCondition(flow.flowId)
  if (editingFlow.value?.flowId === flow.flowId) {
    editingFlow.value = null
  }
  ElMessage.success(`已将「${flow.targetNodeName}」设为默认分支，原条件已清除`)
}

/** 删除某条出线的条件配置，并清掉画布上的残留表达式 */
function removeFlowCondition(flow: { flowId: string; targetNodeName: string }) {
  flowConditions.value = flowConditions.value.filter((item) => item.flowId !== flow.flowId)
  modelerRef.value?.clearFlowCondition(flow.flowId)
  if (editingFlow.value?.flowId === flow.flowId) {
    editingFlow.value = null
  }
}

/**
 * 保存前把「纯默认分支」的画布残留表达式清干净。
 *
 * <p>默认分支不配条件；上次部署注入的表达式还留在画布里，直接保存会让
 * 这条出线同时带着 conditionExpression 与 default，网关行为不确定。</p>
 */
function syncConditionsToCanvas() {
  for (const item of flowConditions.value) {
    const hasCondition = item.mode === 'raw'
      ? Boolean(item.expression?.trim())
      : collectRules(item.condition).length > 0
    if (item.isDefault && !hasCondition) {
      modelerRef.value?.clearFlowCondition(item.flowId)
    }
  }
}

/**
 * 属性面板里改过条件原文：该连线改由原文管理，作废结构化配置里同线条目，
 * 否则保存时后端注入会用结构化配置静默覆盖用户的原文修改。
 */
function onConditionRawChanged(flowId: string) {
  flowConditions.value = flowConditions.value.filter((item) => item.flowId !== flowId)
}

/* ---------------- 字段权限配置 ---------------- */
const permNodeKey = ref('')
const permFields = ref<NodeFieldPermissionItem[]>([])
const savingPerm = ref(false)

/**
 * 可配置字段权限的节点列表。
 *
 * <p>只列<b>已绑定表单</b>的节点：字段权限依附于「节点↔表单」关系，
 * 未绑定时配置无处生效，让用户先到「表单绑定」标签页绑定。</p>
 */
const permNodes = computed(() =>
  nodeFormBinds.value.map((b) => ({ nodeKey: b.nodeKey, nodeName: b.nodeName })),
)

/** 当前选中节点的绑定信息 */
const currentPermBind = computed(() => (permNodeKey.value ? bindOf(permNodeKey.value) : null))

/** 切换节点：载入该节点的字段权限清单 */
async function onPermNodeChange(nodeKey: string) {
  permFields.value = []
  const bind = bindOf(nodeKey)
  if (!editingId.value || !bind) {
    return
  }
  try {
    const vo = await getNodeFieldPermissionsApi(editingId.value, nodeKey, bind.formId)
    permFields.value = vo.fields ?? []
  } catch (error) {
    logAsyncError('加载节点字段权限失败', error)
  }
}

/** 保存当前节点的字段权限（整节点全量覆盖） */
async function saveFieldPermissions() {
  const bind = currentPermBind.value
  if (!editingId.value || !bind || !permFields.value.length) {
    return
  }
  savingPerm.value = true
  try {
    await saveNodeFieldPermissionsApi({
      flowDefId: editingId.value,
      nodeKey: permNodeKey.value,
      nodeName: bind.nodeName,
      formId: bind.formId,
      fields: permFields.value.map((f) => ({
        prop: f.prop,
        fieldLabel: f.label,
        permission: f.permission,
      })),
    })
    ElMessage.success('字段权限已保存')
    // 回读一次，让 configured 标记与后端一致
    await onPermNodeChange(permNodeKey.value)
  } catch (error) {
    logAsyncError('保存字段权限失败', error)
  } finally {
    savingPerm.value = false
  }
}

/**
 * 表单绑定下拉的候选节点。
 *
 * <p>发起环节（init）恒在最前并作为主表单候选；其余为画布上的任务节点。
 * 绑定关系以 nodeKey 为键，与后端 {@code oa_flow_node_form} 的约定一致。</p>
 */
const bindableNodes = computed(() => {
  const rest = nodeConfig.value.map((n) => ({ nodeKey: n.nodeId, nodeName: n.nodeName }))
  return [{ nodeKey: INIT_NODE_KEY, nodeName: '发起申请（init）' }, ...rest]
})

function bindOf(nodeKey: string) {
  return nodeFormBinds.value.find((b) => b.nodeKey === nodeKey) ?? null
}

/** 加载某流程模板的节点表单绑定 */
async function loadNodeForms(flowDefId: number) {
  try {
    const list = await getNodeFormsApi(flowDefId)
    nodeFormBinds.value = list.map((b) => ({
      id: b.id ?? 0,
      nodeKey: b.nodeKey,
      nodeName: b.nodeName ?? b.nodeKey,
      formId: b.formId,
      formName: b.formName ?? '',
      formCode: b.formCode ?? '',
      isPrimary: b.isPrimary ?? 0,
    }))
  } catch (error) {
    logAsyncError('加载节点表单绑定失败', error)
  }
}

/** 绑定/重绑某节点的表单：后端为全量覆盖语义，重绑即替换 */
async function onBindForm(nodeKey: string, formId: number | null) {
  if (!editingId.value) {
    ElMessage.warning('请先保存流程模板，再绑定节点表单')
    return
  }
  const existing = bindOf(nodeKey)
  try {
    if (!formId) {
      if (existing?.id) {
        await unbindNodeFormApi(existing.id)
      }
      nodeFormBinds.value = nodeFormBinds.value.filter((b) => b.nodeKey !== nodeKey)
      return
    }
    const target = formOptions.value.find((f) => f.id === formId)
    // 发起环节默认作为主表单；中间节点一律非主表单（同一流程只允许一个主表单）
    const isPrimary = nodeKey === INIT_NODE_KEY ? 1 : 0
    await bindNodeFormApi({
      flowDefId: editingId.value,
      nodeKey,
      nodeName: bindableNodes.value.find((n) => n.nodeKey === nodeKey)?.nodeName ?? nodeKey,
      formId,
      isPrimary,
    })
    await loadNodeForms(editingId.value)
    ElMessage.success(target ? `已绑定表单「${target.formName}」` : '已绑定表单')
  } catch (error) {
    logAsyncError('绑定节点表单失败', error)
    // 回滚下拉选中值，避免 UI 显示与后端不一致
    await loadNodeForms(editingId.value)
  }
}

const rules: FormRules = {
  flowCode: [
    { required: true, message: '请输入流程编码', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_]+$/, message: '只能包含字母、数字、下划线', trigger: 'blur' },
  ],
  flowName: [{ required: true, message: '请输入流程名称', trigger: 'blur' }],
}

/** 是否已有可发起的部署（决定提示文案） */
const hasDeployment = computed(() => Boolean(editingId.value))

async function loadPage() {
  loading.value = true
  try {
    const res = await getFlowPageApi(query)
    list.value = res.records
    total.value = res.total
  } catch (error) {
    logAsyncError('加载流程模板列表失败', error)
  } finally {
    loading.value = false
  }
}

function handleSizeChange(size: number) {
  query.pageSize = size
  void loadPage()
}

function handlePageChange(page: number) {
  query.pageNo = page
  void loadPage()
}

async function loadOptions() {
  try {
    const [users, roles, depts, categories, forms] = await Promise.all([
      getUserCandidatesApi(),
      getRoleOptionsApi(),
      // 「指定部门主管」策略的部门来源：取全量部门树（含已停用部门，历史模板可能仍指向它）
      getDeptTreeApi(),
      // 「分类」由字典驱动：新增一个分类只需在字典管理里加一条 flow_category 数据，无需改代码发版
      getDictDataByTypeApi('flow_category'),
      // 节点表单绑定候选：只取启用态表单，停用表单不可绑定到新节点
      getFormOptionsApi(),
    ])
    userOptions.value = users
    roleOptions.value = roles
    deptOptions.value = depts
    categoryOptions.value = categories
    formOptions.value = forms.map((f) => ({ id: f.id, formCode: f.formCode, formName: f.formName }))
  } catch (error) {
    logAsyncError('加载审批人选项失败', error)
  }
}

function resetQuery() {
  query.flowName = undefined
  query.status = undefined
  query.category = undefined
  query.pageNo = 1
  void loadPage()
}

// ---------------- 导入 BPMN ----------------
const importVisible = ref(false)
const importing = ref(false)
const importFileName = ref('')
const importXml = ref('')
const importSummary = ref('')

const importForm = reactive({ flowCode: '', flowName: '', category: '' })

function openImport() {
  importVisible.value = true
  importFileName.value = ''
  importXml.value = ''
  importSummary.value = ''
  Object.assign(importForm, { flowCode: '', flowName: '', category: '' })
}

/**
 * 前端先读一遍 XML，用于预填流程编码/名称并提示节点数。
 * 这里只是「预填」——后端仍会用权威解析器重新解析，前端不做审批人判定。
 */
function parseBpmnMeta(xml: string) {
  const doc = new DOMParser().parseFromString(xml, 'application/xml')
  if (doc.getElementsByTagName('parsererror').length > 0) {
    return null
  }
  const process = doc.getElementsByTagNameNS(BPMN_NS, 'process')[0]
  if (!process) {
    return null
  }
  return {
    key: process.getAttribute('id') ?? '',
    name: process.getAttribute('name') ?? '',
    nodeCount: doc.getElementsByTagNameNS(BPMN_NS, 'userTask').length,
  }
}

function handleImportFile(uploadFile: UploadFile) {
  const file = uploadFile.raw
  if (!file) {
    return
  }
  importFileName.value = file.name
  void file.text().then((text) => {
    importXml.value = text
    const meta = parseBpmnMeta(text)
    if (!meta) {
      importSummary.value = '无法解析该文件，请确认是合法的 BPMN 2.0 XML'
      return
    }
    // 默认标识（process_1）没有业务含义，不预填而是提醒用户自己起名——
    // 流程编码保存后不可修改，直接沿用会让模板列表里出现一堆 process_1
    const isDefaultId = DEFAULT_PROCESS_ID.test(meta.key)
    if (!importForm.flowCode && !isDefaultId) {
      importForm.flowCode = meta.key
    }
    if (!importForm.flowName) {
      importForm.flowName = meta.name
    }
    importSummary.value = isDefaultId
      ? `文件用的是默认流程标识 ${meta.key}，请自行填写流程编码；用户任务 ${meta.nodeCount} 个`
      : `process id = ${meta.key}，用户任务 ${meta.nodeCount} 个`
  })
}

async function submitImport() {
  if (!importXml.value) {
    ElMessage.warning('请先选择 BPMN 文件')
    return
  }
  importing.value = true
  try {
    const id = await importFlowApi({
      bpmnXml: importXml.value,
      flowCode: importForm.flowCode.trim() || undefined,
      flowName: importForm.flowName.trim() || undefined,
      category: (importForm.category || '').trim() || undefined,
    })
    ElMessage.success('导入成功，流程已部署')
    importVisible.value = false
    await loadPage()
    // 直接进入设计器：导入只自动解析「已按约定标注」的审批人，其余需在此补配
    await openEdit(await getFlowDetailApi(id))
  } catch (error) {
    logAsyncError('导入 BPMN 失败', error)
  } finally {
    importing.value = false
  }
}

/** 解析模板里存的 JSON 配置，坏数据不影响页面可用 */
function parseJson<T>(json: string | undefined, fallback: T): T {
  if (!json) {
    return fallback
  }
  try {
    return JSON.parse(json) as T
  } catch {
    return fallback
  }
}

async function openCreate() {
  try {
    editingId.value = null
    Object.assign(form, {
      id: undefined,
      flowCode: '',
      flowName: '',
      category: '',
      icon: '',
      status: 1,
      remark: '',
    })
    nodeConfig.value = []
    flowConditions.value = []
    condGatewayId.value = ''
    editingFlow.value = null
    gatewayFlows.value = []
    flowVariables.value = { formFields: [], systemVars: [] }
    formFields.value = [{ field: 'reason', label: '申请事由', type: 'textarea', required: true }]
    currentXml.value = ''
    designerVisible.value = true
  } catch (error) {
    logAsyncError('准备流程模板表单失败', error)
  }
}

async function openEdit(row: FlowDefinitionVO) {
  editingId.value = row.id
  Object.assign(form, {
    id: row.id,
    flowCode: row.flowCode,
    flowName: row.flowName,
    category: row.category ?? '',
    icon: row.icon ?? '',
    status: row.status,
    remark: row.remark ?? '',
  })
  nodeConfig.value = parseJson<NodeConfigItem[]>(row.nodeConfig, [])
  // 归一化：历史模板可能缺 options，补齐后 el-select(multiple) 才不会绑定到 undefined
  formFields.value = parseJson<FormFieldItem[]>(row.formFields, []).map((f) => ({
    ...f,
    options: f.type === 'select' ? (Array.isArray(f.options) ? f.options : []) : f.options,
  }))
  loadFlowConditions(row)
  try {
    currentXml.value = await getFlowBpmnApi(row.id)
  } catch {
    currentXml.value = ''
    ElMessage.warning('该模板暂无 BPMN 定义，请在设计器中重新绘制并保存')
  }
  designerVisible.value = true
  // 等设计器把 BPMN 导入画布后再同步一次：
  // 既为已有节点补齐 nodeKind（历史数据没有该字段，发布校验要用），
  // 也顺带把「BPMN 里存在但 node_config 缺失」的节点补进来（如手工加的 create task）。
  await nextTick()
  syncNodesFromCanvas()
  // 绑定与表单选项依赖已保存的模板 id，新建的模板此刻还没有 id，跳过即可
  if (editingId.value) {
    await loadNodeForms(editingId.value)
    await loadFlowVariables()
  }
}

/**
 * 从当前画布同步节点列表，保留已配置项，剔除已删除的节点。
 *
 * <p>采集范围含<b>用户任务与创建任务</b>（bpmn-js 里分别是 {@code bpmn:UserTask} 与
 * {@code bpmn:Task}）。此前只采前者，导致 create task 节点不进入 {@code node_config}，
 * 而后端以 {@code node_config} 为审批人唯一依据 → 该节点在待办里无人可办。</p>
 */
function syncNodesFromCanvas() {
  const tasks = modelerRef.value?.getUserTasks() ?? []
  const existing = new Map(nodeConfig.value.map((item) => [item.nodeId, item]))
  nodeConfig.value = tasks.map((task) => {
    const old = existing.get(task.id)
    return old
      ? { ...old, nodeName: task.name, nodeKind: task.kind }
      : {
          nodeId: task.id,
          nodeName: task.name,
          // 新节点默认「部门主管」：多数审批场景适用，且用户可改
          assigneeType: ASSIGNEE_LEADER,
          assigneeValue: '',
          nodeKind: task.kind,
        }
  })
  // 网关出线同步刷新：新画的网关要立刻出现在「分支条件」标签页里
  refreshGatewayFlows()
}

/**
 * 画布结构变化后的自动刷新（防抖）。
 *
 * <p>事件来源：{@code shape.added / shape.removed / connection.added / connection.removed /
 * element.changed}。拖拽或连续操作会产生事件洪流，故合并到 300ms 后统一刷新一次。</p>
 *
 * <p><b>为什么只刷节点与网关出线、不刷表单绑定列表</b>：表单绑定页的候选节点是
 * {@code nodeConfig} 的计算属性，节点清单更新后它会自动跟随，无需单独刷新；
 * 而真正需要网络请求的「已绑定关系」只在模板 id 变化时加载一次。</p>
 */
let syncTimer: ReturnType<typeof setTimeout> | null = null
function onCanvasElementsChanged() {
  if (syncTimer) {
    clearTimeout(syncTimer)
  }
  syncTimer = setTimeout(() => {
    syncTimer = null
    syncNodesFromCanvas()
  }, 300)
}




/** 指定人员/角色/指定部门主管时才必须填审批人值（部门主管层级、申请人本人可留空） */
function needAssigneeValue(type: number) {
  return type === ASSIGNEE_USER || type === ASSIGNEE_ROLE || type === ASSIGNEE_DEPT_LEADER
}

async function save() {
  if (!formRef.value) {
    return
  }
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }

  // 保存前先同步：把画布上新增/改名的节点收进 nodeConfig，
  // 网关出线也一并刷新，避免用过期清单做后续校验
  syncNodesFromCanvas()

  if (!modelerRef.value) {
    ElMessage.warning('未能获取流程图内容，请检查设计器是否正常加载')
    return
  }

  const missingAssignee = nodeConfig.value.filter((n) => needAssigneeValue(n.assigneeType) && !n.assigneeValue)
  if (missingAssignee.length > 0) {
    ElMessage.warning(`节点「${missingAssignee[0].nodeName}」尚未指定审批人`)
    return
  }

  // 未配置的节点允许保存（便于分步配置），但要明确提示后果
  const unsetNodes = nodeConfig.value.filter((n) => n.assigneeType === ASSIGNEE_UNSET)
  if (unsetNodes.length > 0) {
    try {
      await ElMessageBox.confirm(
        `有 ${unsetNodes.length} 个节点未配置审批人（${unsetNodes.map((n) => n.nodeName).join('、')}），` +
          '申请发起后这些节点将无人可审批。确定继续保存吗？',
        '审批人未配置',
        { type: 'warning' },
      )
    } catch {
      return
    }
  }

  // 纯默认分支不能带表达式：先把画布上的残留条件清掉，再取 XML，
  // 否则后端注入只覆盖配置出现的连线，默认分支会「条件 + default」并存
  syncConditionsToCanvas()
  const xml = await modelerRef.value.getXml()
  if (!xml) {
    ElMessage.warning('未能获取流程图内容，请检查设计器是否正常加载')
    return
  }

  submitting.value = true
  try {
    await saveFlowApi({
      ...form,
      flowCode: form.flowCode.trim(),
      flowName: form.flowName.trim(),
      // 下拉清空后可能是 undefined/''，统一收敛为「未设置」
      category: (form.category || '').trim() || undefined,
      icon: (form.icon || '').trim() || undefined,
      bpmnXml: xml,
      // 剔除仅前端使用的 nodeKind：后端 DTO 不含该字段，
      // 多余字段会让 node_config 结构与后端约定漂移，回显时反序列化也会带上脏键。
      nodeConfig: JSON.stringify(
        nodeConfig.value.map(({ nodeKind: _omit, ...rest }) => rest),
      ),
      formFields: JSON.stringify(formFields.value),
      // 空配置传 undefined：后端对空 flowConfig 不做任何注入，画布原文整体保留
      flowConfig: flowConditions.value.length
        ? JSON.stringify(flowConditions.value.map(toConditionPayload))
        : undefined,
    })
    ElMessage.success(editingId.value ? '修改成功，流程已重新部署' : '新增成功，流程已部署')
    designerVisible.value = false
    await loadPage()
  } finally {
    submitting.value = false
  }
}

/**
 * 生成提交给后端的分支条件载荷。
 *
 * <p>剔除 {@code logic} / {@code conditions} 两个存量字段：读取时已把它们升级到
 * {@code condition} 条件树，再原样回传会让一份配置里同时存在新旧两套条件，
 * 既增加体积，也容易让人误读「到底以哪份为准」。</p>
 */
function toConditionPayload(item: FlowConditionItem): FlowConditionItem {
  const { logic: _legacyLogic, conditions: _legacyRules, ...rest } = item
  return rest
}

async function remove(row: FlowDefinitionVO) {
  try {
    await ElMessageBox.confirm(
      `确定删除流程模板「${row.flowName}」吗？该操作会同时卸载 Flowable 中的流程定义。`,
      '删除确认',
      { type: 'warning' },
    )
  } catch {
    return
  }
  await deleteFlowApi(row.id)
  ElMessage.success('删除成功')
  await loadPage()
}

/** 流程模板状态：0草稿/未发布 1已发布（复用 oa_flow_definition.status，无 DDL） */
const FLOW_STATUS_DRAFT = 0
const FLOW_STATUS_PUBLISHED = 1

/**
 * 发布前置校验。
 *
 * <p>四条校验缺一不可，每条都对应一类「发布后跑不通」的故障：</p>
 * <ol>
 *   <li>至少 2 个用户任务：单节点流程无流转意义，且无法区分「发起人自己审」；</li>
 *   <li>审批人全部配置：后端以 {@code node_config} 为审批人唯一依据，
 *       未配置的节点在 Flowable 中拿不到候选人 → 待办里无人可办；</li>
 *   <li>已绑定表单：发起页靠主表单渲染申请内容，未绑定时只能提交空
 *       {@code form_data}，等于表单功能形同虚设；</li>
 *   <li>每个排他网关都有默认分支：所有条件都不满足时引擎没有可走的出线，
 *       流程实例会永久卡在网关（既不报错也不推进）。</li>
 * </ol>
 *
 * @returns 校验通过返回 null，否则返回提示文案
 */
function validatePublishable(): string | null {
  const tasks = nodeConfig.value
  if (tasks.length < 2) {
    return `当前仅有 ${tasks.length} 个任务节点，流程至少需要 2 个（发起后至少经一轮审批）`
  }
  const unset = tasks.filter(
    (n) => n.assigneeType === ASSIGNEE_UNSET || (needAssigneeValue(n.assigneeType) && !n.assigneeValue),
  )
  if (unset.length > 0) {
    return `以下节点未配置审批人：${unset.map((n) => n.nodeName).join('、')}`
  }
  if (nodeFormBinds.value.length === 0) {
    return '请先在设计器的「表单绑定」标签页为发起环节绑定已发布的表单'
  }
  const hasPrimary = nodeFormBinds.value.some((b) => b.isPrimary === 1)
  if (!hasPrimary) {
    return '请将发起环节（init）的表单标记为主表单，否则发起页无法渲染申请内容'
  }
  // 默认分支两个来源都认：画布回显的 default（历史模板/上次部署）或本次配置的 isDefault
  for (const gateway of gatewayFlows.value) {
    const hasDefault =
      gateway.defaultFlowId ||
      flowConditions.value.some((item) => item.gatewayId === gateway.gatewayId && item.isDefault)
    if (!hasDefault) {
      return `排他网关「${gateway.gatewayName}」没有默认分支：所有条件都不满足时流程会卡住，请在「分支条件」标签页为一条出线设置默认分支`
    }
  }
  return null
}

/**
 * 发布流程模板。
 *
 * <p>重复操作防护：已发布模板不显示发布按钮，此处再兜一层，
 * 避免并发点击或旧页面状态导致重复请求。</p>
 */
async function publishFlow(row: FlowDefinitionVO) {
  if (row.status === FLOW_STATUS_PUBLISHED) {
    ElMessage.info('该模板已发布')
    return
  }
  // 校验依赖设计器内实时画布；若当前未在设计器中编辑该模板，
  // 先打开并加载最新画布（openEdit 会挂载 modeler + syncNodesFromCanvas），
  // 加载完成后继续做同步与校验，不再提前 return，
  // 避免「要求先手动点同步节点」导致的发布死循环。
  if (!designerVisible.value || editingId.value !== row.id) {
    await openEdit(row)
  }
  syncNodesFromCanvas()
  const err = validatePublishable()
  if (err) {
    ElMessage.warning(err)
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认发布流程「${row.flowName}」？发布后该模板将出现在员工的「发起申请」下拉中。`,
      '发布确认',
      { type: 'warning', confirmButtonText: '确认发布', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  await applyStatus(row, FLOW_STATUS_PUBLISHED, '已发布')
  designerVisible.value = false
  await loadPage()
}

/** 下架流程模板：确认后立即生效，员工侧下拉不再显示 */
async function unpublishFlow(row: FlowDefinitionVO) {
  if (row.status !== FLOW_STATUS_PUBLISHED) {
    ElMessage.info('该模板未处于发布状态')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认下架流程「${row.flowName}」？下架后员工将无法再发起该流程，已发起的审批单不受影响。`,
      '下架确认',
      { type: 'warning', confirmButtonText: '确认下架', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  await applyStatus(row, FLOW_STATUS_DRAFT, '已下架')
}

/** 统一切换状态：就地改行数据，避免整表重拉（与项目「保存不重载」约定一致） */
async function applyStatus(row: FlowDefinitionVO, status: number, tip: string) {
  try {
    await updateFlowStatusApi(row.id, status)
    row.status = status
    ElMessage.success(tip)
  } catch (error) {
    logAsyncError('切换流程模板状态失败', error)
  }
}

/** 状态展示：草稿 / 已发布 */

onMounted(async () => {
  await loadOptions()
  await loadPage()
})
</script>

<template>
  <div class="flow-def">
    <el-card shadow="never">
      <el-form :inline="true" @submit.prevent="loadPage">
        <el-form-item label="流程名称">
          <el-input v-model="query.flowName" placeholder="请输入流程名称" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="query.category" placeholder="全部" clearable style="width: 160px">
            <el-option
              v-for="item in categoryOptions"
              :key="item.id"
              :label="item.itemLabel"
              :value="item.itemValue"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadPage">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="flow-def__toolbar">
        <el-button v-permission="'system:flow:add'" type="primary" @click="openCreate">
          新增流程模板
        </el-button>
        <el-button v-permission="'system:flow:add'" @click="openImport">导入 BPMN</el-button>
        <span class="designer__muted">
          用任意 BPMN 工具画好后导入，或直接用内置样例（请假 / 报销）
        </span>
      </div>

      <DefinitionTable
        :list="list"
        :loading="loading"
        @edit="openEdit"
        @publish="publishFlow"
        @unpublish="unpublishFlow"
        @remove="remove"
      />

      <el-pagination
        v-model:current-page="query.pageNo"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
        class="flow-def__pagination"
      />
    </el-card>

    <ResponsiveDialog
      v-model="designerVisible"
      :title="editingId ? '设计流程：' + form.flowName : '新增流程模板'"
      width="90%"
      top="4vh"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <div class="designer">
        <div class="designer__left">
          <div class="designer__toolbar">
            <el-button size="small" @click="syncNodesFromCanvas">同步节点</el-button>
            <span class="designer__tip">节点已自动同步，此按钮仅作手动兜底</span>
          </div>
          <BpmnModeler
            ref="modelerRef"
            :xml="currentXml"
            :panel-host="panelHost"
            :condition-raw-editable="canEditConditionRaw"
            @condition-raw-changed="onConditionRawChanged"
            @elements-changed="onCanvasElementsChanged"
          />
        </div>

<!-- 右侧面板：标签页组织 -->
<div class="designer__right">
  <el-tabs v-model="activeSideTab" class="designer__tabs">
    <!-- 标签 1：节点基本 -->
    <el-tab-pane label="节点基本" name="node">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="流程编码" prop="flowCode">
          <el-input
            v-model="form.flowCode"
            :disabled="hasDeployment"
            placeholder="如 leave_apply、expense_apply"
          />
          <div class="designer__muted">
            仅限字母、数字、下划线，保存后不可修改。流程图里的流程标识会自动对齐为该编码，无需在画布中另外设置。
          </div>
        </el-form-item>
        <el-form-item label="流程名称" prop="flowName">
          <el-input v-model="form.flowName" placeholder="如 请假流程" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.category" placeholder="请选择分类" clearable style="width: 100%">
            <el-option
              v-for="item in categoryOptions"
              :key="item.id"
              :label="item.itemLabel"
              :value="item.itemValue"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="流程图标">
          <div class="designer__icon-picker">
            <el-select
              v-model="form.icon"
              filterable
              clearable
              placeholder="选择图标（可选）"
              style="width: 260px"
            >
              <el-option v-for="name in LUCIDE_ICON_NAMES" :key="name" :label="name" :value="name">
                <span class="designer__icon-option">
                  <AppIcon :name="name" :size="14" />{{ name }}
                </span>
              </el-option>
            </el-select>
            <span class="designer__icon-preview">
              <AppIcon :name="form.icon || 'FileText'" :size="20" />
              <span class="designer__muted">发起页卡片图标</span>
            </span>
          </div>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="FLOW_STATUS_DRAFT">草稿</el-radio>
            <el-radio :value="FLOW_STATUS_PUBLISHED">已发布</el-radio>
          </el-radio-group>
          <div class="designer__muted">保存后仍需在列表点「发布」才对外可见</div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>

        <el-divider content-position="left">已识别节点</el-divider>
        <el-table :data="nodeConfig" size="small" border max-height="260">
          <el-table-column label="节点" min-width="120">
            <template #default="{ row }">
              <div>{{ row.nodeName }}</div>
              <el-tag size="small" :type="row.nodeKind === 'createTask' ? 'warning' : 'primary'">
                {{ row.nodeKind === 'createTask' ? '创建任务' : '用户任务' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="nodeId" label="节点 ID" min-width="110" show-overflow-tooltip />
          <template #empty>
            <el-empty description="请先绘制任务节点并同步" :image-size="50" />
          </template>
        </el-table>
        <div class="designer__muted designer__muted--block">
          含用户任务与创建任务两类；两者都支持配置审批人。
        </div>
      </el-form>
    </el-tab-pane>

    <!-- 标签 2：审批人配置 -->
    <el-tab-pane label="审批人配置" name="assignee">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="审批人是待办归属的唯一依据"
        description="未配置的节点在流程发起后无人可办，保存与发布时都会校验。「超时(小时)」留空则用全局默认（48 小时），填 0 表示该节点不计超时；「到期动作」在到期后由系统每分钟执行一次（仅标记 / 提醒 / 自动驳回）。"
        class="designer__alert"
      />
      <el-table :data="nodeConfig" size="small" border max-height="420">
        <el-table-column label="节点" min-width="110">
          <template #default="{ row }">
            <div>{{ row.nodeName }}</div>
            <el-tag size="small" :type="row.nodeKind === 'createTask' ? 'warning' : 'primary'">
              {{ row.nodeKind === 'createTask' ? '创建任务' : '用户任务' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="规则" width="120">
          <template #default="{ row }">
            <el-select v-model="row.assigneeType" size="small">
              <el-option
                v-for="opt in ASSIGNEE_TYPE_OPTIONS"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="审批人" min-width="130">
          <template #default="{ row }">
            <el-select
              v-if="row.assigneeType === ASSIGNEE_USER"
              v-model="row.assigneeValue"
              size="small"
              filterable
              placeholder="选择人员"
            >
              <el-option
                v-for="u in userOptions"
                :key="u.id"
                :label="u.realName"
                :value="String(u.id)"
              />
            </el-select>
            <el-select
              v-else-if="row.assigneeType === ASSIGNEE_ROLE"
              v-model="row.assigneeValue"
              size="small"
              placeholder="选择角色"
            >
              <el-option
                v-for="r in roleOptions"
                :key="r.id"
                :label="r.roleName"
                :value="String(r.id)"
              />
            </el-select>
            <el-select
              v-else-if="row.assigneeType === ASSIGNEE_LEADER"
              v-model="row.assigneeValue"
              size="small"
              placeholder="直属主管（默认）"
              style="width: 100%"
            >
              <el-option label="第 1 级 · 直属主管" value="1" />
              <el-option label="第 2 级 · 上级主管" value="2" />
              <el-option label="第 3 级 · 再上一级" value="3" />
            </el-select>
            <el-tree-select
              v-else-if="row.assigneeType === ASSIGNEE_DEPT_LEADER"
              :model-value="row.assigneeValue ? Number(row.assigneeValue) : undefined"
              :data="deptOptions"
              :props="{ label: 'deptName', children: 'children' }"
              node-key="id"
              check-strictly
              :render-after-expand="false"
              filterable
              size="small"
              placeholder="选择部门"
              style="width: 100%"
              @update:model-value="(v: number | null) => (row.assigneeValue = v == null ? '' : String(v))"
            />
            <span v-else-if="row.assigneeType === ASSIGNEE_APPLICANT" class="designer__muted">
              申请人本人审批（会出现在发起人的待办中）
            </span>
            <span v-else class="designer__warn">未配置审批人</span>
          </template>
        </el-table-column>
        <el-table-column label="超时(小时)" width="120">
          <template #default="{ row }">
            <!-- 留空 = 用全局默认；0 = 该节点不计超时；后端最终会 clamp 到 0~8760 -->
            <el-input-number
              v-model="row.timeoutHours"
              size="small"
              :min="0"
              :max="8760"
              :controls="false"
              placeholder="默认"
              style="width: 100%"
            />
          </template>
        </el-table-column>
        <el-table-column label="到期动作" width="120">
          <template #default="{ row }">
            <!-- 默认 0 仅标记：不改状态、只写一条超时记录，避免误伤业务 -->
            <el-select v-model="row.timeoutAction" size="small" placeholder="仅标记">
              <el-option :value="0" label="仅标记" />
              <el-option :value="1" label="提醒" />
              <el-option :value="2" label="自动驳回" />
            </el-select>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="请先绘制任务节点并同步" :image-size="50" />
        </template>
      </el-table>
    </el-tab-pane>

    <!-- 标签 3：表单绑定 -->
    <el-tab-pane label="表单绑定" name="form">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="节点表单的运行时填单页尚未开放"
        description="当前版本完成绑定与持久化；中间节点的填单与字段权限将在后续版本提供。发起环节的表单已可在「发起申请」页使用。"
        class="designer__alert"
      />
      <el-table :data="bindableNodes" size="small" border max-height="420">
        <el-table-column prop="nodeName" label="流程节点" min-width="130" />
        <el-table-column label="绑定表单" min-width="180">
          <template #default="{ row }">
            <el-select
              :model-value="bindOf(row.nodeKey)?.formId ?? null"
              placeholder="未绑定"
              clearable
              filterable
              size="small"
              style="width: 100%"
              @change="(val: number | null) => onBindForm(row.nodeKey, val)"
            >
              <el-option
                v-for="f in formOptions"
                :key="f.id"
                :label="`${f.formName}（${f.formCode}）`"
                :value="f.id"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="主表单" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="bindOf(row.nodeKey)?.isPrimary === 1" type="success" size="small">
              主
            </el-tag>
            <span v-else class="designer__muted">-</span>
          </template>
        </el-table-column>
      </el-table>
      <div class="designer__muted designer__muted--block">
        发起申请（init）默认作为主表单，决定「发起申请」页渲染的申请内容；
        中间节点可各自绑定表单，为后续的节点级填单预留。
      </div>
    </el-tab-pane>

    <!-- 标签 4：字段权限 -->
    <el-tab-pane label="字段权限" name="field">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="未配置的字段默认只读"
        description="审批场景下默认放开风险更高：审批人误改申请人填写的内容比看不到更难追销。如需让某个节点填写部分字段（如销假节点填「销假日期」），在这里把对应字段改为「可编辑」。"
        class="designer__alert"
      />
      <div class="designer__field-bar">
        <el-select
          v-model="permNodeKey"
          placeholder="选择节点"
          style="width: 220px"
          @change="onPermNodeChange"
        >
          <el-option
            v-for="n in permNodes"
            :key="n.nodeKey"
            :label="n.nodeName"
            :value="n.nodeKey"
          />
        </el-select>
        <span v-if="currentPermBind" class="designer__muted">
          表单：{{ currentPermBind.formName }}
        </span>
        <el-button
          type="primary"
          size="small"
          :disabled="!permNodeKey || !currentPermBind || !permFields.length"
          :loading="savingPerm"
          @click="saveFieldPermissions"
        >
          保存权限
        </el-button>
      </div>
      <el-table :data="permFields" size="small" border max-height="400">
        <el-table-column prop="label" label="字段" min-width="140">
          <template #default="{ row }">
            {{ row.label || row.prop }}
            <el-tag v-if="!row.configured" size="small" type="info">默认</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="prop" label="字段标识" min-width="130" show-overflow-tooltip />
        <el-table-column label="权限" width="130">
          <template #default="{ row }">
            <el-select v-model="row.permission" size="small">
              <el-option
                v-for="opt in FIELD_PERMISSION_OPTIONS"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="请先选择节点，并在「表单绑定」标签页为该节点绑定表单" :image-size="50" />
        </template>
      </el-table>
    </el-tab-pane>

    <!-- 标签 5：流程属性（bpmn-js 官方属性面板挂载点，选中画布元素后展示其标准属性） -->
    <el-tab-pane label="流程属性" name="property">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="画布元素的标准属性"
        description="在左侧画布选中节点或连线后，这里展示该元素的通用属性。流程编码、名称、分类与状态请到「节点基本」标签页维护（两处同源，保存时以画布同步结果为准）；节点 ID 是系统关联键，只读不可修改。"
        class="designer__alert"
      />
      <div ref="panelHost" class="designer__panel-host" />
      <div class="designer__muted designer__muted--block">
        选中排他网关的出线时，「分支条件」分组可查看该连线的条件表达式原文；
        是否能编辑由权限码 system:flow:condition-raw 决定（默认仅超管可改）。
        在此改动原文后，该连线的「分支条件」结构化配置会被作废，改由原文管理。
      </div>
    </el-tab-pane>

    <!-- 标签 6：分支条件（排他网关的判定条件与默认分支） -->
    <el-tab-pane label="分支条件" name="condition">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="条件只对排他网关（菱形）生效"
        description="引擎按连线顺序逐个求值，第一条满足的连线被采用，因此条件之间隐含「先到先得」；每个网关必须有一条默认分支兜底，否则所有条件都不满足时流程会卡住。保存时这里的条件会写入流程图，覆盖上次部署生成的表达式。"
        class="designer__alert"
      />
      <div class="designer__cond-bar">
        <span class="designer__muted">排他网关</span>
        <el-select
          v-model="condGatewayId"
          placeholder="请选择网关"
          style="width: 240px"
          @change="onCondGatewayChange"
        >
          <el-option
            v-for="g in gatewayFlows"
            :key="g.gatewayId"
            :label="g.gatewayName || g.gatewayId"
            :value="g.gatewayId"
          />
        </el-select>
        <span v-if="!gatewayFlows.length" class="designer__warn">
          画布上还没有排他网关，绘制后会自动出现在这里
        </span>
      </div>
      <el-table v-if="condGatewayId" :data="currentGatewayFlows" class="designer__cond-table" size="small" border max-height="260">
        <el-table-column label="去向" min-width="110">
          <template #default="{ row }">
            {{ row.targetNodeName || row.targetNodeId }}
          </template>
        </el-table-column>
        <el-table-column label="分支规则" min-width="220">
          <template #default="{ row }">
            <template v-if="isDefaultFlow(row.flowId)">
              <el-tag type="info" size="small">默认分支</el-tag>
              <span class="designer__muted">其余条件都不满足时走这里</span>
            </template>
            <template v-else-if="condOf(row.flowId)">
              <div v-if="condOf(row.flowId)?.name" class="designer__cond-name">
                {{ condOf(row.flowId)?.name }}
              </div>
              <span class="designer__muted">{{ condSummaryOf(row.flowId) }}</span>
            </template>
            <span v-else class="designer__warn">未配置</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210">
          <template #default="{ row }">
            <el-button icon-only
              v-if="!isDefaultFlow(row.flowId)"
              link
              type="primary"
              size="small"
              @click="editFlowCondition({ flowId: row.flowId, gatewayId: condGatewayId, targetNodeId: row.targetNodeId, targetNodeName: row.targetNodeName || row.targetNodeId })"
            >
              {{ condOf(row.flowId) ? '编辑条件' : '配置条件' }}
            </el-button>
            <el-button icon-only
              v-if="!isDefaultFlow(row.flowId)"
              link
              type="warning"
              size="small"
              @click="setFlowAsDefault({ flowId: row.flowId, gatewayId: condGatewayId, targetNodeName: row.targetNodeName || row.targetNodeId })"
            >
              设为默认
            </el-button>
            <el-button icon-only
              v-if="condOf(row.flowId) && !isDefaultFlow(row.flowId)"
              link
              type="danger"
              size="small"
              @click="removeFlowCondition({ flowId: row.flowId, targetNodeName: row.targetNodeName || row.targetNodeId })"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="该网关暂无出线" :image-size="50" />
        </template>
      </el-table>
      <div v-else-if="gatewayFlows.length" class="designer__muted designer__muted--block">
        请先选择一个排他网关。
      </div>

      <!-- 条件编辑区：交给独立组件处理（基础信息 + 模式切换 + 条件组树） -->
      <div v-if="editingFlow" class="designer__cond-editor">
        <el-divider content-position="left">
          编辑条件：通往「{{ editingFlow.targetNodeName || editingFlow.targetNodeId }}」
        </el-divider>
        <!-- key 绑定 flowId：切换编辑对象时让编辑器重新挂载，草稿随之重置 -->
        <BranchConditionEditor
          :key="editingFlow.flowId"
          :item="editingFlow"
          :variables="flowVariables"
          :raw-editable="canEditConditionRaw"
          @update:item="editingFlow = $event"
        />
        <div class="designer__cond-actions">
          <el-button type="primary" size="small" @click="commitEditingFlowCondition">确定</el-button>
          <el-button size="small" @click="editingFlow = null">取消</el-button>
        </div>
      </div>
      <div v-if="condGatewayId" class="designer__muted designer__muted--block">
        变量取自「发起环节主表单字段 + 系统内置变量」两组；条件在流程变量上求值，
        申请内容在发起时已平铺为同名变量。数值按数字比较，日期与文本按字典序比较，
        布尔取 true / false。保存时后端会校验变量名合法性并做表达式安全扫描。
      </div>
    </el-tab-pane>
  </el-tabs>
</div>
      </div>

      <template #footer>
        <el-button @click="designerVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="save">保存</el-button>
      </template>
    </ResponsiveDialog>

    <ResponsiveDialog
      v-model="importVisible"
      title="导入 BPMN 生成流程模板"
      width="600px"
      :close-on-click-modal="false"
    >
      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="import__tip"
        title="节点审批人按约定自动解析；未按约定标注的节点，导入后在设计器里补配即可"
        description="约定一（Flowable 标准属性）：flowable:candidateGroups=&quot;leader&quot; 部门主管，&quot;role:角色id&quot; 指定角色，flowable:candidateUsers=&quot;用户id&quot; 指定人员。约定二（扩展元素）：oa:assignee type=&quot;user|role|leader&quot; value=&quot;...&quot;。"
      />
      <el-form label-width="90px">
        <el-form-item label="BPMN 文件">
          <el-upload
            :auto-upload="false"
            :show-file-list="false"
            accept=".bpmn,.xml"
            :on-change="handleImportFile"
          >
            <el-button>选择文件</el-button>
          </el-upload>
          <span v-if="importFileName" class="import__file">{{ importFileName }}</span>
        </el-form-item>
        <el-form-item label="流程编码">
          <el-input v-model="importForm.flowCode" placeholder="留空则取文件的 process id，建议手动填写" />
        </el-form-item>
        <el-form-item label="流程名称">
          <el-input v-model="importForm.flowName" placeholder="留空取 BPMN 的 process name" />
        </el-form-item>
        <el-form-item label="分类">
          <!-- 同上：与 flow_category 字典同源，导入时不接受字典外的分类 -->
          <el-select v-model="importForm.category" placeholder="请选择分类" clearable style="width: 100%">
            <el-option
              v-for="item in categoryOptions"
              :key="item.id"
              :label="item.itemLabel"
              :value="item.itemValue"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="解析结果">
          <span class="designer__muted">{{ importSummary || '请先选择 BPMN 文件' }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button type="primary" :loading="importing" @click="submitImport">导入并部署</el-button>
      </template>
    </ResponsiveDialog>
  </div>
</template>

<style scoped src="./definition.css"></style>
