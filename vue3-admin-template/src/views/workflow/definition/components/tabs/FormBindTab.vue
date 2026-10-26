<script setup lang="ts">
import type { NodeFormBindRow } from '@/api/types'
/**
 * 设计器「表单绑定」标签页（FE-OPT-05 拆分自 index.vue）。
 *
 * <p>纯展示 + 事件上抛：绑定关系与可绑表单由父页面提供，
 * 用户改下拉时只 emit `bind(nodeKey, formId|null)`，落库仍由父页面负责。</p>
 */
defineProps<{
  /** 可绑定的节点（来自画布 BPMN 解析结果） */
  nodes: { nodeKey: string; nodeName: string }[]
  /** 可绑表单（仅启用态） */
  formOptions: { id: number; formCode: string; formName: string }[]
  /** 当前已保存的绑定关系 */
  binds: NodeFormBindRow[]
}>()

const emit = defineEmits<{
  bind: [nodeKey: string, formId: number | null]
}>()

function boundOf(binds: NodeFormBindRow[], nodeKey: string) {
  return binds.find((b) => b.nodeKey === nodeKey) ?? null
}
</script>

<template>
  <el-alert
    type="warning"
    :closable="false"
    show-icon
    title="节点表单的运行时填单页尚未开放"
    description="当前版本完成绑定与持久化；中间节点的填单与字段权限将在后续版本提供。发起环节的表单已可在「发起申请」页使用。"
    class="designer__alert"
  />
  <el-table :data="nodes" size="small" border max-height="420">
    <el-table-column prop="nodeName" label="流程节点" min-width="130" />
    <el-table-column label="绑定表单" min-width="180">
      <template #default="{ row }">
        <el-select
          :model-value="boundOf(binds, row.nodeKey)?.formId ?? null"
          placeholder="未绑定"
          clearable
          filterable
          size="small"
          style="width: 100%"
          @change="(val: number | null) => emit('bind', row.nodeKey, val)"
        >
          <el-option v-for="f in formOptions" :key="f.id" :label="`${f.formName}（${f.formCode}）`" :value="f.id" />
        </el-select>
      </template>
    </el-table-column>
    <el-table-column label="主表单" width="80" align="center">
      <template #default="{ row }">
        <el-tag v-if="boundOf(binds, row.nodeKey)?.isPrimary === 1" type="success" size="small">主</el-tag>
        <span v-else class="designer__muted">-</span>
      </template>
    </el-table-column>
  </el-table>
  <div class="designer__muted designer__muted--block">
    发起申请（init）默认作为主表单，决定「发起申请」页渲染的申请内容；
    中间节点可各自绑定表单，为后续的节点级填单预留。
  </div>
</template>
