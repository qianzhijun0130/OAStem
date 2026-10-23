<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getMyPayslipsApi } from '@/api/hr'
import { logAsyncError } from '@/utils/asyncGuard'
import type { SalaryPayslipVO } from '@/api/types'

const year = ref(String(new Date().getFullYear()))
const loading = ref(false)
const slips = ref<SalaryPayslipVO[]>([])
const active = ref<SalaryPayslipVO | null>(null)

const current = computed(() => active.value || slips.value[0] || null)

async function load() {
  loading.value = true
  try {
    slips.value = (await getMyPayslipsApi(Number(year.value))) || []
    active.value = slips.value[0] || null
  } catch (error) {
    logAsyncError('loadMyPayslips', error)
  } finally {
    loading.value = false
  }
}

const money = (value?: number) => (value == null ? '-' : `¥${Number(value).toFixed(2)}`)

/** 收入项与扣款项分开展示：工资条的核心是「为什么少了这么多」，混在一起看不出来 */
const incomes = computed(() => (current.value?.items || []).filter((i) => i.itemType === 1))
const deducts = computed(() => (current.value?.items || []).filter((i) => i.itemType === 2))

onMounted(load)
</script>

<template>
  <div class="payslip" v-loading="loading">
    <el-empty v-if="!loading && !slips.length" description="当前年度还没有可查看的工资条（复核后才会出现）" />

    <template v-else>
      <el-card shadow="never" class="payslip__years">
        <div class="payslip__bar">
          <el-date-picker v-model="year" type="year" value-format="YYYY" style="width: 120px" @change="load" />
          <span class="payslip__hint">工资条在 HR 复核后才会显示；草稿状态不对外可见</span>
        </div>
        <div class="payslip__months">
          <button
            v-for="item in slips"
            :key="item.id"
            class="payslip__month"
            :class="{ 'is-active': current?.id === item.id }"
            @click="active = item"
          >
            <span class="payslip__month-label">{{ item.month.slice(5) }} 月</span>
            <span class="payslip__month-net">{{ money(item.netPay) }}</span>
          </button>
        </div>
      </el-card>

      <el-card v-if="current" shadow="never" class="payslip__detail">
        <div class="payslip__head">
          <div>
            <div class="payslip__title">{{ current.month }} 工资条</div>
            <div class="payslip__sub">
              {{ current.realName }} · {{ current.workNumber }} · {{ current.deptName || '-' }}
            </div>
          </div>
          <div class="payslip__net">
            <div class="payslip__net-label">实发合计</div>
            <div class="payslip__net-value">{{ money(current.netPay) }}</div>
          </div>
        </div>

        <el-row :gutter="16" class="payslip__cols">
          <el-col :span="12">
            <div class="payslip__section-title">收入项</div>
            <div v-for="item in incomes" :key="item.itemCode" class="payslip__row">
              <span>{{ item.itemName }}</span>
              <span class="payslip__amount is-income">{{ money(item.amount) }}</span>
            </div>
            <div class="payslip__row is-total">
              <span>应发合计</span>
              <span class="payslip__amount">{{ money(current.grossPay) }}</span>
            </div>
          </el-col>
          <el-col :span="12">
            <div class="payslip__section-title">扣款项</div>
            <div v-for="item in deducts" :key="item.itemCode" class="payslip__row">
              <span>{{ item.itemName }}</span>
              <span class="payslip__amount is-deduct">{{ money(item.amount) }}</span>
            </div>
            <div class="payslip__row is-total">
              <span>扣款合计</span>
              <span class="payslip__amount">
                {{ money((current.attendanceDeduct || 0) + (current.socialPersonal || 0)
                  + (current.fundPersonal || 0) + (current.tax || 0) + (current.otherDeduct || 0)) }}
              </span>
            </div>
          </el-col>
        </el-row>

        <el-divider />
        <div class="payslip__meta">
          <span>社保基数：{{ money(current.socialBase) }}</span>
          <span>专项附加扣除：{{ money(current.specialDeduct) }}</span>
          <span>累计应纳税所得额：{{ money(Math.max(0, current.cumulativeTaxable || 0)) }}</span>
          <span>单位缴纳：{{ money((current.socialCompany || 0) + (current.fundCompany || 0)) }}</span>
          <span>状态：{{ current.statusLabel }}</span>
        </div>
        <el-alert
          class="payslip__tip"
          type="info"
          :closable="false"
          show-icon
          title="个税按累计预扣法计算：年中跳档时当月税额会变化，属正常现象，年度汇算时统一结算。"
        />
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.payslip {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.payslip__bar {
  display: flex;
  align-items: center;
  gap: 12px;
}

.payslip__hint {
  font-size: 12px;
  color: #909399;
}

.payslip__months {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.payslip__month {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 92px;
  padding: 8px 12px;
  background: #f5f7fa;
  border: 1px solid transparent;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;
}

.payslip__month:hover {
  background: #eef4ff;
}

.payslip__month.is-active {
  background: #ecf5ff;
  border-color: #409eff;
}

.payslip__month-label {
  font-size: 12px;
  color: #8a94a5;
}

.payslip__month-net {
  font-size: 14px;
  font-weight: 600;
  color: #1f2a37;
}

.payslip__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding-bottom: 12px;
  border-bottom: 1px solid #eef1f6;
}

.payslip__title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2a37;
}

.payslip__sub {
  margin-top: 4px;
  font-size: 12px;
  color: #8a94a5;
}

.payslip__net {
  text-align: right;
}

.payslip__net-label {
  font-size: 12px;
  color: #8a94a5;
}

.payslip__net-value {
  font-size: 22px;
  font-weight: 700;
  color: #f56c6c;
}

.payslip__cols {
  margin-top: 16px;
}

.payslip__section-title {
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
  color: #5a6577;
}

.payslip__row {
  display: flex;
  justify-content: space-between;
  padding: 6px 0;
  font-size: 13px;
  color: #4b5563;
}

.payslip__row.is-total {
  margin-top: 4px;
  padding-top: 8px;
  font-weight: 600;
  border-top: 1px dashed #e4e7ed;
}

.payslip__amount.is-income {
  color: #67c23a;
}

.payslip__amount.is-deduct {
  color: #f56c6c;
}

.payslip__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 24px;
  font-size: 12px;
  color: #8a94a5;
}

.payslip__tip {
  margin-top: 12px;
}
</style>
