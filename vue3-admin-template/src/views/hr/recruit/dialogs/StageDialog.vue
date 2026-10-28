<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  changeCandidateStageApi,
  RECRUIT_REJECT_TYPE_OPTIONS,
  RECRUIT_STAGE_OPTIONS,
} from '@/api/recruit'
import type { RecruitCandidateVO } from '@/api/recruit'
import { logAsyncError } from '@/utils/asyncGuard'

/**
 * 候选人阶段流转弹窗（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>三条业务规则都在提交前手工拦截（原来 `:rules` 绑了但没有 form ref，
 * 实际从未生效，真正生效的是这三条 if）：</p>
 * <ul>
 *   <li>目标阶段必选；</li>
 *   <li>淘汰（stage=8）必须填淘汰原因；</li>
 *   <li>回退必须填理由（且后端校验 HR 权限）。</li>
 * </ul>
 */
const emit = defineEmits<{ saved: [] }>()

const visible = ref(false)
const submitting = ref(false)

const form = reactive({
  candidateId: 0,
  candidateName: '',
  stage: undefined as number | undefined,
  rejectType: undefined as number | undefined,
  rejectReason: '',
  rollback: false,
  reason: '',
})

function open(row: RecruitCandidateVO) {
  form.candidateId = row.id
  form.candidateName = row.name
  form.stage = row.stage
  form.rejectType = undefined
  form.rejectReason = ''
  form.rollback = false
  form.reason = ''
  visible.value = true
}

async function submit() {
  if (form.stage == null) {
    ElMessage.warning('请选择目标阶段')
    return
  }
  if (form.stage === 8 && !form.rejectReason) {
    ElMessage.warning('请填写淘汰原因')
    return
  }
  if (form.rollback && !form.reason) {
    ElMessage.warning('回退须填写理由')
    return
  }
  submitting.value = true
  try {
    await changeCandidateStageApi(form.candidateId, {
      stage: form.stage,
      rejectType: form.stage === 8 ? form.rejectType : undefined,
      rejectReason: form.stage === 8 ? form.rejectReason : undefined,
      rollback: form.rollback,
      reason: form.rollback ? form.reason : undefined,
    })
    ElMessage.success('阶段已更新')
    visible.value = false
    emit('saved')
  } catch (error) {
    logAsyncError('changeStage', error)
  } finally {
    submitting.value = false
  }
}

defineExpose({ open })
</script>

<template>
  <el-dialog v-model="visible" title="阶段流转" width="480px" destroy-on-close>
    <el-form :model="form" label-width="90px">
      <el-form-item label="候选人">
        <span>{{ form.candidateName }}</span>
      </el-form-item>
      <el-form-item label="目标阶段" required>
        <el-select v-model="form.stage" style="width: 100%">
          <el-option v-for="o in RECRUIT_STAGE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <template v-if="form.stage === 8">
        <el-form-item label="淘汰类型">
          <el-select v-model="form.rejectType" clearable placeholder="请选择" style="width: 100%">
            <el-option v-for="o in RECRUIT_REJECT_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="淘汰原因">
          <el-input v-model="form.rejectReason" type="textarea" :rows="2" data-action="reject-reason" />
        </el-form-item>
      </template>
      <el-form-item label="回退">
        <el-switch v-model="form.rollback" data-action="rollback" />
        <span class="recruit__hint">回退需 HR 权限并填写理由</span>
      </el-form-item>
      <el-form-item v-if="form.rollback" label="回退理由">
        <el-input v-model="form.reason" type="textarea" :rows="2" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" data-action="submit" @click="submit">确定</el-button>
    </template>
  </el-dialog>
</template>
