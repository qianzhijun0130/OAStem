/**
 * Flowable 适配的属性面板 Provider（bpmn-js-properties-panel 扩展点）。
 *
 * <h2>为什么需要自写 provider</h2>
 * <p>官方 {@code BpmnPropertiesProviderModule} 自带一套基础分组（通用/说明/多实例…），
 * 但有两处不满足本系统要求：</p>
 * <ol>
 *   <li><b>流程级属性双入口</b>：官方对 {@code bpmn:Process} 根元素同样渲染
 *       「名称 / 标识 / 可执行」，而流程编码、名称、分类、状态、备注已在设计器
 *       「节点基本」标签页维护。两处都能改同一份数据 = 必然漂移；</li>
 *   <li><b>节点标识可改</b>：节点 ID 是 {@code node_config}（审批人）、节点表单绑定、
 *       字段权限三处的关联键，画布上改 ID 会让这些配置静默失效。</li>
 * </ol>
 *
 * <h2>实现方式：后置修补而非重写</h2>
 * <p>本 provider 以<b>更低优先级（500 &lt; 官方默认 1000）</b>注册，did 事件按优先级
 * 降序触发 → 官方 provider 的分组先入链，本 provider 的 updater 最后执行，拿到的是
 * 「已含官方分组」的数组，只做裁剪与追加：</p>
 * <ul>
 *   <li>{@code bpmn:Process}：通用组替换为「只读流程标识 + 指向节点基本标签页的说明」；</li>
 *   <li>其余元素：通用组里的「标识」条目替换为只读输入框；</li>
 *   <li>{@code bpmn:SequenceFlow}：追加「分支条件」分组，承载条件表达式原文
 *       （是否可编辑由权限码 {@code system:flow:condition-raw} 决定，见
 *       {@link setConditionRawEditable}）。</li>
 *   <li>所有分组统一声明 {@code shouldOpen: true}：库默认折叠分组，不补则用户
 *       选中元素后看不到任何属性（详见 {@link postProcessGroups}）。</li>
 * </ul>
 *
 * <h2>条件原文的覆盖语义</h2>
 * <p>结构化条件（「分支条件」标签页 → {@code flow_config}）在<b>保存时由后端注入</b>，
 * 且只覆盖配置中出现的连线；未出现的连线保留画布原文。因此在本面板改过某连线的
 * 原文后，必须让该连线的结构化条目作废，否则保存时会被静默覆盖 —— 原文写入成功后
 * 会向 eventBus 抛 {@link CONDITION_RAW_CHANGED_EVENT}，由 BpmnModeler 转成 Vue
 * 事件通知页面移除对应配置项。</p>
 */
import { getBusinessObject, is } from 'bpmn-js/lib/util/ModelUtil'
import {
  DescriptionEntry,
  Group,
  TextAreaEntry,
  TextFieldEntry,
  isTextAreaEntryEdited,
} from '@bpmn-io/properties-panel'
import { useService } from 'bpmn-js-properties-panel'
import type { Element } from 'diagram-js/lib/model/Types'
import type { EntryDefinition, EntryProps, GroupDefinition } from '@bpmn-io/properties-panel'

/**
 * 属性面板分组定义（本地扩展）。
 *
 * <p>{@code shouldOpen} 是 @bpmn-io/properties-panel 实际支持的运行时属性（决定分组初始
 * 是否展开，默认 false），但库的公开类型 {@code GroupDefinition} 没有声明它——
 * 直接用会在 {@code vue-tsc} 下报「对象字面量只能指定已知属性」。</p>
 *
 * <p>这里集中扩展一次，而不是在两处补 {@code as} 断言：断言会把类型系统在这一片架空，
 * 之后再拼错字段名也检查不出来。</p>
 */
type PanelGroupDefinition = GroupDefinition & { shouldOpen?: boolean }

/* ------------------------------------------------------------------ 常量 */

/**
 * 本 provider 的注册优先级：官方默认 1000（bpmn-js-properties-panel 源码
 * DEFAULT_PRIORITY），本 provider 必须取更低值，保证分组后处理最后执行。
 */
const POST_PROCESS_PRIORITY = 500

/** 官方基础 provider 的「通用」分组 id */
const GENERAL_GROUP_ID = 'general'

/** 官方「标识」条目 id（源码 IdProps: id: 'id'） */
const ID_ENTRY_ID = 'id'

