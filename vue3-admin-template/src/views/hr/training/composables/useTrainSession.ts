import { ElMessage, ElMessageBox } from 'element-plus'
import { cancelSessionApi, getSessionPageApi, type TrainSessionVO } from '@/api/training'
import { logAsyncError } from '@/utils/asyncGuard'
import { useListQuery } from '@/composables/useListQuery'

/**
 * 「培训期次」tab 的列表与取消期次（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>取消期次会让已报名人员受影响，后端有联动，所以确认框文案要写清后果。</p>
 */
export function useTrainSession() {
  const q = useListQuery<TrainSessionVO, { keyword: string; courseId?: number; status?: number }>({
    initialQuery: () => ({ keyword: '', courseId: undefined, status: undefined }),
    fetcher: (p) => getSessionPageApi(p),
    immediate: false,
  })

  async function cancelSession(row: TrainSessionVO) {
    try {
      await ElMessageBox.confirm(`确定取消期次「${row.sessionName}」吗？已报名人员将受到影响。`, '取消期次', {
        type: 'warning',
        confirmButtonText: '取消期次',
        cancelButtonText: '返回',
      })
    } catch {
      return
    }
    try {
      await cancelSessionApi(row.id)
      ElMessage.success('已取消期次')
      await q.refresh()
    } catch (error) {
      logAsyncError('cancelSession', error)
    }
  }

  return { ...q, cancelSession }
}
