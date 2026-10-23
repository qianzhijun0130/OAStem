<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { clockApi, getMyAttendanceApi, getTodayAttendanceApi } from '@/api/hr'
import { logAsyncError } from '@/utils/asyncGuard'
import type { AttendanceVO, TodayAttendanceVO } from '@/api/types'

const today = ref<TodayAttendanceVO | null>(null)
const todayLoading = ref(false)
const clocking = ref(false)

const month = ref(currentMonth())
const rows = ref<AttendanceVO[]>([])
const listLoading = ref(false)

function currentMonth(): string {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}

const STATUS_META: Record<number, { label: string; type: 'success' | 'warning' | 'danger' | 'info' }> = {
  1: { label: '正常', type: 'success' },
  2: { label: '迟到', type: 'warning' },
  3: { label: '早退', type: 'warning' },
  4: { label: '缺勤', type: 'danger' },
  5: { label: '假勤', type: 'info' },
}

const workedText = computed(() => {
  const minutes = today.value?.workedMinutes
  if (minutes === undefined || minutes === null) {
    return '-'
  }
  return `${Math.floor(minutes / 60)} 小时 ${minutes % 60} 分钟`
})

async function loadToday() {
  todayLoading.value = true
  try {
    today.value = await getTodayAttendanceApi()
  } catch (error) {
    logAsyncError('loadTodayAttendance', error)
  } finally {
    todayLoading.value = false
  }
}

async function loadMonth() {
  listLoading.value = true
  try {
    rows.value = (await getMyAttendanceApi(month.value)) || []
  } catch (error) {
    logAsyncError('loadMyAttendance', error)
  } finally {
    listLoading.value = false
  }
}

async function clock(direction: 1 | 2) {
  clocking.value = true
  try {
    await clockApi({ direction })
    ElMessage.success(direction === 1 ? '上班卡已打' : '下班卡已打')
    await loadToday()
    await loadMonth()
  } catch (error) {
    logAsyncError('clock', error)
  } finally {
    clocking.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadToday(), loadMonth()])
})
</script>

<template>
  <div class="my-att">
    <el-card v-loading="todayLoading" shadow="never" class="my-att__today">
      <div class="my-att__today-head">
        <div>
          <div class="my-att__date">{{ today?.date || '-' }}</div>
          <div class="my-att__shift">
            <template v-if="today?.scheduled">
              班次：{{ today.shiftName }}（{{ today.workStart }} ~ {{ today.workEnd }}）
            </template>
            <template v-else>今日未排班，无需打卡</template>
          </div>
        </div>
        <el-tag v-if="today?.attendanceStatusLabel" :type="STATUS_META[today.attendanceStatus || 1]?.type" effect="plain">
          {{ today.attendanceStatusLabel }}
        </el-tag>
      </div>

      <div class="my-att__grid">
        <div class="my-att__cell">
          <span class="my-att__label">上班打卡</span>
          <span class="my-att__value">{{ today?.checkInTime?.slice(11, 19) || '未打卡' }}</span>
        </div>
        <div class="my-att__cell">
          <span class="my-att__label">下班打卡</span>
          <span class="my-att__value">{{ today?.checkOutTime?.slice(11, 19) || '未打卡' }}</span>
        </div>
        <div class="my-att__cell">
          <span class="my-att__label">已工作时长</span>
          <span class="my-att__value">{{ workedText }}</span>
        </div>
        <div class="my-att__cell">
          <span class="my-att__label">迟到 / 早退</span>
          <span class="my-att__value">{{ today?.lateMinutes ?? 0 }} / {{ today?.earlyMinutes ?? 0 }} 分钟</span>
        </div>
      </div>

      <div class="my-att__actions">
        <el-button type="primary" :disabled="!today?.canClockIn" :loading="clocking" @click="clock(1)">上班打卡</el-button>
        <el-button type="success" :disabled="!today?.canClockOut" :loading="clocking" @click="clock(2)">下班打卡</el-button>
        <span class="my-att__hint">只缺一次卡不会被判旷工，可在考勤管理中提交补卡申请。</span>
      </div>
    </el-card>

    <el-card shadow="never" class="my-att__list">
      <div class="my-att__list-head">
        <span class="my-att__title">我的考勤明细</span>
        <el-date-picker v-model="month" type="month" value-format="YYYY-MM" style="width: 140px" @change="loadMonth" />
      </div>
      <el-table v-loading="listLoading" :data="rows" border>
        <el-table-column prop="attendanceDate" label="日期" width="120" />
        <el-table-column prop="shiftName" label="班次" width="120">
          <template #default="{ row }">{{ row.shiftName || '-' }}</template>
        </el-table-column>
        <el-table-column label="上班" width="110">
          <template #default="{ row }">{{ row.checkInTime ? row.checkInTime.slice(11, 19) : '-' }}</template>
        </el-table-column>
        <el-table-column label="下班" width="110">
          <template #default="{ row }">{{ row.checkOutTime ? row.checkOutTime.slice(11, 19) : '-' }}</template>
        </el-table-column>
        <el-table-column prop="workDuration" label="工时(h)" width="100">
          <template #default="{ row }">{{ row.workDuration ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="STATUS_META[row.attendanceStatus]?.type || 'info'" effect="plain">
              {{ row.attendanceStatusLabel || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="迟到/早退(分)" width="130">
          <template #default="{ row }">{{ row.lateMinutes || 0 }} / {{ row.earlyMinutes || 0 }}</template>
        </el-table-column>
        <el-table-column label="缺卡" width="100">
          <template #default="{ row }">
            <span v-if="row.cardMissing">{{ row.cardMissing === 3 ? '缺两次' : row.cardMissing === 1 ? '缺上班' : '缺下班' }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!listLoading && !rows.length" description="本月暂无考勤记录（未排班不产生记录）" :image-size="70" />
    </el-card>
  </div>
</template>

<style scoped>
.my-att {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.my-att__today-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.my-att__date {
  font-size: 18px;
  font-weight: 600;
}

.my-att__shift {
  margin-top: 4px;
  color: #8a94a5;
  font-size: 13px;
}

.my-att__grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin: 18px 0 16px;
}

.my-att__cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 14px;
  background: var(--el-fill-color-lighter);
  border-radius: 10px;
}

.my-att__label {
  color: #8a94a5;
  font-size: 12px;
}

.my-att__value {
  font-size: 16px;
  font-weight: 600;
}

.my-att__actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.my-att__hint {
  color: #8a94a5;
  font-size: 12px;
}

.my-att__list-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.my-att__title {
  font-size: 15px;
  font-weight: 600;
}
</style>
