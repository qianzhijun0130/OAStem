<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { RECRUIT_CONCLUSION_OPTIONS, submitFeedbackApi } from '@/api/recruit'
import type { RecruitFeedbackRequest, RecruitInterviewVO } from '@/api/recruit'
import { logAsyncError } from '@/utils/asyncGuard'

/**
 * 提交面试评价弹窗（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>四维评分默认 3 星（中位），结论默认「通过」——面试官通常只需改少数几项。</p>
 */
const emit = defineEmits<{ saved: [] }>()

const visible = ref(false)
const submitting = ref(false)

const form = reactive<RecruitFeedbackRequest>({
  interviewId: undefined,
  professional: 3,
  communication: 3,
  logicScore: 3,
  cultureMatch: 3,
  conclusion: 1,
  comment: '',
})

function open(row: RecruitInterviewVO) {
  Object.assign(form, {
    interviewId: row.id,
    professional: 3,
    communication: 3,
    logicScore: 3,
    cultureMatch: 3,
    conclusion: 1,
    comment: '',
  })
  visible.value = true
}

async function submit() {
  if (form.interviewId == null) {
    return
  }
  submitting.value = true
  try {
    await submitFeedbackApi({ ...form })
    ElMessage.success('评价已提交')
    visible.value = false
    emit('saved')
  } catch (error) {
    logAsyncError('submitFeedback', error)
  } finally {
    submitting.value = false
  }
}

defineExpose({ open })
</script>

<template>
  <el-dialog v-model="visible" title="提交面试评价" width="480px" destroy-on-close>
    <el-form :model="form" label-width="100px">
      <el-form-item label="专业能力">
        <el-rate v-model="form.professional" :max="5" />
      </el-form-item>
      <el-form-item label="沟通表达">
        <el-rate v-model="form.communication" :max="5" />
      </el-form-item>
      <el-form-item label="逻辑思维">
        <el-rate v-model="form.logicScore" :max="5" />
      </el-form-item>
      <el-form-item label="文化匹配">
        <el-rate v-model="form.cultureMatch" :max="5" />
      </el-form-item>
      <el-form-item label="结论">
        <el-select v-model="form.conclusion" style="width: 100%">
          <el-option v-for="o in RECRUIT_CONCLUSION_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="评语">
        <el-input v-model="form.comment" type="textarea" :rows="3" maxlength="500" show-word-limit />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" data-action="submit" @click="submit">提交</el-button>
    </template>
  </el-dialog>
</template>
