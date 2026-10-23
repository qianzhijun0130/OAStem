<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  adjustLeaveQuotaApi,
  cancelHrRequestApi,
  cancelLeaveApi,
  getHrRequestPageApi,
  getLeavePageApi,
  getMyLeaveQuotaApi,
  submitHrRequestApi,
  submitLeaveApi,
} from '@/api/hr'
import { logAsyncError } from '@/utils/asyncGuard'
import type {
  HrRequestSaveRequest,
  HrRequestVO,
  LeaveQuotaVO,
  LeaveSubmitRequest,
  LeaveVO,
} from '@/api/types'

const LEAVE_TYPES = [
  { value: 1, label: '事假' },
  { value: 2, label: '病假' },
  { value: 3, label: '年假' },
  { value: 4, label: '婚假' },
  { value: 5, label: '产假' },
  { value: 6, label: '陪产假' },
  { value: 7, label: '丧假' },
  { value: 8, label: '调休' },
]

const LEAVE_STATUS = [
  { value: 0, label: '草稿', type: 'info' as const },
  { value: 1, label: '审批中', type: 'warning' as const },
  { value: 2, label: '已通过', type: 'success' as const },
  { value: 3, label: '已驳回', type: 'danger' as const },
  { value: 4, label: '已撤销', type: 'info' as const },
]

const BIZ_TABS = [
  { type: 1, name: 'overtime', label: '加班' },
  { type: 2, name: 'trip', label: '出差' },
  { type: 3, name: 'card', label: '补卡' },
]

const activeTab = ref('quota')
// 年份选择器用字符串（value-format="YYYY" 期待 string），调接口时再转数字，
// 否则选择器会显示空白、用户以为没选年份
const year = ref(String(new Date().getFullYear()))

function statusMeta(status?: number) {
  return LEAVE_STATUS.find((s) => s.value === status) || { label: '-', type: 'info' as const }
}

function leaveTypeLabel(type?: number) {
  return LEAVE_TYPES.find((t) => t.value === type)?.label || '-'
}

/* ---------------- 我的额度 ---------------- */

const quotas = ref<LeaveQuotaVO[]>([])
const quotaLoading = ref(false)

async function loadQuota() {
  quotaLoading.value = true
  try {
    quotas.value = (await getMyLeaveQuotaApi(Number(year.value))) || []
  } catch (error) {
    logAsyncError('loadMyQuota', error)
  } finally {
    quotaLoading.value = false
  }
}

const adjustVisible = ref(false)
const adjusting = ref(false)
const adjustForm = reactive({ userId: undefined as number | undefined, leaveType: 3, totalDays: 0, remark: '' })

function openAdjust() {
  Object.assign(adjustForm, { userId: undefined, leaveType: 3, totalDays: 0, remark: '' })
  adjustVisible.value = true
}

async function submitAdjust() {
  if (!adjustForm.userId) {
    ElMessage.warning('请填写员工用户ID')
    return
  }
  adjusting.value = true
  try {
    await adjustLeaveQuotaApi({
      userId: adjustForm.userId,
      leaveType: adjustForm.leaveType,
      year: Number(year.value),
      totalDays: adjustForm.totalDays,
      remark: adjustForm.remark,
    })
    ElMessage.success('额度已调整')
    adjustVisible.value = false
    await loadQuota()
  } catch (error) {
    logAsyncError('adjustQuota', error)
  } finally {
    adjusting.value = false
  }
}

/* ---------------- 请假单 ---------------- */

const leaveQuery = reactive({ month: '', leaveStatus: undefined as number | undefined, keyword: '' })
const leaves = ref<LeaveVO[]>([])
const leaveTotal = ref(0)
const leaveLoading = ref(false)
const leavePage = reactive({ pageNo: 1, pageSize: 10 })

async function loadLeaves() {
  leaveLoading.value = true
  try {
    const res = await getLeavePageApi({ ...leaveQuery, ...leavePage })
    leaves.value = res?.records || []
    leaveTotal.value = res?.total || 0
  } catch (error) {
    logAsyncError('loadLeaves', error)
  } finally {
    leaveLoading.value = false
  }
}

const leaveDialogVisible = ref(false)
const leaveSubmitting = ref(false)
const leaveFormRef = ref<FormInstance>()
const leaveForm = reactive<LeaveSubmitRequest>({
  leaveType: 3,
  startTime: '',
  endTime: '',
  reason: '',
})

