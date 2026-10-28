<script setup lang="ts">
import { onMounted, ref } from 'vue'
import IndicatorFormDialog from '../dialogs/IndicatorFormDialog.vue'
import { usePerfIndicator } from '../composables/usePerfIndicator'
import type { PerfOptions } from '../composables/usePerfOptions'
import type { PerfIndicatorVO } from '@/api/types'

/**
 * 「指标库」标签页（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>保存指标后要刷「指标下拉」——「绩效方案」页签的弹窗用它。同方案页的反向依赖。</p>
 */
const props = defineProps<{
  options: PerfOptions
}>()

const { query, list, total, loading, load, remove } = usePerfIndicator()

onMounted(load)

const formRef = ref<InstanceType<typeof IndicatorFormDialog> | null>(null)

function openCreate() {
  formRef.value?.openCreate()
}

function openEdit(row: PerfIndicatorVO) {
  formRef.value?.openEdit(row)
}

async function onSaved() {
  await load()
  await props.options.loadIndicatorOptions(true)
}
</script>

<template>
  <el-form :inline="true" @submit.prevent>
    <el-form-item label="指标">
      <el-input v-model="query.keyword" clearable style="width: 180px" @keyup.enter="query.pageNo = 1; load()" />
    </el-form-item>
    <el-form-item label="类型">
      <el-select v-model="query.type" clearable placeholder="全部" style="width: 120px">
        <el-option label="定量" :value="1" />
        <el-option label="定性" :value="2" />
        <el-option label="加分项" :value="3" />
        <el-option label="扣分项" :value="4" />
      </el-select>
    </el-form-item>
    <el-form-item>
      <el-button type="primary" data-action="search" @click="query.pageNo = 1; load()">查询</el-button>
      <el-button v-permission="'hr:performance:indicator'" type="primary" plain data-action="create" @click="openCreate">
        新建指标
      </el-button>
    </el-form-item>
  </el-form>

  <el-table v-loading="loading" :data="list" border class="perf__indicator-table">
    <el-table-column prop="indicatorCode" label="编码" width="150" />
    <el-table-column prop="indicatorName" label="名称" min-width="160" />
    <el-table-column prop="indicatorTypeLabel" label="类型" width="100" />
    <el-table-column prop="unit" label="单位" width="80" />
    <el-table-column prop="definition" label="定义" min-width="200" show-overflow-tooltip />
    <el-table-column label="操作" width="140" fixed="right">
      <template #default="{ row }">
        <el-button v-permission="'hr:performance:indicator'" link type="primary" data-action="edit" @click="openEdit(row)">
          编辑
        </el-button>
        <el-button v-permission="'hr:performance:indicator'" link type="danger" data-action="delete" @click="remove(row)">
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

  <IndicatorFormDialog ref="formRef" @saved="onSaved" />
</template>
