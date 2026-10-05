<script setup lang="ts">
/**
 * 审批详情抽屉：审批单信息 + 动态表单数据 + 流转时间轴 + 只读流程图（高亮当前节点）。
 */
import { ref, watch } from 'vue'
import BpmnViewer from '@/components/bpmn/BpmnViewer.vue'
import { getApprovalDetailApi } from '@/api/approval'
import { getFlowBpmnApi, getFlowPageApi } from '@/api/flow'
import type { ApprovalDetailVO, FormFieldItem } from '@/api/types'

const props = defineProps<{ visible: boolean; instanceId?: number }>()
const emit = defineEmits<{ (e: 'update:visible', v: boolean): void }>()

const loading = ref(false)
const detail = ref<ApprovalDetailVO | null>(null)
const bpmnXml = ref('')
/** 表单数据 JSON 解析后的键值对，按模板字段顺序展示 */
const formDataEntries = ref<{ label: string; value: string }[]>([])

/** 模板 formFields 是 JSON 字符串，解析失败时按「无字段声明」处理，不影响详情展示 */
function parseFormFields(json?: string): FormFieldItem[] {
  if (!json) {
    return []
  }
  try {
    return JSON.parse(json) as FormFieldItem[]
  } catch {
    return []
  }
}

function statusTagType(status?: number): 'success' | 'info' | 'danger' | 'warning' {
  if (status === 3) return 'success'
  if (status === 4) return 'danger'
  if (status === 5) return 'info'
  return 'warning'
}

/**
 * 把审批单的 formData 还原成「显示名 → 值」列表。
 *
 * 按模板 formFields 声明的字段顺序与显示名展示，模板里没声明但表单里有的字段追加在末尾，
 * 保证同一份数据对用户是可读的中文标签而不是 days / reason 这类字段名。
 */
function parseFormData(json: string | undefined, fields: FormFieldItem[]) {
  if (!json) {
    return []
  }
  let obj: Record<string, unknown>
  try {
    obj = JSON.parse(json) as Record<string, unknown>
  } catch {
    return []
  }
  const labelMap = new Map(fields.map((f) => [f.field, f.label || f.field]))
  const declared = fields.map((f) => f.field)
  const extra = Object.keys(obj).filter((k) => !declared.includes(k))
  return [...declared, ...extra].map((field) => ({
    label: labelMap.get(field) ?? field,
    value: formatValue(obj[field]),
  }))
}

/** 空值统一显示为「-」，其余原样转字符串 */
function formatValue(value: unknown) {
  if (value === null || value === undefined || value === '') {
    return '-'
  }
  return typeof value === 'object' ? JSON.stringify(value) : String(value)
}

async function load() {
  if (!props.instanceId) {
    return
  }
  loading.value = true
  try {
    detail.value = await getApprovalDetailApi(props.instanceId)

    // 按 flowCode 反查模板：formFields 用于把字段名还原成中文显示名，BPMN 用于只读流程图
    const page = await getFlowPageApi({ pageNo: 1, pageSize: 1, flowCode: detail.value.instance.flowCode })
    const def = page.records[0]
    formDataEntries.value = parseFormData(
      detail.value.instance.formData,
      parseFormFields(def?.formFields),
    )
    bpmnXml.value = def ? await getFlowBpmnApi(def.id).catch(() => '') : ''
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.visible, props.instanceId],
  async ([visible]) => {
    if (visible && props.instanceId) {
      await load()
    } else if (!visible) {
      detail.value = null
      bpmnXml.value = ''
    }
  },
  { immediate: true },
)
</script>

<template>
  <el-drawer
    :model-value="visible"
    :size="'60%'"
    title="审批详情"
    @update:model-value="(v: boolean) => emit('update:visible', v)"
  >
    <div v-loading="loading">
      <template v-if="detail">
        <el-descriptions :column="2" border class="detail__desc">
          <el-descriptions-item label="申请标题">{{ detail.instance.title }}</el-descriptions-item>
          <el-descriptions-item label="流程名称">
            {{ detail.instance.flowName }}
          </el-descriptions-item>
          <el-descriptions-item label="发起人">
            {{ detail.instance.applicantName || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTagType(detail.instance.status)">
              {{ detail.instance.statusName }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="当前节点">
            {{ detail.instance.currentNode || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="发起时间">
            {{ detail.instance.createTime || '-' }}
          </el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">申请内容</el-divider>
        <el-descriptions v-if="formDataEntries.length" :column="1" border>
          <el-descriptions-item v-for="item in formDataEntries" :key="item.label" :label="item.label">
            {{ item.value }}
          </el-descriptions-item>
        </el-descriptions>
        <el-empty v-else description="无表单数据" :image-size="60" />

        <el-divider content-position="left">流程图</el-divider>
        <BpmnViewer v-if="bpmnXml" :xml="bpmnXml" />
        <el-empty v-else description="未获取到流程图" :image-size="60" />

        <el-divider content-position="left">流转记录</el-divider>
        <el-timeline>
          <el-timeline-item
            v-for="(rec, idx) in detail.records"
            :key="idx"
            :timestamp="rec.createTime || ''"
            :type="rec.action === 'reject' ? 'danger' : rec.action === 'approve' ? 'success' : 'primary'"
            placement="top"
          >
            <div class="detail__record">
              <strong>{{ rec.nodeName || '-' }}</strong>
              <el-tag size="small" class="detail__action">{{ rec.actionName }}</el-tag>
              <span class="detail__handler">{{ rec.handlerName || '-' }}</span>
            </div>
            <div v-if="rec.opinion" class="detail__opinion">意见：{{ rec.opinion }}</div>
          </el-timeline-item>
        </el-timeline>
      </template>
      <el-empty v-else-if="!loading" description="暂无详情" />
    </div>
  </el-drawer>
</template>

<style scoped>
.detail__desc {
  margin-bottom: 8px;
}

.detail__record {
  display: flex;
  align-items: center;
  gap: 8px;
}

.detail__action {
  margin-left: 4px;
}

.detail__handler {
  color: var(--el-text-color-secondary);
}

.detail__opinion {
  margin-top: 4px;
  color: var(--el-text-color-regular);
  font-size: 13px;
}
</style>