/** 本 provider 追加的「分支条件」分组 id */
const CONDITION_GROUP_ID = 'flowCondition'

/** 条件表达式原文条目 id */
const CONDITION_EXPRESSION_ENTRY_ID = 'conditionExpression'

/** 条件分组内的提示行 id */
const CONDITION_HINT_ENTRY_ID = 'conditionHint'

/**
 * 条件原文被修改后抛出的事件。
 *
 * <p>载荷 {@code { flowId }}：被改写的连线 id。监听方（页面「分支条件」标签页）
 * 应把该连线对应的结构化配置项移除，避免保存时被后端注入逻辑静默覆盖。</p>
 */
export const CONDITION_RAW_CHANGED_EVENT = 'flowable.condition.rawChanged'

/* ------------------------------------------------------------------ 类型 */

/** zh-CN.ts 注入的 translate 服务形状 */
type TranslateFn = (template: string, replacements?: Record<string, string>) => string

/** 属性面板服务（本 provider 只用 registerProvider） */
interface PropertiesPanelService {
  registerProvider: (priority: number, provider: unknown) => void
}

/** didi 注入器（构造期取 translate） */
interface InjectorLike {
  get: (name: string, strict?: boolean) => unknown
}

/** commandStack（只用到 execute） */
interface CommandStackLike {
  execute: (command: string, context: Record<string, unknown>) => void
}

/** bpmnFactory（创建 FormalExpression） */
interface BpmnFactoryLike {
  create: (type: string, attrs: Record<string, unknown>) => ModdleElementLike
}

/** eventBus（只用到 fire） */
interface EventBusLike {
  fire: (name: string, payload: Record<string, unknown>) => unknown
}

/** moddle 元素最小形状：属性读写 + 父指针 */
interface ModdleElementLike {
  get: (name: string) => unknown
  $parent?: ModdleElementLike
}

/* ------------------------------------------------------------------ 条件原文编辑开关 */

/**
 * 条件原文编辑开关（模块级单例）。
 *
 * <p>didi 模块无法直接接收 Vue 运行期数据，故用「挂载前写入、渲染时读取」的
 * 单例承接权限标志：BpmnModeler 在 {@code new Modeler()} 之前调用
 * {@link setConditionRawEditable}，provider 渲染条目时再读。</p>
 *
 * <p>权限码 {@code system:flow:condition-raw} 不建菜单、无 DDL：超管经角色
 * 自动放行，其他角色默认无此码 → 实际仅超级管理员可编辑原文。</p>
 */
const conditionRawState = { editable: false }

/** 设置条件原文是否可编辑（应在创建 modeler 之前调用） */
export function setConditionRawEditable(editable: boolean): void {
  conditionRawState.editable = editable
}

/* ------------------------------------------------------------------ 条目组件 */

/**
 * 只读「标识」条目。
 *
 * <p>节点 ID 是审批人配置、表单绑定、字段权限的关联键，禁止在属性面板修改；
 * 需要查看时以禁用输入框展示。</p>
 */
function readOnlyIdEntry(): EntryDefinition {
  return {
    id: ID_ENTRY_ID,
    component: ReadOnlyId,
    // 只读字段不存在「未保存编辑」，不显示编辑圆点
    isEdited: () => false,
  }
}

function ReadOnlyId(props: EntryProps) {
  const translate = useService<TranslateFn>('translate')
  // debounceInput 由官方 BpmnPropertiesPanelModule 依赖的 DebounceInputModule 注册；
  // 所有 TextFieldEntry/TextAreaEntry 都必须显式透传，否则条目渲染时 debounce 为 undefined 直接抛错
  const debounce = useService<unknown>('debounceInput')
  const getValue = (element: Element) => String(getBusinessObject(element).id ?? '')
  return TextFieldEntry({
    element: props.element,
    id: ID_ENTRY_ID,
    label: translate('ID'),
    getValue,
    // disabled 输入框不会触发 setValue；空实现仅为满足工厂函数签名
    setValue: () => undefined,
    debounce,
    disabled: true,
  })
}

/**
 * 流程根元素的「标识」只读条目。
 *
 * <p>流程标识保存部署时由后端对齐为流程编码（BpmnXmlNormalizer.overrideProcessId），
 * 面板上改动没有意义，只读展示。</p>
 */
function readOnlyProcessIdEntry(): EntryDefinition {
  return {
    id: 'processIdReadonly',
    component: ReadOnlyProcessId,
    isEdited: () => false,
  }
}