const leaveRules: FormRules = {
  leaveType: [{ required: true, message: '请选择假别', trigger: 'change' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }],
}

function openLeaveCreate() {
  Object.assign(leaveForm, { leaveType: 3, startTime: '', endTime: '', leaveDays: undefined, reason: '' })
  leaveDialogVisible.value = true
}

async function submitLeave() {
  const valid = await leaveFormRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  leaveSubmitting.value = true
  try {
    await submitLeaveApi({ ...leaveForm })
    ElMessage.success('请假已提交，等待审批')
    leaveDialogVisible.value = false
    await Promise.all([loadLeaves(), loadQuota()])
  } catch (error) {
    logAsyncError('submitLeave', error)
  } finally {
    leaveSubmitting.value = false
  }
}

async function cancelLeave(row: LeaveVO) {
  try {
    await ElMessageBox.confirm(`确定撤销单据「${row.billCode || row.id}」吗？额度会立即释放。`, '撤销请假', {
      type: 'warning',
      confirmButtonText: '确定撤销',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await cancelLeaveApi(row.id)
    ElMessage.success('已撤销')
    await Promise.all([loadLeaves(), loadQuota()])
  } catch (error) {
    logAsyncError('cancelLeave', error)
  }
}

/* ---------------- 加班 / 出差 / 补卡 ---------------- */

interface BizState {
  list: HrRequestVO[]
  total: number
  loading: boolean
  query: { month: string; status?: number; keyword: string; pageNo: number; pageSize: number }
}

const bizState = reactive<Record<number, BizState>>({})
BIZ_TABS.forEach((tab) => {
  bizState[tab.type] = {
    list: [],
    total: 0,
    loading: false,
    query: { month: '', status: undefined, keyword: '', pageNo: 1, pageSize: 10 },
  }
})

async function loadBiz(type: number) {
  const state = bizState[type]
  state.loading = true
  try {
    const res = await getHrRequestPageApi({ bizType: type, ...state.query })
    state.list = res?.records || []
    state.total = res?.total || 0
  } catch (error) {
    logAsyncError('loadHrRequest', error)
  } finally {
    state.loading = false
  }
}

const bizDialogVisible = ref(false)
const bizSubmitting = ref(false)
const bizType = ref(1)
const bizForm = reactive<HrRequestSaveRequest>({ bizType: 1, overtimeType: 1, compensateType: 1, direction: 1 })

function openBizCreate(type: number) {
  bizType.value = type
  Object.assign(bizForm, {
    bizType: type,
    overtimeType: 1,
    compensateType: 1,
    direction: 1,
    overtimeDate: '',
    startTime: '',
    endTime: '',
    hours: undefined,
    destination: '',
    startDate: '',
    endDate: '',
    budget: undefined,
    cardDate: '',
    cardTime: '',
    reason: '',
  })
  bizDialogVisible.value = true
}

/** 把日期 + 时间拼成后端需要的 ISO 字符串（LocalDateTime 无时区） */
function joinDateTime(date?: string, time?: string): string | undefined {
  if (!date) {
    return undefined
  }
  return `${date}T${time || '09:00:00'}`
}

async function submitBiz() {
  bizSubmitting.value = true
  try {
    const payload: HrRequestSaveRequest = { ...bizForm, bizType: bizType.value }
    if (bizType.value === 1) {
      payload.startTime = joinDateTime(payload.overtimeDate, payload.startTime?.slice(11) || '19:00:00')
      payload.endTime = joinDateTime(payload.overtimeDate, payload.endTime?.slice(11) || '21:00:00')
    }
    if (bizType.value === 3) {
      payload.cardTime = joinDateTime(payload.cardDate, payload.cardTime?.slice(11) || '09:00:00')
    }
    await submitHrRequestApi(payload)
    ElMessage.success('已提交，等待审批')
    bizDialogVisible.value = false
    await loadBiz(bizType.value)
  } catch (error) {
    logAsyncError('submitHrRequest', error)
  } finally {
    bizSubmitting.value = false
  }
}

async function cancelBiz(row: HrRequestVO) {
  try {
    await ElMessageBox.confirm(`确定撤销「${row.billCode || row.id}」吗？`, '撤销申请', {
      type: 'warning',
      confirmButtonText: '确定撤销',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await cancelHrRequestApi(row.bizType, row.id)
    ElMessage.success('已撤销')
    await loadBiz(row.bizType)
  } catch (error) {
    logAsyncError('cancelHrRequest', error)
  }
}

/* ---------------- 初始化 ---------------- */

const currentBizLabel = computed(() => BIZ_TABS.find((t) => t.type === bizType.value)?.label || '')

function onTabChange(name: string) {
  if (name === 'quota') {
    void loadQuota()
  } else if (name === 'leave') {
    void loadLeaves()
  } else {
    const tab = BIZ_TABS.find((t) => t.name === name)
    if (tab) {
      void loadBiz(tab.type)
    }
  }
}

onMounted(() => {
  void loadQuota()
})
</script>

<template>
  <div class="hr-leave">
    <el-card shadow="never">
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <!-- ============ 我的额度 ============ -->
        <el-tab-pane label="我的额度" name="quota">
          <div class="hr-leave__bar">
            <el-date-picker v-model="year" type="year" value-format="YYYY" :clearable="false" style="width: 120px" @change="loadQuota" />
            <el-button v-permission="'hr:leave:quota'" @click="openAdjust">调整额度</el-button>
            <span class="hr-leave__hint">年假按累计工龄计算（1~10 年 5 天 / 10~20 年 10 天 / 20 年以上 15 天），调休由加班累积</span>
          </div>
          <el-table v-loading="quotaLoading" :data="quotas" border>
            <el-table-column prop="leaveTypeLabel" label="假别" width="100" />
            <el-table-column prop="totalDays" label="总额度" width="100" />
            <el-table-column prop="carryOverDays" label="结转" width="90" />
            <el-table-column prop="usedDays" label="已使用" width="100" />
            <el-table-column prop="lockedDays" label="审批中占用" width="120" />
            <el-table-column label="可用" width="100">
              <template #default="{ row }">
                <b :class="{ 'hr-leave__danger': row.availableDays <= 0 }">{{ row.availableDays }}</b>
              </template>
            </el-table-column>
            <el-table-column prop="expireDate" label="结转失效日" width="130" />
            <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
          </el-table>
        </el-tab-pane>

        <!-- ============ 请假 ============ -->
        <el-tab-pane label="请假单" name="leave">
          <div class="hr-leave__bar">
            <el-date-picker v-model="leaveQuery.month" type="month" value-format="YYYY-MM" placeholder="全部月份" style="width: 140px" />
            <el-select v-model="leaveQuery.leaveStatus" clearable placeholder="全部状态" style="width: 130px">
              <el-option v-for="s in LEAVE_STATUS" :key="s.value" :label="s.label" :value="s.value" />
            </el-select>
            <el-input v-model="leaveQuery.keyword" placeholder="姓名 / 工号" clearable style="width: 160px" />
            <el-button type="primary" @click="leavePage.pageNo = 1; loadLeaves()">查询</el-button>
            <el-button v-permission="'hr:leave:create'" type="primary" plain @click="openLeaveCreate">发起请假</el-button>
          </div>
          <el-table v-loading="leaveLoading" :data="leaves" border>
            <el-table-column prop="billCode" label="单据编号" width="190" />
            <el-table-column prop="realName" label="申请人" width="100" />
            <el-table-column prop="deptName" label="部门" width="120" />
            <el-table-column label="假别" width="90">
              <template #default="{ row }">{{ row.leaveTypeLabel || leaveTypeLabel(row.leaveType) }}</template>
            </el-table-column>
            <el-table-column label="起止时间" min-width="230">
              <template #default="{ row }">
                {{ (row.startTime || '').replace('T', ' ').slice(0, 16) }} ~
                {{ (row.endTime || '').replace('T', ' ').slice(0, 16) }}
              </template>
            </el-table-column>
            <el-table-column prop="leaveDays" label="天数" width="80" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="statusMeta(row.leaveStatus).type" effect="plain">
                  {{ row.leaveStatusLabel || statusMeta(row.leaveStatus).label }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="reason" label="事由" min-width="160" show-overflow-tooltip />
            <el-table-column label="操作" width="110" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.canCancel" icon-only link type="danger" title="撤销" @click="cancelLeave(row)">
                  撤销
                </el-button>
                <span v-else class="hr-leave__muted">-</span>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            class="hr-leave__pagination"
            :current-page="leavePage.pageNo"
            :page-size="leavePage.pageSize"
            :total="leaveTotal"
            layout="total, prev, pager, next"
            background
            @current-change="(p: number) => { leavePage.pageNo = p; loadLeaves() }"
          />
        </el-tab-pane>

        <!-- ============ 加班 / 出差 / 补卡 ============ -->
        <el-tab-pane v-for="tab in BIZ_TABS" :key="tab.name" :label="tab.label" :name="tab.name">
          <div class="hr-leave__bar">
            <el-date-picker
              v-model="bizState[tab.type].query.month"
              type="month"
              value-format="YYYY-MM"
              placeholder="全部月份"
              style="width: 140px"
            />
            <el-select v-model="bizState[tab.type].query.status" clearable placeholder="全部状态" style="width: 130px">
              <el-option v-for="s in LEAVE_STATUS" :key="s.value" :label="s.label" :value="s.value" />
            </el-select>
            <el-input v-model="bizState[tab.type].query.keyword" placeholder="姓名 / 工号" clearable style="width: 160px" />
            <el-button type="primary" @click="bizState[tab.type].query.pageNo = 1; loadBiz(tab.type)">查询</el-button>
            <el-button type="primary" plain @click="openBizCreate(tab.type)">发起{{ tab.label }}</el-button>
          </div>

          <el-table v-loading="bizState[tab.type].loading" :data="bizState[tab.type].list" border>
            <el-table-column prop="billCode" label="单据编号" width="190" />
            <el-table-column prop="realName" label="申请人" width="100" />
            <el-table-column prop="deptName" label="部门" width="120" />
            <template v-if="tab.type === 1">
              <el-table-column prop="bizDate" label="加班日期" width="115" />
              <el-table-column prop="hours" label="时长(h)" width="90" />
              <el-table-column label="补偿" width="100">
                <template #default="{ row }">{{ row.compensateType === 1 ? '转调休' : '加班费' }}</template>
              </el-table-column>
            </template>
            <template v-else-if="tab.type === 2">
              <el-table-column prop="destination" label="出差地点" min-width="130" show-overflow-tooltip />
              <el-table-column label="起止" min-width="190">
                <template #default="{ row }">{{ row.startDate }} ~ {{ row.endDate }}</template>
              </el-table-column>
              <el-table-column prop="days" label="天数" width="80" />
              <el-table-column prop="budget" label="预算" width="100" />
            </template>
            <template v-else>
              <el-table-column prop="bizDate" label="补卡日期" width="115" />
              <el-table-column label="方向" width="90">
                <template #default="{ row }">{{ row.direction === 1 ? '上班' : '下班' }}</template>
              </el-table-column>
              <el-table-column label="补卡时间" width="120">
                <template #default="{ row }">{{ (row.startTime || '').replace('T', ' ').slice(11, 19) }}</template>
              </el-table-column>
            </template>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="statusMeta(row.status).type" effect="plain">
                  {{ row.statusLabel || statusMeta(row.status).label }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="reason" label="事由" min-width="150" show-overflow-tooltip />
            <el-table-column label="操作" width="110" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.canCancel" icon-only link type="danger" title="撤销" @click="cancelBiz(row)">
                  撤销
                </el-button>
                <span v-else class="hr-leave__muted">-</span>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            class="hr-leave__pagination"
            :current-page="bizState[tab.type].query.pageNo"
            :page-size="bizState[tab.type].query.pageSize"
            :total="bizState[tab.type].total"
            layout="total, prev, pager, next"
            background
            @current-change="(p: number) => { bizState[tab.type].query.pageNo = p; loadBiz(tab.type) }"
          />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 调整额度 -->
    <el-dialog v-model="adjustVisible" title="调整假期额度" width="480px" destroy-on-close>
      <el-alert
        class="hr-leave__tip"
        type="info"
        :closable="false"
        show-icon
        title="这里填的是「调后总额度」，不是增量；每次调整都会记入台账备注。"
      />
      <el-form :model="adjustForm" label-width="100px">
        <el-form-item label="员工ID">
          <el-input-number v-model="adjustForm.userId" :min="1" :controls="false" placeholder="用户ID" />
        </el-form-item>
        <el-form-item label="假别">
          <el-select v-model="adjustForm.leaveType" style="width: 180px">
            <el-option v-for="t in LEAVE_TYPES" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="总额度">
          <el-input-number v-model="adjustForm.totalDays" :min="0" :max="365" :precision="1" />
          <span class="hr-leave__hint">天</span>
        </el-form-item>
        <el-form-item label="调整说明">
          <el-input v-model="adjustForm.remark" maxlength="255" placeholder="必填，写入台账备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjustVisible = false">取消</el-button>
        <el-button type="primary" :loading="adjusting" @click="submitAdjust">确定</el-button>
      </template>
    </el-dialog>

    <!-- 发起请假 -->
    <el-dialog v-model="leaveDialogVisible" title="发起请假" width="520px" destroy-on-close>
      <el-form ref="leaveFormRef" :model="leaveForm" :rules="leaveRules" label-width="92px">
        <el-form-item label="假别" prop="leaveType">
          <el-select v-model="leaveForm.leaveType" style="width: 180px">
            <el-option v-for="t in LEAVE_TYPES" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始时间" prop="startTime">
          <el-date-picker v-model="leaveForm.startTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 220px" />
        </el-form-item>
        <el-form-item label="结束时间" prop="endTime">
          <el-date-picker v-model="leaveForm.endTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 220px" />
        </el-form-item>
        <el-form-item label="请假天数">
          <el-input-number v-model="leaveForm.leaveDays" :min="0.5" :max="365" :step="0.5" :precision="1" />
          <span class="hr-leave__hint">留空按起止时间自动计算</span>
        </el-form-item>
        <el-form-item label="事由">
          <el-input v-model="leaveForm.reason" type="textarea" :rows="3" maxlength="1000" show-word-limit />
        </el-form-item>
        <el-form-item v-if="leaveForm.leaveType === 2" label="证明材料">
          <el-input v-model="leaveForm.attachmentIds" placeholder="病假 ≥3 天必须上传，填 file_id" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="leaveDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="leaveSubmitting" @click="submitLeave">提交</el-button>
      </template>
    </el-dialog>

    <!-- 发起加班 / 出差 / 补卡 -->
    <el-dialog v-model="bizDialogVisible" :title="`发起${currentBizLabel}`" width="520px" destroy-on-close>
      <el-form :model="bizForm" label-width="100px">
        <template v-if="bizType === 1">
          <el-form-item label="加班日期">
            <el-date-picker v-model="bizForm.overtimeDate" type="date" value-format="YYYY-MM-DD" />
          </el-form-item>
          <el-form-item label="加班类型">
            <el-select v-model="bizForm.overtimeType" style="width: 180px">
              <el-option label="工作日" :value="1" />
              <el-option label="休息日" :value="2" />
              <el-option label="法定节假日" :value="3" />
            </el-select>
          </el-form-item>
          <el-form-item label="时长(小时)">
            <el-input-number v-model="bizForm.hours" :min="0.5" :max="24" :step="0.5" :precision="1" />
          </el-form-item>
          <el-form-item label="补偿方式">
            <el-radio-group v-model="bizForm.compensateType">
              <el-radio :value="1">转调休</el-radio>
              <el-radio :value="2">加班费</el-radio>
            </el-radio-group>
          </el-form-item>
        </template>

        <template v-else-if="bizType === 2">
          <el-form-item label="出差地点">
            <el-input v-model="bizForm.destination" maxlength="200" />
          </el-form-item>
          <el-form-item label="开始日期">
            <el-date-picker v-model="bizForm.startDate" type="date" value-format="YYYY-MM-DD" />
          </el-form-item>
          <el-form-item label="结束日期">
            <el-date-picker v-model="bizForm.endDate" type="date" value-format="YYYY-MM-DD" />
          </el-form-item>
          <el-form-item label="预算">
            <el-input-number v-model="bizForm.budget" :min="0" :precision="2" />
          </el-form-item>
        </template>

        <template v-else>
          <el-form-item label="补卡日期">
            <el-date-picker v-model="bizForm.cardDate" type="date" value-format="YYYY-MM-DD" />
            <span class="hr-leave__hint">只能补近 30 天，每月最多 3 次</span>
          </el-form-item>
          <el-form-item label="方向">
            <el-radio-group v-model="bizForm.direction">
              <el-radio :value="1">上班</el-radio>
              <el-radio :value="2">下班</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="补卡时间">
            <el-date-picker v-model="bizForm.cardTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" />
          </el-form-item>
        </template>

        <el-form-item label="事由">
          <el-input v-model="bizForm.reason" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bizDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="bizSubmitting" @click="submitBiz">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.hr-leave__bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.hr-leave__hint {
  color: #8a94a5;
  font-size: 12px;
}

.hr-leave__muted {
  color: #b8c0cc;
}

.hr-leave__danger {
  color: var(--el-color-danger);
}

.hr-leave__pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

.hr-leave__tip {
  margin-bottom: 12px;
}
</style>
