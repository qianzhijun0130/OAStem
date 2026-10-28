<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { convertOfferApi } from '@/api/recruit'
import type { RecruitConvertRequest, RecruitOfferVO } from '@/api/recruit'
import { logAsyncError } from '@/utils/asyncGuard'
import type { RecruitOptions } from '../composables/useRecruitOptions'

/**
 * Offer 转正为员工弹窗（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>两种处理方式：复用已有档案（选目标员工）或新建档案（可指定工号，留空按规则生成）。
 * 选择「复用」还是「新建」决定后端是更新还是插入 `oa_user`。</p>
 */
const props = defineProps<{ options: RecruitOptions }>()

const emit = defineEmits<{ saved: [] }>()

const visible = ref(false)
const submitting = ref(false)

const form = reactive<RecruitConvertRequest>({
  offerId: undefined,
  reuseMode: 1,
  targetUserId: undefined,
  workNumber: '',
  remark: '',
})

function open(row: RecruitOfferVO) {
  Object.assign(form, {
    offerId: row.id,
    reuseMode: 1,
    targetUserId: undefined,
    workNumber: '',
    remark: '',
  })
  visible.value = true
}

async function submit() {
  if (form.offerId == null) {
    return
  }
  submitting.value = true
  try {
    await convertOfferApi({ ...form })
    ElMessage.success('已转为员工')
    visible.value = false
    emit('saved')
  } catch (error) {
    logAsyncError('convertOffer', error)
  } finally {
    submitting.value = false
  }
}

defineExpose({ open })
</script>

<template>
  <el-dialog v-model="visible" title="Offer 转正为员工" width="480px" destroy-on-close>
    <el-form :model="form" label-width="100px">
      <el-form-item label="处理方式">
        <el-radio-group v-model="form.reuseMode">
          <el-radio :value="1">复用已有档案</el-radio>
          <el-radio :value="2">新建档案</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="form.reuseMode === 1" label="目标员工">
        <el-select v-model="form.targetUserId" clearable filterable placeholder="请选择" style="width: 100%">
          <el-option v-for="u in props.options.state.userCandidates" :key="u.id" :label="u.realName" :value="u.id" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="form.reuseMode === 2" label="工号">
        <el-input v-model="form.workNumber" maxlength="50" placeholder="不填按规则生成" />
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
