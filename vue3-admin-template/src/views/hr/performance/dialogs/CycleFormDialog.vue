<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { savePerfCycleApi } from '@/api/perf'
import { logAsyncError } from '@/utils/asyncGuard'
import type { PerfPlanVO } from '@/api/types'

/**
 * 新建考核周期弹窗（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>保存即「按方案范围批量生成考核表」，提示里会带出后端返回的周期 ID——
 * 原来这是排查「表没生成」时唯一能对上的线索，保留。</p>
 *
 * <p>方案下拉由调用方在 `openCreate()` 前加载（`options.loadPlanOptions()`），
 * 本组件只负责渲染与提交。</p>
 */
const props = defineProps<{
  planOptions: PerfPlanVO[]
}>()

const emit = defineEmits<{ saved: [] }>()

const visible = ref(false)
const submitting = ref(false)

const form = reactive({
  id: undefined as number | undefined,
  cycleCode: '',
  cycleName: '',
  planId: undefined as number | undefined,
  range: [] as string[],
  targetDeadline: '',
  selfDeadline: '',
  scoreDeadline: '',
  calibrateDeadline: '',
  confirmDeadline: '',
  remark: '',
})

function openCreate() {
  Object.assign(form, {
    id: undefined,
    cycleCode: '',
    cycleName: '',
    planId: props.planOptions[0]?.id,
    range: [],
    targetDeadline: '',
    selfDeadline: '',
    scoreDeadline: '',
    calibrateDeadline: '',
    confirmDeadline: '',
    remark: '',
  })
  visible.value = true
}

async function submit() {
  if (!form.cycleCode || !form.planId || form.range.length !== 2) {
    ElMessage.warning('请填写周期编码、方案与起止日期')
    return
  }
  submitting.value = true
  try {
    const count = await savePerfCycleApi({
      id: form.id,
      cycleCode: form.cycleCode,
      cycleName: form.cycleName || form.cycleCode,
      planId: form.planId,
      startDate: form.range[0],
      endDate: form.range[1],
      targetDeadline: form.targetDeadline || undefined,
      selfDeadline: form.selfDeadline || undefined,
      scoreDeadline: form.scoreDeadline || undefined,
      calibrateDeadline: form.calibrateDeadline || undefined,
      confirmDeadline: form.confirmDeadline || undefined,
      remark: form.remark,
    })
    ElMessage.success(`周期已创建，自动生成考核表（周期ID ${count}）`)
    visible.value = false
    emit('saved')
  } catch (error) {
    logAsyncError('savePerfCycle', error)
  } finally {
    submitting.value = false
  }
}

defineExpose({ openCreate })
</script>

<template>
  <el-dialog v-model="visible" title="绩效考核周期" width="620px">
    <el-form :model="form" label-width="110px">
      <el-form-item label="周期编码" required>
        <el-input v-model="form.cycleCode" placeholder="如 2026Q4" />
      </el-form-item>
      <el-form-item label="周期名称" required>
        <el-input v-model="form.cycleName" placeholder="如 2026 年第四季度" />
      </el-form-item>
      <el-form-item label="绩效方案" required>
        <el-select v-model="form.planId" style="width: 100%">
          <el-option v-for="p in props.planOptions" :key="p.id" :label="p.planName" :value="p.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="起止日期" required>
        <el-date-picker
          v-model="form.range"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始"
          end-placeholder="结束"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="目标设定截止">
        <el-date-picker v-model="form.targetDeadline" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
      </el-form-item>
      <el-form-item label="自评截止">
        <el-date-picker v-model="form.selfDeadline" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
      </el-form-item>
      <el-form-item label="评分截止">
        <el-date-picker v-model="form.scoreDeadline" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
      </el-form-item>
      <el-form-item label="校准截止">
        <el-date-picker v-model="form.calibrateDeadline" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
      </el-form-item>
      <el-form-item label="确认截止">
        <el-date-picker v-model="form.confirmDeadline" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" data-action="submit" @click="submit">保存并生成考核表</el-button>
    </template>
  </el-dialog>
</template>
