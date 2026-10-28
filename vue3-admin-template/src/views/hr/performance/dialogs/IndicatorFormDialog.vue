<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { savePerfIndicatorApi } from '@/api/perf'
import { logAsyncError } from '@/utils/asyncGuard'
import type { PerfIndicatorVO } from '@/api/types'

/**
 * 新建 / 编辑绩效指标弹窗（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>保存后除了重载列表，还要刷新「方案表单」里的指标下拉——
 * 那是另一个页签的弹窗在用。刷新动作由调用方通过 `saved` 事件完成。</p>
 */
const emit = defineEmits<{ saved: [] }>()

const visible = ref(false)
const submitting = ref(false)

const form = reactive({
  id: undefined as number | undefined,
  indicatorCode: '',
  indicatorName: '',
  definition: '',
  indicatorType: 1,
  unit: '',
  scoreStandard: '',
  series: '',
  status: 1,
  remark: '',
})

function openCreate() {
  Object.assign(form, {
    id: undefined,
    indicatorCode: '',
    indicatorName: '',
    definition: '',
    indicatorType: 1,
    unit: '',
    scoreStandard: '',
    series: '',
    status: 1,
    remark: '',
  })
  visible.value = true
}

function openEdit(row: PerfIndicatorVO) {
  Object.assign(form, { ...row })
  visible.value = true
}

async function submit() {
  if (!form.indicatorCode || !form.indicatorName) {
    ElMessage.warning('请填写指标编码与名称')
    return
  }
  submitting.value = true
  try {
    await savePerfIndicatorApi({ ...form })
    ElMessage.success('指标已保存')
    visible.value = false
    emit('saved')
  } catch (error) {
    logAsyncError('savePerfIndicator', error)
  } finally {
    submitting.value = false
  }
}

defineExpose({ openCreate, openEdit })
</script>

<template>
  <el-dialog v-model="visible" title="绩效指标" width="620px">
    <el-form :model="form" label-width="110px">
      <el-form-item label="指标编码" required>
        <el-input v-model="form.indicatorCode" placeholder="KPI_DELIVERY" />
      </el-form-item>
      <el-form-item label="指标名称" required>
        <el-input v-model="form.indicatorName" />
      </el-form-item>
      <el-form-item label="指标类型">
        <el-select v-model="form.indicatorType" style="width: 100%">
          <el-option label="定量" :value="1" />
          <el-option label="定性" :value="2" />
          <el-option label="加分项" :value="3" />
          <el-option label="扣分项" :value="4" />
        </el-select>
      </el-form-item>
      <el-form-item label="计量单位">
        <el-input v-model="form.unit" placeholder="%、个、天" />
      </el-form-item>
      <el-form-item label="指标定义">
        <el-input v-model="form.definition" type="textarea" :rows="2" />
      </el-form-item>
      <el-form-item label="评分标准">
        <el-input
          v-model="form.scoreStandard"
          type="textarea"
          :rows="3"
          placeholder="优秀/良好/合格/待改进/不合格 五档描述"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" data-action="submit" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>
