<script setup lang="ts">
import { onMounted } from 'vue'
import { usePerfDistribution } from '../composables/usePerfDistribution'
import { money1 } from '../display'
import type { PerfCycleBoard } from '../composables/usePerfCycleBoard'

/**
 * 「等级分布」标签页（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>只做「A 占比超过建议上限」的预警，不拦截任何人定级。统计对象是页面共享的
 * 当前周期，切周期即重算。</p>
 */
const props = defineProps<{
  cycleBoard: PerfCycleBoard
}>()

const board = props.cycleBoard.state
const { ensureLoaded } = props.cycleBoard
const { state, load } = usePerfDistribution()

function reload() {
  return load(board.cycleId)
}

onMounted(async () => {
  await ensureLoaded()
  await load(board.cycleId)
})

defineExpose({ refresh: reload })
</script>

<template>
  <el-form :inline="true" @submit.prevent>
    <el-form-item label="周期">
      <el-select v-model="board.cycleId" style="width: 200px" @change="reload">
        <el-option v-for="c in board.list" :key="c.id" :label="c.cycleName" :value="c.id" />
      </el-select>
    </el-form-item>
    <el-form-item>
      <el-button type="primary" data-action="search" @click="reload">统计</el-button>
    </el-form-item>
  </el-form>

  <el-alert
    class="perf__tip"
    type="info"
    :closable="false"
    show-icon
    title="强制分布默认关闭：这里只做「A 占比超过建议上限」的预警，不拦截任何人定级。"
  />
  <el-table v-loading="state.loading" :data="state.list" border class="perf__dist-table">
    <el-table-column prop="dimension" label="部门" min-width="150" />
    <el-table-column prop="total" label="人数" width="90" />
    <el-table-column prop="countA" label="A" width="80" />
    <el-table-column prop="countB" label="B" width="80" />
    <el-table-column prop="countC" label="C" width="80" />
    <el-table-column prop="countD" label="D" width="80" />
    <el-table-column label="A 占比" width="130">
      <template #default="{ row }">
        {{ (Number(row.aRatio) * 100).toFixed(1) }}%
        <el-tag v-if="row.overLimit === 1" size="small" type="warning" effect="plain">超建议上限</el-tag>
      </template>
    </el-table-column>
    <el-table-column label="平均分" width="100" align="right">
      <template #default="{ row }">{{ money1(row.avgScore) }}</template>
    </el-table-column>
  </el-table>
</template>
