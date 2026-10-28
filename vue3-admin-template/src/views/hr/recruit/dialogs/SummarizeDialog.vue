<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getInterviewDetailApi,
  RECRUIT_CONCLUSION_OPTIONS,
  summarizeInterviewApi,
} from '@/api/recruit'
import type { RecruitInterviewVO } from '@/api/recruit'
import { logAsyncError } from '@/utils/asyncGuard'

/**
 * 汇总结论弹窗（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>两条前置规则：</p>
 * <ul>
 *   <li>还有面试官没提交评价时不允许汇总（按钮后端也会拦，这里先给出原因）；</li>
 *   <li>若有人给了「不通过」而你要继续推进，必须填继续理由——这是留痕要求。</li>
 * </ul>
 */
const emit = defineEmits<{ saved: [] }>()

const visible = ref(false)
const submitting = ref(false)
const target = ref<RecruitInterviewVO | null>(null)

const form = reactive({
  conclusion: 1,
  summary: '',
  continueReason: '',
})

/** 有人给「不通过」（conclusion=3）却仍要继续推进 → 必须写理由 */
const needReason = computed(
  () => target.value?.feedbacks?.some((f) => f.conclusion === 3) && form.conclusion === 1,
)

async function open(row: RecruitInterviewVO) {
  if (!row.allSubmitted) {
    ElMessage.warning('还有面试官未提交评价，无法汇总')
    return
  }
  try {
    target.value = await getInterviewDetailApi(row.id)
  } catch (error) {
    logAsyncError('interviewDetail', error)
    return
  }
  form.conclusion = 1
  form.summary = ''
  form.continueReason = ''
  visible.value = true
}

async function submit() {
  if (!target.value) {
    return
  }
  if (needReason.value && !form.continueReason) {
    ElMessage.warning('存在「不通过」评价时，继续推进须填写理由')
    return
  }
  submitting.value = true
  try {
    await summarizeInterviewApi(
      target.value.id,
      form.conclusion,
      form.summary || undefined,
      form.continueReason || undefined,
    )
    ElMessage.success('已汇总')
    visible.value = false
    emit('saved')
  } catch (error) {
    logAsyncError('summarize', error)
  } finally {
    submitting.value = false
  }
}

defineExpose({ open })
</script>

<template>
  <el-dialog v-model="visible" title="汇总面试结论" width="480px" destroy-on-close>
    <el-form :model="form" label-width="100px">
      <el-form-item label="结论">
        <el-select v-model="form.conclusion" style="width: 100%">
          <el-option v-for="o in RECRUIT_CONCLUSION_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="总结">
        <el-input v-model="form.summary" type="textarea" :rows="3" maxlength="500" show-word-limit />
      </el-form-item>
      <el-form-item v-if="needReason" label="继续理由">
        <el-input
          v-model="form.continueReason"
          type="textarea"
          :rows="2"
          placeholder="存在不通过评价，须说明继续推进理由"
          data-action="continue-reason"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" data-action="submit" @click="submit">确定</el-button>
    </template>
  </el-dialog>
</template>
