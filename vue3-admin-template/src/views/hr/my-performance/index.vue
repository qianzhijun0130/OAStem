<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  appealPerfApi,
  confirmPerfApi,
  getMyPerfHistoryApi,
  getMyPerfSheetApi,
  savePerfTargetApi,
  submitPerfSelfApi,
} from '@/api/perf'
import { logAsyncError } from '@/utils/asyncGuard'
import type { PerfSheetDetailVO, PerfSheetVO } from '@/api/types'

const loading = ref(false)
const history = ref<PerfSheetVO[]>([])
const activeCycleId = ref<number>()
const sheet = ref<PerfSheetDetailVO | null>(null)

/** 目标填写态：只让改目标值与衡量标准 */
const targetItems = reactive<{ itemId: number; targetValue: string; measureStandard: string }[]>([])
/** 自评态 */
const selfItems = reactive<{ itemId: number; score?: number; comment: string }[]>([])
const appealReason = ref('')

const number1 = (value?: number) => (value == null ? '-' : Number(value).toFixed(1))
const typeLabel = (type: number) => ['', '定量', '定性', '加分', '扣分'][type] || '-'

async function loadHistory() {
  loading.value = true
  try {
    const page = await getMyPerfHistoryApi({ pageNo: 1, pageSize: 50 })
    history.value = page.records || []
    if (!activeCycleId.value && history.value.length) {
      activeCycleId.value = history.value[0].cycleId
    }
    if (activeCycleId.value) {
      await loadSheet(activeCycleId.value)
    }
  } catch (error) {
    logAsyncError('loadMyPerfHistory', error)
  } finally {
    loading.value = false
  }
}

async function loadSheet(cycleId: number) {
  try {
    const data = await getMyPerfSheetApi(cycleId)
    sheet.value = data
    targetItems.splice(0, targetItems.length,
      ...(data.items || []).map((i) => ({
        itemId: i.id,
        targetValue: i.targetValue || '',
        measureStandard: i.measureStandard || '',
      })))
    selfItems.splice(0, selfItems.length,
      ...(data.items || []).map((i) => ({
        itemId: i.id,
        score: i.selfScore ?? undefined,
        comment: i.selfComment || '',
      })))
    appealReason.value = ''
  } catch (error) {
    sheet.value = null
    logAsyncError('loadMyPerfSheet', error)
  }
}

async function submitTarget() {
  if (!sheet.value) return
  if (!targetItems.some((i) => i.targetValue.trim())) {
    ElMessage.warning('请至少填写一项指标的目标值')
    return
  }
  try {
    await savePerfTargetApi({ sheetId: sheet.value.id, items: targetItems })
    ElMessage.success('目标已提交，等待上级审核')
    await loadSheet(sheet.value.cycleId)
  } catch (error) {
    logAsyncError('savePerfTarget', error)
  }
}

async function submitSelf() {
  if (!sheet.value) return
  if (selfItems.some((i) => i.score == null)) {
    ElMessage.warning('每一项都要打分后才能提交自评')
    return
  }
  try {
    await submitPerfSelfApi({
      sheetId: sheet.value.id,
      items: selfItems.map((i) => ({ itemId: i.itemId, score: i.score, comment: i.comment })),
    })
    ElMessage.success('自评已提交')
    await loadSheet(sheet.value.cycleId)
    await loadHistory()
  } catch (error) {
    logAsyncError('submitPerfSelf', error)
  }
}

async function confirmResult() {
  if (!sheet.value) return
  try {
    await confirmPerfApi(sheet.value.id)
    ElMessage.success('已确认知悉')
    await loadSheet(sheet.value.cycleId)
    await loadHistory()
  } catch (error) {
    logAsyncError('confirmPerf', error)
  }
}

async function submitAppeal() {
  if (!sheet.value) return
  if (!appealReason.value.trim()) {
    ElMessage.warning('请填写申诉理由')
    return
  }
  try {
    await appealPerfApi({ sheetId: sheet.value.id, reason: appealReason.value.trim() })
    ElMessage.success('申诉已提交，HR 会在复核后反馈')
    await loadSheet(sheet.value.cycleId)
    await loadHistory()
  } catch (error) {
    logAsyncError('appealPerf', error)
  }
}

