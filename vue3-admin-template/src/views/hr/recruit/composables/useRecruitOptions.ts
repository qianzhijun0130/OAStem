import { reactive } from 'vue'
import { getDeptTreeApi } from '@/api/dept'
import { listPositionOptionsApi } from '@/api/hr'
import { getUserCandidatesApi } from '@/api/user'
import { getCandidatePageApi, getDemandPageApi, getPostPageApi } from '@/api/recruit'
import { logAsyncError } from '@/utils/asyncGuard'
import type { DeptVO, PositionVO, UserVO } from '@/api/types'
import type { RecruitCandidateVO, RecruitDemandVO, RecruitPostVO } from '@/api/recruit'

/**
 * 招聘页的六份共享下拉（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>原实现是 onMounted 里一次 `Promise.all` 六连发，不管用户看不看得到；
 * 拆 tab 后改成 <b>谁用谁取</b>：部门树给需求页签的筛选器，其余五份都只在弹窗里用，
 * 打开弹窗时 `ensureXxx()` 一次，之后命中缓存。首屏因此从 6 条降到 1 条。</p>
 *
 * <p><b>为什么必须共享单实例</b>：需求、职位、候选人是条流水线——新建需求后「职位弹窗」
 * 的关联需求下拉要立刻能选到；候选人推进到终试后「Offer 弹窗」的候选人下拉要立刻多一条。
 * 各弹窗自己拉一份就必然看到旧数据。保存后由页面调 `ensureXxx(true)` 强刷。</p>
 */
export function useRecruitOptions() {
  const state = reactive({
    deptTree: [] as DeptVO[],
    userCandidates: [] as UserVO[],
    positionOptions: [] as PositionVO[],
    /** 职位全量：候选人/面试/Offer 弹窗用 */
    postOptions: [] as RecruitPostVO[],
    /** 需求全量：职位/候选人弹窗用 */
    demandOptions: [] as RecruitDemandVO[],
    /** 候选人全量：面试弹窗、Offer 终试过滤用 */
    candidateOptions: [] as RecruitCandidateVO[],
  })

  const loaded = {
    deptTree: false,
    users: false,
    positions: false,
    posts: false,
    demands: false,
    candidates: false,
  }
  /** 在途请求：并发 ensure 时共用，避免同一份下拉打两次 */
  const pending: Record<string, Promise<void> | null> = {}

  /** 通用加载器：缓存 + 并发去重 */
  function loader(key: keyof typeof loaded, load: () => Promise<void>) {
    return async function ensure(force = false) {
      if (loaded[key] && !force) {
        return
      }
      if (!pending[key]) {
        pending[key] = load().finally(() => {
          pending[key] = null
        })
      }
      await pending[key]
    }
  }

  const ensureDeptTree = loader('deptTree', async () => {
    try {
      state.deptTree = (await getDeptTreeApi()) || []
      loaded.deptTree = true
    } catch (error) {
      logAsyncError('loadDeptTree', error)
    }
  })

  const ensureUsers = loader('users', async () => {
    try {
      state.userCandidates = (await getUserCandidatesApi()) || []
      loaded.users = true
    } catch (error) {
      logAsyncError('loadRecruitUsers', error)
    }
  })

  const ensurePositions = loader('positions', async () => {
    try {
      state.positionOptions = (await listPositionOptionsApi()) || []
      loaded.positions = true
    } catch (error) {
      logAsyncError('loadRecruitPositions', error)
    }
  })

  const ensurePosts = loader('posts', async () => {
    try {
      const page = await getPostPageApi({ pageSize: 200 })
      state.postOptions = page?.records ?? []
      loaded.posts = true
    } catch (error) {
      logAsyncError('loadRecruitPosts', error)
    }
  })

  const ensureDemands = loader('demands', async () => {
    try {
      const page = await getDemandPageApi({ pageSize: 200 })
      state.demandOptions = page?.records ?? []
      loaded.demands = true
    } catch (error) {
      logAsyncError('loadRecruitDemands', error)
    }
  })

  const ensureCandidates = loader('candidates', async () => {
    try {
      const page = await getCandidatePageApi({ pageSize: 200 })
      state.candidateOptions = page?.records ?? []
      loaded.candidates = true
    } catch (error) {
      logAsyncError('loadRecruitCandidates', error)
    }
  })

  return {
    state,
    ensureDeptTree,
    ensureUsers,
    ensurePositions,
    ensurePosts,
    ensureDemands,
    ensureCandidates,
  }
}

/** 共享下拉容器：页面创建，页签与弹窗以 props 接收 */
export type RecruitOptions = ReturnType<typeof useRecruitOptions>
