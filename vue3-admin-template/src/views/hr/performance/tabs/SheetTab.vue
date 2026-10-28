<script setup lang="ts">
import { onMounted, ref } from 'vue'
import SheetDetailDrawer from '../dialogs/SheetDetailDrawer.vue'
import { usePerfSheet } from '../composables/usePerfSheet'
import { gradeTagType, money1 } from '../display'
import type { PerfCycleBoard } from '../composables/usePerfCycleBoard'
import type { PerfSheetVO } from '@/api/types'

/**
 * 「考核表」标签页（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>自包含：列表、筛选、导出、明细抽屉。周期下拉来自页面共享的周期看板；
 * 本页签自己的「周期」筛选留空时回落到看板当前周期（与改造前一致）。</p>
 */
const props = defineProps<{
  cycleBoard: PerfCycleBoard
}>()

const board = props.cycleBoard.state
const { ensureLoaded } = props.cycleBoard
const { query, list, total, loading, exporting, load, exportResult } = usePerfSheet()

/** 列表统一入口：把共享周期作为回落值传给 composable */
function reload() {
  return load(board.cycleId)
}

// 先等周期就位再查：列表要拿当前周期做回落筛选。子组件的 onMounted 早于父组件的，
// 所以这里主动 await 一次幂等加载（已加载过直接返回），而不是假定页面已经拉好。
onMounted(async () => {
  await ensureLoaded()
  await reload()
})

const drawerRef = ref<InstanceType<typeof SheetDetailDrawer> | null>(null)

function openSheet(row: PerfSheetVO) {
  drawerRef.value?.open(row)
}

defineExpose({ refresh: reload })
</script>

<template>
  <el-form :inline="true" @submit.prevent>
    <el-form-item label="周期">
      <el-select v-model="query.cycleId" clearable placeholder="全部周期" style="width: 200px">
        <el-option v-for="c in board.list" :key="c.id" :label="c.cycleName" :value="c.id" />
      </el-select>
    </el-form-item>
    <el-form-item label="进度">
      <el-select v-model="query.stage" style="width: 130px">
        <el-option label="全部" value="all" />
        <el-option label="目标未锁定" value="target" />
        <el-option label="待自评" value="self" />
        <el-option label="待上级评分" value="manager" />
        <el-option label="待确认" value="confirm" />
      </el-select>
    </el-form-item>
    <el-form-item label="姓名/工号">
      <el-input v-model="query.keyword" clearable style="width: 150px" @keyup.enter="query.pageNo = 1; reload()" />
    </el-form-item>
    <el-form-item>
      <el-button type="primary" data-action="search" @click="query.pageNo = 1; reload()">查询</el-button>
      <el-button v-permission="'hr:performance:export'" :loading="exporting" data-action="export" @click="exportResult(board.cycleId)">
        导出
      </el-button>
    </el-form-item>
  </el-form>

  <el-table v-loading="loading" :data="list" border class="perf__sheet-table">
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
        <el-button link type="primary" data-action="detail" @click="openSheet(row)">查看</el-button>
      </template>
    </el-table-column>
  </el-table>
  <el-pagination
    class="perf__pager"
    :current-page="query.pageNo"
    :page-size="query.pageSize"
    :total="total"
    layout="total, prev, pager, next"
    background
    @current-change="(p: number) => { query.pageNo = p; reload() }"
  />

  <SheetDetailDrawer ref="drawerRef" @saved="reload" />
</template>
