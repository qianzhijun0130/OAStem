import { reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  advancePerfCycleApi,
  archivePerfCycleApi,
  deletePerfCycleApi,
  generatePerfSheetsApi,
  getPerfCyclePageApi,
} from '@/api/perf'
import { logAsyncError } from '@/utils/asyncGuard'
import type { PerfCycleVO } from '@/api/types'

/**
 * 考核周期看板（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p><b>为什么由页面创建、单实例下发</b>：周期列表被三个页签共用——
 * 「考核表」用它做筛选下拉与默认周期、「考核周期」展示与推进、「等级分布」按周期统计。
 * 若三个 tab 各持一份，推进阶段后另外两个页签看到的还是旧状态。</p>
 *
 * <p>状态用 `reactive`：子组件 `v-model="board.state.cycleId"` 能直接写回。</p>
 */
export function usePerfCycleBoard() {
  const state = reactive({
    list: [] as PerfCycleVO[],
    /** 当前「关注」的周期：考核表默认筛选项、等级分布的统计对象 */
    cycleId: undefined as number | undefined,
  })

  let loaded = false
  /** 在途请求：并发调用 ensureLoaded 时共用同一个 Promise，避免重复打接口 */
  let pending: Promise<void> | null = null

  async function load() {
    try {
      const page = await getPerfCyclePageApi({ pageNo: 1, pageSize: 50 })
      state.list = page.records || []
      if (!state.cycleId && state.list.length) {
        state.cycleId = state.list[0].id
      }
      loaded = true
    } catch (error) {
      logAsyncError('loadPerfCycles', error)
    }
  }

  /**
   * 首次加载（幂等）。
   *
   * <p>「考核表」页签的列表要拿当前周期做回落筛选，必须等周期到位再查；
   * 而子组件的 onMounted 早于父组件的 onMounted，所以由需要的人各自 `await` 一次，
   * 谁先到谁触发、后到的人直接复用，全页只发一条周期请求。</p>
   */
  async function ensureLoaded() {
    if (loaded) {
      return
    }
    if (!pending) {
      pending = load().finally(() => {
        pending = null
      })
    }
    await pending
  }

  async function advance(row: PerfCycleVO) {
    const nextLabel = ['', '自评中', '上级评分', '校准', '结果确认', '已归档'][row.stage] || '下一阶段'
    try {
      await ElMessageBox.confirm(
        `将「${row.cycleName}」从「${row.stageLabel}」推进到「${nextLabel}」。推进后本阶段未完成的动作将无法补做，确定继续？`,
        '推进阶段',
        { type: 'warning', confirmButtonText: '确定推进', cancelButtonText: '取消' },
      )
    } catch {
      return false
    }
    try {
      const count = await advancePerfCycleApi(row.id)
      ElMessage.success(`已推进，涉及 ${count} 张考核表`)
      await load()
      return true
    } catch (error) {
      logAsyncError('advancePerfCycle', error)
      return false
    }
  }

  async function archive(row: PerfCycleVO) {
    try {
      await ElMessageBox.confirm(
        `归档后「${row.cycleName}」的结果转为只读，薪酬将引用本期系数。确定归档？`,
        '归档周期',
        { type: 'warning', confirmButtonText: '确定归档', cancelButtonText: '取消' },
      )
    } catch {
      return false
    }
    try {
      const count = await archivePerfCycleApi(row.id)
      ElMessage.success(`已归档，${count} 张考核表定级完成`)
      await load()
      return true
    } catch (error) {
      logAsyncError('archivePerfCycle', error)
      return false
    }
  }

  async function generate(row: PerfCycleVO) {
    try {
      const count = await generatePerfSheetsApi(row.id)
      ElMessage.success(count ? `已补齐 ${count} 张考核表` : '没有需要补齐的考核表')
      await load()
      return true
    } catch (error) {
      logAsyncError('generatePerfSheets', error)
      return false
    }
  }

  async function remove(row: PerfCycleVO) {
    try {
      await ElMessageBox.confirm(`确定删除周期「${row.cycleName}」吗？`, '删除周期', {
        type: 'warning',
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
      })
    } catch {
      return false
    }
    try {
      await deletePerfCycleApi(row.id)
      ElMessage.success('已删除')
      await load()
      return true
    } catch (error) {
      logAsyncError('deletePerfCycle', error)
      return false
    }
  }

  return { state, load, ensureLoaded, advance, archive, generate, remove }
}

/** 周期看板类型：页面创建、三个页签共享 */
export type PerfCycleBoard = ReturnType<typeof usePerfCycleBoard>
