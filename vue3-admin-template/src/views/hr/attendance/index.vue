<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  batchScheduleApi,
  deleteShiftApi,
  exportAttendanceMonthApi,
  getAttendanceMonthApi,
  getAttendancePageApi,
  listScheduleApi,
  listShiftsApi,
  lockAttendanceMonthApi,
  rebuildAttendanceApi,
  saveShiftApi,
  unlockAttendanceMonthApi,
  updateShiftStatusApi,
} from '@/api/hr'
import { getDeptTreeApi } from '@/api/dept'
import { useListQuery } from '@/composables/useListQuery'
import { logAsyncError } from '@/utils/asyncGuard'
import type {
  AttendanceMonthVO,
  AttendancePageQuery,
  AttendanceVO,
  DeptVO,
  ScheduleVO,
  ShiftSaveRequest,
  ShiftVO,
} from '@/api/types'

const activeTab = ref('detail')
const deptTree = ref<DeptVO[]>([])
const shifts = ref<ShiftVO[]>([])

const STATUS_OPTIONS = [
  { value: 1, label: '正常', type: 'success' as const },
  { value: 2, label: '迟到', type: 'warning' as const },
  { value: 3, label: '早退', type: 'warning' as const },
  { value: 4, label: '缺勤', type: 'danger' as const },
  { value: 5, label: '假勤', type: 'info' as const },
]

function statusMeta(status?: number) {
  return STATUS_OPTIONS.find((s) => s.value === status) || { label: '-', type: 'info' as const }
}

function currentMonth(): string {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}

/* ---------------- 明细 ---------------- */

const detailQuery = useListQuery<AttendanceVO, AttendancePageQuery>({
  initialQuery: () => ({ month: currentMonth(), deptId: undefined, attendanceStatus: undefined, keyword: '' }),
  fetcher: (params) => getAttendancePageApi(params),
})

/* ---------------- 月度汇总 ---------------- */

const monthQuery = useListQuery<AttendanceMonthVO, { month: string; deptId?: number }>({
  initialQuery: () => ({ month: currentMonth(), deptId: undefined }),
  fetcher: (params) => getAttendanceMonthApi(params),
  immediate: false,
})

/** 导出月度汇总：财务签字要用，不能只截个图 */
const monthExporting = ref(false)

async function exportMonth() {
  const month = monthQuery.query.month || currentMonth()
  monthExporting.value = true
  try {
    // 下载动作与文件名解析收敛在 exportAttendanceMonthApi → downloadFile，
    // 页面不再接触 Blob（此前 `blob as Blob` 强转拿到的是 AxiosResponse，点了没反应）
    await exportAttendanceMonthApi({ month, deptId: monthQuery.query.deptId })
    ElMessage.success('导出完成')
  } catch (error) {
    logAsyncError('exportAttendanceMonth', error)
  } finally {
    monthExporting.value = false
  }
}

/* ---------------- 重算 / 封账 ---------------- */

