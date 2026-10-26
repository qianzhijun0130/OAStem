import { computed, ref, type Ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getFlowVariablesApi } from '@/api/flow'
import { logAsyncError } from '@/utils/asyncGuard'
import { useUserStore } from '@/stores/user'
import type {
  ConditionGroup,
  FlowConditionItem,
  FlowConditionRule,
  FlowDefinitionVO,
  FlowVariableVO,
} from '@/api/types'
// GatewayFlowInfo 由 BpmnModeler 自己导出（画布采集结果的形状定义在画布组件旁）
import type { GatewayFlowInfo } from '@/components/bpmn/BpmnModeler.vue'

/**
 * 流程模板设计器的「分支条件 / 排他网关」状态与操作（FE-OPT-05 从 index.vue 抽出）。
 *
 * <p>模板里用到的名字全部由本函数返回，父页面同名解构即可，模板一行不用改。
 * 常量一并搬走：`CONDITION_OP_OPTIONS` / `VALUELESS_OPS` / `IDENTIFIER_PATTERN`
 * 只被本模块的校验与摘要用到，留在页面里会形成「两处各一份」的隐患。</p>
 */

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


export interface FlowConditionDeps {
  /** 当前编辑的模板 id（载入条件变量清单用；新建时为 null） */
  editingId: Ref<number | null>
  /**
   * 画布能力，只暴露本模块用到的两个方法。
   *
   * <p>不直接传 modelerRef：`Ref<InstanceType<typeof BpmnModeler>>` 与窄接口之间
   * 因 Ref 的属性可变性无法结构化赋值；传方法对象既解开类型纠缠，也让依赖一目了然。</p>
   */
  modeler: {
    getGatewayFlows: () => GatewayFlowInfo[]
    clearFlowCondition: (flowId: string) => void
  }
  /** 模板里存的 JSON 解析（父级已有实现，避免两份口径） */
  parseJson: <T>(json: string | undefined, fallback: T) => T
}

export function useFlowConditions(deps: FlowConditionDeps) {
  const { editingId, modeler, parseJson } = deps

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
    gatewayFlows.value = modeler.getGatewayFlows() ?? []
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
    modeler.clearFlowCondition(flow.flowId)
    if (editingFlow.value?.flowId === flow.flowId) {
      editingFlow.value = null
    }
    ElMessage.success(`已将「${flow.targetNodeName}」设为默认分支，原条件已清除`)
  }

  /** 删除某条出线的条件配置，并清掉画布上的残留表达式 */
  function removeFlowCondition(flow: { flowId: string; targetNodeName: string }) {
    flowConditions.value = flowConditions.value.filter((item) => item.flowId !== flow.flowId)
    modeler.clearFlowCondition(flow.flowId)
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
        modeler.clearFlowCondition(item.flowId)
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

  return {
    gatewayFlows,
    flowConditions,
    condGatewayId,
    editingFlow,
    flowVariables,
    canEditConditionRaw,
    currentGatewayFlows,
    condOf,
    isDefaultFlow,
    summarizeCondition,
    describeGroup,
    condSummaryOf,
    normalizeConditionItem,
    loadFlowConditions,
    refreshGatewayFlows,
    onCondGatewayChange,
    loadFlowVariables,
    editFlowCondition,
    collectRules,
    commitEditingFlowCondition,
    setFlowAsDefault,
    removeFlowCondition,
    syncConditionsToCanvas,
    onConditionRawChanged,
  }
}
