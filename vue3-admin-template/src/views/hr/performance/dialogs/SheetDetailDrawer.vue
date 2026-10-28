<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  appealPerfApi,
  approvePerfTargetApi,
  calibratePerfApi,
  confirmPerfApi,
  getPerfSheetApi,
  handlePerfAppealApi,
  submitPerfManagerApi,
} from '@/api/perf'
import { logAsyncError } from '@/utils/asyncGuard'
import { gradeTagType, money1 } from '../display'
import type { PerfSheetDetailVO, PerfSheetVO } from '@/api/types'

/**
 * 考核表明细抽屉（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>一张考核表上挂着五段动作，且每段都由「阶段 + 状态」严格决定是否出现
 * （目标审核 / 上级评分 / HR 校准 / 结果确认 / 申诉处理）。原实现把这些分散在
 * 页面的 200 行脚本里，和列表逻辑混在一起；独立出来后在同一个文件里能一眼看完
 * 「什么状态能做什么动作」。</p>
 *
 * <p>动作完成后 `emit('saved')`，由「考核表」页签重载列表——抽屉不持有列表状态。</p>
 */
const emit = defineEmits<{ saved: [] }>()

const visible = ref(false)
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

async function open(row: PerfSheetVO) {
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
    visible.value = true
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
    visible.value = false
    emit('saved')
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
    visible.value = false
    emit('saved')
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
    visible.value = false
    emit('saved')
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
    visible.value = false
    emit('saved')
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
    visible.value = false
    emit('saved')
  } catch (error) {
    logAsyncError('appealPerf', error)
  }
}

async function confirmResult() {
  if (!detail.value) return
  try {
    await confirmPerfApi(detail.value.id)
    ElMessage.success('已确认知悉')
    visible.value = false
    emit('saved')
  } catch (error) {
    logAsyncError('confirmPerf', error)
  }
}

/* ---------- 段落可见性：全部由「阶段 + 各环节状态」决定 ---------- */

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

const canAppealAsSelf = computed(() => {
  const d = detail.value
  return !!d && d.confirmStatus === 1 && !d.appeal && d.stage >= 5 && d.stage !== 6
})

defineExpose({ open })
</script>

<template>
  <el-drawer v-model="visible" title="绩效考核表" size="60%">
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
        <el-button v-permission="'hr:performance:target'" type="primary" data-action="target-pass" @click="approveTarget(true)">
          通过并锁定
        </el-button>
        <el-button v-permission="'hr:performance:target'" data-action="target-reject" @click="approveTarget(false)">
          驳回重填
        </el-button>
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
        <el-button type="primary" data-action="submit-score" @click="submitManagerScore">提交评分</el-button>
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
            <el-button v-permission="'hr:performance:calibrate'" type="primary" data-action="calibrate" @click="submitCalibrate">
              保存校准
            </el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 结果确认 -->
      <div v-if="canConfirm" class="perf__section">
        <el-divider content-position="left">结果确认</el-divider>
        <el-button type="primary" data-action="confirm" @click="confirmResult">确认知悉</el-button>
      </div>

      <!-- 申诉（已提交，HR 处理） -->
      <div v-if="detail.appeal" class="perf__section">
        <el-divider content-position="left">申诉</el-divider>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="申诉理由">{{ detail.appeal.reason }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ detail.appeal.statusLabel }}</el-descriptions-item>
          <el-descriptions-item label="处理前">
            {{ money1(detail.appeal.beforeScore) }} / {{ detail.appeal.beforeGrade }}
          </el-descriptions-item>
          <el-descriptions-item label="处理后">
            {{ money1(detail.appeal.afterScore) }} / {{ detail.appeal.afterGrade }}
          </el-descriptions-item>
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
            <el-button v-permission="'hr:performance:calibrate'" type="primary" data-action="appeal-handle" @click="handleAppeal">
              处理
            </el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 申诉（本人发起） -->
      <div v-if="canAppealAsSelf" class="perf__section">
        <el-divider content-position="left">申诉</el-divider>
        <el-form :inline="true" @submit.prevent>
          <el-form-item label="理由">
            <el-input v-model="appealForm.reason" style="width: 320px" placeholder="如对分数有异议请说明" />
          </el-form-item>
          <el-form-item>
            <el-button data-action="appeal-submit" @click="submitAppealAsSelf">提交申诉（本人）</el-button>
          </el-form-item>
        </el-form>
      </div>
    </template>
  </el-drawer>
</template>
