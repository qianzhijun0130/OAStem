<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { submitHrRequestApi } from '@/api/hr'
import { logAsyncError } from '@/utils/asyncGuard'
import { BIZ_TABS } from '../constants'
import type { HrRequestSaveRequest } from '@/api/types'

/**
 * 发起加班 / 出差 / 补卡弹窗（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>三类申请共用一张表，按 `type` 切换字段分组；原来是页面级的 `bizType` + 一个弹窗，
 * 拆 tab 后每个 tab 各持一个实例，用 prop 传类型，页面不再需要「当前 bizType」状态。</p>
 */
const props = defineProps<{
  /** 1 加班 / 2 出差 / 3 补卡 */
  type: number
}>()

const emit = defineEmits<{ saved: [] }>()

const visible = ref(false)
const submitting = ref(false)
const title = computed(() => `发起${BIZ_TABS.find((t) => t.type === props.type)?.label || ''}`)

const form = reactive<HrRequestSaveRequest>({ bizType: 1, overtimeType: 1, compensateType: 1, direction: 1 })

function open() {
  Object.assign(form, {
    bizType: props.type,
    overtimeType: 1,
    compensateType: 1,
    direction: 1,
    overtimeDate: '',
    startTime: '',
    endTime: '',
    hours: undefined,
    destination: '',
    startDate: '',
    endDate: '',
    budget: undefined,
    cardDate: '',
    cardTime: '',
    reason: '',
  })
  visible.value = true
}

/** 把日期 + 时间拼成后端需要的 ISO 字符串（LocalDateTime 无时区） */
function joinDateTime(date?: string, time?: string): string | undefined {
  if (!date) {
    return undefined
  }
  return `${date}T${time || '09:00:00'}`
}

async function submit() {
  submitting.value = true
  try {
    const payload: HrRequestSaveRequest = { ...form, bizType: props.type }
    if (props.type === 1) {
      payload.startTime = joinDateTime(payload.overtimeDate, payload.startTime?.slice(11) || '19:00:00')
      payload.endTime = joinDateTime(payload.overtimeDate, payload.endTime?.slice(11) || '21:00:00')
    }
    if (props.type === 3) {
      payload.cardTime = joinDateTime(payload.cardDate, payload.cardTime?.slice(11) || '09:00:00')
    }
    await submitHrRequestApi(payload)
    ElMessage.success('已提交，等待审批')
    visible.value = false
    emit('saved')
  } catch (error) {
    logAsyncError('submitHrRequest', error)
  } finally {
    submitting.value = false
  }
}

defineExpose({ open })
</script>

<template>
  <el-dialog v-model="visible" :title="title" width="520px" destroy-on-close>
    <el-form :model="form" label-width="100px">
      <template v-if="type === 1">
        <el-form-item label="加班日期">
          <el-date-picker v-model="form.overtimeDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="加班类型">
          <el-select v-model="form.overtimeType" style="width: 180px">
            <el-option label="工作日" :value="1" />
            <el-option label="休息日" :value="2" />
            <el-option label="法定节假日" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="时长(小时)">
          <el-input-number v-model="form.hours" :min="0.5" :max="24" :step="0.5" :precision="1" />
        </el-form-item>
        <el-form-item label="补偿方式">
          <el-radio-group v-model="form.compensateType">
            <el-radio :value="1">转调休</el-radio>
            <el-radio :value="2">加班费</el-radio>
          </el-radio-group>
        </el-form-item>
      </template>

      <template v-else-if="type === 2">
        <el-form-item label="出差地点">
          <el-input v-model="form.destination" maxlength="200" />
        </el-form-item>
        <el-form-item label="开始日期">
          <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="预算">
          <el-input-number v-model="form.budget" :min="0" :precision="2" />
        </el-form-item>
      </template>

      <template v-else>
        <el-form-item label="补卡日期">
          <el-date-picker v-model="form.cardDate" type="date" value-format="YYYY-MM-DD" />
          <span class="hr-leave__hint">只能补近 30 天，每月最多 3 次</span>
        </el-form-item>
        <el-form-item label="方向">
          <el-radio-group v-model="form.direction">
            <el-radio :value="1">上班</el-radio>
            <el-radio :value="2">下班</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="补卡时间">
          <el-date-picker v-model="form.cardTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" />
        </el-form-item>
      </template>

      <el-form-item label="事由">
        <el-input v-model="form.reason" type="textarea" :rows="3" maxlength="500" show-word-limit />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">提交</el-button>
    </template>
  </el-dialog>
</template>
