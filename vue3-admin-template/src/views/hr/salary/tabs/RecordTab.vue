<script setup lang="ts">
import { onMounted, ref } from 'vue'
import type { SalaryRecordVO } from '@/api/types'
import { money, prorationText, statusMeta } from '../display'
import DetailDialog from '../dialogs/DetailDialog.vue'
import { useSalaryRecord } from '../composables/useSalaryRecord'

/**
 * 「月度核算」标签页（FE-OPT-06 拆分自 index.vue）。
 *
 * <p>自包含：列表状态、核算/复核/封账/发放、以及明细弹窗都在本组件内。
 * 页面只传部门下拉（列表筛选与档案页共用同一份树，避免重复请求）。</p>
 */
defineProps<{
  /** 部门树扁平选项：由页面统一加载，两个 tab 共用 */
  deptOptions: { value: number; label: string }[]
}>()

const {
  loadRecords,
  searchRecords,
  runCalc,
  transit,
  recordQuery,
  records,
  recordTotal,
  recordLoading,
  calcLoading,
} = useSalaryRecord()

// 列表数据由本组件自己加载：页面不再持有列表状态，也就不该由页面代为触发
onMounted(loadRecords)

const detailRef = ref<InstanceType<typeof DetailDialog> | null>(null)

function openDetail(row: SalaryRecordVO) {
  void detailRef.value?.open(row)
}
</script>

<template>
    <el-form :inline="true" @submit.prevent>
      <el-form-item label="月份">
        <el-date-picker v-model="recordQuery.month" type="month" value-format="YYYY-MM" style="width: 140px" />
      </el-form-item>
      <el-form-item label="部门">
        <el-select v-model="recordQuery.deptId" clearable placeholder="全部" style="width: 170px">
          <el-option v-for="d in deptOptions" :key="d.value" :label="d.label" :value="d.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="姓名/工号">
        <el-input v-model="recordQuery.keyword" clearable style="width: 150px" @keyup.enter="searchRecords" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="searchRecords">查询</el-button>
        <el-button v-permission="'hr:salary:calc'" type="primary" plain :loading="calcLoading" @click="runCalc">
          核算
        </el-button>
        <el-button v-permission="'hr:salary:review'" @click="transit('review')">复核</el-button>
        <el-button v-permission="'hr:salary:lock'" @click="transit('lock')">封账</el-button>
        <el-button v-permission="'hr:salary:pay'" @click="transit('pay')">发放</el-button>
      </el-form-item>
    </el-form>

    <el-alert
      class="salary__tip"
      type="info"
      :closable="false"
      show-icon
      title="核算前置：该月考勤月结已封账。顺序为 草稿 → 复核 → 封账 → 发放，复核后本人即可在「我的工资条」查看。"
    />

    <el-table v-loading="recordLoading" :data="records" border class="salary__record-table">
      <el-table-column prop="month" label="月份" width="95" />
      <el-table-column prop="workNumber" label="工号" width="105" />
      <el-table-column label="姓名" width="150">
        <template #default="{ row }">
          <span>{{ row.realName }}</span>
          <el-tooltip v-if="row.priorEmployerIncome" :content="row.priorEmployerTip" placement="top">
            <el-tag size="small" type="warning" effect="plain" class="salary__prior">跨单位</el-tag>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column prop="deptName" label="部门" min-width="120" show-overflow-tooltip />
      <el-table-column label="折算系数" width="110" align="center">
        <template #default="{ row }">
          <el-tooltip
            v-if="row.prorationFactor != null && row.prorationFactor < 1"
            content="入职或离职当月按实际在职应出勤天数折算"
            placement="top"
          >
            <span class="salary__proration">{{ prorationText(row.prorationFactor) }}</span>
          </el-tooltip>
          <span v-else class="salary__muted">{{ prorationText(row.prorationFactor) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="折算后标准工资" width="140" align="right">
        <template #default="{ row }">{{ money(row.proratedStandard) }}</template>
      </el-table-column>
      <el-table-column label="应发" width="120" align="right">
        <template #default="{ row }">{{ money(row.grossPay) }}</template>
      </el-table-column>
      <el-table-column label="考勤扣款" width="115" align="right">
        <template #default="{ row }">{{ money(row.attendanceDeduct) }}</template>
      </el-table-column>
      <el-table-column label="社保+公积金" width="130" align="right">
        <template #default="{ row }">{{ money((row.socialPersonal || 0) + (row.fundPersonal || 0)) }}</template>
      </el-table-column>
      <el-table-column label="个税" width="110" align="right">
        <template #default="{ row }">{{ money(row.tax) }}</template>
      </el-table-column>
      <el-table-column label="实发" width="120" align="right">
        <template #default="{ row }">
          <b class="salary__net">{{ money(row.netPay) }}</b>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="95">
        <template #default="{ row }">
          <el-tag :type="statusMeta(row.status).type" effect="plain">{{ row.statusLabel }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">明细</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      class="salary__pager"
      :current-page="recordQuery.pageNo"
      :page-size="recordQuery.pageSize"
      :total="recordTotal"
      layout="total, prev, pager, next"
      background
      @current-change="(p: number) => { recordQuery.pageNo = p; loadRecords() }"
    />


  <!-- 明细弹窗：调整成功后重载本页列表 -->
  <DetailDialog ref="detailRef" @saved="loadRecords" />
</template>
