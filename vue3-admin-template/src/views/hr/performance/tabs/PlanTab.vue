<script setup lang="ts">
import { onMounted, ref } from 'vue'
import PlanFormDialog from '../dialogs/PlanFormDialog.vue'
import { usePerfPlan } from '../composables/usePerfPlan'
import type { PerfOptions } from '../composables/usePerfOptions'
import type { PerfPlanVO } from '@/api/types'

/**
 * 「绩效方案」标签页（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>保存方案后要顺手刷「方案下拉」——「考核周期」页签的新建弹窗用它。
 * 这是两个 tab 之间的隐性依赖，靠页面共享的 options 实例传递。</p>
 */
const props = defineProps<{
  options: PerfOptions
}>()

const { query, list, total, loading, load, remove } = usePerfPlan()

onMounted(load)

const formRef = ref<InstanceType<typeof PlanFormDialog> | null>(null)

async function openCreate() {
  await props.options.loadIndicatorOptions()
  formRef.value?.openCreate()
}

async function openEdit(row: PerfPlanVO) {
  await props.options.loadIndicatorOptions()
  formRef.value?.openEdit(row)
}

async function onSaved() {
  await load()
  await props.options.loadPlanOptions(true)
}
</script>

<template>
  <el-form :inline="true" @submit.prevent>
    <el-form-item label="方案">
      <el-input v-model="query.keyword" clearable style="width: 180px" @keyup.enter="query.pageNo = 1; load()" />
    </el-form-item>
    <el-form-item>
      <el-button type="primary" data-action="search" @click="query.pageNo = 1; load()">查询</el-button>
      <el-button v-permission="'hr:performance:plan'" type="primary" plain data-action="create" @click="openCreate">
        新建方案
      </el-button>
    </el-form-item>
  </el-form>

  <el-table v-loading="loading" :data="list" border class="perf__plan-table">
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
        <el-button v-permission="'hr:performance:plan'" link type="primary" data-action="edit" @click="openEdit(row)">
          编辑
        </el-button>
        <el-button v-permission="'hr:performance:plan'" link type="danger" data-action="delete" @click="remove(row)">
          删除
        </el-button>
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
    @current-change="(p: number) => { query.pageNo = p; load() }"
  />

  <PlanFormDialog ref="formRef" :indicator-options="props.options.state.indicatorOptions" @saved="onSaved" />
</template>
