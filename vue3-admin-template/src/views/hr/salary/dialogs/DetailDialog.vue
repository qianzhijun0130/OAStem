<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { adjustSalaryApi, getSalaryRecordDetailApi } from '@/api/hr'
import { logAsyncError } from '@/utils/asyncGuard'
import type { SalaryPayslipVO, SalaryRecordVO } from '@/api/types'
import { money } from '../display'

/**
 * 薪资明细 / 手工调整弹窗（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>自持状态：明细、调整草稿、可调整判定都在这里，列表侧只需 `open(row)`；
 * 调整成功后 emit `saved` 让列表自己重载——弹窗不反向调用列表的加载函数。</p>
 */
const emit = defineEmits<{ saved: [] }>()

const visible = ref(false)
const detail = ref<SalaryPayslipVO | null>(null)
const adjustForm = reactive({ otherIncome: 0, otherDeduct: 0, remark: '' })
/** 已封账(3)/已发放(4) 不允许再调整 */
const canAdjust = computed(() => (detail.value?.status ?? 0) < 3)

async function open(row: SalaryRecordVO) {
  try {
    const data = await getSalaryRecordDetailApi(row.id)
    detail.value = data
    adjustForm.otherIncome = Number(data.otherIncome || 0)
    adjustForm.otherDeduct = Number(data.otherDeduct || 0)
    adjustForm.remark = ''
    visible.value = true
  } catch (error) {
    logAsyncError('loadSalaryDetail', error)
  }
}

async function submitAdjust() {
  if (!detail.value) {
    return
  }
  if (!adjustForm.remark.trim()) {
    ElMessage.warning('手工调整必须填写原因（审计要求）')
    return
  }
  try {
    await adjustSalaryApi(detail.value.id, {
      otherIncome: adjustForm.otherIncome,
      otherDeduct: adjustForm.otherDeduct,
      remark: adjustForm.remark.trim(),
    })
    ElMessage.success('已调整，应发/个税/实发已同步重算')
    visible.value = false
    emit('saved')
  } catch (error) {
    logAsyncError('adjustSalary', error)
  }
}

defineExpose({ open })
</script>

<template>
  <el-dialog v-model="visible" title="薪资明细" width="760px">
    <template v-if="detail">
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="姓名">{{ detail.realName }}</el-descriptions-item>
        <el-descriptions-item label="工号">{{ detail.workNumber }}</el-descriptions-item>
        <el-descriptions-item label="月份">{{ detail.month }}</el-descriptions-item>
        <el-descriptions-item label="应发">{{ money(detail.grossPay) }}</el-descriptions-item>
        <el-descriptions-item label="实发">
          <b class="salary__net">{{ money(detail.netPay) }}</b>
        </el-descriptions-item>
        <el-descriptions-item label="社保基数">{{ money(detail.socialBase) }}</el-descriptions-item>
        <el-descriptions-item label="累计应纳税所得额">{{ money(detail.cumulativeTaxable) }}</el-descriptions-item>
        <el-descriptions-item label="单位成本">
          {{ money((detail.socialCompany || 0) + (detail.fundCompany || 0)) }}
        </el-descriptions-item>
        <el-descriptions-item label="状态">{{ detail.statusLabel }}</el-descriptions-item>
      </el-descriptions>

      <el-table :data="detail.items" border size="small" class="salary__items">
        <el-table-column prop="itemName" label="项目" min-width="140" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag :type="row.itemType === 1 ? 'success' : 'danger'" effect="plain" size="small">
              {{ row.itemType === 1 ? '收入' : '扣款' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="130" align="right">
          <template #default="{ row }">{{ money(row.amount) }}</template>
        </el-table-column>
      </el-table>

      <el-divider content-position="left">手工调整</el-divider>
      <el-alert
        v-if="!canAdjust"
        type="warning"
        :closable="false"
        show-icon
        title="已封账/已发放，不能再调整"
      />
      <el-form v-else :inline="true" label-width="90px">
        <el-form-item label="其他收入">
          <el-input-number v-model="adjustForm.otherIncome" :min="0" :precision="2" :step="100" style="width: 150px" />
        </el-form-item>
        <el-form-item label="其他扣款">
          <el-input-number v-model="adjustForm.otherDeduct" :min="0" :precision="2" :step="100" style="width: 150px" />
        </el-form-item>
        <el-form-item label="调整原因">
          <el-input v-model="adjustForm.remark" placeholder="必填，会写入调整留痕" style="width: 260px" />
        </el-form-item>
      </el-form>
    </template>
    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
      <el-button v-if="canAdjust" type="primary" @click="submitAdjust">保存调整</el-button>
    </template>
  </el-dialog>
</template>
