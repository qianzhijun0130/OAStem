<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getPerfPlanApi, savePerfPlanApi } from '@/api/perf'
import { logAsyncError } from '@/utils/asyncGuard'
import type { PerfGradeVO, PerfIndicatorVO, PerfPlanIndicatorVO, PerfPlanVO } from '@/api/types'

/**
 * 新建 / 编辑绩效方案弹窗（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>方案的核心是两张可编辑子表：指标+权重（合计必须 100%）、等级映射与系数。
 * 权重合计实时计算，不满足时保存按钮直接禁用（比点了再弹提示更直观）。</p>
 *
 * <p>指标下拉由调用方在打开前加载（`options.loadIndicatorOptions()`）。</p>
 */
const props = defineProps<{
  indicatorOptions: PerfIndicatorVO[]
}>()

const emit = defineEmits<{ saved: [] }>()

const visible = ref(false)
const submitting = ref(false)

const form = reactive({
  id: undefined as number | undefined,
  planCode: '',
  planName: '',
  cycleType: 2,
  status: 1,
  deptIds: '',
  series: '',
  forceDistribution: 0,
  distributionLimit: 0.2,
  remark: '',
  grades: [] as PerfGradeVO[],
  indicators: [] as { indicatorId?: number; weight: number }[],
})

const weightTotal = computed(() => form.indicators.reduce((sum, i) => sum + Number(i.weight || 0), 0))
const weightOk = computed(() => Math.abs(weightTotal.value - 100) < 0.01)

/** 默认四档：A 1.2 / B 1.0 / C 0.8 / D 0.6，与后端种子一致 */
function defaultGrades(): PerfGradeVO[] {
  return [
    { gradeCode: 'A', gradeName: '优秀', minScore: 90, maxScore: 120, coefficient: 1.2 },
    { gradeCode: 'B', gradeName: '良好', minScore: 80, maxScore: 89.9, coefficient: 1.0 },
    { gradeCode: 'C', gradeName: '合格', minScore: 70, maxScore: 79.9, coefficient: 0.8 },
    { gradeCode: 'D', gradeName: '待改进', minScore: 0, maxScore: 69.9, coefficient: 0.6 },
  ]
}

function openCreate() {
  Object.assign(form, {
    id: undefined,
    planCode: '',
    planName: '',
    cycleType: 2,
    status: 1,
    deptIds: '',
    series: '',
    forceDistribution: 0,
    distributionLimit: 0.2,
    remark: '',
    grades: defaultGrades(),
    indicators: [{ indicatorId: undefined, weight: 100 }],
  })
  visible.value = true
}

async function openEdit(row: PerfPlanVO) {
  try {
    const detailData = await getPerfPlanApi(row.id)
    Object.assign(form, {
      id: detailData.id,
      planCode: detailData.planCode,
      planName: detailData.planName,
      cycleType: detailData.cycleType,
      status: detailData.status,
      deptIds: detailData.deptIds || '',
      series: detailData.series || '',
      forceDistribution: detailData.forceDistribution,
      distributionLimit: Number(detailData.distributionLimit || 0.2),
      remark: detailData.remark || '',
      grades: (detailData.grades || []).map((g) => ({ ...g })),
      indicators: (detailData.indicators || []).map((i: PerfPlanIndicatorVO) => ({
        indicatorId: i.indicatorId,
        weight: Number(i.weight),
      })),
    })
    visible.value = true
  } catch (error) {
    logAsyncError('loadPerfPlanDetail', error)
  }
}

