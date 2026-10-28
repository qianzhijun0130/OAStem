import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { exportPerfResultApi, getPerfSheetPageApi } from '@/api/perf'
import { logAsyncError } from '@/utils/asyncGuard'
import type { PerfSheetVO } from '@/api/types'

/**
 * 「考核表」列表与导出（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>`load(fallbackCycleId)`：页签自身的「周期」筛选为空时，回落到页面当前关注的周期
 * （与改造前 `sheetQuery.cycleId || cycleId.value` 一致）。回落值由页面传入，
 * 因为周期看板是三个页签共享的、不归本页签所有。</p>
 */
export function usePerfSheet() {
  const query = reactive({
    cycleId: undefined as number | undefined,
    stage: 'all',
    keyword: '',
    pageNo: 1,
    pageSize: 10,
  })
  const list = ref<PerfSheetVO[]>([])
  const total = ref(0)
  const loading = ref(false)
  const exporting = ref(false)

  async function load(fallbackCycleId?: number) {
    loading.value = true
    try {
      const page = await getPerfSheetPageApi({ ...query, cycleId: query.cycleId || fallbackCycleId })
      list.value = page.records || []
      total.value = Number(page.total || 0)
    } catch (error) {
      logAsyncError('loadPerfSheets', error)
    } finally {
      loading.value = false
    }
  }

  /** 导出：下载与文件名解析收敛在 exportPerfResultApi → downloadFile，页面不接触 Blob */
  async function exportResult(cycleId?: number) {
    if (!cycleId) {
      return
    }
    exporting.value = true
    try {
      await exportPerfResultApi(cycleId)
      ElMessage.success('导出完成')
    } catch (error) {
      logAsyncError('exportPerfResult', error)
    } finally {
      exporting.value = false
    }
  }

  return { query, list, total, loading, exporting, load, exportResult }
}
