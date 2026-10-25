<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  advancePerfCycleApi,
  appealPerfApi,
  archivePerfCycleApi,
  calibratePerfApi,
  confirmPerfApi,
  deletePerfCycleApi,
  deletePerfIndicatorApi,
  deletePerfPlanApi,
  exportPerfResultApi,
  generatePerfSheetsApi,
  getPerfCyclePageApi,
  getPerfDistributionApi,
  getPerfIndicatorPageApi,
  getPerfPlanApi,
  getPerfPlanPageApi,
  getPerfSheetApi,
  getPerfSheetPageApi,
  handlePerfAppealApi,
  approvePerfTargetApi,
  listPerfIndicatorOptionsApi,
  listPerfPlanOptionsApi,
  savePerfCycleApi,
  savePerfIndicatorApi,
  savePerfPlanApi,
  submitPerfManagerApi,
} from '@/api/perf'
import { logAsyncError } from '@/utils/asyncGuard'
import type {
  PerfCycleVO,
  PerfDistributionVO,
  PerfGradeVO,
  PerfIndicatorVO,
  PerfPlanIndicatorVO,
  PerfPlanVO,
  PerfSheetDetailVO,
  PerfSheetVO,
} from '@/api/types'

const activeTab = ref('sheet')

const money1 = (value?: number) => (value == null ? '-' : Number(value).toFixed(1))
const gradeTagType = (grade?: string) => {
  if (grade === 'A') return 'success'
  if (grade === 'B') return 'primary'
  if (grade === 'C') return 'warning'
  if (grade === 'D') return 'danger'
  return 'info'
}

/* ==================== 周期（多处复用） ==================== */

const cycles = ref<PerfCycleVO[]>([])
const cycleId = ref<number>()

async function loadCycles() {
  try {
    const page = await getPerfCyclePageApi({ pageNo: 1, pageSize: 50 })
    cycles.value = page.records || []
    if (!cycleId.value && cycles.value.length) {
      cycleId.value = cycles.value[0].id
    }
  } catch (error) {
    logAsyncError('loadPerfCycles', error)
  }
}

/* ==================== 考核表列表 ==================== */

const sheetQuery = reactive({
  cycleId: undefined as number | undefined,
  stage: 'all',
  keyword: '',
  pageNo: 1,
  pageSize: 10,
})
const sheets = ref<PerfSheetVO[]>([])
const sheetTotal = ref(0)
const sheetLoading = ref(false)

async function loadSheets() {
  sheetLoading.value = true
  try {
    const page = await getPerfSheetPageApi({ ...sheetQuery, cycleId: sheetQuery.cycleId || cycleId.value })
    sheets.value = page.records || []
    sheetTotal.value = Number(page.total || 0)
  } catch (error) {
    logAsyncError('loadPerfSheets', error)
  } finally {
    sheetLoading.value = false
  }
}

/* ---------- 明细抽屉 ---------- */

const drawerVisible = ref(false)
const detail = ref<PerfSheetDetailVO | null>(null)
const scoreForm = reactive<{ items: { itemId: number; score?: number; comment?: string }[]; comment: string }>({
  items: [],
  comment: '',
})
const calibrateForm = reactive<{ score?: number; gradeCode?: string; reason: string }>({
  score: undefined,
  gradeCode: undefined,
  reason: '',
})
const appealForm = reactive<{ reason: string }>({ reason: '' })
const appealHandle = reactive<{ status: number; opinion: string; score?: number }>({
  status: 2,
  opinion: '',
  score: undefined,
})

async function openSheet(row: PerfSheetVO) {
  try {
    const data = await getPerfSheetApi(row.id)
    detail.value = data
    scoreForm.items = (data.items || []).map((i) => ({
      itemId: i.id,
      score: i.managerScore ?? undefined,
      comment: i.managerComment || '',
    }))
    scoreForm.comment = data.managerComment || ''
    calibrateForm.score = data.totalScore ?? undefined
    calibrateForm.gradeCode = data.gradeCode ?? undefined
    calibrateForm.reason = ''
    appealForm.reason = ''
    appealHandle.status = 2
    appealHandle.opinion = ''
    appealHandle.score = undefined
    drawerVisible.value = true
  } catch (error) {
    logAsyncError('loadPerfSheet', error)
  }
}

/** 上级评分 / HR 代评共用：后端按「是否直属上级」判断，非直属会留痕为代评 */
async function submitManagerScore() {
  if (!detail.value) return
  if (!scoreForm.comment.trim()) {
    ElMessage.warning('请填写总评语')
    return
  }
  try {
    await submitPerfManagerApi({
      sheetId: detail.value.id,
      comment: scoreForm.comment.trim(),
      items: scoreForm.items.filter((i) => i.score !== undefined && i.score !== null),
    })
    ElMessage.success('评分已提交')
    drawerVisible.value = false
    await loadSheets()
  } catch (error) {
    logAsyncError('submitPerfManagerScore', error)
  }
}

async function submitCalibrate() {
  if (!detail.value) return
  if (!calibrateForm.reason.trim()) {
    ElMessage.warning('校准调整必须填写理由')
    return
  }
  try {
    await calibratePerfApi({
      sheetId: detail.value.id,
      score: calibrateForm.score,
      gradeCode: calibrateForm.gradeCode,
      reason: calibrateForm.reason.trim(),
    })
    ElMessage.success('校准已保存（留痕含原值/新值/操作人）')
    drawerVisible.value = false
    await loadSheets()
  } catch (error) {
    logAsyncError('calibratePerf', error)
  }
}

