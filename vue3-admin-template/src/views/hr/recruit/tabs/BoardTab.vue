<script setup lang="ts">
import { onMounted } from 'vue'
import { useRecruitBoard } from '../composables/useRecruitBoard'

/**
 * 「招聘看板」标签页（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>四张统计表：阶段漏斗、渠道转化、淘汰原因、部门维度。指标均由后端一次算好。</p>
 */
const { loading, board, load } = useRecruitBoard()

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="recruit__board">
    <el-row :gutter="12">
      <el-col :span="6">
        <el-statistic title="在招职位数" :value="board?.postingCount ?? 0" />
      </el-col>
      <el-col :span="6">
        <el-statistic title="需求总人数" :value="board?.demandHeadCount ?? 0" />
      </el-col>
      <el-col :span="6">
        <el-statistic title="已招到岗" :value="board?.hiredCount ?? 0" />
      </el-col>
      <el-col :span="6">
        <el-statistic title="平均周期(天)" :value="board?.avgCycleDays ?? 0" />
      </el-col>
    </el-row>

    <el-divider content-position="left">阶段漏斗</el-divider>
    <el-table :data="board?.stageFunnel ?? []" border size="small">
      <el-table-column prop="label" label="阶段" width="120" />
      <el-table-column prop="count" label="人数" width="100" />
      <el-table-column label="转化率" width="100">
        <template #default="{ row }">{{ row.convertRate != null ? row.convertRate + '%' : '-' }}</template>
      </el-table-column>
    </el-table>

    <el-divider content-position="left">渠道转化</el-divider>
    <el-table :data="board?.channelStats ?? []" border size="small">
      <el-table-column prop="label" label="渠道" width="120" />
      <el-table-column prop="resumeCount" label="简历数" width="100" />
      <el-table-column prop="interviewCount" label="面试数" width="100" />
      <el-table-column prop="hireCount" label="入职数" width="100" />
      <el-table-column label="面试转化率" width="120">
        <template #default="{ row }">{{ row.interviewRate != null ? row.interviewRate + '%' : '-' }}</template>
      </el-table-column>
      <el-table-column label="入职转化率" width="120">
        <template #default="{ row }">{{ row.hireRate != null ? row.hireRate + '%' : '-' }}</template>
      </el-table-column>
    </el-table>

    <el-divider content-position="left">淘汰原因分布</el-divider>
    <el-table :data="board?.rejectStats ?? []" border size="small">
      <el-table-column prop="label" label="原因" width="160" />
      <el-table-column prop="count" label="人数" width="100" />
    </el-table>

    <el-divider content-position="left">部门维度</el-divider>
    <el-table :data="board?.deptStats ?? []" border size="small">
      <el-table-column prop="deptName" label="部门" min-width="140" />
      <el-table-column prop="demandCount" label="需求数" width="100" />
      <el-table-column prop="headCount" label="需求人数" width="100" />
      <el-table-column prop="hiredCount" label="到岗人数" width="100" />
    </el-table>
  </div>
</template>
