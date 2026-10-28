import { ref } from 'vue'
import { getStatisticsApi, type TrainStatisticVO } from '@/api/training'
import { logAsyncError } from '@/utils/asyncGuard'

/**
 * 「培训统计」tab 的汇总数据（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>年度用字符串放：输入框留空表示「今年」，后端按 null 处理。</p>
 */
export function useTrainStat() {
  const year = ref('')
  const loading = ref(false)
  const stat = ref<TrainStatisticVO | null>(null)

  async function load() {
    loading.value = true
    try {
      stat.value = await getStatisticsApi(year.value || undefined)
    } catch (error) {
      logAsyncError('loadStatistics', error)
    } finally {
      loading.value = false
    }
  }

  return { year, loading, stat, load }
}
