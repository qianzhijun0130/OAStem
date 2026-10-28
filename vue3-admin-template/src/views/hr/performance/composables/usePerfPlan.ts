import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deletePerfPlanApi, getPerfPlanPageApi } from '@/api/perf'
import { logAsyncError } from '@/utils/asyncGuard'
import type { PerfPlanVO } from '@/api/types'

/** 「绩效方案」列表与删除（FE-OPT-06 从 index.vue 抽出） */
export function usePerfPlan() {
  const query = reactive({ keyword: '', pageNo: 1, pageSize: 10 })
  const list = ref<PerfPlanVO[]>([])
  const total = ref(0)
  const loading = ref(false)

  async function load() {
    loading.value = true
    try {
      const page = await getPerfPlanPageApi({ ...query })
      list.value = page.records || []
      total.value = Number(page.total || 0)
    } catch (error) {
      logAsyncError('loadPerfPlans', error)
    } finally {
      loading.value = false
    }
  }

  async function remove(row: PerfPlanVO) {
    try {
      await ElMessageBox.confirm(`确定删除方案「${row.planName}」吗？`, '删除方案', {
        type: 'warning',
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
      })
    } catch {
      return
    }
    try {
      await deletePerfPlanApi(row.id)
      ElMessage.success('已删除')
      await load()
    } catch (error) {
      logAsyncError('deletePerfPlan', error)
    }
  }

  return { query, list, total, loading, load, remove }
}
