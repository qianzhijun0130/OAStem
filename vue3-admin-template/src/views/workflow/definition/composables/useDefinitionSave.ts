import { ElMessage, ElMessageBox } from 'element-plus'
import type { Ref } from 'vue'
import { saveFlowApi } from '@/api/flow'
import type {
  FlowConditionItem,
  FlowDefinitionSaveRequest,
  FormFieldItem,
  NodeConfigItem,
  NodeFormBindRow,
} from '@/api/types'
import type { GatewayFlowInfo } from '@/components/bpmn/BpmnModeler.vue'
import {
  ASSIGNEE_DEPT_LEADER,
  ASSIGNEE_ROLE,
  ASSIGNEE_UNSET,
  ASSIGNEE_USER,
} from '../assigneeConstants'

/**
 * 流程模板的「保存」与「发布前校验」（FE-OPT-05 从 index.vue 抽出）。
 *
 * <p>两者放在一起是有原因的：它们共用同一套「节点审批人是否配齐」的判定
 * （`needAssigneeValue`），拆开极易出现「保存放行、发布才报错」的口径不一致。</p>
 *
 * <p>外部状态一律依赖注入而非直接 import 页面变量——这样本模块可以脱离页面单测
 * 「载荷拼装」与「发布校验」两段纯逻辑。</p>
 */
export interface DefinitionSaveDeps {
  /** 表单字段（reactive 对象，本模块只读） */
  form: FlowDefinitionSaveRequest
  editingId: Ref<number | null>
  submitting: Ref<boolean>
  designerVisible: Ref<boolean>
  nodeConfig: Ref<NodeConfigItem[]>
  formFields: Ref<FormFieldItem[]>
  flowConditions: Ref<FlowConditionItem[]>
  nodeFormBinds: Ref<NodeFormBindRow[]>
  gatewayFlows: Ref<GatewayFlowInfo[]>
  /** 「节点基本」页签暴露的表单校验；页签未挂载时返回 undefined，按未通过处理 */
  validateForm: () => Promise<boolean | undefined> | undefined
  /** 画布能力：未挂载时返回 null（保存需要取 XML） */
  canvas: () => { getXml: () => Promise<string | undefined> } | null
  /** 保存前把画布节点同步进 nodeConfig（父级实现，含网关出线刷新） */
  syncNodes: () => void
  /** 保存前清掉「纯默认分支」在画布上的残留表达式（来自 useFlowConditions） */
  syncConditions: () => void
  /** 保存成功后刷新列表 */
  reloadList: () => Promise<void>
}

export function useDefinitionSave(deps: DefinitionSaveDeps) {
  const {
    form,
    editingId,
    submitting,
    designerVisible,
    nodeConfig,
    formFields,
    flowConditions,
    nodeFormBinds,
    gatewayFlows,
    validateForm,
    canvas,
    syncNodes,
    syncConditions,
    reloadList,
  } = deps

  /** 指定人员/角色/指定部门主管时才必须填审批人值（部门主管层级、申请人本人可留空） */
  function needAssigneeValue(type: number) {
    return type === ASSIGNEE_USER || type === ASSIGNEE_ROLE || type === ASSIGNEE_DEPT_LEADER
  }

  async function save() {
    const valid = await validateForm()
    if (!valid) {
      return
    }

    // 保存前先同步：把画布上新增/改名的节点收进 nodeConfig，
    // 网关出线也一并刷新，避免用过期清单做后续校验
    syncNodes()

    const canvasApi = canvas()
    if (!canvasApi) {
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
    syncConditions()
    const xml = await canvasApi.getXml()
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
      await reloadList()
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

  return { save, validatePublishable }
}
