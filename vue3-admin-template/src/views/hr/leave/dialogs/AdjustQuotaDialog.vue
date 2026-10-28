<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { adjustLeaveQuotaApi } from '@/api/hr'
import { logAsyncError } from '@/utils/asyncGuard'
import { LEAVE_TYPES } from '../constants'

/**
 * 调整假期额度弹窗（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>自包含：表单状态、校验、提交都在本组件；年度由父级传入（额度表按年度展示），
 * 保存成功后 `emit('saved')` 让 tab 重载额度列表。</p>
 */
const props = defineProps<{
  /** 当前正在查看的年度，调整直接落到该年度 */
  year: number
}>()

const emit = defineEmits<{ saved: [] }>()

const visible = ref(false)
const submitting = ref(false)
const form = reactive({ userId: undefined as number | undefined, leaveType: 3, totalDays: 0, remark: '' })

function open() {
  Object.assign(form, { userId: undefined, leaveType: 3, totalDays: 0, remark: '' })
  visible.value = true
}

async function submit() {
  if (!form.userId) {
    ElMessage.warning('请填写员工用户ID')
    return
  }
  submitting.value = true
  try {
    await adjustLeaveQuotaApi({
      userId: form.userId,
      leaveType: form.leaveType,
      year: props.year,
      totalDays: form.totalDays,
      remark: form.remark,
    })
    ElMessage.success('额度已调整')
    visible.value = false
    emit('saved')
  } catch (error) {
    logAsyncError('adjustQuota', error)
  } finally {
    submitting.value = false
  }
}

defineExpose({ open })
</script>

<template>
  <el-dialog v-model="visible" title="调整假期额度" width="480px" destroy-on-close>
    <el-alert
      class="hr-leave__tip"
      type="info"
      :closable="false"
      show-icon
      title="这里填的是「调后总额度」，不是增量；每次调整都会记入台账备注。"
    />
    <el-form :model="form" label-width="100px">
      <el-form-item label="员工ID">
        <el-input-number v-model="form.userId" :min="1" :controls="false" placeholder="用户ID" />
      </el-form-item>
      <el-form-item label="假别">
        <el-select v-model="form.leaveType" style="width: 180px">
          <el-option v-for="t in LEAVE_TYPES" :key="t.value" :label="t.label" :value="t.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="总额度">
        <el-input-number v-model="form.totalDays" :min="0" :max="365" :precision="1" />
        <span class="hr-leave__hint">天</span>
      </el-form-item>
      <el-form-item label="调整说明">
        <el-input v-model="form.remark" maxlength="255" placeholder="必填，写入台账备注" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">确定</el-button>
    </template>
  </el-dialog>
</template>
