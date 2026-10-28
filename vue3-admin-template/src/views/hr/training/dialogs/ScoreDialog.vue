<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { scoreEnrollApi, TRAIN_ASSESS_WAY_OPTIONS, type TrainEnrollVO, type TrainScoreRequest } from '@/api/training'
import { logAsyncError } from '@/utils/asyncGuard'

/**
 * 成绩录入弹窗（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>「报名管理」和「成绩管理」两个页签都调它：一个给单条录分入口，
 * 一个做批量整理。默认带出已有成绩，避免二次编辑时把 60 分覆盖掉真实值。</p>
 */
const emit = defineEmits<{ saved: [] }>()

const visible = ref(false)
const submitting = ref(false)

const form = reactive<TrainScoreRequest>({
  enrollId: undefined,
  score: 60,
  assessWay: 1,
  satisfied: undefined,
  certFileId: undefined,
  remark: '',
})

function open(row: TrainEnrollVO) {
  form.enrollId = row.id
  form.score = row.score != null ? row.score : 60
  form.assessWay = row.assessWay || 1
  form.satisfied = row.satisfied
  form.certFileId = row.certFileId
  form.remark = row.remark || ''
  visible.value = true
}

async function submit() {
  if (form.enrollId == null || form.score == null) {
    ElMessage.warning('请填写成绩')
    return
  }
  submitting.value = true
  try {
    await scoreEnrollApi({ ...form })
    ElMessage.success('成绩已录入')
    visible.value = false
    emit('saved')
  } catch (error) {
    logAsyncError('scoreEnroll', error)
  } finally {
    submitting.value = false
  }
}

defineExpose({ open })
</script>

<template>
  <el-dialog v-model="visible" title="成绩录入" width="460px" destroy-on-close>
    <el-form :model="form" label-width="90px">
      <el-form-item label="成绩" required>
        <el-input-number v-model="form.score" :min="0" :max="100" :precision="1" controls-position="right" />
      </el-form-item>
      <el-form-item label="考核方式">
        <el-select v-model="form.assessWay" style="width: 100%">
          <el-option v-for="o in TRAIN_ASSESS_WAY_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="满意度">
        <el-rate v-model="form.satisfied" :max="5" allow-half />
      </el-form-item>
      <el-form-item label="评语">
        <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="500" show-word-limit />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">确定</el-button>
    </template>
  </el-dialog>
</template>