const canEditTarget = computed(() =>
  !!sheet.value && sheet.value.stage === 1 && (sheet.value.targetStatus === 0 || sheet.value.targetStatus === 3),
)
const canSubmitSelf = computed(() =>
  !!sheet.value && sheet.value.stage === 2 && sheet.value.selfStatus === 0 && sheet.value.targetStatus === 2,
)
const canConfirm = computed(() =>
  !!sheet.value && sheet.value.stage >= 5 && sheet.value.stage !== 6 && sheet.value.confirmStatus === 0,
)
const canAppeal = computed(() =>
  !!sheet.value && sheet.value.confirmStatus === 1 && !sheet.value.appeal,
)
const gradeTagType = (grade?: string) => {
  if (grade === 'A') return 'success'
  if (grade === 'B') return 'primary'
  if (grade === 'C') return 'warning'
  if (grade === 'D') return 'danger'
  return 'info'
}

onMounted(loadHistory)
</script>

<template>
  <div class="myperf" v-loading="loading">
    <el-empty v-if="!loading && !history.length" description="还没有绩效考核记录（考核表由 HR 在周期创建时生成）" />

    <template v-else>
      <el-card shadow="never" class="myperf__pick">
        <div class="myperf__pick-bar">
          <span class="myperf__label">考核周期</span>
          <el-select v-model="activeCycleId" style="width: 220px" @change="(v: number) => loadSheet(v)">
            <el-option v-for="h in history" :key="h.id" :label="h.cycleName" :value="h.cycleId" />
          </el-select>
          <span v-if="sheet" class="myperf__stage">{{ sheet.stageLabel }}</span>
        </div>
      </el-card>

      <el-card v-if="sheet" shadow="never" class="myperf__main">
        <div class="myperf__head">
          <div>
            <div class="myperf__title">{{ sheet.cycleName }} 绩效考核</div>
            <div class="myperf__sub">
              {{ sheet.realName }} · {{ sheet.workNumber }} · {{ sheet.deptName || '-' }} ·
              直属上级：{{ sheet.leaderName || '-' }}
            </div>
          </div>
          <div class="myperf__score">
            <div class="myperf__score-label">最终得分</div>
            <div class="myperf__score-value">{{ number1(sheet.totalScore) }}</div>
            <el-tag v-if="sheet.gradeCode" :type="gradeTagType(sheet.gradeCode)" effect="plain">
              {{ sheet.gradeCode }} {{ sheet.gradeName || '' }}
            </el-tag>
          </div>
        </div>

        <el-alert
          v-if="sheet.selfMissing === 1"
          class="myperf__tip"
          type="warning"
          :closable="false"
          show-icon
          title="你未在截止时间内提交自评，本期按「未自评」记录，已直接进入上级评分。"
        />

        <el-table :data="sheet.items" border size="small" class="myperf__items">
          <el-table-column prop="indicatorName" label="指标" min-width="150" />
          <el-table-column label="类型" width="80">
            <template #default="{ row }">{{ typeLabel(row.indicatorType) }}</template>
          </el-table-column>
          <el-table-column label="权重" width="80" align="right">
            <template #default="{ row }">{{ row.indicatorType >= 3 ? '-' : `${row.weight}%` }}</template>
          </el-table-column>
          <el-table-column prop="targetValue" label="目标值" min-width="140" show-overflow-tooltip />
          <el-table-column label="自评" width="90" align="right">
            <template #default="{ row }">{{ number1(row.selfScore) }}</template>
          </el-table-column>
          <el-table-column label="上级评分" width="100" align="right">
            <template #default="{ row }">{{ number1(row.managerScore) }}</template>
          </el-table-column>
          <el-table-column label="最终分" width="90" align="right">
            <template #default="{ row }">{{ number1(row.finalScore) }}</template>
          </el-table-column>
        </el-table>

        <!-- 目标填写 -->
        <div v-if="canEditTarget" class="myperf__block">
          <el-divider content-position="left">填写目标（提交后由上级审核，通过即锁定）</el-divider>
          <el-table :data="targetItems" border size="small">
            <el-table-column label="指标" min-width="150">
              <template #default="{ $index }">{{ sheet.items[$index]?.indicatorName }}</template>
            </el-table-column>
            <el-table-column label="目标值" min-width="180">
              <template #default="{ row }">
                <el-input v-model="row.targetValue" size="small" placeholder="如 95% / 12 个" />
              </template>
            </el-table-column>
            <el-table-column label="衡量标准" min-width="220">
              <template #default="{ row }">
                <el-input v-model="row.measureStandard" size="small" placeholder="如何判定达成" />
              </template>
            </el-table-column>
          </el-table>
          <el-button class="myperf__action" type="primary" @click="submitTarget">提交目标</el-button>
        </div>

        <!-- 自评 -->
        <div v-if="canSubmitSelf" class="myperf__block">
          <el-divider content-position="left">自评打分（0~100；加分项 ≤10 分、扣分项 ≤20 分）</el-divider>
          <el-table :data="selfItems" border size="small">
            <el-table-column label="指标" min-width="150">
              <template #default="{ $index }">{{ sheet.items[$index]?.indicatorName }}</template>
            </el-table-column>
            <el-table-column label="目标值" min-width="130" show-overflow-tooltip>
              <template #default="{ $index }">{{ sheet.items[$index]?.targetValue || '-' }}</template>
            </el-table-column>
            <el-table-column label="自评分" width="150">
              <template #default="{ row }">
                <el-input-number v-model="row.score" :min="0" :max="100" :precision="1" size="small" style="width: 120px" />
              </template>
            </el-table-column>
            <el-table-column label="说明" min-width="200">
              <template #default="{ row }">
                <el-input v-model="row.comment" size="small" placeholder="可选：说明达成情况" />
              </template>
            </el-table-column>
          </el-table>
          <el-button class="myperf__action" type="primary" @click="submitSelf">提交自评</el-button>
        </div>

        <!-- 确认 / 申诉 -->
        <div v-if="canConfirm || canAppeal" class="myperf__block">
          <el-divider content-position="left">结果确认</el-divider>
          <el-button v-if="canConfirm" type="primary" @click="confirmResult">确认知悉</el-button>
          <template v-if="canAppeal">
            <el-form :inline="true" class="myperf__appeal" @submit.prevent>
              <el-form-item label="申诉理由">
                <el-input v-model="appealReason" style="width: 320px" placeholder="对结果有异议请说明（仅可申诉一次，确认后 5 个工作日内）" />
              </el-form-item>
              <el-form-item>
                <el-button @click="submitAppeal">提交申诉</el-button>
              </el-form-item>
            </el-form>
          </template>
        </div>

        <div v-if="sheet.appeal" class="myperf__block">
          <el-divider content-position="left">我的申诉</el-divider>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="理由">{{ sheet.appeal.reason }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ sheet.appeal.statusLabel }}</el-descriptions-item>
            <el-descriptions-item label="处理意见" :span="2">{{ sheet.appeal.handleOpinion || '待处理' }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <el-alert
          v-if="sheet.managerComment"
          class="myperf__tip"
          type="success"
          :closable="false"
          :title="`上级评语：${sheet.managerComment}`"
        />
      </el-card>

      <el-card shadow="never">
        <div class="myperf__history-title">历史记录</div>
        <el-table :data="history" border size="small" class="myperf__history">
          <el-table-column prop="cycleName" label="周期" min-width="160" />
          <el-table-column label="总分" width="100" align="right">
            <template #default="{ row }">{{ number1(row.totalScore) }}</template>
          </el-table-column>
          <el-table-column label="等级" width="90">
            <template #default="{ row }">
              <el-tag v-if="row.gradeCode" size="small" :type="gradeTagType(row.gradeCode)">{{ row.gradeCode }}</el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="系数" width="90" align="right">
            <template #default="{ row }">{{ row.coefficient == null ? '-' : Number(row.coefficient).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column label="结果" width="110">
            <template #default="{ row }">
              <el-tag size="small" effect="plain" :type="row.confirmStatus === 1 ? 'success' : 'info'">
                {{ row.confirmStatus === 1 ? '已确认' : '未确认' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.myperf {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.myperf__pick-bar {
  display: flex;
  align-items: center;
  gap: 12px;
}

.myperf__label {
  font-size: 13px;
  color: #5a6577;
}

.myperf__stage {
  font-size: 12px;
  color: #409eff;
}

.myperf__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding-bottom: 12px;
  border-bottom: 1px solid #eef1f6;
}

.myperf__title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2a37;
}

.myperf__sub {
  margin-top: 4px;
  font-size: 12px;
  color: #8a94a5;
}

.myperf__score {
  text-align: right;
}

.myperf__score-label {
  font-size: 12px;
  color: #8a94a5;
}

.myperf__score-value {
  font-size: 24px;
  font-weight: 700;
  color: #409eff;
}

.myperf__tip {
  margin-top: 12px;
}

.myperf__items {
  margin-top: 12px;
}

.myperf__block {
  margin-top: 16px;
}

.myperf__action {
  margin-top: 12px;
}

.myperf__appeal {
  margin-top: 12px;
}

.myperf__history-title {
  margin-bottom: 12px;
  font-size: 14px;
  font-weight: 600;
  color: #1f2a37;
}
</style>