async function rebuild() {
  const month = detailQuery.query.month || currentMonth()
  try {
    await ElMessageBox.confirm(
      `将重算 ${month} 的考勤（逐日日结 + 月度汇总）。已封账的月份不会被覆盖，确定继续？`,
      '重算考勤',
      { type: 'warning', confirmButtonText: '开始重算', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    const count = await rebuildAttendanceApi(month)
    ElMessage.success(`重算完成，处理 ${count} 条`)
    await detailQuery.refresh()
    await monthQuery.refresh()
  } catch (error) {
    logAsyncError('rebuildAttendance', error)
  }
}

async function lockMonth() {
  const month = monthQuery.query.month || currentMonth()
  try {
    await ElMessageBox.confirm(
      `封账后 ${month} 的考勤结果不能再修改，薪资将引用该结果。确定封账？`,
      '考勤封账',
      { type: 'warning', confirmButtonText: '确定封账', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await lockAttendanceMonthApi(month)
    ElMessage.success('已封账')
    await monthQuery.refresh()
  } catch (error) {
    logAsyncError('lockAttendanceMonth', error)
  }
}

async function unlockMonth() {
  const month = monthQuery.query.month || currentMonth()
  try {
    const { value } = await ElMessageBox.prompt('解封会让已定稿的考勤重新可改，请填写原因（写入操作日志）', '解封考勤', {
      type: 'warning',
      inputPlaceholder: '例如：补录漏打卡数据',
      inputValidator: (v) => (v && v.trim() ? true : '原因不能为空'),
    })
    await unlockAttendanceMonthApi(month, value)
    ElMessage.success('已解封')
    await monthQuery.refresh()
  } catch (error) {
    if (error !== 'cancel') {
      logAsyncError('unlockAttendanceMonth', error)
    }
  }
}

/* ---------------- 班次 ---------------- */

const shiftDialogVisible = ref(false)
const shiftFormRef = ref<FormInstance>()
const shiftSubmitting = ref(false)
const shiftEditingId = ref<number | null>(null)

const shiftForm = reactive<ShiftSaveRequest>({
  shiftCode: '',
  shiftName: '',
  workStart: '09:00:00',
  workEnd: '18:00:00',
  restMinutes: 60,
  lateGrace: 5,
  earlyGrace: 0,
  flexible: 0,
  freeClock: 0,
  status: 1,
})

const shiftRules: FormRules = {
  shiftCode: [{ required: true, message: '请输入班次编码', trigger: 'blur' }],
  shiftName: [{ required: true, message: '请输入班次名称', trigger: 'blur' }],
}

async function loadShifts() {
  try {
    shifts.value = (await listShiftsApi()) || []
  } catch (error) {
    logAsyncError('listShifts', error)
  }
}

function openShiftCreate() {
  shiftEditingId.value = null
  Object.assign(shiftForm, {
    shiftCode: '',
    shiftName: '',
    workStart: '09:00:00',
    workEnd: '18:00:00',
    restMinutes: 60,
    lateGrace: 5,
    earlyGrace: 0,
    flexible: 0,
    freeClock: 0,
    status: 1,
  })
  shiftDialogVisible.value = true
}

function openShiftEdit(row: ShiftVO) {
  shiftEditingId.value = row.id
  Object.assign(shiftForm, {
    shiftCode: row.shiftCode,
    shiftName: row.shiftName,
    workStart: row.workStart,
    workEnd: row.workEnd,
    restMinutes: row.restMinutes,
    lateGrace: row.lateGrace,
    earlyGrace: row.earlyGrace,
    flexible: row.flexible,
    freeClock: row.freeClock,
    status: row.status,
  })
  shiftDialogVisible.value = true
}

async function submitShift() {
  const valid = await shiftFormRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  shiftSubmitting.value = true
  try {
    await saveShiftApi({ ...shiftForm, id: shiftEditingId.value ?? undefined })
    ElMessage.success('已保存')
    shiftDialogVisible.value = false
    await loadShifts()
  } catch (error) {
    logAsyncError('saveShift', error)
  } finally {
    shiftSubmitting.value = false
  }
}

async function removeShift(row: ShiftVO) {
  try {
    await ElMessageBox.confirm(`确定删除班次「${row.shiftName}」吗？已被排班引用的班次不能删除。`, '删除班次', {
      type: 'warning',
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deleteShiftApi(row.id)
    ElMessage.success('已删除')
    await loadShifts()
  } catch (error) {
    logAsyncError('deleteShift', error)
  }
}

async function toggleShiftStatus(row: ShiftVO) {
  try {
    await updateShiftStatusApi(row.id, row.status === 1 ? 0 : 1)
    await loadShifts()
  } catch (error) {
    logAsyncError('toggleShiftStatus', error)
  }
}

/* ---------------- 排班 ---------------- */

const scheduleQuery = reactive({ deptId: undefined as number | undefined, month: currentMonth(), shiftId: undefined as number | undefined })
const scheduleRows = ref<ScheduleVO[]>([])
const scheduleLoading = ref(false)
const includeWeekend = ref(0)
const overwrite = ref(1)

async function loadSchedule() {
  scheduleLoading.value = true
  try {
    scheduleRows.value = (await listScheduleApi({ deptId: scheduleQuery.deptId, month: scheduleQuery.month })) || []
  } catch (error) {
    logAsyncError('listSchedule', error)
  } finally {
    scheduleLoading.value = false
  }
}

async function generateSchedule() {
  if (!scheduleQuery.deptId || !scheduleQuery.shiftId) {
    ElMessage.warning('请先选择部门与班次')
    return
  }
  try {
    await ElMessageBox.confirm(
      `将为「${scheduleQuery.month}」生成整月排班：工作日套用所选班次，周六日按休息处理${
        includeWeekend.value === 1 ? '（本次包含周末）' : ''
      }。已存在的排班${overwrite.value === 1 ? '会被覆盖' : '会跳过'}。`,
      '批量排班',
      { type: 'warning', confirmButtonText: '开始生成', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    const count = await batchScheduleApi({
      deptId: scheduleQuery.deptId,
      month: scheduleQuery.month,
      shiftId: scheduleQuery.shiftId,
      includeWeekend: includeWeekend.value,
      overwrite: overwrite.value,
    })
    ElMessage.success(`已生成 ${count} 条排班`)
    await loadSchedule()
  } catch (error) {
    logAsyncError('batchSchedule', error)
  }
}

onMounted(async () => {
  try {
    deptTree.value = (await getDeptTreeApi()) || []
  } catch (error) {
    logAsyncError('loadDeptTree', error)
  }
  await Promise.all([loadShifts(), detailQuery.load()])
})
</script>

<template>
  <div class="hr-att">
    <el-card shadow="never">
      <el-tabs v-model="activeTab">
        <!-- ============ 考勤明细 ============ -->
        <el-tab-pane label="考勤明细" name="detail">
          <el-form :inline="true" @submit.prevent>
            <el-form-item label="月份">
              <el-date-picker v-model="detailQuery.query.month" type="month" value-format="YYYY-MM" style="width: 140px" />
            </el-form-item>
            <el-form-item label="部门">
              <el-tree-select
                v-model="detailQuery.query.deptId"
                :data="deptTree"
                :props="{ label: 'deptName', value: 'id', children: 'children' }"
                node-key="id"
                check-strictly
                clearable
                placeholder="全部（含下级）"
                style="width: 170px"
              />
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="detailQuery.query.attendanceStatus" clearable placeholder="全部" style="width: 120px">
                <el-option v-for="s in STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="关键词">
              <el-input v-model="detailQuery.query.keyword" placeholder="姓名 / 工号" clearable style="width: 150px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="detailQuery.search">查询</el-button>
              <el-button @click="detailQuery.reset">重置</el-button>
              <el-button v-permission="'hr:attendance:rebuild'" @click="rebuild">重算考勤</el-button>
            </el-form-item>
          </el-form>

          <el-table v-loading="detailQuery.loading.value" :data="detailQuery.list.value" border>
            <el-table-column prop="attendanceDate" label="日期" width="115" />
            <el-table-column prop="realName" label="姓名" width="100" />
            <el-table-column prop="workNumber" label="工号" width="100" />
            <el-table-column prop="deptName" label="部门" min-width="120" show-overflow-tooltip />
            <el-table-column prop="shiftName" label="班次" width="110">
              <template #default="{ row }">{{ row.shiftName || '-' }}</template>
            </el-table-column>
            <el-table-column label="上班打卡" width="110">
              <template #default="{ row }">{{ row.checkInTime ? row.checkInTime.slice(11, 19) : '-' }}</template>
            </el-table-column>
            <el-table-column label="下班打卡" width="110">
              <template #default="{ row }">{{ row.checkOutTime ? row.checkOutTime.slice(11, 19) : '-' }}</template>
            </el-table-column>
            <el-table-column prop="workDuration" label="工时(h)" width="90">
              <template #default="{ row }">{{ row.workDuration ?? '-' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="statusMeta(row.attendanceStatus).type" effect="plain">
                  {{ row.attendanceStatusLabel || statusMeta(row.attendanceStatus).label }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="迟到/早退(分)" width="120">
              <template #default="{ row }">
                <span v-if="row.lateMinutes || row.earlyMinutes">{{ row.lateMinutes }} / {{ row.earlyMinutes }}</span>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="缺卡" width="90">
              <template #default="{ row }">
                <el-tag v-if="row.cardMissing" type="warning" effect="plain" size="small">
                  {{ row.cardMissing === 3 ? '缺两次' : row.cardMissing === 1 ? '缺上班' : '缺下班' }}
                </el-tag>
                <span v-else>-</span>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            class="hr-att__pagination"
            :current-page="detailQuery.pageNo.value"
            :page-size="detailQuery.pageSize.value"
            :page-sizes="[10, 20, 50]"
            :total="detailQuery.total.value"
            layout="total, sizes, prev, pager, next, jumper"
            background
            @size-change="detailQuery.handleSizeChange"
            @current-change="detailQuery.handlePageChange"
          />
        </el-tab-pane>

        <!-- ============ 月度汇总 ============ -->
        <el-tab-pane label="月度汇总" name="month">
          <el-form :inline="true" @submit.prevent>
            <el-form-item label="月份">
              <el-date-picker v-model="monthQuery.query.month" type="month" value-format="YYYY-MM" style="width: 140px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="monthQuery.search">查询</el-button>
              <el-button v-permission="'hr:attendance:lock'" @click="lockMonth">封账</el-button>
              <el-button v-permission="'hr:attendance:unlock'" @click="unlockMonth">解封</el-button>
              <el-button
                v-permission="'hr:attendance:export'"
                type="primary"
                plain
                :loading="monthExporting"
                @click="exportMonth"
              >
                导出
              </el-button>
            </el-form-item>
          </el-form>
          <el-alert
            class="hr-att__tip"
            type="info"
            :closable="false"
            show-icon
            title="封账后该月考勤不可修改，薪资核算引用此结果；解封仅超级管理员可操作且必须填写原因。"
          />
          <el-table v-loading="monthQuery.loading.value" :data="monthQuery.list.value" border>
            <el-table-column prop="month" label="月份" width="100" />
            <el-table-column prop="realName" label="姓名" width="100" />
            <el-table-column prop="workNumber" label="工号" width="100" />
            <el-table-column prop="deptName" label="部门" min-width="130" show-overflow-tooltip />
            <el-table-column prop="shouldDays" label="应出勤" width="90" />
            <el-table-column prop="actualDays" label="实际出勤" width="100" />
            <el-table-column prop="absentDays" label="旷工" width="80" />
            <el-table-column prop="lateCount" label="迟到(次)" width="95" />
            <el-table-column prop="earlyCount" label="早退(次)" width="95" />
            <el-table-column prop="missingCount" label="缺卡(次)" width="95" />
            <el-table-column prop="tripDays" label="出差(天)" width="95" />
            <el-table-column label="假别明细" min-width="170">
              <template #default="{ row }">
                <span v-if="row.leaveDaysText">{{ row.leaveDaysText }}</span>
                <span v-else class="hr-att__muted">-</span>
              </template>
            </el-table-column>
            <el-table-column label="加班时数" min-width="170">
              <template #default="{ row }">
                <span v-if="row.overtimeText">{{ row.overtimeText }}</span>
                <span v-else class="hr-att__muted">-</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 2 ? 'success' : 'info'" effect="plain">
                  {{ row.status === 2 ? '已封账' : '未封账' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            class="hr-att__pagination"
            :current-page="monthQuery.pageNo.value"
            :page-size="monthQuery.pageSize.value"
            :total="monthQuery.total.value"
            layout="total, prev, pager, next"
            background
            @current-change="monthQuery.handlePageChange"
          />
        </el-tab-pane>

        <!-- ============ 班次 ============ -->
        <el-tab-pane label="班次" name="shift">
          <div class="hr-att__toolbar">
            <el-button v-permission="'hr:attendance:shift'" type="primary" @click="openShiftCreate">新增班次</el-button>
          </div>
          <el-table :data="shifts" border>
            <el-table-column prop="shiftCode" label="编码" width="130" />
            <el-table-column prop="shiftName" label="名称" min-width="130" />
            <el-table-column label="上下班" width="150">
              <template #default="{ row }">{{ row.workStart }} ~ {{ row.workEnd }}</template>
            </el-table-column>
            <el-table-column prop="restMinutes" label="休息(分)" width="100" />
            <el-table-column prop="lateGrace" label="迟到宽限" width="100" />
            <el-table-column prop="standardHours" label="标准工时" width="100" />
            <el-table-column label="弹性/免打卡" width="120">
              <template #default="{ row }">{{ row.flexible ? '弹性' : '固定' }} / {{ row.freeClock ? '免打卡' : '需打卡' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'" effect="plain">
                  {{ row.status === 1 ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button v-permission="'hr:attendance:shift'" icon-only link type="primary" title="编辑" @click="openShiftEdit(row)">
                  编辑
                </el-button>
                <el-button v-permission="'hr:attendance:shift'" icon-only link type="warning" title="启停" @click="toggleShiftStatus(row)">
                  {{ row.status === 1 ? '停用' : '启用' }}
                </el-button>
                <el-button v-permission="'hr:attendance:shift'" icon-only link type="danger" title="删除" @click="removeShift(row)">
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- ============ 排班 ============ -->
        <el-tab-pane label="排班" name="schedule">
          <el-form :inline="true" @submit.prevent>
            <el-form-item label="部门">
              <el-tree-select
                v-model="scheduleQuery.deptId"
                :data="deptTree"
                :props="{ label: 'deptName', value: 'id', children: 'children' }"
                node-key="id"
                check-strictly
                placeholder="请选择部门"
                style="width: 180px"
              />
            </el-form-item>
            <el-form-item label="月份">
              <el-date-picker v-model="scheduleQuery.month" type="month" value-format="YYYY-MM" style="width: 140px" />
            </el-form-item>
            <el-form-item label="班次">
              <el-select v-model="scheduleQuery.shiftId" clearable placeholder="请选择班次" style="width: 160px">
                <el-option v-for="s in shifts" :key="s.id" :label="s.shiftName" :value="s.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="包含周末">
              <el-switch v-model="includeWeekend" :active-value="1" :inactive-value="0" />
            </el-form-item>
            <el-form-item label="覆盖已存在">
              <el-switch v-model="overwrite" :active-value="1" :inactive-value="0" />
            </el-form-item>
            <el-form-item>
              <el-button v-permission="'hr:attendance:schedule'" type="primary" @click="generateSchedule">批量生成</el-button>
              <el-button @click="loadSchedule">查询排班</el-button>
            </el-form-item>
          </el-form>

          <el-table v-loading="scheduleLoading" :data="scheduleRows" border max-height="520">
            <el-table-column prop="scheduleDate" label="日期" width="115" />
            <el-table-column prop="realName" label="姓名" width="100" />
            <el-table-column prop="workNumber" label="工号" width="100" />
            <el-table-column prop="shiftName" label="班次" width="130">
              <template #default="{ row }">{{ row.shiftName || '休息' }}</template>
            </el-table-column>
            <el-table-column label="日期类型" width="110">
              <template #default="{ row }">
                {{ row.dayType === 3 ? '法定节假日' : row.dayType === 2 ? '休息日' : row.dayType === 4 ? '调休上班' : '工作日' }}
              </template>
            </el-table-column>
            <el-table-column label="日结" width="90">
              <template #default="{ row }">
                <el-tag :type="row.settled ? 'success' : 'info'" effect="plain" size="small">
                  {{ row.settled ? '已日结' : '未日结' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="shiftDialogVisible" :title="shiftEditingId ? '编辑班次' : '新增班次'" width="520px" destroy-on-close>
      <el-form ref="shiftFormRef" :model="shiftForm" :rules="shiftRules" label-width="100px">
        <el-form-item label="班次编码" prop="shiftCode">
          <el-input v-model="shiftForm.shiftCode" maxlength="32" placeholder="如 DAY" />
        </el-form-item>
        <el-form-item label="班次名称" prop="shiftName">
          <el-input v-model="shiftForm.shiftName" maxlength="64" placeholder="如 常白班" />
        </el-form-item>
        <el-form-item label="上班时间">
          <el-time-picker v-model="shiftForm.workStart" value-format="HH:mm:ss" placeholder="09:00:00" />
        </el-form-item>
        <el-form-item label="下班时间">
          <el-time-picker v-model="shiftForm.workEnd" value-format="HH:mm:ss" placeholder="18:00:00" />
          <div class="hr-att__hint">下班早于上班表示跨天（夜班）</div>
        </el-form-item>
        <el-form-item label="休息时长">
          <el-input-number v-model="shiftForm.restMinutes" :min="0" :max="480" controls-position="right" />
          <span class="hr-att__hint">分钟，用于计算工时</span>
        </el-form-item>
        <el-form-item label="迟到宽限">
          <el-input-number v-model="shiftForm.lateGrace" :min="0" :max="120" controls-position="right" />
          <span class="hr-att__hint">分钟内不算迟到</span>
        </el-form-item>
        <el-form-item label="早退宽限">
          <el-input-number v-model="shiftForm.earlyGrace" :min="0" :max="120" controls-position="right" />
        </el-form-item>
        <el-form-item label="弹性班">
          <el-switch v-model="shiftForm.flexible" :active-value="1" :inactive-value="0" />
          <span class="hr-att__hint">弹性班不判迟到早退</span>
        </el-form-item>
        <el-form-item label="免打卡">
          <el-switch v-model="shiftForm.freeClock" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="shiftForm.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="shiftDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="shiftSubmitting" @click="submitShift">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.hr-att__toolbar {
  margin-bottom: 12px;
}

.hr-att__tip {
  margin: 4px 0 12px;
}

.hr-att__muted {
  color: #c0c4cc;
}

.hr-att__pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

.hr-att__hint {
  margin-left: 8px;
  color: #8a94a5;
  font-size: 12px;
}
</style>
