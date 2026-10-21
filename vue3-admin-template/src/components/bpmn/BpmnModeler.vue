<script setup lang="ts">
/**
 * BPMN 流程设计器（基于 bpmn-js Modeler）。
 *
 * 用法：父组件通过 :xml 传入已有 BPMN（为空则新建空白流程），
 * 通过 ref 调用 getXml() / getUserTasks() 取回设计结果。
 *
 * 界面已汉化：bpmn-js 自带的调色板提示、右键菜单、属性面板标签在校验与错误提示
 * 全部是英文，通过 additionalModules 覆盖其 translate 服务实现（见 zh-CN.ts）。
 *
 * 右侧属性面板：通过 :panel-host 传入宿主元素即可在指定容器内渲染 bpmn-js 官方
 * 属性面板（含 Flowable 适配裁剪，见 FlowablePropertiesProvider.ts）。宿主可在
 * 组件挂载后就地传入，也可稍后通过 attachPropertiesPanel() 延迟挂载。
 */
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import BpmnModeler from 'bpmn-js/lib/Modeler'
import zhCN from './zh-CN'
import {
  CONDITION_RAW_CHANGED_EVENT,
  flowablePropertiesProviderModule,
  setConditionRawEditable,
} from './FlowablePropertiesProvider'
import { BpmnPropertiesPanelModule, BpmnPropertiesProviderModule } from 'bpmn-js-properties-panel'
import 'bpmn-js/dist/assets/diagram-js.css'
import 'bpmn-js/dist/assets/bpmn-js.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css'
import '@bpmn-io/properties-panel/assets/properties-panel.css'

const props = defineProps<{
  xml?: string
  /** 属性面板宿主元素（通常是右侧标签页里的一个 div） */
  panelHost?: HTMLElement | null
  /** 是否允许编辑条件表达式原文（权限码 system:flow:condition-raw） */
  conditionRawEditable?: boolean
}>()

const emit = defineEmits<{
  /** 某连线的条件原文被修改（页面据此作废该连线的结构化配置） */
  (e: 'conditionRawChanged', flowId: string): void
  /**
   * 画布结构发生变化（新增/删除节点或连线、节点改名）。
   * 宿主据此实时刷新「节点基本 / 审批人 / 表单绑定」的节点清单与「分支条件」的网关出线，
   * 无需再手动点「同步节点」。
   */
  (e: 'elementsChanged'): void
}>()

/** 新建流程时的骨架：一个开始事件，保证画布可直接拖拽编辑 */
const EMPTY_DIAGRAM = `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
             xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
             xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
             targetNamespace="http://www.flowable.org/processdef">
  <process id="process_1" name="新流程" isExecutable="true">
    <startEvent id="startEvent_1" name="发起申请" />
  </process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="process_1">
      <bpmndi:BPMNShape id="_BPMNShape_StartEvent_1" bpmnElement="startEvent_1">
        <dc:Bounds x="180" y="160" width="36" height="36" />
      </bpmndi:BPMNShape>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</definitions>`

const container = ref<HTMLDivElement>()
let modeler: BpmnModeler | null = null

interface BpmnElement {
  id: string
  type: string
  businessObject?: { name?: string; $type?: string; get?: (name: string) => unknown }
  /** 连线元素的源 / 目标节点 */
  source?: BpmnElement
  target?: BpmnElement
  /** 形状元素的出线（网关取分支用） */
  outgoing?: BpmnElement[]
}

type ElementRegistry = {
  filter: (fn: (el: BpmnElement) => boolean) => BpmnElement[]
}

/** eventBus 最小形状（订阅条件原文变更与画布变更事件用；不同事件负载不同，故用泛型） */
type MiniEventBus = {
  on: <T>(event: string, handler: (event: T) => void) => void
  off: <T>(event: string, handler: (event: T) => void) => void
}

/**
 * 需要监听的画布变更事件。
 *
 * <p>只挑「结构变化」类事件：新增/删除图形、新增/删除连线、元素属性变更（改名）。
 * 不监听 {@code element.changed} 之外的拖拽中间态事件（如 {@code shape.move}），
 * 否则拖一次节点会触发上百次刷新。</p>
 */
const CANVAS_CHANGED_EVENTS = [
  'shape.added',
  'shape.removed',
  'connection.added',
  'connection.removed',
  'element.changed',
] as const

/** 属性面板服务最小形状（延迟挂载宿主用） */
type MiniPropertiesPanel = {
  attachTo: (parent: HTMLElement) => void
}

