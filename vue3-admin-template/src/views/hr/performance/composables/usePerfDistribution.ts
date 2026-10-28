import { reactive } from 'vue'
import { getPerfDistributionApi } from '@/api/perf'
import { logAsyncError } from '@/utils/asyncGuard'
import type { PerfDistributionVO } from '@/api/types'

/**
 * 「等级分布」统计（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>按周期统计，周期为空时什么都不做（等同改造前 `if (!cycleId) return`）。</p>
 */
export function usePerfDistribution() {
  const state = reactive({
    list: [] as PerfDistributionVO[],
    loading: false,
  })

  async function load(cycleId?: number) {
    if (!cycleId) {
      return
    }
    state.loading = true
    try {
      state.list = (await getPerfDistributionApi(cycleId)) || []
    } catch (error) {
      logAsyncError('loadPerfDistribution', error)
    } finally {
      state.loading = false
    }
  }

  return { state, load }
}

/** 分布数据容器类型：页签内自持，页面通过 ref 调用 refresh */
export type PerfDistribution = ReturnType<typeof usePerfDistribution>
