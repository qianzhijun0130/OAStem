<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { enrollBatchApi, type TrainEnrollBatchRequest } from '@/api/training'
import { logAsyncError } from '@/utils/asyncGuard'
import type { UserVO } from '@/api/types'

/**
 * HR 批量指派报名弹窗（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>期次由调用方在 `open(sessionId)` 时确定——按钮本身要求先选期次，
 * 弹窗里不再重复展示期次下拉，避免两处状态不同步。</p>
 */
const props = defineProps<{
  /** 员工候选：由页面统一加载后传入 */
  userCandidates: UserVO[]
}>()

const emit = defineEmits<{ saved: [] }>()

const visible = ref(false)
const submitting = ref(false)

const form = reactive<TrainEnrollBatchRequest>({
  sessionId: undefined,
  userIds: [],
  reason: '',
})

function open(sessionId: number) {
  form.sessionId = sessionId
  form.userIds = []
  form.reason = ''
  visible.value = true
}

async function submit() {
  if (!form.sessionId || !form.userIds || form.userIds.length === 0) {
    ElMessage.warning('请至少选择一名员工')
    return
  }
  submitting.value = true
  try {
    await enrollBatchApi({ ...form })
    ElMessage.success('已指派报名')
    visible.value = false
    emit('saved')
  } catch (error) {
    logAsyncError('enrollBatch', error)
  } finally {
    submitting.value = false
  }
}

defineExpose({ open })
</script>

<template>
  <el-dialog v-model="visible" title="HR 批量指派报名" width="520px" destroy-on-close>
    <el-form :model="form" label-width="90px">
      <el-form-item label="员工" required>
        <el-select v-model="form.userIds" multiple filterable placeholder="请选择员工" style="width: 100%">
          <el-option v-for="u in props.userCandidates" :key="u.id" :label="u.realName" :value="u.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="指派原因">
        <el-input v-model="form.reason" type="textarea" :rows="3" maxlength="500" show-word-limit />
        <span class="training__hint">超额指派须说明原因</span>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">确定指派</el-button>
    </template>
  </el-dialog>
</template>