/** commandStack 最小形状（清除连线条件用） */
type MiniCommandStack = {
  execute: (command: string, context: Record<string, unknown>) => unknown
}

/**
 * 需要采集审批人的任务节点类型。
 *
 * <p>bpmn-js 调色板里「用户任务」（User Task）与「创建任务」（Create Task）是**两个不同的类型**：
 * 前者是 {@code bpmn:UserTask}，后者是 {@code bpmn:Task}。</p>
 *
 * <p>此前只采集 {@code bpmn:UserTask}，导致 create task 节点从未进入 {@code node_config}，
 * 而后端 {@code ApprovalServiceImpl} 明确「审批人以 node_config 为准」，于是该节点
 * 在 Flowable 中拿不到候选人 → 待办里没人能处理。</p>
 */
const TASK_TYPES = new Set(['bpmn:UserTask', 'bpmn:Task'])

async function importDiagram() {
  if (!modeler) {
    return
  }
  const xml = props.xml?.trim()
  try {
    if (xml) {
      await modeler.importXML(xml)
    } else {
      await modeler.importXML(EMPTY_DIAGRAM)
    }
    const canvas = modeler.get('canvas') as { zoom: (v: string) => void }
    canvas.zoom('fit-viewport')
  } catch (e) {
    // 导入失败时回退到空白流程，避免画布不可用
    console.error('[BpmnModeler] 导入 BPMN 失败，回退空白流程', e)
    await modeler.importXML(EMPTY_DIAGRAM)
  }
}

/** 把 provider 抛出的条件原文变更事件转成 Vue 事件 */
function onConditionRawChanged(event: { flowId: string }) {
  emit('conditionRawChanged', event.flowId)
}

/**
 * 画布结构变化 → 通知宿主刷新。
 *
 * <p><b>不在此处做防抖</b>：单个事件（如一次拖拽结束后的 element.changed）可能连发多次，
 * 但更常见的是「连续拖拽 + 改名」产生事件洪流。防抖交给宿主统一处理，
 * 组件只负责如实上报，职责更清晰。</p>
 */
function onCanvasChanged() {
  emit('elementsChanged')
}

onMounted(async () => {
  if (!container.value) {
    return
  }
  // 权限标志必须在 modeler 创建前写入：provider 的条目在首次渲染时读取该单例
  setConditionRawEditable(Boolean(props.conditionRawEditable))
  modeler = new BpmnModeler({
    container: container.value,
    // 覆盖 bpmn-js 内置的 translate 服务，实现界面汉化
    additionalModules: [
      zhCN,
      BpmnPropertiesPanelModule,
      BpmnPropertiesProviderModule,
      flowablePropertiesProviderModule,
    ],
    // 宿主就绪则直接挂载；未就准时留空，后续经 attachPropertiesPanel 延迟挂载
    ...(props.panelHost ? { propertiesPanel: { parent: props.panelHost } } : {}),
  })
  await importDiagram()
  const eventBus = modeler.get('eventBus') as MiniEventBus
  eventBus.on<{ flowId: string }>(CONDITION_RAW_CHANGED_EVENT, onConditionRawChanged)
  for (const event of CANVAS_CHANGED_EVENTS) {
    eventBus.on<unknown>(event, onCanvasChanged)
  }
})

// 宿主后到（如标签页懒渲染）：补挂载，避免属性面板没有容器可挂
watch(
  () => props.panelHost,
  (host) => {
    if (host && modeler) {
      attachPropertiesPanel(host)
    }
  },
)

onBeforeUnmount(() => {
  if (modeler) {
    const eventBus = modeler.get('eventBus') as MiniEventBus
    eventBus.off<{ flowId: string }>(CONDITION_RAW_CHANGED_EVENT, onConditionRawChanged)
    for (const event of CANVAS_CHANGED_EVENTS) {
      eventBus.off<unknown>(event, onCanvasChanged)
    }
  }
  // destroy 会触发 diagram.destroy → 属性面板自行 detach，无需手动摘除
  modeler?.destroy()
  modeler = null
})

/** 导出设计结果（BPMN XML） */
async function getXml(): Promise<string> {
  if (!modeler) {
    return ''
  }
  const result = await modeler.saveXML({ format: true })
  return result.xml ?? ''
}

/**
 * 把属性面板挂载到指定宿主（供 modeler 创建时宿主尚未就绪的场景）。
 */
