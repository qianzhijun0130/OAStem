import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  calcSalaryApi,
  getSalaryRecordPageApi,
  lockSalaryApi,
  paySalaryApi,
  reviewSalaryApi,
} from '@/api/hr'
import { logAsyncError } from '@/utils/asyncGuard'
import type {
  SalaryRecordVO,
} from '@/api/types'

import { lastMonth } from '../period'

/**
 * 「月度核算」与「薪资明细/手工调整」（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>明细与列表放一起：明细的唯一入口是列表某一行，调整提交后必须重载列表，
 * 两者共用 recordQuery/records，拆开反而要互相传引用。</p>
 */
export function useSalaryRecord() {

const recordQuery = reactive({
  month: lastMonth(),
  deptId: undefined as number | undefined,
  status: undefined as number | undefined,
  keyword: '',
  pageNo: 1,
  pageSize: 10,
})
const records = ref<SalaryRecordVO[]>([])
const recordTotal = ref(0)
const recordLoading = ref(false)
const calcLoading = ref(false)

async function loadRecords() {
  recordLoading.value = true
  try {
    const page = await getSalaryRecordPageApi({ ...recordQuery })
    records.value = page.records || []
    recordTotal.value = Number(page.total || 0)
  } catch (error) {
    logAsyncError('loadSalaryRecords', error)
  } finally {
    recordLoading.value = false
  }
}

function searchRecords() {
  recordQuery.pageNo = 1
  loadRecords()
}

/**
 * 一键核算。
 *
 * <p>不静默执行：它会覆盖该月所有「草稿」记录，先让用户确认月份与范围，
 * 避免手滑点了把别的事业部的草稿冲掉。</p>
 */
async function runCalc() {
  const scope = recordQuery.deptId ? '所选部门（含下级）' : '全部可见范围'
  try {
    await ElMessageBox.confirm(
      `将按「薪资档案 + 已封账的考勤月结」重算 ${recordQuery.month} 的薪资（${scope}）。已复核及之后的记录不会被覆盖。`,
      '核算薪资',
      { type: 'warning', confirmButtonText: '开始核算', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  calcLoading.value = true
  try {
    const count = await calcSalaryApi({ month: recordQuery.month, deptId: recordQuery.deptId })
    ElMessage.success(`核算完成，处理 ${count} 人`)
    await loadRecords()
  } catch (error) {
    logAsyncError('calcSalary', error)
  } finally {
    calcLoading.value = false
  }
}

async function transit(action: 'review' | 'lock' | 'pay') {
  const meta = {
    review: { title: '复核薪资', text: '复核后本人即可在「我的工资条」看到该月数据，且不能再自动重算。', ok: '确定复核' },
    lock: { title: '薪资封账', text: '封账后该月薪资不可再调整，只能往后走发放。', ok: '确定封账' },
    pay: { title: '标记发放', text: '确认银行已完成发放？标记后该月流程结束。', ok: '确定发放' },
  }[action]
  try {
    await ElMessageBox.confirm(`${meta.text}（月份：${recordQuery.month}）`, meta.title, {
      type: 'warning',
      confirmButtonText: meta.ok,
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    const params = { month: recordQuery.month, deptId: recordQuery.deptId }
    const count = action === 'review'
      ? await reviewSalaryApi(params)
      : action === 'lock'
        ? await lockSalaryApi(params)
        : await paySalaryApi(params)
    ElMessage.success(`${meta.title}完成，${count} 条`)
    await loadRecords()
  } catch (error) {
    logAsyncError('salaryTransit', error)
  }
}

  return {
    loadRecords,
    searchRecords,
    runCalc,
    transit,
    recordQuery,
    records,
    recordTotal,
    recordLoading,
    calcLoading,
  }
}
