import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteCourseApi, getCoursePageApi, type TrainCourseVO } from '@/api/training'
import { logAsyncError } from '@/utils/asyncGuard'
import { useListQuery } from '@/composables/useListQuery'

/**
 * 「培训课程」tab 的列表与删除（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>`immediate: false`：页签懒渲染，由 tab 自己在首次挂载时 `load()`。</p>
 */
export function useTrainCourse() {
  const q = useListQuery<TrainCourseVO, { keyword: string; type?: number; required?: number }>({
    initialQuery: () => ({ keyword: '', type: undefined, required: undefined }),
    fetcher: (p) => getCoursePageApi(p),
    immediate: false,
  })

  async function remove(row: TrainCourseVO) {
    try {
      await ElMessageBox.confirm(`确定删除课程「${row.courseName}」吗？`, '删除课程', {
        type: 'warning',
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
      })
    } catch {
      return
    }
    try {
      await deleteCourseApi(row.id)
      ElMessage.success('已删除')
      await q.refresh()
    } catch (error) {
      logAsyncError('deleteCourse', error)
    }
  }

  return { ...q, remove }
}