function attachPropertiesPanel(host: HTMLElement) {
  if (!modeler) {
    return
  }
  const panel = modeler.get('propertiesPanel', false) as MiniPropertiesPanel | null
  panel?.attachTo(host)
}

/** 画布上任务节点的归一化类型标识 */
export type TaskNodeKind = 'userTask' | 'createTask'

/** 供「节点审批人配置」使用的节点信息 */
export interface TaskNodeInfo {
  id: string
  name: string
  /** 类型归一化标识：userTask=用户任务，createTask=创建任务 */
  kind: TaskNodeKind
  /** bpmn-js 原始类型，供展示与排查 */
  rawType: string
}

/**
 * 取出画布上的全部任务节点（用户任务 + 创建任务），供「节点审批人配置」使用。
 *
 * <p><b>命名保持 getUserTasks 是为兼容既有调用方</b>，实际返回的是「所有任务节点」；
 * 后续如需更名，应同步修改流程模板页与组件解构。</p>
 */
function getUserTasks(): TaskNodeInfo[] {
  if (!modeler) {
    return []
  }
  const registry = modeler.get('elementRegistry') as ElementRegistry
  return registry
    .filter((el) => TASK_TYPES.has(el.type))
    .map((el) => ({
      id: el.id,
      name: el.businessObject?.name ?? el.id,
      kind: (el.type === 'bpmn:Task' ? 'createTask' : 'userTask') as TaskNodeKind,
      rawType: el.type,
    }))
}

/** 网关出线信息（供「分支条件」标签页配置条件与默认分支） */
export interface GatewayFlowInfo {
  gatewayId: string
  gatewayName: string
  /** 画布 XML 回显的默认出线 id（上次部署由后端注入的 default 属性） */
  defaultFlowId: string | null
  flows: Array<{ flowId: string; targetNodeId: string; targetNodeName: string }>
}

/**
 * 采集画布上全部排他网关及其出线。
 *
 * <p>条件表达式挂在连线上、默认分支是网关属性，因此按网关分组返回；
 * defaultFlowId 取自业务对象，用于回显「上次部署已设置的默认分支」——
 * 该属性由后端 injectConditions 写入，前端只读展示、不在此处修改。</p>
 */
function getGatewayFlows(): GatewayFlowInfo[] {
  if (!modeler) {
    return []
  }
  const registry = modeler.get('elementRegistry') as ElementRegistry
  return registry
    .filter((el) => el.type === 'bpmn:ExclusiveGateway')
    .map((gateway) => {
      const businessObject = gateway.businessObject
      const rawDefault = businessObject?.get?.('default')
      return {
        gatewayId: gateway.id,
        gatewayName: gateway.businessObject?.name ?? gateway.id,
        defaultFlowId: typeof rawDefault === 'string' ? rawDefault : null,
        flows: (gateway.outgoing ?? [])
          .filter((flow) => flow.type === 'bpmn:SequenceFlow')
          .map((flow) => ({
            flowId: flow.id,
            targetNodeId: flow.target?.id ?? '',
            targetNodeName: flow.target?.businessObject?.name ?? flow.target?.id ?? flow.id,
          })),
      }
    })
}

/**
 * 清除某条连线上的条件表达式（恢复普通流转）。
 *
 * <p>用于「分支条件」标签页把出线改为默认分支或删除条件配置时：
 * 后端注入只覆盖配置中出现的连线，若画布里残留上次部署的表达式，
 * 默认分支就会同时带着条件，网关行为变得不确定。走 commandStack 以支持撤销。</p>
 */
function clearFlowCondition(flowId: string) {
  if (!modeler) {
    return
  }
  const registry = modeler.get('elementRegistry') as ElementRegistry
  const flow = registry.filter((el) => el.id === flowId)[0]
  const businessObject = flow?.businessObject
  if (!flow || typeof businessObject?.get !== 'function' || !businessObject.get('conditionExpression')) {
    return
  }
  const commandStack = modeler.get('commandStack') as MiniCommandStack
  commandStack.execute('element.updateModdleProperties', {
    element: flow,
    moddleElement: businessObject,
    properties: { conditionExpression: undefined },
  })
}

defineExpose({ getXml, getUserTasks, attachPropertiesPanel, getGatewayFlows, clearFlowCondition })
</script>

<template>
  <div ref="container" class="bpmn-modeler" />
</template>

<style scoped>
.bpmn-modeler {
  width: 100%;
  height: 100%;
  min-height: 520px;
  border: 1px solid var(--el-border-color-light, #dcdfe6);
  border-radius: 4px;
  background: #fff;
}
</style>
