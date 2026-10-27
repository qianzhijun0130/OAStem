<script setup lang="ts">
import { onMounted, ref } from 'vue'
import type { SalaryProfileVO } from '@/api/types'
import { money } from '../display'
import ProfileFormDialog from '../dialogs/ProfileFormDialog.vue'
import HistoryDialog from '../dialogs/HistoryDialog.vue'
import { useSalaryProfile } from '../composables/useSalaryProfile'

/**
 * 「薪资档案」标签页（FE-OPT-06 拆分自 index.vue）。
 *
 * <p>自包含：档案列表 + 新增/调薪 + 历史两个弹窗。调薪保存后重载列表。</p>
 */
defineProps<{
  deptOptions: { value: number; label: string }[]
}>()

const { loadProfiles, profileQuery, profiles, profileTotal, profileLoading } = useSalaryProfile()

// 列表数据由本组件自己加载（拆分前由页面 onMounted 统一触发）
onMounted(loadProfiles)

const profileFormRef = ref<InstanceType<typeof ProfileFormDialog> | null>(null)
const historyRef = ref<InstanceType<typeof HistoryDialog> | null>(null)

/** 不传 row 为新增；传 row 为调薪 */
function openProfileForm(row?: SalaryProfileVO) {
  void profileFormRef.value?.open(row)
}

function openHistory(row: SalaryProfileVO) {
  void historyRef.value?.open(row)
}
</script>

<template>
    <el-form :inline="true" @submit.prevent>
      <el-form-item label="姓名/工号">
        <el-input v-model="profileQuery.keyword" clearable style="width: 160px" @keyup.enter="loadProfiles" />
      </el-form-item>
      <el-form-item label="部门">
        <el-select v-model="profileQuery.deptId" clearable placeholder="全部" style="width: 170px">
          <el-option v-for="d in deptOptions" :key="d.value" :label="d.label" :value="d.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="profileQuery.pageNo = 1; loadProfiles()">查询</el-button>
        <el-button v-permission="'hr:salary:profile'" type="primary" plain @click="openProfileForm()">
          新增档案
        </el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="profileLoading" :data="profiles" border class="salary__profile-table">
      <el-table-column prop="workNumber" label="工号" width="110" />
      <el-table-column prop="realName" label="姓名" width="95" />
      <el-table-column prop="deptName" label="部门" min-width="120" show-overflow-tooltip />
      <el-table-column prop="effectiveDate" label="生效日期" width="120" />
      <el-table-column label="基本工资" width="110" align="right">
        <template #default="{ row }">{{ money(row.baseSalary) }}</template>
      </el-table-column>
      <el-table-column label="岗位工资" width="110" align="right">
        <template #default="{ row }">{{ money(row.postSalary) }}</template>
      </el-table-column>
      <el-table-column label="绩效基数" width="110" align="right">
        <template #default="{ row }">{{ money(row.perfSalary) }}</template>
      </el-table-column>
      <el-table-column label="津贴" width="100" align="right">
        <template #default="{ row }">{{ money(row.allowance) }}</template>
      </el-table-column>
      <el-table-column label="月标准" width="115" align="right">
        <template #default="{ row }"><b>{{ money(row.standardSalary) }}</b></template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button v-permission="'hr:salary:profile'" link type="primary" @click="openProfileForm(row)">调薪</el-button>
          <el-button link type="primary" @click="openHistory(row)">历史</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      class="salary__pager"
      :current-page="profileQuery.pageNo"
      :page-size="profileQuery.pageSize"
      :total="profileTotal"
      layout="total, prev, pager, next"
      background
      @current-change="(p: number) => { profileQuery.pageNo = p; loadProfiles() }"
    />


  <ProfileFormDialog ref="profileFormRef" @saved="loadProfiles" />
  <HistoryDialog ref="historyRef" />
</template>
