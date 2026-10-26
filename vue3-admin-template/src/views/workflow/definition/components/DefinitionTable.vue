<script setup lang="ts">
import type { FlowDefinitionVO } from '@/api/types'

/**
 * 流程模板列表表格（FE-OPT-05 拆分自 index.vue）。
 *
 * <p>只负责渲染与事件上抛：发布/下架/删除/设计的业务处理仍在父页面，
 * 组件对接口零假设；权限码由 v-permission（全局指令）直接作用在按钮上。</p>
 */
const FLOW_STATUS_PUBLISHED = 1

defineProps<{
  list: FlowDefinitionVO[]
  loading: boolean
}>()

const emit = defineEmits<{
  edit: [row: FlowDefinitionVO]
  publish: [row: FlowDefinitionVO]
  unpublish: [row: FlowDefinitionVO]
  remove: [row: FlowDefinitionVO]
}>()

function statusTag(row: FlowDefinitionVO): { type: 'success' | 'info'; label: string } {
  return row.status === FLOW_STATUS_PUBLISHED
    ? { type: 'success', label: '已发布' }
    : { type: 'info', label: '草稿' }
}
</script>

<template>
  <el-table v-loading="loading" :data="list" border>
    <el-table-column prop="flowCode" label="流程编码" width="160" />
    <el-table-column prop="flowName" label="流程名称" min-width="160" />
    <el-table-column prop="category" label="分类" width="120">
      <template #default="{ row }">{{ row.category || '-' }}</template>
    </el-table-column>
    <el-table-column prop="bpmnKey" label="BPMN Key" width="160">
      <template #default="{ row }">{{ row.bpmnKey || '-' }}</template>
    </el-table-column>
    <el-table-column label="状态" width="100">
      <template #default="{ row }">
        <el-tag :type="statusTag(row).type">{{ statusTag(row).label }}</el-tag>
      </template>
    </el-table-column>
    <el-table-column prop="createTime" label="创建时间" width="180" />
    <el-table-column label="操作" width="280" fixed="right">
      <template #default="{ row }">
        <el-button icon-only link type="primary" @click="emit('edit', row)">设计</el-button>
        <el-button
          v-if="row.status !== FLOW_STATUS_PUBLISHED"
          v-permission="'system:flow:edit'"
          icon-only
          link
          type="success"
          @click="emit('publish', row)"
        >
          发布
        </el-button>
        <el-button
          v-if="row.status === FLOW_STATUS_PUBLISHED"
          v-permission="'system:flow:edit'"
          icon-only
          link
          type="warning"
          @click="emit('unpublish', row)"
        >
          下架
        </el-button>
        <el-button v-permission="'system:flow:delete'" icon-only link type="danger" @click="emit('remove', row)">
          删除
        </el-button>
      </template>
    </el-table-column>
    <template #empty>
      <el-empty description="暂无流程模板" />
    </template>
  </el-table>
</template>
