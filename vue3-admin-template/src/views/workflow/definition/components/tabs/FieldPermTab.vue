<script setup lang="ts">
import type { NodeFieldPermissionItem } from '@/api/types'

/**
 * 设计器「字段权限」标签页（FE-OPT-05 拆分自 index.vue）。
 *
 * <p>受控组件：节点选择、字段行、保存中状态全部由父页面提供；
 * 字段行内的「权限」下拉直接落在传入的行对象上（对象来自父级 ref，改动对父级可见），
 * 保存动作 emit 回父页面，组件不持有接口调用。</p>
 */
const FIELD_PERMISSION_OPTIONS: Array<{ value: number; label: string }> = [
  { value: 1, label: '只读' },
  { value: 2, label: '可编辑' },
  { value: 3, label: '隐藏' },
]

defineProps<{
  /** 当前选中的节点键 */
  nodeKey: string
  /** 可配置的节点（已绑定表单的节点） */
  nodeOptions: { nodeKey: string; nodeName: string }[]
  /** 当前节点绑定的表单名（未绑定为 null） */
  formName: string | null
  /** 该节点的字段权限行 */
  fields: NodeFieldPermissionItem[]
  /** 保存中 */
  saving: boolean
}>()

const emit = defineEmits<{
  'update:nodeKey': [value: string]
  change: [nodeKey: string]
  save: []
}>()

function onNodeChange(value: string) {
  emit('update:nodeKey', value)
  emit('change', value)
}
</script>

<template>
  <el-alert
    type="info"
    :closable="false"
    show-icon
    title="未配置的字段默认只读"
    description="审批场景下默认放开风险更高：审批人误改申请人填写的内容比看不到更难追销。如需让某个节点填写部分字段（如销假节点填「销假日期」），在这里把对应字段改为「可编辑」。"
    class="designer__alert"
  />
  <div class="designer__field-bar">
    <el-select :model-value="nodeKey" placeholder="选择节点" style="width: 220px" @change="onNodeChange">
      <el-option v-for="n in nodeOptions" :key="n.nodeKey" :label="n.nodeName" :value="n.nodeKey" />
    </el-select>
    <span v-if="formName" class="designer__muted">表单：{{ formName }}</span>
    <el-button
      type="primary"
      size="small"
      :disabled="!nodeKey || !formName || !fields.length"
      :loading="saving"
      @click="emit('save')"
    >
      保存权限
    </el-button>
  </div>
  <el-table :data="fields" size="small" border max-height="400">
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
          <el-option v-for="opt in FIELD_PERMISSION_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
      </template>
    </el-table-column>
    <template #empty>
      <el-empty description="请先选择节点，并在「表单绑定」标签页为该节点绑定表单" :image-size="50" />
    </template>
  </el-table>
</template>
