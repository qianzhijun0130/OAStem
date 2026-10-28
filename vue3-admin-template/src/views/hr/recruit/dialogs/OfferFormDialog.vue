<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { RECRUIT_SEND_WAY_OPTIONS, saveOfferApi } from '@/api/recruit'
import type { RecruitOfferSaveRequest } from '@/api/recruit'
import { logAsyncError } from '@/utils/asyncGuard'
import type { RecruitOptions } from '../composables/useRecruitOptions'

/**
 * 发起 Offer 弹窗（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>候选人下拉只放「终试（stage=4）」的人——没走到终试就发 Offer 属于越流程，
 * 后端也会拦，前端先收窄选择范围。</p>
 */
const props = defineProps<{ options: RecruitOptions }>()

const emit = defineEmits<{ saved: [] }>()

const visible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()

const form = reactive<RecruitOfferSaveRequest>({
  candidateId: undefined,
  postId: undefined,
  positionId: undefined,
  positionName: '',
  salary: undefined,
  probationMonths: 3,
  reportDate: '',
  expireDate: '',
  sendWay: 1,
  remark: '',
})

const rules: FormRules = {
  candidateId: [{ required: true, message: '请选择候选人', trigger: 'change' }],
  salary: [{ required: true, message: '请输入薪资', trigger: 'blur' }],
}

/** 仅终试（stage=4）可发起 Offer */
const offerCandidateOptions = computed(() => props.options.state.candidateOptions.filter((c) => c.stage === 4))

function openCreate() {
  Object.assign(form, {
    candidateId: undefined,
    postId: undefined,
    positionId: undefined,
    positionName: '',
    salary: undefined,
    probationMonths: 3,
    reportDate: '',
    expireDate: '',
    sendWay: 1,
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
    await saveOfferApi({ ...form })
    ElMessage.success('已发起 Offer')
    visible.value = false
    emit('saved')
  } catch (error) {
    logAsyncError('saveOffer', error)
  } finally {
    submitting.value = false
  }
}

defineExpose({ openCreate })
</script>

<template>
  <el-dialog v-model="visible" title="发起 Offer" width="560px" destroy-on-close>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="候选人" prop="candidateId">
        <el-select v-model="form.candidateId" filterable placeholder="仅终试候选人" style="width: 100%">
          <el-option
            v-for="c in offerCandidateOptions"
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
      <el-form-item label="岗位名称">
        <el-input v-model="form.positionName" maxlength="100" />
      </el-form-item>
      <el-form-item label="薪资" prop="salary">
        <el-input-number v-model="form.salary" :min="0" :max="9999999" controls-position="right" />
      </el-form-item>
      <el-form-item label="试用期(月)">
        <el-input-number v-model="form.probationMonths" :min="0" :max="12" controls-position="right" />
      </el-form-item>
      <el-form-item label="报到日期">
        <el-date-picker v-model="form.reportDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width: 100%" />
      </el-form-item>
      <el-form-item label="有效期至">
        <el-date-picker v-model="form.expireDate" type="date" value-format="YYYY-MM-DD" placeholder="不填按5个工作日" style="width: 100%" />
      </el-form-item>
      <el-form-item label="发送方式">
        <el-select v-model="form.sendWay" style="width: 100%">
          <el-option v-for="o in RECRUIT_SEND_WAY_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
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