function ReadOnlyProcessId(props: EntryProps) {
  const translate = useService<TranslateFn>('translate')
  const debounce = useService<unknown>('debounceInput')
  const getValue = (element: Element) => String(getBusinessObject(element).id ?? '')
  return TextFieldEntry({
    element: props.element,
    id: 'processIdReadonly',
    label: translate('流程标识'),
    getValue,
    setValue: () => undefined,
    debounce,
    disabled: true,
  })
}

/**
 * 流程根属性的去向说明：引导用户到「节点基本」标签页维护流程级四项。
 */
function processNoticeEntry(): EntryDefinition {
  return {
    id: 'processNotice',
    component: ProcessNotice,
    isEdited: () => false,
  }
}

function ProcessNotice(props: EntryProps) {
  const translate = useService<TranslateFn>('translate')
  return DescriptionEntry({
    element: props.element,
    forId: 'processIdReadonly',
    value: translate('流程编码、名称、分类、状态与备注统一在「节点基本」标签页维护；保存部署时流程标识会自动对齐为流程编码。'),
  })
}

/**
 * 条件表达式原文条目（多行等宽文本框）。
 *
 * <p>无编辑权限时整体禁用；有权限时可改，写入成功后抛
 * {@link CONDITION_RAW_CHANGED_EVENT} 通知外层作废该连线的结构化配置。</p>
 */
function conditionExpressionEntry(): EntryDefinition {
  return {
    id: CONDITION_EXPRESSION_ENTRY_ID,
    component: ConditionExpression,
    isEdited: isTextAreaEntryEdited,
  }
}

function ConditionExpression(props: EntryProps) {
  const commandStack = useService<CommandStackLike>('commandStack')
  const bpmnFactory = useService<BpmnFactoryLike>('bpmnFactory')
  const eventBus = useService<EventBusLike>('eventBus')
  const debounce = useService<unknown>('debounceInput')
  const translate = useService<TranslateFn>('translate')

  // 与官方条目一致：getValue 取渲染时传入的 element，避免闭包捕获过期实例
  const getValue = (element: Element) =>
    readConditionBody(getBusinessObject(element) as ModdleElementLike)

  const setValue = (value: string | undefined) => {
    const businessObject = getBusinessObject(props.element) as ModdleElementLike
    const existing = businessObject.get('conditionExpression') as ModdleElementLike | undefined
    if (value) {
      if (existing) {
        // 已有表达式：只改 body，保留元素本身
        commandStack.execute('element.updateModdleProperties', {
          element: props.element,
          moddleElement: existing,
          properties: { body: value },
        })
      } else {
        // 新建 FormalExpression；$parent 必须指向 businessObject，
        // 否则保存 XML 时元素挂载位置不正确（与官方 createElement 行为一致）
        const expression = bpmnFactory.create('bpmn:FormalExpression', { body: value })
        expression.$parent = businessObject
        commandStack.execute('element.updateModdleProperties', {
          element: props.element,
          moddleElement: businessObject,
          properties: { conditionExpression: expression },
        })
      }
    } else if (existing) {
      // 清空即删除条件（恢复普通流转）
      commandStack.execute('element.updateModdleProperties', {
        element: props.element,
        moddleElement: businessObject,
        properties: { conditionExpression: undefined },
      })
    }
    eventBus.fire(CONDITION_RAW_CHANGED_EVENT, { flowId: props.element.id })
  }

  return TextAreaEntry({
    element: props.element,
    id: CONDITION_EXPRESSION_ENTRY_ID,
    label: translate('条件表达式'),
    getValue,
    setValue,
    debounce,
    monospace: true,
    disabled: !conditionRawState.editable,
  })
}

/** 条件分组内的指引行：按编辑权限给出不同文案 */
function conditionHintEntry(): EntryDefinition {
  return {
    id: CONDITION_HINT_ENTRY_ID,
    component: ConditionHint,
    isEdited: () => false,
  }
}

function ConditionHint(props: EntryProps) {
  const translate = useService<TranslateFn>('translate')
  return DescriptionEntry({
    element: props.element,
    forId: CONDITION_EXPRESSION_ENTRY_ID,
    value: conditionRawState.editable
      ? translate('此处为高级用法；连线的结构化条件请在「分支条件」标签页配置，保存时结构化配置优先。')
      : translate('条件由「分支条件」标签页统一配置，此处只读展示；需要原文编辑请申请 system:flow:condition-raw 权限。'),
  })
}

