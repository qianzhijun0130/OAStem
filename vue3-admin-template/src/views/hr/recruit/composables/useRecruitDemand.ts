import { ElMessage, ElMessageBox } from 'element-plus'
import { approveDemandApi, closeDemandApi, getDemandPageApi } from '@/api/recruit'
import { logAsyncError } from '@/utils/asyncGuard'
import { useListQuery } from '@/composables/useListQuery'
import type { RecruitDemandVO } from '@/api/recruit'

/** 「招聘需求」页签的列表 + 审批 + 关闭（FE-OPT-06 从 index.vue 抽出） */
export function useRecruitDemand() {
  const q = useListQuery<RecruitDemandVO, { keyword: string; status?: number; deptId?: number }>({
    initialQuery: () => ({ keyword: '', status: undefined, deptId: undefined }),
    fetcher: (p) => getDemandPageApi(p),
    immediate: false,
  })

  /** 返回值表示是否真的改了数据，页签据此决定要不要刷共享需求下拉 */
  async function approve(row: RecruitDemandVO): Promise<boolean> {
    try {
      await ElMessageBox.confirm(`确定审批通过需求「${row.demandCode}」吗？`, '审批需求', {
        type: 'warning',
        confirmButtonText: '通过',
        cancelButtonText: '取消',
      })
    } catch {
      return false
    }
    try {
      await approveDemandApi(row.id)
      ElMessage.success('已审批通过')
      await q.refresh()
      return true
    } catch (error) {
      logAsyncError('approveDemand', error)
      return false
    }
  }

  async function close(row: RecruitDemandVO): Promise<boolean> {
    try {
      await ElMessageBox.confirm(`确定关闭需求「${row.demandCode}」吗？`, '关闭需求', {
        type: 'warning',
        confirmButtonText: '关闭',
        cancelButtonText: '取消',
      })
    } catch {
      return false
    }
    try {
      await closeDemandApi(row.id)
      ElMessage.success('已关闭')
      await q.refresh()
      return true
    } catch (error) {
      logAsyncError('closeDemand', error)
      return false
    }
  }

  return { ...q, approve, close }
}
