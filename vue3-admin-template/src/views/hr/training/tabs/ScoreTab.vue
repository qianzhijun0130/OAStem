<script setup lang="ts">
import { ref } from 'vue'
import type { TrainEnrollVO, TrainSessionVO } from '@/api/training'
import ScoreDialog from '../dialogs/ScoreDialog.vue'
import type { TrainEnrollBoard } from '../composables/useTrainEnroll'

/**
 * 「成绩管理」标签页（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>与「报名管理」同一份花名册（看板由页面创建），这里只多展示及格/满意度列，
 * 并提供逐条录分。期次选择器两页共享，切过去不用重选。</p>
 */
const props = defineProps<{
  enroll: TrainEnrollBoard
  sessionOptions: TrainSessionVO[]
}>()

const board = props.enroll.state
const { load } = props.enroll

const scoreRef = ref<InstanceType<typeof ScoreDialog> | null>(null)

function openScore(row: TrainEnrollVO) {
  scoreRef.value?.open(row)
}
</script>

<template>
  <div class="training__head">
    <el-form :inline="true" @submit.prevent>
      <el-form-item label="期次">
        <el-select v-model="board.sessionId" clearable filterable placeholder="请选择期次" style="width: 280px">
          <el-option
            v-for="s in props.sessionOptions"
            :key="s.id"
            :label="`${s.courseName} · ${s.sessionName}`"
            :value="s.id"
          />
        </el-select>
      </el-form-item>
    </el-form>
  </div>

  <el-table v-loading="board.loading" :data="board.list" border>
    <el-table-column prop="userName" label="姓名" width="100" />
    <el-table-column prop="deptName" label="部门" min-width="120" show-overflow-tooltip />
    <el-table-column label="状态" width="90">
      <template #default="{ row }">
        <el-tag effect="plain">{{ row.statusLabel }}</el-tag>
      </template>
    </el-table-column>
    <el-table-column label="成绩" width="80">
      <template #default="{ row }">{{ row.score != null ? row.score : '-' }}</template>
    </el-table-column>
    <el-table-column label="及格" width="80">
      <template #default="{ row }">
        <el-tag :type="row.passed === 1 ? 'success' : 'info'" effect="plain">
          {{ row.passed === 1 ? '及格' : row.passed === 0 ? '不及格' : '-' }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column label="满意度" width="90">
      <template #default="{ row }">{{ row.satisfied != null ? row.satisfied + ' 星' : '-' }}</template>
    </el-table-column>
    <el-table-column label="操作" width="150" fixed="right" header-align="center" align="center">
      <template #default="{ row }">
        <el-button
          v-permission="'hr:training:score'"
          icon-only
          link
          type="primary"
          title="成绩录入"
          data-action="score"
          @click="openScore(row)"
        >
          录入
        </el-button>
      </template>
    </el-table-column>
  </el-table>

  <ScoreDialog ref="scoreRef" @saved="load" />
</template>
