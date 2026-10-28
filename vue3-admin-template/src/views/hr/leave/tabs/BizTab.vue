<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { LEAVE_STATUS, statusMeta } from '../constants'
import { useLeaveBiz } from '../composables/useLeaveBiz'
import BizFormDialog from '../dialogs/BizFormDialog.vue'

/**
 * 加班 / 出差 / 补卡标签页（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>三类申请结构相同（`oa_hr_request` + 同一套状态流转），差异只在表格列与表单字段，
 * 因此共用一个组件、靠 `type` 切换。原实现是模板里 `v-for` 出三个 pane 共用一份
 * `bizState[type]`，拆分后每个实例各持一份状态，互不影响。</p>
 */
const props = defineProps<{
  /** 1 加班 / 2 出差 / 3 补卡 */
  type: number
  /** 标签文案，用于按钮「发起xxx」 */
  label: string
}>()

const { query, list, total, loading, load, cancel } = useLeaveBiz(props.type)

onMounted(load)

const formRef = ref<InstanceType<typeof BizFormDialog> | null>(null)

function openCreate() {
  formRef.value?.open()
}
</script>

<template>
  <div class="hr-leave__bar">
    <el-date-picker v-model="query.month" type="month" value-format="YYYY-MM" placeholder="全部月份" style="width: 140px" />
    <el-select v-model="query.status" clearable placeholder="全部状态" style="width: 130px">
      <el-option v-for="s in LEAVE_STATUS" :key="s.value" :label="s.label" :value="s.value" />
    </el-select>
    <el-input v-model="query.keyword" placeholder="姓名 / 工号" clearable style="width: 160px" />
    <el-button type="primary" data-action="search" @click="query.pageNo = 1; load()">查询</el-button>
    <el-button type="primary" plain data-action="create" @click="openCreate">发起{{ label }}</el-button>
  </div>

  <el-table v-loading="loading" :data="list" border>
    <el-table-column prop="billCode" label="单据编号" width="190" />
    <el-table-column prop="realName" label="申请人" width="100" />
    <el-table-column prop="deptName" label="部门" width="120" />
    <template v-if="type === 1">
      <el-table-column prop="bizDate" label="加班日期" width="115" />
      <el-table-column prop="hours" label="时长(h)" width="90" />
      <el-table-column label="补偿" width="100">
        <template #default="{ row }">{{ row.compensateType === 1 ? '转调休' : '加班费' }}</template>
      </el-table-column>
    </template>
    <template v-else-if="type === 2">
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
        <el-button v-if="row.canCancel" icon-only link type="danger" title="撤销" data-action="cancel" @click="cancel(row)">
          撤销
        </el-button>
        <span v-else class="hr-leave__muted">-</span>
      </template>
    </el-table-column>
  </el-table>
  <el-pagination
    class="hr-leave__pagination"
    :current-page="query.pageNo"
    :page-size="query.pageSize"
    :total="total"
    layout="total, prev, pager, next"
    background
    @current-change="(p: number) => { query.pageNo = p; load() }"
  />

  <BizFormDialog ref="formRef" :type="type" @saved="load" />
</template>
