<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useLeaveQuota } from '../composables/useLeaveQuota'
import AdjustQuotaDialog from '../dialogs/AdjustQuotaDialog.vue'

/**
 * 「我的额度」标签页（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>自包含：额度列表、年度筛选、调整弹窗都在本组件内；对外只暴露 `refresh()`，
 * 供请假 tab 提交/撤销后回补额度（请假会立即占用或释放额度）。</p>
 */
const { quotas, loading, year, load } = useLeaveQuota()

// 列表由本组件自己加载：tab 是懒渲染的，首次切到这里时才会触发
onMounted(load)

const adjustRef = ref<InstanceType<typeof AdjustQuotaDialog> | null>(null)

function openAdjust() {
  adjustRef.value?.open()
}

defineExpose({ refresh: load })
</script>

<template>
  <div class="hr-leave__bar">
    <el-date-picker v-model="year" type="year" value-format="YYYY" :clearable="false" style="width: 120px" @change="load" />
    <el-button v-permission="'hr:leave:quota'" data-action="adjust" @click="openAdjust">调整额度</el-button>
    <span class="hr-leave__hint">年假按累计工龄计算（1~10 年 5 天 / 10~20 年 10 天 / 20 年以上 15 天），调休由加班累积</span>
  </div>
  <el-table v-loading="loading" :data="quotas" border>
    <el-table-column prop="leaveTypeLabel" label="假别" width="100" />
    <el-table-column prop="totalDays" label="总额度" width="100" />
    <el-table-column prop="carryOverDays" label="结转" width="90" />
    <el-table-column prop="usedDays" label="已使用" width="100" />
    <el-table-column prop="lockedDays" label="审批中占用" width="120" />
    <el-table-column label="可用" width="100">
      <template #default="{ row }">
        <b :class="{ 'hr-leave__danger': row.availableDays <= 0 }">{{ row.availableDays }}</b>
      </template>
    </el-table-column>
    <el-table-column prop="expireDate" label="结转失效日" width="130" />
    <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
  </el-table>

  <AdjustQuotaDialog ref="adjustRef" :year="Number(year)" @saved="load" />
</template>
