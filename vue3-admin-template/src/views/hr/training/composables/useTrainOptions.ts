import { ref } from 'vue'
import { getCoursePageApi, getSessionPageApi, type TrainCourseVO, type TrainSessionVO } from '@/api/training'
import { getUserCandidatesApi } from '@/api/user'
import { logAsyncError } from '@/utils/asyncGuard'
import type { UserVO } from '@/api/types'

/**
 * 培训页三份下拉选项（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>原实现在页面 onMounted 里一次性取「员工候选 / 课程全量 / 期次全量」，
 * 因为四个弹窗和两个页签都要用。拆 tab 后仍只在这里取一次，由页面把结果
 * 作为 props 传给 tab 与弹窗——避免拆完后每个组件各拉一遍。</p>
 */
export function useTrainOptions() {
  const userCandidates = ref<UserVO[]>([])
  const courseOptions = ref<TrainCourseVO[]>([])
  const sessionOptions = ref<TrainSessionVO[]>([])

  async function load() {
    try {
      const [users, courses, sessions] = await Promise.all([
        getUserCandidatesApi(),
        getCoursePageApi({ pageSize: 200 }),
        getSessionPageApi({ pageSize: 300 }),
      ])
      userCandidates.value = users || []
      courseOptions.value = courses?.records ?? []
      sessionOptions.value = sessions?.records ?? []
    } catch (error) {
      logAsyncError('loadTrainOptions', error)
    }
  }

  return { userCandidates, courseOptions, sessionOptions, load }
}
