<script setup lang="ts">
import { onMounted } from 'vue'
import { useTrainStat } from '../composables/useTrainStat'

/**
 * 「培训统计」标签页（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>自包含：年度筛选 + 8 个指标 + 按课程/按部门的明细表。年度留空即今年（后端语义）。</p>
 */
const { year, loading, stat, load } = useTrainStat()

onMounted(load)
</script>

<template>
  <div class="training__head">
    <el-form :inline="true" @submit.prevent>
      <el-form-item label="年度">
        <el-input v-model="year" placeholder="留空=今年" clearable style="width: 140px" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" data-action="search" @click="load">查询</el-button>
      </el-form-item>
    </el-form>
  </div>

  <div v-loading="loading" class="training__stat">
    <el-row :gutter="12">
      <el-col :span="6"><el-statistic title="人均学时" :value="stat?.avgHours ?? 0" /></el-col>
      <el-col :span="6"><el-statistic title="总学时" :value="stat?.totalHours ?? 0" /></el-col>
      <el-col :span="6"><el-statistic title="必修完成率(%)" :value="stat?.requiredFinishRate ?? 0" /></el-col>
      <el-col :span="6"><el-statistic title="课程满意度" :value="stat?.satisfaction ?? 0" /></el-col>
    </el-row>
    <el-row :gutter="12" style="margin-top: 8px">
      <el-col :span="6"><el-statistic title="在职人数" :value="stat?.staffCount ?? 0" /></el-col>
      <el-col :span="6"><el-statistic title="培训总投入" :value="stat?.totalCost ?? 0" /></el-col>
      <el-col :span="6"><el-statistic title="应完成必修" :value="stat?.requiredShouldCount ?? 0" /></el-col>
      <el-col :span="6"><el-statistic title="已完成必修" :value="stat?.requiredDoneCount ?? 0" /></el-col>
    </el-row>

    <el-divider content-position="left">按课程统计</el-divider>
    <el-table :data="stat?.courseStats ?? []" border size="small">
      <el-table-column prop="courseName" label="课程" min-width="140" show-overflow-tooltip />
      <el-table-column label="必修" width="80">
        <template #default="{ row }">{{ row.required === 1 ? '必修' : '选修' }}</template>
      </el-table-column>
      <el-table-column prop="enrollCount" label="报名" width="80" />
      <el-table-column prop="completedCount" label="结业" width="80" />
      <el-table-column label="出勤率" width="90">
        <template #default="{ row }">{{ row.attendRate != null ? row.attendRate + '%' : '-' }}</template>
      </el-table-column>
      <el-table-column label="平均学时" width="90">
        <template #default="{ row }">{{ row.avgHours != null ? row.avgHours : '-' }}</template>
      </el-table-column>
      <el-table-column label="满意度" width="90">
        <template #default="{ row }">{{ row.satisfaction != null ? row.satisfaction : '-' }}</template>
      </el-table-column>
    </el-table>

    <el-divider content-position="left">按部门统计</el-divider>
    <el-table :data="stat?.deptStats ?? []" border size="small">
      <el-table-column prop="deptName" label="部门" min-width="140" show-overflow-tooltip />
      <el-table-column prop="enrollCount" label="参训" width="90" />
      <el-table-column prop="completedCount" label="结业" width="90" />
      <el-table-column label="总学时" width="100">
        <template #default="{ row }">{{ row.totalHours != null ? row.totalHours : '-' }}</template>
      </el-table-column>
    </el-table>
  </div>
</template>
