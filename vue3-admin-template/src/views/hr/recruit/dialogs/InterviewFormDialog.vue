<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { RECRUIT_INTERVIEW_WAY_OPTIONS, saveInterviewApi } from '@/api/recruit'
import { logAsyncError } from '@/utils/asyncGuard'
import type { RecruitOptions } from '../composables/useRecruitOptions'

/**
 * 安排面试弹窗（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>面试官可多选（至少一位），候选人下拉来自共享 options。</p>
 */
const props = defineProps<{ options: RecruitOptions }>()

const emit = defineEmits<{ saved: [] }>()

const visible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  id: null as number | null,
  candidateId: undefined as number | undefined,
  postId: undefined as number | undefined,
  roundNo: 1,
  interviewTime: '',
  way: 1,
  location: '',
  interviewerIds: [] as number[],
  remark: '',
})

const rules: FormRules = {
  candidateId: [{ required: true, message: '请选择候选人', trigger: 'change' }],
  interviewTime: [{ required: true, message: '请选择面试时间', trigger: 'change' }],
  interviewerIds: [{ type: 'array', required: true, message: '至少指定一位面试官', trigger: 'change' }],
}

function openCreate() {
  Object.assign(form, {
    id: null,
    candidateId: undefined,
    postId: undefined,
    roundNo: 1,
    interviewTime: '',
    way: 1,
    location: '',
    interviewerIds: [],
    remark: '',
  })
  visible.value = true
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  submitting.value = true
  try {
    await saveInterviewApi({ ...form })
    ElMessage.success('已安排面试')
    visible.value = false
    emit('saved')
  } catch (error) {
    logAsyncError('saveInterview', error)
  } finally {
    submitting.value = false
  }
}

defineExpose({ openCreate })
</script>

<template>
  <el-dialog v-model="visible" title="安排面试" width="580px" destroy-on-close>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="候选人" prop="candidateId">
        <el-select v-model="form.candidateId" filterable placeholder="请选择" style="width: 100%">
          <el-option
            v-for="c in props.options.state.candidateOptions"
            :key="c.id"
            :label="`${c.name}（${c.mobileMasked}）`"
            :value="c.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="职位">
        <el-select v-model="form.postId" clearable filterable placeholder="请选择" style="width: 100%">
          <el-option v-for="p in props.options.state.postOptions" :key="p.id" :label="p.postName" :value="p.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="轮次">
        <el-input-number v-model="form.roundNo" :min="1" :max="20" controls-position="right" />
      </el-form-item>
      <el-form-item label="面试时间" prop="interviewTime">
        <el-date-picker
          v-model="form.interviewTime"
          type="datetime"
          value-format="YYYY-MM-DD HH:mm:ss"
          placeholder="选择时间"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="方式">
        <el-select v-model="form.way" style="width: 100%">
          <el-option v-for="o in RECRUIT_INTERVIEW_WAY_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="地点/链接">
        <el-input v-model="form.location" maxlength="200" />
      </el-form-item>
      <el-form-item label="面试官" prop="interviewerIds">
        <el-select v-model="form.interviewerIds" multiple filterable placeholder="请选择" style="width: 100%">
          <el-option v-for="u in props.options.state.userCandidates" :key="u.id" :label="u.realName" :value="u.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" data-action="submit" @click="submit">确定</el-button>
    </template>
  </el-dialog>
</template>
