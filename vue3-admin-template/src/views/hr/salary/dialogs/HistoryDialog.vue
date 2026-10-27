<script setup lang="ts">
import { ref } from 'vue'
import { getSalaryProfileHistoryApi } from '@/api/hr'
import { logAsyncError } from '@/utils/asyncGuard'
import type { SalaryProfileVO } from '@/api/types'
import { money } from '../display'

/**
 * 薪资档案历史弹窗（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>只读：按用户拉全部档案段。之所以独立成组件，是因为它的打开参数是「员工」，
 * 而不是列表行本身——从别处（如员工档案页）也能直接复用。</p>
 */
const visible = ref(false)
const history = ref<SalaryProfileVO[]>([])
const loading = ref(false)

async function open(row: SalaryProfileVO) {
  loading.value = true
  try {
    history.value = await getSalaryProfileHistoryApi(row.userId)
    visible.value = true
  } catch (error) {
    logAsyncError('loadSalaryProfileHistory', error)
  } finally {
    loading.value = false
  }
}

defineExpose({ open })
</script>

<template>
  <el-dialog v-model="visible" title="薪资档案历史" width="760px">
    <el-table v-loading="loading" :data="history" border size="small">
      <el-table-column prop="effectiveDate" label="生效日期" width="120" />
      <el-table-column label="基本" width="100" align="right">
        <template #default="{ row }">{{ money(row.baseSalary) }}</template>
      </el-table-column>
      <el-table-column label="岗位" width="100" align="right">
        <template #default="{ row }">{{ money(row.postSalary) }}</template>
      </el-table-column>
      <el-table-column label="绩效" width="100" align="right">
        <template #default="{ row }">{{ money(row.perfSalary) }}</template>
      </el-table-column>
      <el-table-column label="津贴" width="100" align="right">
        <template #default="{ row }">{{ money(row.allowance) }}</template>
      </el-table-column>
      <el-table-column label="月标准" width="115" align="right">
        <template #default="{ row }"><b>{{ money(row.standardSalary) }}</b></template>
      </el-table-column>
      <el-table-column prop="changeReason" label="原因" min-width="150" show-overflow-tooltip />
    </el-table>
  </el-dialog>
</template>
