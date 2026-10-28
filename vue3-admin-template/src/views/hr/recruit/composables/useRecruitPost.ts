import { ElMessage, ElMessageBox } from 'element-plus'
import { deletePostApi, getPostPageApi } from '@/api/recruit'
import { logAsyncError } from '@/utils/asyncGuard'
import { useListQuery } from '@/composables/useListQuery'
import type { RecruitPostVO } from '@/api/recruit'

/** 「招聘职位」页签的列表 + 删除（FE-OPT-06 从 index.vue 抽出） */
export function useRecruitPost() {
  const q = useListQuery<RecruitPostVO, { keyword: string; status?: number; demandId?: number }>({
    initialQuery: () => ({ keyword: '', status: undefined, demandId: undefined }),
    fetcher: (p) => getPostPageApi(p),
    immediate: false,
  })

  async function remove(row: RecruitPostVO): Promise<boolean> {
    try {
      await ElMessageBox.confirm(`确定删除职位「${row.postName}」吗？`, '删除职位', {
        type: 'warning',
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
      })
    } catch {
      return false
    }
    try {
      await deletePostApi(row.id)
      ElMessage.success('已删除')
      await q.refresh()
      return true
    } catch (error) {
      logAsyncError('deletePost', error)
      return false
    }
  }

  return { ...q, remove }
}