async function approveTarget(pass: boolean) {
  if (!detail.value) return
  try {
    await ElMessageBox.confirm(
      pass ? '通过后目标将锁定，周期内不可再改。' : '驳回后员工可重新填写目标。',
      pass ? '通过目标' : '驳回目标',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await approvePerfTargetApi(detail.value.id, pass, '')
    ElMessage.success(pass ? '目标已锁定' : '已驳回')
    drawerVisible.value = false
    await loadSheets()
  } catch (error) {
    logAsyncError('approvePerfTarget', error)
  }
}

async function handleAppeal() {
  if (!detail.value?.appeal) return
  if (appealHandle.status === 3 && appealHandle.score == null) {
    ElMessage.warning('「调整」结论必须填写调整后分数')
    return
  }
  try {
    await handlePerfAppealApi({
      appealId: detail.value.appeal.id,
      status: appealHandle.status,
      opinion: appealHandle.opinion,
      score: appealHandle.score,
    })
    ElMessage.success('申诉已处理')
    drawerVisible.value = false
    await loadSheets()
  } catch (error) {
    logAsyncError('handlePerfAppeal', error)
  }
}

/** HR 代员工发起申诉用不到；这里是给「以本人身份调试」留的入口，正常由我的绩效页发起 */
async function submitAppealAsSelf() {
  if (!detail.value) return
  if (!appealForm.reason.trim()) {
    ElMessage.warning('请填写申诉理由')
    return
  }
  try {
    await appealPerfApi({ sheetId: detail.value.id, reason: appealForm.reason.trim() })
    ElMessage.success('申诉已提交')
    drawerVisible.value = false
    await loadSheets()
  } catch (error) {
    logAsyncError('appealPerf', error)
  }
}

const canScore = computed(() => {
  const d = detail.value
  if (!d) return false
  return (d.stage ?? 0) >= 3 && d.managerStatus === 0 && (d.selfStatus === 1 || d.selfMissing === 1)
})

const canCalibrate = computed(() => {
  const d = detail.value
  return !!d && d.managerStatus === 1 && (d.stage ?? 0) >= 4 && d.stage !== 6
})

const canConfirm = computed(() => {
  const d = detail.value
  return !!d && d.confirmStatus === 0 && d.managerStatus === 1 && (d.stage ?? 0) >= 5 && d.stage !== 6
})

async function confirmResult() {
  if (!detail.value) return
  try {
    await confirmPerfApi(detail.value.id)
    ElMessage.success('已确认知悉')
    drawerVisible.value = false
    await loadSheets()
  } catch (error) {
    logAsyncError('confirmPerf', error)
  }
}

/* ==================== 周期页签 ==================== */

const cycleDialogVisible = ref(false)
const planOptions = ref<PerfPlanVO[]>([])
const cycleForm = reactive({
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

async function loadPlanOptions() {
  try {
    planOptions.value = await listPerfPlanOptionsApi()
  } catch (error) {
    logAsyncError('loadPerfPlanOptions', error)
  }
}

function openCycleForm() {
  cycleDialogVisible.value = true
  Object.assign(cycleForm, {
    id: undefined,
    cycleCode: '',
    cycleName: '',
    planId: planOptions.value[0]?.id,
    range: [],
    targetDeadline: '',
    selfDeadline: '',
    scoreDeadline: '',
    calibrateDeadline: '',
    confirmDeadline: '',
    remark: '',
  })
}

async function submitCycle() {
  if (!cycleForm.cycleCode || !cycleForm.planId || cycleForm.range.length !== 2) {
    ElMessage.warning('请填写周期编码、方案与起止日期')
    return
  }
  try {
    const count = await savePerfCycleApi({
      id: cycleForm.id,
      cycleCode: cycleForm.cycleCode,
      cycleName: cycleForm.cycleName || cycleForm.cycleCode,
      planId: cycleForm.planId,
      startDate: cycleForm.range[0],
      endDate: cycleForm.range[1],
      targetDeadline: cycleForm.targetDeadline || undefined,
      selfDeadline: cycleForm.selfDeadline || undefined,
      scoreDeadline: cycleForm.scoreDeadline || undefined,
      calibrateDeadline: cycleForm.calibrateDeadline || undefined,
      confirmDeadline: cycleForm.confirmDeadline || undefined,
      remark: cycleForm.remark,
    })
    ElMessage.success(`周期已创建，自动生成考核表（周期ID ${count}）`)
    cycleDialogVisible.value = false
    await loadCycles()
    await loadSheets()
  } catch (error) {
    logAsyncError('savePerfCycle', error)
  }
}

async function advanceCycle(row: PerfCycleVO) {
  const nextLabel = ['', '自评中', '上级评分', '校准', '结果确认', '已归档'][row.stage] || '下一阶段'
  try {
    await ElMessageBox.confirm(
      `将「${row.cycleName}」从「${row.stageLabel}」推进到「${nextLabel}」。推进后本阶段未完成的动作将无法补做，确定继续？`,
      '推进阶段',
      { type: 'warning', confirmButtonText: '确定推进', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    const count = await advancePerfCycleApi(row.id)
    ElMessage.success(`已推进，涉及 ${count} 张考核表`)
    await loadCycles()
    await loadSheets()
    if (activeTab.value === 'dist') {
      await loadDistribution()
    }
  } catch (error) {
    logAsyncError('advancePerfCycle', error)
  }
}

async function archiveCycle(row: PerfCycleVO) {
  try {
    await ElMessageBox.confirm(
      `归档后「${row.cycleName}」的结果转为只读，薪酬将引用本期系数。确定归档？`,
      '归档周期',
      { type: 'warning', confirmButtonText: '确定归档', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    const count = await archivePerfCycleApi(row.id)
    ElMessage.success(`已归档，${count} 张考核表定级完成`)
    await loadCycles()
  } catch (error) {
    logAsyncError('archivePerfCycle', error)
  }
}

async function generateSheets(row: PerfCycleVO) {
  try {
    const count = await generatePerfSheetsApi(row.id)
    ElMessage.success(count ? `已补齐 ${count} 张考核表` : '没有需要补齐的考核表')
    await loadCycles()
  } catch (error) {
    logAsyncError('generatePerfSheets', error)
  }
}

async function removeCycle(row: PerfCycleVO) {
  try {
    await ElMessageBox.confirm(`确定删除周期「${row.cycleName}」吗？`, '删除周期', {
      type: 'warning',
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deletePerfCycleApi(row.id)
    ElMessage.success('已删除')
    await loadCycles()
  } catch (error) {
    logAsyncError('deletePerfCycle', error)
  }
}

/* ==================== 分布 ==================== */

const distribution = ref<PerfDistributionVO[]>([])
const distLoading = ref(false)

async function loadDistribution() {
  if (!cycleId.value) return
  distLoading.value = true
  try {
    distribution.value = await getPerfDistributionApi(cycleId.value)
  } catch (error) {
    logAsyncError('loadPerfDistribution', error)
  } finally {
    distLoading.value = false
  }
}

/* ==================== 方案 ==================== */

const plans = ref<PerfPlanVO[]>([])
const planTotal = ref(0)
const planQuery = reactive({ keyword: '', pageNo: 1, pageSize: 10 })
const planLoading = ref(false)
const planDialogVisible = ref(false)
const indicatorOptions = ref<PerfIndicatorVO[]>([])
const planForm = reactive({
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

const weightTotal = computed(() =>
  planForm.indicators.reduce((sum, i) => sum + Number(i.weight || 0), 0),
)
const weightOk = computed(() => Math.abs(weightTotal.value - 100) < 0.01)

async function loadPlans() {
  planLoading.value = true
  try {
    const page = await getPerfPlanPageApi({ ...planQuery })
    plans.value = page.records || []
    planTotal.value = Number(page.total || 0)
  } catch (error) {
    logAsyncError('loadPerfPlans', error)
  } finally {
    planLoading.value = false
  }
}

function defaultGrades(): PerfGradeVO[] {
  return [
    { gradeCode: 'A', gradeName: '优秀', minScore: 90, maxScore: 120, coefficient: 1.2 },
    { gradeCode: 'B', gradeName: '良好', minScore: 80, maxScore: 89.9, coefficient: 1.0 },
    { gradeCode: 'C', gradeName: '合格', minScore: 70, maxScore: 79.9, coefficient: 0.8 },
    { gradeCode: 'D', gradeName: '待改进', minScore: 0, maxScore: 69.9, coefficient: 0.6 },
  ]
}

async function openPlanForm(row?: PerfPlanVO) {
  planDialogVisible.value = true
  if (row) {
    const detailData = await getPerfPlanApi(row.id)
    Object.assign(planForm, {
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
  } else {
    Object.assign(planForm, {
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
  }
}

async function submitPlan() {
  if (!planForm.planCode || !planForm.planName) {
    ElMessage.warning('请填写方案编码与名称')
    return
  }
  if (!weightOk.value) {
    ElMessage.warning(`指标权重合计必须等于 100%，当前 ${weightTotal.value}%`)
    return
  }
  const invalid = planForm.indicators.some((i) => !i.indicatorId)
  if (invalid) {
    ElMessage.warning('请为每一行选择指标')
    return
  }
  try {
    await savePerfPlanApi({
      id: planForm.id,
      planCode: planForm.planCode,
      planName: planForm.planName,
      cycleType: planForm.cycleType,
      status: planForm.status,
      deptIds: planForm.deptIds || undefined,
      series: planForm.series || undefined,
      forceDistribution: planForm.forceDistribution,
      distributionLimit: Number(planForm.distributionLimit),
      remark: planForm.remark,
      grades: planForm.grades,
      indicators: planForm.indicators.map((i, idx) => ({
        indicatorId: i.indicatorId as number,
        weight: Number(i.weight),
        sort: idx + 1,
      })),
    })
    ElMessage.success('方案已保存')
    planDialogVisible.value = false
    await loadPlans()
    await loadPlanOptions()
  } catch (error) {
    logAsyncError('savePerfPlan', error)
  }
}

async function removePlan(row: PerfPlanVO) {
  try {
    await ElMessageBox.confirm(`确定删除方案「${row.planName}」吗？`, '删除方案', {
      type: 'warning',
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deletePerfPlanApi(row.id)
    ElMessage.success('已删除')
    await loadPlans()
  } catch (error) {
    logAsyncError('deletePerfPlan', error)
  }
}

/* ==================== 指标库 ==================== */

const indicators = ref<PerfIndicatorVO[]>([])
const indicatorTotal = ref(0)
const indicatorQuery = reactive({ keyword: '', type: undefined as number | undefined, pageNo: 1, pageSize: 10 })
const indicatorLoading = ref(false)
const indicatorDialogVisible = ref(false)
const indicatorForm = reactive({
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

async function loadIndicators() {
  indicatorLoading.value = true
  try {
    const page = await getPerfIndicatorPageApi({ ...indicatorQuery })
    indicators.value = page.records || []
    indicatorTotal.value = Number(page.total || 0)
  } catch (error) {
    logAsyncError('loadPerfIndicators', error)
  } finally {
    indicatorLoading.value = false
  }
}

function openIndicatorForm(row?: PerfIndicatorVO) {
  indicatorDialogVisible.value = true
  Object.assign(indicatorForm, row ? { ...row } : {
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
}

async function submitIndicator() {
  if (!indicatorForm.indicatorCode || !indicatorForm.indicatorName) {
    ElMessage.warning('请填写指标编码与名称')
    return
  }
  try {
    await savePerfIndicatorApi({ ...indicatorForm })
    ElMessage.success('指标已保存')
    indicatorDialogVisible.value = false
    await loadIndicators()
    indicatorOptions.value = await listPerfIndicatorOptionsApi()
  } catch (error) {
    logAsyncError('savePerfIndicator', error)
  }
}

async function removeIndicator(row: PerfIndicatorVO) {
  try {
    await ElMessageBox.confirm(`确定删除指标「${row.indicatorName}」吗？`, '删除指标', {
      type: 'warning',
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deletePerfIndicatorApi(row.id)
    ElMessage.success('已删除')
    await loadIndicators()
  } catch (error) {
    logAsyncError('deletePerfIndicator', error)
  }
}

/* ==================== 导出 ==================== */

const exporting = ref(false)

async function exportResult() {
  if (!cycleId.value) return
  exporting.value = true
  try {
    // 下载动作与文件名解析收敛在 exportPerfResultApi → downloadFile，
    // 页面不再接触 Blob（此前 `blob as Blob` 强转拿到的是 AxiosResponse，点了没反应）
    await exportPerfResultApi(cycleId.value)
    ElMessage.success('导出完成')
  } catch (error) {
    logAsyncError('exportPerfResult', error)
  } finally {
    exporting.value = false
  }
}

onMounted(async () => {
  await loadPlanOptions()
  await loadCycles()
  await Promise.all([loadSheets(), loadPlans(), loadIndicators(), listPerfIndicatorOptionsApi().then((r) => { indicatorOptions.value = r })])
})
</script>

<template>
  <div class="perf">
    <el-card shadow="never">
      <el-tabs v-model="activeTab">
        <!-- ============ 考核表 ============ -->
        <el-tab-pane label="考核表" name="sheet">
          <el-form :inline="true" @submit.prevent>
            <el-form-item label="周期">
              <el-select v-model="sheetQuery.cycleId" clearable placeholder="全部周期" style="width: 200px">
                <el-option v-for="c in cycles" :key="c.id" :label="c.cycleName" :value="c.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="进度">
              <el-select v-model="sheetQuery.stage" style="width: 130px">
                <el-option label="全部" value="all" />
                <el-option label="目标未锁定" value="target" />
                <el-option label="待自评" value="self" />
                <el-option label="待上级评分" value="manager" />
                <el-option label="待确认" value="confirm" />
              </el-select>
            </el-form-item>
            <el-form-item label="姓名/工号">
              <el-input v-model="sheetQuery.keyword" clearable style="width: 150px" @keyup.enter="sheetQuery.pageNo = 1; loadSheets()" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="sheetQuery.pageNo = 1; loadSheets()">查询</el-button>
              <el-button v-permission="'hr:performance:export'" :loading="exporting" @click="exportResult">导出</el-button>
            </el-form-item>
          </el-form>

          <el-table v-loading="sheetLoading" :data="sheets" border class="perf__sheet-table">
            <el-table-column prop="cycleName" label="周期" width="150" show-overflow-tooltip />
            <el-table-column prop="workNumber" label="工号" width="105" />
            <el-table-column prop="realName" label="姓名" width="90" />
            <el-table-column prop="deptName" label="部门" min-width="110" show-overflow-tooltip />
            <el-table-column prop="leaderName" label="直属上级" width="100" />
            <el-table-column label="目标" width="95">
              <template #default="{ row }">
                <el-tag size="small" effect="plain" :type="row.targetStatus === 2 ? 'success' : 'info'">
                  {{ row.targetStatusLabel }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="自评" width="90">
              <template #default="{ row }">
                <el-tag v-if="row.selfStatus === 1" size="small" effect="plain" type="success">已自评</el-tag>
                <el-tag v-else-if="row.selfMissing === 1" size="small" effect="plain" type="warning">未自评</el-tag>
                <span v-else class="perf__muted">-</span>
              </template>
            </el-table-column>
            <el-table-column label="上级评分" width="110">
              <template #default="{ row }">
                <el-tag v-if="row.managerStatus === 1" size="small" effect="plain" :type="row.hrScored === 1 ? 'warning' : 'success'">
                  {{ row.hrScored === 1 ? 'HR 代评' : '已评分' }}
                </el-tag>
                <span v-else class="perf__muted">未评分</span>
              </template>
            </el-table-column>
            <el-table-column label="总分" width="80" align="right">
              <template #default="{ row }">{{ money1(row.totalScore) }}</template>
            </el-table-column>
            <el-table-column label="等级" width="80">
              <template #default="{ row }">
                <el-tag v-if="row.gradeCode" size="small" :type="gradeTagType(row.gradeCode)">{{ row.gradeCode }}</el-tag>
                <span v-else class="perf__muted">-</span>
              </template>
            </el-table-column>
            <el-table-column label="系数" width="80" align="right">
              <template #default="{ row }">{{ row.coefficient == null ? '-' : Number(row.coefficient).toFixed(2) }}</template>
            </el-table-column>
            <el-table-column label="申诉" width="90">
              <template #default="{ row }">
                <el-tag v-if="row.appealStatus === 1" size="small" type="warning" effect="plain">待处理</el-tag>
                <el-tag v-else-if="row.appealStatus" size="small" type="info" effect="plain">已处理</el-tag>
                <span v-else class="perf__muted">-</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openSheet(row)">查看</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            class="perf__pager"
            :current-page="sheetQuery.pageNo"
            :page-size="sheetQuery.pageSize"
            :total="sheetTotal"
            layout="total, prev, pager, next"
            background
            @current-change="(p: number) => { sheetQuery.pageNo = p; loadSheets() }"
          />
        </el-tab-pane>

        <!-- ============ 周期 ============ -->
        <el-tab-pane label="考核周期" name="cycle">
          <div class="perf__toolbar">
            <el-button v-permission="'hr:performance:plan'" type="primary" plain @click="openCycleForm">新建周期</el-button>
            <span class="perf__hint">周期创建时会按方案范围自动生成考核表（入职不满 1 个月不生成）</span>
          </div>
          <el-table :data="cycles" border class="perf__cycle-table">
            <el-table-column prop="cycleName" label="周期" min-width="150" />
            <el-table-column prop="cycleCode" label="编码" width="110" />
            <el-table-column prop="planName" label="方案" min-width="130" show-overflow-tooltip />
            <el-table-column label="起止" width="190">
              <template #default="{ row }">{{ row.startDate }} ~ {{ row.endDate }}</template>
            </el-table-column>
            <el-table-column label="阶段" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 2 ? 'info' : 'primary'" effect="plain">{{ row.stageLabel }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="进度（目标/自评/评分/确认）" width="220">
              <template #default="{ row }">
                {{ row.targetDone }} / {{ row.selfDone }} / {{ row.managerDone }} / {{ row.confirmDone }}
                <span class="perf__muted">（共 {{ row.sheetCount }}）</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="290" fixed="right">
              <template #default="{ row }">
                <el-button v-permission="'hr:performance:plan'" link type="primary" :disabled="row.status === 2" @click="advanceCycle(row)">
                  推进
                </el-button>
                <el-button v-permission="'hr:performance:calibrate'" link type="primary" :disabled="row.status === 2" @click="archiveCycle(row)">
                  归档
                </el-button>
                <el-button v-permission="'hr:performance:plan'" link @click="generateSheets(row)">补齐考核表</el-button>
                <el-button v-permission="'hr:performance:plan'" link type="danger" @click="removeCycle(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- ============ 分布 ============ -->
        <el-tab-pane label="等级分布" name="dist">
          <el-form :inline="true" @submit.prevent>
            <el-form-item label="周期">
              <el-select v-model="cycleId" style="width: 200px" @change="loadDistribution">
                <el-option v-for="c in cycles" :key="c.id" :label="c.cycleName" :value="c.id" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="loadDistribution">统计</el-button>
            </el-form-item>
          </el-form>
          <el-alert
            class="perf__tip"
            type="info"
            :closable="false"
            show-icon
            title="强制分布默认关闭：这里只做「A 占比超过建议上限」的预警，不拦截任何人定级。"
          />
          <el-table v-loading="distLoading" :data="distribution" border class="perf__dist-table">
            <el-table-column prop="dimension" label="部门" min-width="150" />
            <el-table-column prop="total" label="人数" width="90" />
            <el-table-column prop="countA" label="A" width="80" />
            <el-table-column prop="countB" label="B" width="80" />
            <el-table-column prop="countC" label="C" width="80" />
            <el-table-column prop="countD" label="D" width="80" />
            <el-table-column label="A 占比" width="130">
              <template #default="{ row }">
                {{ (Number(row.aRatio) * 100).toFixed(1) }}%
                <el-tag v-if="row.overLimit === 1" size="small" type="warning" effect="plain">超建议上限</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="平均分" width="100" align="right">
              <template #default="{ row }">{{ money1(row.avgScore) }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- ============ 方案 ============ -->
        <el-tab-pane label="绩效方案" name="plan">
          <el-form :inline="true" @submit.prevent>
            <el-form-item label="方案">
              <el-input v-model="planQuery.keyword" clearable style="width: 180px" @keyup.enter="planQuery.pageNo = 1; loadPlans()" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="planQuery.pageNo = 1; loadPlans()">查询</el-button>
              <el-button v-permission="'hr:performance:plan'" type="primary" plain @click="openPlanForm()">新建方案</el-button>
            </el-form-item>
          </el-form>
          <el-table v-loading="planLoading" :data="plans" border class="perf__plan-table">
            <el-table-column prop="planCode" label="编码" width="150" />
            <el-table-column prop="planName" label="名称" min-width="150" />
            <el-table-column prop="cycleTypeLabel" label="周期类型" width="100" />
            <el-table-column prop="deptNames" label="适用部门" min-width="120" show-overflow-tooltip />
            <el-table-column prop="series" label="适用序列" width="100">
              <template #default="{ row }">{{ row.series || '全部' }}</template>
            </el-table-column>
            <el-table-column label="权重合计" width="110" align="right">
              <template #default="{ row }">
                <span :class="Math.abs(Number(row.weightTotal) - 100) < 0.01 ? '' : 'perf__warn'">
                  {{ Number(row.weightTotal || 0) }}%
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="statusLabel" label="状态" width="90" />
            <el-table-column label="操作" width="140" fixed="right">
              <template #default="{ row }">
                <el-button v-permission="'hr:performance:plan'" link type="primary" @click="openPlanForm(row)">编辑</el-button>
                <el-button v-permission="'hr:performance:plan'" link type="danger" @click="removePlan(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            class="perf__pager"
            :current-page="planQuery.pageNo"
            :page-size="planQuery.pageSize"
            :total="planTotal"
            layout="total, prev, pager, next"
            background
            @current-change="(p: number) => { planQuery.pageNo = p; loadPlans() }"
          />
        </el-tab-pane>

        <!-- ============ 指标库 ============ -->
        <el-tab-pane label="指标库" name="indicator">
          <el-form :inline="true" @submit.prevent>
            <el-form-item label="指标">
              <el-input v-model="indicatorQuery.keyword" clearable style="width: 180px" @keyup.enter="indicatorQuery.pageNo = 1; loadIndicators()" />
            </el-form-item>
            <el-form-item label="类型">
              <el-select v-model="indicatorQuery.type" clearable placeholder="全部" style="width: 120px">
                <el-option label="定量" :value="1" />
                <el-option label="定性" :value="2" />
                <el-option label="加分项" :value="3" />
                <el-option label="扣分项" :value="4" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="indicatorQuery.pageNo = 1; loadIndicators()">查询</el-button>
              <el-button v-permission="'hr:performance:indicator'" type="primary" plain @click="openIndicatorForm()">新建指标</el-button>
            </el-form-item>
          </el-form>
          <el-table v-loading="indicatorLoading" :data="indicators" border class="perf__indicator-table">
            <el-table-column prop="indicatorCode" label="编码" width="150" />
            <el-table-column prop="indicatorName" label="名称" min-width="160" />
            <el-table-column prop="indicatorTypeLabel" label="类型" width="100" />
            <el-table-column prop="unit" label="单位" width="80" />
            <el-table-column prop="definition" label="定义" min-width="200" show-overflow-tooltip />
            <el-table-column label="操作" width="140" fixed="right">
              <template #default="{ row }">
                <el-button v-permission="'hr:performance:indicator'" link type="primary" @click="openIndicatorForm(row)">编辑</el-button>
                <el-button v-permission="'hr:performance:indicator'" link type="danger" @click="removeIndicator(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            class="perf__pager"
            :current-page="indicatorQuery.pageNo"
            :page-size="indicatorQuery.pageSize"
            :total="indicatorTotal"
            layout="total, prev, pager, next"
            background
            @current-change="(p: number) => { indicatorQuery.pageNo = p; loadIndicators() }"
          />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- ============ 考核表明细 ============ -->
    <el-drawer v-model="drawerVisible" title="绩效考核表" size="60%">
      <template v-if="detail">
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="姓名">{{ detail.realName }}</el-descriptions-item>
          <el-descriptions-item label="工号">{{ detail.workNumber }}</el-descriptions-item>
          <el-descriptions-item label="部门">{{ detail.deptName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="周期">{{ detail.cycleName }}</el-descriptions-item>
          <el-descriptions-item label="阶段">{{ detail.stageLabel }}</el-descriptions-item>
          <el-descriptions-item label="直属上级">{{ detail.leaderName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="自评总分">{{ money1(detail.selfTotal) }}</el-descriptions-item>
          <el-descriptions-item label="上级评分">{{ money1(detail.managerTotal) }}</el-descriptions-item>
          <el-descriptions-item label="最终总分">
            <b>{{ money1(detail.totalScore) }}</b>
          </el-descriptions-item>
          <el-descriptions-item label="等级">
            <el-tag v-if="detail.gradeCode" :type="gradeTagType(detail.gradeCode)">
              {{ detail.gradeCode }} {{ detail.gradeName || '' }}
            </el-tag>
            <span v-else class="perf__muted">未定级</span>
          </el-descriptions-item>
          <el-descriptions-item label="系数">
            {{ detail.coefficient == null ? '-' : Number(detail.coefficient).toFixed(2) }}
          </el-descriptions-item>
          <el-descriptions-item label="确认">
            {{ detail.confirmStatus === 1 ? '已确认' : '未确认' }}
          </el-descriptions-item>
        </el-descriptions>

        <el-table :data="detail.items" border size="small" class="perf__items">
          <el-table-column prop="indicatorName" label="指标" min-width="150" />
          <el-table-column prop="indicatorType" label="类型" width="80">
            <template #default="{ row }">
              {{ ['', '定量', '定性', '加分', '扣分'][row.indicatorType] || '-' }}
            </template>
          </el-table-column>
          <el-table-column label="权重" width="80" align="right">
            <template #default="{ row }">{{ row.indicatorType >= 3 ? '-' : `${row.weight}%` }}</template>
          </el-table-column>
          <el-table-column prop="targetValue" label="目标值" min-width="130" show-overflow-tooltip />
          <el-table-column label="自评" width="80" align="right">
            <template #default="{ row }">{{ money1(row.selfScore) }}</template>
          </el-table-column>
          <el-table-column label="上级评分" width="95" align="right">
            <template #default="{ row }">{{ money1(row.managerScore) }}</template>
          </el-table-column>
          <el-table-column label="加权分" width="90" align="right">
            <template #default="{ row }">{{ money1(row.weightedScore) }}</template>
          </el-table-column>
        </el-table>

        <!-- 目标审核 -->
        <div v-if="detail.targetStatus === 1" class="perf__section">
          <el-divider content-position="left">目标审核</el-divider>
          <el-button v-permission="'hr:performance:target'" type="primary" @click="approveTarget(true)">通过并锁定</el-button>
          <el-button v-permission="'hr:performance:target'" @click="approveTarget(false)">驳回重填</el-button>
        </div>

        <!-- 上级评分 -->
        <div v-if="canScore" class="perf__section">
          <el-divider content-position="left">上级评分（可直接改分后提交）</el-divider>
          <el-table :data="scoreForm.items" border size="small">
            <el-table-column label="指标" min-width="160">
              <template #default="{ $index }">
                {{ detail.items[$index]?.indicatorName }}
              </template>
            </el-table-column>
            <el-table-column label="自评分" width="90" align="right">
              <template #default="{ $index }">{{ money1(detail.items[$index]?.selfScore) }}</template>
            </el-table-column>
            <el-table-column label="评分" width="150">
              <template #default="{ row }">
                <el-input-number v-model="row.score" :min="0" :max="100" :precision="1" size="small" style="width: 120px" />
              </template>
            </el-table-column>
            <el-table-column label="评语" min-width="180">
              <template #default="{ row }">
                <el-input v-model="row.comment" size="small" placeholder="可选" />
              </template>
            </el-table-column>
          </el-table>
          <el-form-item label="总评语" class="perf__comment">
            <el-input v-model="scoreForm.comment" type="textarea" :rows="2" placeholder="必填：给员工的整体反馈" />
          </el-form-item>
          <el-button type="primary" @click="submitManagerScore">提交评分</el-button>
        </div>

        <!-- HR 校准 -->
        <div v-if="canCalibrate" class="perf__section">
          <el-divider content-position="left">HR 校准（必须填理由，留痕）</el-divider>
          <el-form :inline="true" @submit.prevent>
            <el-form-item label="调整后总分">
              <el-input-number v-model="calibrateForm.score" :min="0" :max="120" :precision="1" style="width: 140px" />
            </el-form-item>
            <el-form-item label="等级">
              <el-select v-model="calibrateForm.gradeCode" clearable placeholder="按分数自动" style="width: 130px">
                <el-option v-for="g in ['A', 'B', 'C', 'D']" :key="g" :label="g" :value="g" />
              </el-select>
            </el-form-item>
            <el-form-item label="校准理由">
              <el-input v-model="calibrateForm.reason" placeholder="必填" style="width: 260px" />
            </el-form-item>
            <el-form-item>
              <el-button v-permission="'hr:performance:calibrate'" type="primary" @click="submitCalibrate">保存校准</el-button>
            </el-form-item>
          </el-form>
        </div>

        <!-- 确认 / 申诉 -->
        <div v-if="canConfirm" class="perf__section">
          <el-divider content-position="left">结果确认</el-divider>
          <el-button type="primary" @click="confirmResult">确认知悉</el-button>
        </div>

        <div v-if="detail.appeal" class="perf__section">
          <el-divider content-position="left">申诉</el-divider>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="申诉理由">{{ detail.appeal.reason }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ detail.appeal.statusLabel }}</el-descriptions-item>
            <el-descriptions-item label="处理前">{{ money1(detail.appeal.beforeScore) }} / {{ detail.appeal.beforeGrade }}</el-descriptions-item>
            <el-descriptions-item label="处理后">{{ money1(detail.appeal.afterScore) }} / {{ detail.appeal.afterGrade }}</el-descriptions-item>
            <el-descriptions-item label="处理意见" :span="2">{{ detail.appeal.handleOpinion || '-' }}</el-descriptions-item>
          </el-descriptions>
          <el-form v-if="detail.appeal.status === 1" :inline="true" class="perf__appeal-form" @submit.prevent>
            <el-form-item label="结论">
              <el-select v-model="appealHandle.status" style="width: 140px">
                <el-option label="维持原结果" :value="2" />
                <el-option label="调整" :value="3" />
              </el-select>
            </el-form-item>
            <el-form-item label="调整后分数">
              <el-input-number
                v-model="appealHandle.score"
                :min="0"
                :max="120"
                :precision="1"
                :disabled="appealHandle.status !== 3"
                style="width: 140px"
              />
            </el-form-item>
            <el-form-item label="意见">
              <el-input v-model="appealHandle.opinion" style="width: 240px" placeholder="可选" />
            </el-form-item>
            <el-form-item>
              <el-button v-permission="'hr:performance:calibrate'" type="primary" @click="handleAppeal">处理</el-button>
            </el-form-item>
          </el-form>
        </div>

        <div v-if="detail.confirmStatus === 1 && !detail.appeal && detail.stage >= 5 && detail.stage !== 6" class="perf__section">
          <el-divider content-position="left">申诉</el-divider>
          <el-form :inline="true" @submit.prevent>
            <el-form-item label="理由">
              <el-input v-model="appealForm.reason" style="width: 320px" placeholder="如对分数有异议请说明" />
            </el-form-item>
            <el-form-item>
              <el-button @click="submitAppealAsSelf">提交申诉（本人）</el-button>
            </el-form-item>
          </el-form>
        </div>
      </template>
    </el-drawer>

    <!-- ============ 周期表单 ============ -->
    <el-dialog v-model="cycleDialogVisible" title="绩效考核周期" width="620px">
      <el-form :model="cycleForm" label-width="110px">
        <el-form-item label="周期编码" required>
          <el-input v-model="cycleForm.cycleCode" placeholder="如 2026Q4" />
        </el-form-item>
        <el-form-item label="周期名称" required>
          <el-input v-model="cycleForm.cycleName" placeholder="如 2026 年第四季度" />
        </el-form-item>
        <el-form-item label="绩效方案" required>
          <el-select v-model="cycleForm.planId" style="width: 100%">
            <el-option v-for="p in planOptions" :key="p.id" :label="p.planName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="起止日期" required>
          <el-date-picker
            v-model="cycleForm.range"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始"
            end-placeholder="结束"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="目标设定截止">
          <el-date-picker v-model="cycleForm.targetDeadline" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="自评截止">
          <el-date-picker v-model="cycleForm.selfDeadline" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="评分截止">
          <el-date-picker v-model="cycleForm.scoreDeadline" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="校准截止">
          <el-date-picker v-model="cycleForm.calibrateDeadline" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="确认截止">
          <el-date-picker v-model="cycleForm.confirmDeadline" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cycleDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCycle">保存并生成考核表</el-button>
      </template>
    </el-dialog>

    <!-- ============ 方案表单 ============ -->
    <el-dialog v-model="planDialogVisible" title="绩效方案" width="880px">
      <el-form :model="planForm" label-width="110px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="方案编码" required>
              <el-input v-model="planForm.planCode" placeholder="PLAN_2026_TECH" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="方案名称" required>
              <el-input v-model="planForm.planName" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="周期类型">
              <el-select v-model="planForm.cycleType" style="width: 100%">
                <el-option label="月度" :value="1" />
                <el-option label="季度" :value="2" />
                <el-option label="半年" :value="3" />
                <el-option label="年度" :value="4" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="适用序列">
              <el-input v-model="planForm.series" placeholder="空=全部" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="状态">
              <el-select v-model="planForm.status" style="width: 100%">
                <el-option label="草稿" :value="1" />
                <el-option label="启用" :value="2" />
                <el-option label="停用" :value="3" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="适用部门">
              <el-input v-model="planForm.deptIds" placeholder="部门ID，逗号分隔；空=全部" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="强制分布">
              <el-switch v-model="planForm.forceDistribution" :active-value="1" :inactive-value="0" />
              <span class="perf__hint perf__hint--inline">仅预警，不拦截</span>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">指标与权重（合计必须 100%）</el-divider>
        <el-table :data="planForm.indicators" border size="small">
          <el-table-column label="指标" min-width="240">
            <template #default="{ row }">
              <el-select v-model="row.indicatorId" filterable placeholder="选择指标" style="width: 100%">
                <el-option
                  v-for="i in indicatorOptions"
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
              <el-button link type="danger" @click="planForm.indicators.splice($index, 1)">移除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="perf__weight" :class="{ 'is-bad': !weightOk }">
          权重合计：{{ weightTotal }}%
          <span v-if="!weightOk">（必须等于 100%）</span>
          <el-button link type="primary" @click="planForm.indicators.push({ indicatorId: undefined, weight: 0 })">
            + 添加指标
          </el-button>
        </div>

        <el-divider content-position="left">等级映射与系数</el-divider>
        <el-table :data="planForm.grades" border size="small">
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
              <el-input-number v-model="row.coefficient" :min="0" :max="3" :precision="2" :step="0.1" size="small" style="width: 110px" />
            </template>
          </el-table-column>
        </el-table>
      </el-form>
      <template #footer>
        <el-button @click="planDialogVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!weightOk" @click="submitPlan">保存</el-button>
      </template>
    </el-dialog>

    <!-- ============ 指标表单 ============ -->
    <el-dialog v-model="indicatorDialogVisible" title="绩效指标" width="620px">
      <el-form :model="indicatorForm" label-width="110px">
        <el-form-item label="指标编码" required>
          <el-input v-model="indicatorForm.indicatorCode" placeholder="KPI_DELIVERY" />
        </el-form-item>
        <el-form-item label="指标名称" required>
          <el-input v-model="indicatorForm.indicatorName" />
        </el-form-item>
        <el-form-item label="指标类型">
          <el-select v-model="indicatorForm.indicatorType" style="width: 100%">
            <el-option label="定量" :value="1" />
            <el-option label="定性" :value="2" />
            <el-option label="加分项" :value="3" />
            <el-option label="扣分项" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item label="计量单位">
          <el-input v-model="indicatorForm.unit" placeholder="%、个、天" />
        </el-form-item>
        <el-form-item label="指标定义">
          <el-input v-model="indicatorForm.definition" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="评分标准">
          <el-input v-model="indicatorForm.scoreStandard" type="textarea" :rows="3" placeholder="优秀/良好/合格/待改进/不合格 五档描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="indicatorDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitIndicator">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.perf__toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.perf__hint {
  font-size: 12px;
  color: #909399;
}

.perf__hint--inline {
  margin-left: 8px;
}

.perf__muted {
  color: #c0c4cc;
}

.perf__warn {
  color: #f56c6c;
  font-weight: 600;
}

.perf__pager {
  margin-top: 12px;
  justify-content: flex-end;
}

.perf__tip {
  margin-bottom: 12px;
}

.perf__items {
  margin-top: 12px;
}

.perf__section {
  margin-top: 16px;
}

.perf__comment {
  display: block;
  margin-top: 12px;
}

.perf__appeal-form {
  margin-top: 12px;
}

.perf__weight {
  margin-top: 8px;
  font-size: 13px;
  color: #67c23a;
}

.perf__weight.is-bad {
  color: #f56c6c;
}
</style>
