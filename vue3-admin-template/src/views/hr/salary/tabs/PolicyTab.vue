<script setup lang="ts">
import { onMounted, ref } from 'vue'
import type { SocialPolicyVO } from '@/api/types'
import PolicyFormDialog from '../dialogs/PolicyFormDialog.vue'
import { useSocialPolicy } from '../composables/useSocialPolicy'

/**
 * 「社保政策」标签页（FE-OPT-06 拆分自 index.vue）。
 *
 * <p>自包含：政策列表 + 新增/编辑弹窗 + 删除。政策是核算输入，
 * 改动后核算结果会变，因此保存/删除都重载列表。</p>
 */
const { loadPolicies, removePolicy, policies, policyLoading } = useSocialPolicy()

// 列表数据由本组件自己加载（拆分前由页面 onMounted 统一触发）
onMounted(loadPolicies)

const policyFormRef = ref<InstanceType<typeof PolicyFormDialog> | null>(null)

function openPolicyForm(row?: SocialPolicyVO) {
  void policyFormRef.value?.open(row)
}
</script>

<template>
    <div class="salary__toolbar">
      <el-button v-permission="'hr:salary:profile'" type="primary" plain @click="openPolicyForm()">
        新增政策
      </el-button>
      <span class="salary__hint">基数为 0 表示不设限；比例用小数（0.08 = 8%）</span>
    </div>
    <el-table v-loading="policyLoading" :data="policies" border class="salary__policy-table">
      <el-table-column prop="city" label="城市" width="110" />
      <el-table-column prop="policyYear" label="年度" width="90" />
      <el-table-column label="社保基数区间" width="190">
        <template #default="{ row }">{{ row.baseMin }} ~ {{ row.baseMax || '不封顶' }}</template>
      </el-table-column>
      <el-table-column label="公积金基数区间" width="200">
        <template #default="{ row }">{{ row.fundBaseMin }} ~ {{ row.fundBaseMax || '不封顶' }}</template>
      </el-table-column>
      <el-table-column label="个人比例" width="110">
        <template #default="{ row }">{{ (Number(row.personalRateTotal) * 100).toFixed(2) }}%</template>
      </el-table-column>
      <el-table-column label="单位比例" min-width="110">
        <template #default="{ row }">
          {{ (((Number(row.pensionCompany) + Number(row.medicalCompany) + Number(row.unemploymentCompany)
            + Number(row.injuryCompany) + Number(row.maternityCompany) + Number(row.fundCompany)) * 100)).toFixed(2) }}%
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button v-permission="'hr:salary:profile'" link type="primary" @click="openPolicyForm(row)">编辑</el-button>
          <el-button v-permission="'hr:salary:profile'" link type="danger" @click="removePolicy(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>


  <PolicyFormDialog ref="policyFormRef" @saved="loadPolicies" />
</template>
