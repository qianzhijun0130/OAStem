import { reactive } from 'vue'
import { listPerfIndicatorOptionsApi, listPerfPlanOptionsApi } from '@/api/perf'
import { logAsyncError } from '@/utils/asyncGuard'
import type { PerfIndicatorVO, PerfPlanVO } from '@/api/types'

/**
 * 两串「下拉选项」（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>原实现在页面 onMounted 里就取了两串选项，但它们各自的唯一消费者是弹窗：
 * 方案下拉只给「新建周期」用，指标下拉只给「绩效方案」用。拆 tab 后改成
 * <b>谁打开弹窗谁触发</b>（首次取过就缓存），首屏因此少两条请求。</p>
 *
 * <p>保存在 `reactive` 里而不是两个 ref：这两串要作为 props 直接传给弹窗
 * （`PerfPlanVO[]`），若用 ref 包着，模板里 `props.options.planOptions` 拿到的是
 * Ref 对象、不会自动解包，类型与渲染都会错。</p>
 *
 * <p>保存后要 `force` 刷一次：新建的方案要能立刻出现在周期表单里，
 * 新建的指标要能立刻出现在方案表单里——这是页面内两个 tab 之间的隐性依赖。</p>
 */
export function usePerfOptions() {
  const state = reactive({
    planOptions: [] as PerfPlanVO[],
    indicatorOptions: [] as PerfIndicatorVO[],
  })
  let planLoaded = false
  let indicatorLoaded = false

  /** 周期表单的「绩效方案」下拉 */
  async function loadPlanOptions(force = false) {
    if (planLoaded && !force) {
      return
    }
    try {
      state.planOptions = (await listPerfPlanOptionsApi()) || []
      planLoaded = true
    } catch (error) {
      logAsyncError('loadPerfPlanOptions', error)
    }
  }

  /** 方案表单的「指标」下拉 */
  async function loadIndicatorOptions(force = false) {
    if (indicatorLoaded && !force) {
      return
    }
    try {
      state.indicatorOptions = (await listPerfIndicatorOptionsApi()) || []
      indicatorLoaded = true
    } catch (error) {
      logAsyncError('loadPerfIndicatorOptions', error)
    }
  }

  return { state, loadPlanOptions, loadIndicatorOptions }
}

/** 选项容器类型：页面创建后 props 下发给 tab 与弹窗 */
export type PerfOptions = ReturnType<typeof usePerfOptions>
