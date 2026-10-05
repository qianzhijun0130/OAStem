<script setup lang="ts">
/**
 * 发起申请：选择流程模板 → 按模板 formFields 动态渲染表单 → 提交。
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getFlowOptionsApi } from '@/api/flow'
import { startApprovalApi } from '@/api/approval'
import { logAsyncError } from '@/utils/asyncGuard'
import type { FlowOptionVO, FormFieldItem } from '@/api/types'

const flowOptions = ref<FlowOptionVO[]>([])
const selectedFlowCode = ref('')
const title = ref('')
/** 动态表单值：字段名 → 值 */
const formValues = reactive<Record<string, string | number>>({})
const submitting = ref(false)

const currentFlow = computed(() => flowOptions.value.find((f) => f.flowCode === selectedFlowCode.value))

const currentFields = computed<FormFieldItem[]>(() => {
  if (!currentFlow.value?.formFields) {
    return []
  }
  try {
    return JSON.parse(currentFlow.value.formFields) as FormFieldItem[]
  } catch {
    return []
  }
})

/**
 * 加载可发起的流程模板。
 *
 * <p>刻意不用分页接口 `getFlowPageApi`：它的鉴权是 `system:flow:query`（模板管理职能），
 * 专员 / 员工角色没有该权限，打开本页就会 403、模板下拉为空、无法发起。
 * 这里用只读的 options 接口，鉴权为 `system:approval:apply`（面向全体员工）。</p>
 */
async function loadFlows() {
  try {
    flowOptions.value = await getFlowOptionsApi()
  } catch (error) {
    logAsyncError('加载可发起流程模板失败', error)
  }
}

/** 切换模板时重置表单值并给出默认值 */
watch(selectedFlowCode, () => {
  for (const key of Object.keys(formValues)) {
    delete formValues[key]
  }
  title.value = ''
  for (const field of currentFields.value) {
    formValues[field.field] = field.type === 'number' ? 0 : ''
  }
})

async function submit() {
  if (!selectedFlowCode.value) {
    ElMessage.warning('请选择流程模板')
    return
  }
  if (!title.value.trim()) {
    ElMessage.warning('请填写申请标题')
    return
  }
  const missing = currentFields.value.filter((f) => f.required && !formValues[f.field])
  if (missing.length > 0) {
    ElMessage.warning(`请填写「${missing[0].label}」`)
    return
  }

  submitting.value = true
  try {
    await startApprovalApi({
      flowCode: selectedFlowCode.value,
      title: title.value.trim(),
      formData: JSON.stringify(formValues),
    })
    ElMessage.success('申请已提交')
    title.value = ''
    for (const key of Object.keys(formValues)) {
      delete formValues[key]
    }
    for (const field of currentFields.value) {
      formValues[field.field] = field.type === 'number' ? 0 : ''
    }
  } finally {
    submitting.value = false
  }
}

onMounted(loadFlows)
</script>

<template>
  <div class="apply">
    <el-card shadow="never">
      <el-form label-width="110px" class="apply__form">
        <el-form-item label="流程模板" required>
          <el-select v-model="selectedFlowCode" placeholder="请选择流程模板" style="width: 320px">
            <el-option
              v-for="f in flowOptions"
              :key="f.id"
              :label="`${f.flowName}（${f.flowCode}）`"
              :value="f.flowCode"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="申请标题" required>
          <el-input v-model="title" placeholder="如：张三 2026-10-01 请假申请" style="width: 320px" />
        </el-form-item>

        <template v-if="currentFields.length">
          <el-divider content-position="left">申请内容</el-divider>
          <el-form-item
            v-for="field in currentFields"
            :key="field.field"
            :label="field.label"
            :required="field.required"
          >
            <el-input
              v-if="field.type === 'text'"
              v-model="formValues[field.field]"
              style="width: 320px"
            />
            <el-input
              v-else-if="field.type === 'textarea'"
              v-model="formValues[field.field]"
              type="textarea"
              :rows="3"
              style="width: 420px"
            />
            <el-input-number v-else-if="field.type === 'number'" v-model="formValues[field.field]" />
            <el-date-picker
              v-else-if="field.type === 'date'"
              v-model="formValues[field.field]"
              type="date"
              value-format="YYYY-MM-DD"
              style="width: 220px"
            />
            <el-select v-else-if="field.type === 'select'" v-model="formValues[field.field]">
              <el-option v-for="opt in field.options ?? []" :key="opt" :label="opt" :value="opt" />
            </el-select>
          </el-form-item>
        </template>

        <el-form-item>
          <el-button
            v-permission="'system:approval:start'"
            type="primary"
            :loading="submitting"
            @click="submit"
          >
            提交申请
          </el-button>
        </el-form-item>
      </el-form>

      <el-alert
        v-if="!flowOptions.length"
        type="info"
        show-icon
        :closable="false"
        title="暂无已启用的流程模板，请先在「流程模板」中新增并部署流程"
      />
    </el-card>
  </div>
</template>

<style scoped>
.apply__form {
  max-width: 720px;
}
</style>
