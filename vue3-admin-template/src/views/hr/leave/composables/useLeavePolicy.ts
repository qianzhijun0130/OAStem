import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteLeavePolicyApi, getLeavePolicyListApi } from '@/api/hr'
import { logAsyncError } from '@/utils/asyncGuard'
import { useListQuery } from '@/composables/useListQuery'
import { leaveTypeLabel } from '../constants'
import type { LeavePolicyVO } from '@/api/types'

/** 假别政策的查询条件：后端按「年度 + 城市」返回全量数组，不分页 */
interface LeavePolicyQuery {
  year: string
  city: string
}

/**
 * 「假别政策」tab 的列表与删除（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>`immediate: false`：本 tab 是懒加载的，由组件自己在首次挂载时取数，
 * 不让 composable 里的 onMounted 提前打请求。</p>
 */
export function useLeavePolicy() {
  const {
    list,
    loading,
    query,
    search,
    refresh,
    load,
  } = useListQuery<LeavePolicyVO, LeavePolicyQuery>({
    // 必须返回新对象：reset() 会拿它做干净副本
    initialQuery: () => ({ year: String(new Date().getFullYear()), city: '' }),
    // 后端返回全量数组（不分页），这里适配成统一分页结构
    fetcher: async (params) => {
      const records = await getLeavePolicyListApi({
        year: params.year ? Number(params.year) : undefined,
        city: params.city || undefined,
      })
      return { records: records || [], total: records?.length || 0 }
    },
    immediate: false,
  })

  async function remove(row: LeavePolicyVO) {
    try {
      await ElMessageBox.confirm(
        `确定删除「${row.city} · ${row.leaveTypeLabel || leaveTypeLabel(row.leaveType)}」的政策吗？`,
        '删除政策',
        {
          type: 'warning',
          confirmButtonText: '确定删除',
          cancelButtonText: '取消',
        },
      )
    } catch {
      return
    }
    if (row.id == null) {
      return
    }
    try {
      await deleteLeavePolicyApi(row.id)
      ElMessage.success('已删除')
      await refresh()
    } catch (error) {
      logAsyncError('deleteLeavePolicy', error)
    }
  }

  return { list, loading, query, search, refresh, load, remove }
}
