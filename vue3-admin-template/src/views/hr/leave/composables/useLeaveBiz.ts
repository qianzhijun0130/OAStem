import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { cancelHrRequestApi, getHrRequestPageApi } from '@/api/hr'
import { logAsyncError } from '@/utils/asyncGuard'
import type { HrRequestVO } from '@/api/types'

/**
 * 加班 / 出差 / 补卡三类通用申请的列表（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>原实现用 `Record<number, BizState>` 把三个 tab 的状态塞在一个对象里，
 * 模板里到处写 `bizState[tab.type].query.month`。拆成 tab 组件后每个实例只服务一个
 * `bizType`，这里改成 `useLeaveBiz(type)`——状态天然隔离，模板也不需要下标。</p>
 */
export function useLeaveBiz(bizType: number) {
  const query = reactive({ month: '', status: undefined as number | undefined, keyword: '', pageNo: 1, pageSize: 10 })
  const list = ref<HrRequestVO[]>([])
  const total = ref(0)
  const loading = ref(false)

  async function load() {
    loading.value = true
    try {
      const res = await getHrRequestPageApi({ bizType, ...query })
      list.value = res?.records || []
      total.value = res?.total || 0
    } catch (error) {
      logAsyncError('loadHrRequest', error)
    } finally {
      loading.value = false
    }
  }

  async function cancel(row: HrRequestVO) {
    try {
      await ElMessageBox.confirm(`确定撤销「${row.billCode || row.id}」吗？`, '撤销申请', {
        type: 'warning',
        confirmButtonText: '确定撤销',
        cancelButtonText: '取消',
      })
    } catch {
      return
    }
    try {
      await cancelHrRequestApi(row.bizType, row.id)
      ElMessage.success('已撤销')
      await load()
    } catch (error) {
      logAsyncError('cancelHrRequest', error)
    }
  }

  return { query, list, total, loading, load, cancel }
}
