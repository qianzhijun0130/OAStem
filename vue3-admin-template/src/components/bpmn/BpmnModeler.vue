<script setup lang="ts">
/**
 * BPMN 流程设计器（基于 bpmn-js Modeler）。
 *
 * 用法：父组件通过 :xml 传入已有 BPMN（为空则新建空白流程），
 * 通过 ref 调用 getXml() / getUserTasks() 取回设计结果。
 */
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import BpmnModeler from 'bpmn-js/lib/Modeler'
import 'bpmn-js/dist/assets/diagram-js.css'
import 'bpmn-js/dist/assets/bpmn-js.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css'

const props = defineProps<{ xml?: string }>()

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
  businessObject?: { name?: string; $type?: string }
}

type ElementRegistry = {
  filter: (fn: (el: BpmnElement) => boolean) => BpmnElement[]
}

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

onMounted(async () => {
  if (!container.value) {
    return
  }
  modeler = new BpmnModeler({ container: container.value })
  await importDiagram()
})

watch(() => props.xml, importDiagram)

onBeforeUnmount(() => {
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

/** 取出画布上的全部用户任务，供「节点审批人配置」使用 */
function getUserTasks(): { id: string; name: string }[] {
  if (!modeler) {
    return []
  }
  const registry = modeler.get('elementRegistry') as ElementRegistry
  return registry
    .filter((el) => el.type === 'bpmn:UserTask')
    .map((el) => ({ id: el.id, name: el.businessObject?.name ?? el.id }))
}

defineExpose({ getXml, getUserTasks })
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
