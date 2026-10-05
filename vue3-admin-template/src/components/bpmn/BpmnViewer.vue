<script setup lang="ts">
/**
 * BPMN 只读查看器（详情抽屉里展示流程图与高亮当前节点）。
 */
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import BpmnViewer from 'bpmn-js/lib/Viewer'
import 'bpmn-js/dist/assets/diagram-js.css'
import 'bpmn-js/dist/assets/bpmn-js.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css'

const props = defineProps<{
  xml?: string
  /** 需要高亮的节点 id（当前待办节点） */
  activeNodeIds?: string[]
}>()

const container = ref<HTMLDivElement>()
let viewer: BpmnViewer | null = null

type CanvasLike = {
  zoom: (v: string) => void
  addMarker: (id: string, marker: string) => void
}

async function render() {
  if (!viewer || !props.xml?.trim()) {
    return
  }
  try {
    await viewer.importXML(props.xml)
    const canvas = viewer.get('canvas') as CanvasLike
    canvas.zoom('fit-viewport')
    for (const nodeId of props.activeNodeIds ?? []) {
      try {
        canvas.addMarker(nodeId, 'bpmn-active')
      } catch {
        // 节点 id 不存在时忽略（如流程已结束）
      }
    }
  } catch (e) {
    console.error('[BpmnViewer] 渲染 BPMN 失败', e)
  }
}

onMounted(async () => {
  if (!container.value) {
    return
  }
  viewer = new BpmnViewer({ container: container.value })
  await render()
})

watch(() => [props.xml, props.activeNodeIds], render)

onBeforeUnmount(() => {
  viewer?.destroy()
  viewer = null
})
</script>

<template>
  <div ref="container" class="bpmn-viewer" />
</template>

<style scoped>
.bpmn-viewer {
  width: 100%;
  height: 320px;
  border: 1px solid var(--el-border-color-light, #dcdfe6);
  border-radius: 4px;
  background: #fff;
}

/* 当前节点高亮 */
:deep(.bpmn-active .djs-visual > :nth-child(1)) {
  stroke: #67c23a !important;
  fill: #f0f9eb !important;
}
</style>
