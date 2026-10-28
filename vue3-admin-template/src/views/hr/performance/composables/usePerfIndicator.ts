import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deletePerfIndicatorApi, getPerfIndicatorPageApi } from '@/api/perf'
import { logAsyncError } from '@/utils/asyncGuard'
import type { PerfIndicatorVO } from '@/api/types'

/** 「指标库」列表与删除（FE-OPT-06 从 index.vue 抽出） */
export function usePerfIndicator() {
  const query = reactive({ keyword: '', type: undefined as number | undefined, pageNo: 1, pageSize: 10 })
  const list = ref<PerfIndicatorVO[]>([])
  const total = ref(0)
  const loading = ref(false)

  async function load() {
    loading.value = true
    try {
      const page = await getPerfIndicatorPageApi({ ...query })
      list.value = page.records || []
      total.value = Number(page.total || 0)
    } catch (error) {
      logAsyncError('loadPerfIndicators', error)
    } finally {
      loading.value = false
    }
  }

  async function remove(row: PerfIndicatorVO) {
    try {
      await ElMessageBox.confirm(`确定删除指标「${row.indicatorName}」吗？`, '删除指标', {
        type: 'warning',
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
      })
    } catch {
      return
    }
    try {
      await deletePerfIndicatorApi(row.id)
      ElMessage.success('已删除')
      await load()
    } catch (error) {
      logAsyncError('deletePerfIndicator', error)
    }
  }

  return { query, list, total, loading, load, remove }
}