/* ------------------------------------------------------------------ 分组后处理 */

/** 读取连线上的条件表达式原文（无则空串） */
function readConditionBody(businessObject: ModdleElementLike): string {
  const expression = businessObject.get('conditionExpression') as ModdleElementLike | undefined
  const body = expression?.get('body')
  return typeof body === 'string' ? body : ''
}

/**
 * 裁剪「通用」分组。
 *
 * <p>流程根元素：官方分组含名称/标识/可执行三项，全部在「节点基本」标签页或
 * 部署对齐逻辑中维护，整体替换为只读标识 + 去向说明。</p>
 * <p>其余元素：仅把可编辑的「标识」替换为只读版本，名称等条目保持官方能力
 * （与画布双击改名称是同一数据，保存时 syncNodesFromCanvas 会刷新右侧清单）。</p>
 */
function trimGeneralGroup(group: GroupDefinition, element: Element): GroupDefinition {
  if (is(element, 'bpmn:Process')) {
    return {
      ...group,
      entries: [readOnlyProcessIdEntry(), processNoticeEntry()],
    }
  }
  return {
    ...group,
    entries: group.entries.map((entry) =>
      entry.id === ID_ENTRY_ID ? readOnlyIdEntry() : entry,
    ),
  }
}

/** 为连线构建「分支条件」分组 */
function buildConditionGroup(translate: TranslateFn): PanelGroupDefinition {
  return {
    id: CONDITION_GROUP_ID,
    label: translate('分支条件'),
    component: Group,
    entries: [conditionExpressionEntry(), conditionHintEntry()],
    // 分组默认折叠（@bpmn-io/properties-panel 的 shouldOpen 默认 false）会让
    // 用户选中元素后看不到任何属性，必须手动点开，体验不可接受 → 显式声明默认展开
    shouldOpen: true,
  }
}

/**
 * 对官方分组做后处理：裁剪通用组 + 为连线追加条件分组。
 *
 * <p>入参 groups 已包含官方 provider 的产出（本 provider 优先级更低，最后执行）。</p>
 */
function postProcessGroups(
  groups: PanelGroupDefinition[],
  element: Element,
  translate: TranslateFn,
): PanelGroupDefinition[] {
  // 显式标注元素类型：不加会让 TS 把 map 的结果推断成「shouldOpen 必填」的字面量数组，
  // 之后 push 进来的分组（shouldOpen 可选）就类型不匹配了
  const result: PanelGroupDefinition[] = groups.map((group) => {
    const trimmed = group.id === GENERAL_GROUP_ID ? trimGeneralGroup(group, element) : group
    // 统一声明默认展开：库的 shouldOpen 默认 false，不补的话所有分组（含官方的
    // 通用/说明组）初始都是折叠态。展开状态仍可由用户点击分组头切换。
    return { ...trimmed, shouldOpen: true }
  })
  if (is(element, 'bpmn:SequenceFlow')) {
    result.push(buildConditionGroup(translate))
  }
  return result
}

/* ------------------------------------------------------------------ Provider */

/**
 * didi 模块声明。
 *
 * <p>与 {@code BpmnPropertiesProviderModule} 一并放入 modeler 的
 * {@code additionalModules}：官方模块负责注册基础 provider，本模块的后置
 * provider 再对其分组做系统化裁剪。</p>
 */
export default class FlowablePropertiesProvider {
  static $inject = ['propertiesPanel', 'injector']

  private readonly _translate: TranslateFn

  constructor(propertiesPanel: PropertiesPanelService, injector: InjectorLike) {
    this._translate = injector.get('translate') as TranslateFn
    // 低于官方 provider 的优先级注册：分组链上最后执行，才能拿到并修改官方分组
    propertiesPanel.registerProvider(POST_PROCESS_PRIORITY, this)
  }

  /**
   * 属性面板 provider 契约：返回「分组数组 → 分组数组」的变换函数。
   */
  getGroups(element: Element): (groups: PanelGroupDefinition[]) => PanelGroupDefinition[] {
    return (groups) => postProcessGroups(groups, element, this._translate)
  }
}

/** additionalModules 用的 didi 模块包装 */
export const flowablePropertiesProviderModule = {
  __init__: ['flowablePropertiesProvider'],
  flowablePropertiesProvider: ['type', FlowablePropertiesProvider],
}