async function submit() {
  if (!form.planCode || !form.planName) {
    ElMessage.warning('请填写方案编码与名称')
    return
  }
  if (!weightOk.value) {
    ElMessage.warning(`指标权重合计必须等于 100%，当前 ${weightTotal.value}%`)
    return
  }
  if (form.indicators.some((i) => !i.indicatorId)) {
    ElMessage.warning('请为每一行选择指标')
    return
  }
  submitting.value = true
  try {
    await savePerfPlanApi({
      id: form.id,
      planCode: form.planCode,
      planName: form.planName,
      cycleType: form.cycleType,
      status: form.status,
      deptIds: form.deptIds || undefined,
      series: form.series || undefined,
      forceDistribution: form.forceDistribution,
      distributionLimit: Number(form.distributionLimit),
      remark: form.remark,
      grades: form.grades,
      indicators: form.indicators.map((i, idx) => ({
        indicatorId: i.indicatorId as number,
        weight: Number(i.weight),
        sort: idx + 1,
      })),
    })
    ElMessage.success('方案已保存')
    visible.value = false
    emit('saved')
  } catch (error) {
    logAsyncError('savePerfPlan', error)
  } finally {
    submitting.value = false
  }
}

defineExpose({ openCreate, openEdit })
</script>

<template>
  <el-dialog v-model="visible" title="绩效方案" width="880px">
    <el-form :model="form" label-width="110px">
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="方案编码" required>
            <el-input v-model="form.planCode" placeholder="PLAN_2026_TECH" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="方案名称" required>
            <el-input v-model="form.planName" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="周期类型">
            <el-select v-model="form.cycleType" style="width: 100%">
              <el-option label="月度" :value="1" />
              <el-option label="季度" :value="2" />
              <el-option label="半年" :value="3" />
              <el-option label="年度" :value="4" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="适用序列">
            <el-input v-model="form.series" placeholder="空=全部" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="状态">
            <el-select v-model="form.status" style="width: 100%">
              <el-option label="草稿" :value="1" />
              <el-option label="启用" :value="2" />
              <el-option label="停用" :value="3" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="适用部门">
            <el-input v-model="form.deptIds" placeholder="部门ID，逗号分隔；空=全部" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="强制分布">
            <el-switch v-model="form.forceDistribution" :active-value="1" :inactive-value="0" />
            <span class="perf__hint perf__hint--inline">仅预警，不拦截</span>
          </el-form-item>
        </el-col>
      </el-row>

      <el-divider content-position="left">指标与权重（合计必须 100%）</el-divider>
      <el-table :data="form.indicators" border size="small">
        <el-table-column label="指标" min-width="240">
          <template #default="{ row }">
            <el-select v-model="row.indicatorId" filterable placeholder="选择指标" style="width: 100%">
              <el-option
                v-for="i in props.indicatorOptions"
                :key="i.id"
                :label="`${i.indicatorName}（${i.indicatorTypeLabel}）`"
                :value="i.id"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="权重(%)" width="140">
          <template #default="{ row }">
            <el-input-number v-model="row.weight" :min="0" :max="100" :precision="2" size="small" style="width: 110px" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ $index }">
            <el-button link type="danger" data-action="remove-indicator" @click="form.indicators.splice($index, 1)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="perf__weight" :class="{ 'is-bad': !weightOk }">
        权重合计：{{ weightTotal }}%
        <span v-if="!weightOk">（必须等于 100%）</span>
        <el-button link type="primary" data-action="add-indicator" @click="form.indicators.push({ indicatorId: undefined, weight: 0 })">
          + 添加指标
        </el-button>
      </div>

      <el-divider content-position="left">等级映射与系数</el-divider>
      <el-table :data="form.grades" border size="small">
        <el-table-column prop="gradeCode" label="等级" width="90" />
        <el-table-column prop="gradeName" label="名称" width="130" />
        <el-table-column label="分数下限" width="130">
          <template #default="{ row }">
            <el-input-number v-model="row.minScore" :min="0" :max="120" :precision="1" size="small" style="width: 100px" />
          </template>
        </el-table-column>
        <el-table-column label="分数上限" width="130">
          <template #default="{ row }">
            <el-input-number v-model="row.maxScore" :min="0" :max="120" :precision="1" size="small" style="width: 100px" />
          </template>
        </el-table-column>
        <el-table-column label="绩效系数" width="140">
          <template #default="{ row }">
            <el-input-number
              v-model="row.coefficient"
              :min="0"
              :max="3"
              :precision="2"
              :step="0.1"
              size="small"
              style="width: 110px"
            />
          </template>
        </el-table-column>
      </el-table>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :disabled="!weightOk" :loading="submitting" data-action="submit" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>
