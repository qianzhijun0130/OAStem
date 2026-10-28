<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { submitLeaveApi } from '@/api/hr'
import { logAsyncError } from '@/utils/asyncGuard'
import { LEAVE_TYPES } from '../constants'
import type { LeaveSubmitRequest } from '@/api/types'

/**
 * 发起请假弹窗（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>表单 ref 必须留在组件内——模板 ref 只绑定本 SFC 的模板作用域，
 * 搬进 composable 后 `validate()` 会变成 undefined，校验静默失效（tsc 也发现不了）。</p>
 */
const emit = defineEmits<{ saved: [] }>()

const visible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<LeaveSubmitRequest>({
  leaveType: 3,
  startTime: '',
  endTime: '',
  reason: '',
})

const rules: FormRules = {
  leaveType: [{ required: true, message: '请选择假别', trigger: 'change' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }],
}

function open() {
  Object.assign(form, { leaveType: 3, startTime: '', endTime: '', leaveDays: undefined, reason: '' })
  visible.value = true
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  submitting.value = true
  try {
    await submitLeaveApi({ ...form })
    ElMessage.success('请假已提交，等待审批')
    visible.value = false
    emit('saved')
  } catch (error) {
    logAsyncError('submitLeave', error)
  } finally {
    submitting.value = false
  }
}

defineExpose({ open })
</script>

<template>
  <el-dialog v-model="visible" title="发起请假" width="520px" destroy-on-close>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
      <el-form-item label="假别" prop="leaveType">
        <el-select v-model="form.leaveType" style="width: 180px">
          <el-option v-for="t in LEAVE_TYPES" :key="t.value" :label="t.label" :value="t.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="开始时间" prop="startTime">
        <el-date-picker v-model="form.startTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 220px" />
      </el-form-item>
      <el-form-item label="结束时间" prop="endTime">
        <el-date-picker v-model="form.endTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 220px" />
      </el-form-item>
      <el-form-item label="请假天数">
        <el-input-number v-model="form.leaveDays" :min="0.5" :max="365" :step="0.5" :precision="1" />
        <span class="hr-leave__hint">留空按起止时间自动计算</span>
      </el-form-item>
      <el-form-item label="事由">
        <el-input v-model="form.reason" type="textarea" :rows="3" maxlength="1000" show-word-limit />
      </el-form-item>
      <el-form-item v-if="form.leaveType === 2" label="证明材料">
        <el-input v-model="form.attachmentIds" placeholder="病假 ≥3 天必须上传，填 file_id" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">提交</el-button>
    </template>
  </el-dialog>
</template>
