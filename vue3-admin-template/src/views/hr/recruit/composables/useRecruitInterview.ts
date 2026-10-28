import { getInterviewPageApi } from '@/api/recruit'
import { useListQuery } from '@/composables/useListQuery'
import type { RecruitInterviewVO } from '@/api/recruit'

/**
 * 「面试」页签的列表（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>安排面试、提交评价、汇总结论三件事各自独立成弹窗，都通过 `saved` 事件
 * 让本页签重载，所以这里只留列表状态。</p>
 */
export function useRecruitInterview() {
  return useListQuery<RecruitInterviewVO, { candidateId?: number; status?: number }>({
    initialQuery: () => ({ candidateId: undefined, status: undefined }),
    fetcher: (p) => getInterviewPageApi(p),
    immediate: false,
  })
}
