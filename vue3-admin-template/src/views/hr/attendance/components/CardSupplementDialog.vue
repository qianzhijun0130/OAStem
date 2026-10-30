<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { submitHrRequestApi } from '@/api/hr'
import { logAsyncError } from '@/utils/asyncGuard'
import type { HrRequestSaveRequest, MissingCardVO } from '@/api/types'

/**
 * 漏卡认领弹窗（只服务「系统已识别缺卡」的场景）。
 *
 * <p>与假勤页的补卡表单分开是有意的：那里要用户自己挑日期、判有没有超 30 天，
 * 这里日期和方向都来自那条考勤行，用户只需要确认时间与填原因。
 * 少两个必填项，自助补卡才可能在手机上点完——这是「漏卡清单」存在的全部意义。</p>
 */
const emit = defineEmits<{ saved: [] }>()

const visible = ref(false)
const submitting = ref(false)
const row = ref<MissingCardVO | null>(null)
const form = ref<HrRequestSaveRequest>({ bizType: 3 })

function open(target: MissingCardVO) {
  row.value = target
  const directions = target.missingDirections || []
  const first = directions[0] || 1
  form.value = {
    bizType: 3,
    cardDate: target.attendanceDate,
    direction: first,
    // 缺上班卡默认按上班时间、缺下班卡默认按下班时间；用户可改
    cardTime: `${target.attendanceDate}T${first === 1 ? '09:00:00' : '18:00:00'}`,
    reason: '',
  }
  visible.value = true
}

async function submit() {
  if (!form.value.reason || form.value.reason.trim().length < 2) {
    ElMessage.warning('请填写补卡原因（至少 2 个字）')
    return
  }
  submitting.value = true
  try {
    await submitHrRequestApi({ ...form.value, reason: form.value.reason.trim() })
    ElMessage.success('补卡申请已提交，审批通过后考勤自动更新')
    visible.value = false
    emit('saved')
  } catch (error) {
    logAsyncError('submitCardSupplement', error)
  } finally {
    submitting.value = false
  }
}

defineExpose({ open })
</script>

<template>
  <ResponsiveDialog v-model="visible" title="申请补卡" width="480px">
    <el-form label-width="90px">
      <el-form-item label="补卡日期">
        <span>{{ row?.attendanceDate }}</span>
      </el-form-item>
      <el-form-item label="补卡方向">
        <el-radio-group v-model="form.direction" :disabled="(row?.missingDirections?.length || 0) <= 1">
          <el-radio
            v-for="d in row?.missingDirections || []"
            :key="d"
            :value="d"
          >
            {{ d === 1 ? '上班卡' : '下班卡' }}
          </el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="补卡时间">
        <el-date-picker
          v-model="form.cardTime"
          type="datetime"
          value-format="YYYY-MM-DDTHH:mm:ss"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="补卡原因">
        <el-input v-model="form.reason" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="如：忘带工牌，门禁未记录" />
      </el-form-item>
    </el-form>
    <p class="card-dlg__hint">
      这是系统已识别的漏卡，不计入每月 3 次的主动补卡额度。
    </p>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">提交</el-button>
    </template>
  </ResponsiveDialog>
</template>

<style scoped>
.card-dlg__hint {
  margin: 0 0 4px 90px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}
</style>
