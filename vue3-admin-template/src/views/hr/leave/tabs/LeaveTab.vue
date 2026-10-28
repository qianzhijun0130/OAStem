<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { LEAVE_STATUS, statusMeta, leaveTypeLabel } from '../constants'
import { useLeaveList } from '../composables/useLeaveList'
import LeaveFormDialog from '../dialogs/LeaveFormDialog.vue'
import type { LeaveVO } from '@/api/types'

/**
 * 「请假单」标签页（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>自包含：列表、筛选、导出、发起请假、撤销/销假都在本组件内。
 * 请假会占用额度，撤销/销假会释放额度，因此这些操作成功后向父级发 `changed`，
 * 由页面去刷新「我的额度」tab（额度状态在兄弟组件里，跨 tab 只能靠事件）。</p>
 */
const emit = defineEmits<{ changed: [] }>()

const { query, list, total, loading, page, exporting, load, exportList, canReturn, cancel, returnFromLeave } =
  useLeaveList()

onMounted(load)

const formRef = ref<InstanceType<typeof LeaveFormDialog> | null>(null)

function openCreate() {
  formRef.value?.open()
}

function onSaved() {
  void load()
  emit('changed')
}

async function onCancel(row: LeaveVO) {
  if (await cancel(row)) {
    emit('changed')
  }
}

async function onReturn(row: LeaveVO) {
  if (await returnFromLeave(row)) {
    emit('changed')
  }
}
</script>

<template>
  <div class="hr-leave__bar">
    <el-date-picker v-model="query.month" type="month" value-format="YYYY-MM" placeholder="全部月份" style="width: 140px" />
    <el-select v-model="query.leaveStatus" clearable placeholder="全部状态" style="width: 130px">
      <el-option v-for="s in LEAVE_STATUS" :key="s.value" :label="s.label" :value="s.value" />
    </el-select>
    <el-input v-model="query.keyword" placeholder="姓名 / 工号" clearable style="width: 160px" />
    <el-button type="primary" data-action="search" @click="page.pageNo = 1; load()">查询</el-button>
    <el-button v-permission="'hr:leave:export'" :loading="exporting" data-action="export" @click="exportList">导出</el-button>
    <el-button v-permission="'hr:leave:create'" type="primary" plain data-action="create" @click="openCreate">
      发起请假
    </el-button>
  </div>
  <el-table v-loading="loading" :data="list" border>
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
    <el-table-column label="天数" width="110">
      <template #default="{ row }">
        {{ row.leaveDays }}
        <el-tooltip v-if="row.actualDays != null" :content="`实际休假 ${row.actualDays} 天，返岗 ${row.returnDate}`">
          <el-tag size="small" type="success" effect="plain">已销假</el-tag>
        </el-tooltip>
      </template>
    </el-table-column>
    <el-table-column label="状态" width="100">
      <template #default="{ row }">
        <el-tag :type="statusMeta(row.leaveStatus).type" effect="plain">
          {{ row.leaveStatusLabel || statusMeta(row.leaveStatus).label }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column prop="reason" label="事由" min-width="160" show-overflow-tooltip />
    <el-table-column label="操作" width="180" fixed="right">
      <template #default="{ row }">
        <el-button v-if="row.canCancel" icon-only link type="danger" title="撤销" data-action="cancel" @click="onCancel(row)">
          撤销
        </el-button>
        <el-button
          v-if="canReturn(row)"
          icon-only
          link
          type="primary"
          title="销假"
          data-action="return"
          @click="onReturn(row)"
        >
          销假
        </el-button>
        <span v-if="!row.canCancel && !canReturn(row)" class="hr-leave__muted">-</span>
      </template>
    </el-table-column>
  </el-table>
  <el-pagination
    class="hr-leave__pagination"
    :current-page="page.pageNo"
    :page-size="page.pageSize"
    :total="total"
    layout="total, prev, pager, next"
    background
    @current-change="(p: number) => { page.pageNo = p; load() }"
  />

  <LeaveFormDialog ref="formRef" @saved="onSaved" />
</template>
