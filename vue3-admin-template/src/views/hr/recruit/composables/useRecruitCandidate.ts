import { getCandidatePageApi } from '@/api/recruit'
import { useListQuery } from '@/composables/useListQuery'
import type { RecruitCandidateVO } from '@/api/recruit'

/**
 * 「候选人」页签的列表（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>详情抽屉自己取详情与面试记录、手机号查重跟着表单走，都不放在这里——
 * 页签只需要「列表 + 分页」。</p>
 */
export function useRecruitCandidate() {
  return useListQuery<
    RecruitCandidateVO,
    { keyword: string; stage?: number; source?: number; postId?: number }
  >({
    initialQuery: () => ({ keyword: '', stage: undefined, source: undefined, postId: undefined }),
    fetcher: (p) => getCandidatePageApi(p),
    immediate: false,
  })
}
