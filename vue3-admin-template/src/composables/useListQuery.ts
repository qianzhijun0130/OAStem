import { onMounted, reactive, ref, type Ref } from 'vue'
import type { PageParams, PageResult } from '@/components/list/types'

/**
 * 列表查询组合式函数：把「分页 + 加载态 + 查询条件 + 搜索/重置」这套每个列表页都要重写一遍的逻辑收口。
 *
 * <p><b>职责边界</b>：只管理「查询状态与加载过程」，不关心页面长什么样、也不关心接口怎么实现——
 * 取数函数由调用方注入（{@link UseListQueryOptions.fetcher}），因此同一套逻辑既能用于流程实例，
 * 也能用于用户/角色等任意分页接口。</p>
 *
 * <p><b>与组件的关系</b>：与 {@code ListPage.vue} 是「逻辑/视图」分离的两半——本函数不依赖任何 UI，
 * 可单独在纯逻辑场景（如导出前预取）复用；{@code ListPage} 也可搭配其它数据源使用。</p>
 */

/** 调用方可选的行为配置 */
export interface UseListQueryOptions<T, Q extends object> {
  /**
   * 生成初始查询条件（**每次调用都要返回新对象**）。
   *
   * <p>用工厂函数而非静态对象，是为了让「重置」能拿到一份干净副本，
   * 避免多个页面共享同一个对象引用导致条件串味。</p>
   */
  initialQuery: () => Q
  /** 取数函数：接收「查询条件 + 分页参数」，返回统一分页结构 */
  fetcher: (params: Q & PageParams) => Promise<PageResult<T> | null | undefined>
  /** 每页条数默认值，缺省 10 */
  pageSize?: number
  /** 是否在 onMounted 时自动加载，缺省 true */
  immediate?: boolean
  /**
   * 取数前对查询条件做最后加工（原地修改）。
   *
   * <p>典型用途：把日期区间 {@code dateRange: ['2026-10-01','2026-10-31']} 拆成后端需要的
   * {@code beginTime/endTime}。这类「页面私有的映射规则」不应污染本函数，故以钩子注入。</p>
   */
  beforeFetch?: (query: Q) => void
  /**
   * 取数失败时的回调。
   *
   * <p>缺省为空（错误提示统一由 axios 拦截器给出）。本函数在失败时**不清空已有数据**，
   * 与页面原有行为保持一致，避免网络抖动把列表刷白。</p>
   */
  onError?: (error: unknown) => void
}

/** 对外暴露的状态与方法 */
export interface UseListQueryReturn<T, Q extends object> {
  /** 当前页数据 */
  list: Ref<T[]>
  /** 总条数 */
  total: Ref<number>
  /** 加载态（可直接绑到 v-loading） */
  loading: Ref<boolean>
  /** 查询条件（reactive，页面表单直接 v-model 绑定） */
  query: Q
  pageNo: Ref<number>
  pageSize: Ref<number>
  /** 按当前条件与页码加载 */
  load: () => Promise<void>
  /** 搜索：回到第 1 页再加载 */
  search: () => Promise<void>
  /** 重置：条件恢复初始值并回到第 1 页 */
  reset: () => Promise<void>
  /** 刷新当前页（增删改后调用） */
  refresh: () => Promise<void>
  /** 每页条数变化 */
  handleSizeChange: (size: number) => Promise<void>
  /** 页码变化 */
  handlePageChange: (page: number) => Promise<void>
}

export function useListQuery<T, Q extends object>(
  options: UseListQueryOptions<T, Q>,
): UseListQueryReturn<T, Q> {
  const { initialQuery, fetcher, pageSize: initialPageSize = 10, immediate = true } = options

  const list = ref([]) as Ref<T[]>
  const total = ref(0)
  const loading = ref(false)
  const pageNo = ref(1)
  const pageSize = ref(initialPageSize)
  const query = reactive(initialQuery()) as unknown as Q

  /**
   * 请求序号：并发场景下（快速连点搜索 / 翻页）后发请求可能先返回，
   * 用序号丢弃过期响应，避免旧结果覆盖新结果。
   */
  let requestSeq = 0

  async function load(): Promise<void> {
    const seq = ++requestSeq
    loading.value = true
    try {
      options.beforeFetch?.(query)
      const params = { ...(query as Record<string, unknown>), pageNo: pageNo.value, pageSize: pageSize.value } as Q & PageParams
      const res = await fetcher(params)
      if (seq !== requestSeq) {
        return
      }
      // 容错：后端异常分支可能返回 null / 缺字段，统一降级为空页，避免表格渲染报错
      list.value = Array.isArray(res?.records) ? res.records : []
      total.value = typeof res?.total === 'number' && res.total >= 0 ? res.total : 0
    } catch (error) {
      if (seq !== requestSeq) {
        return
      }
      // 失败不清空已有数据（与各页面原行为一致），提示交给拦截器 / 调用方
      options.onError?.(error)
    } finally {
      if (seq === requestSeq) {
        loading.value = false
      }
    }
  }

  async function search(): Promise<void> {
    pageNo.value = 1
    await load()
  }

  async function reset(): Promise<void> {
    // 先删后赋：条件对象可能被页面扩展过字段，直接 Object.assign 会残留旧键
    const initial = initialQuery()
    Object.keys(query).forEach((key) => {
      delete (query as Record<string, unknown>)[key]
    })
    Object.assign(query, initial)
    pageNo.value = 1
    await load()
  }

  async function refresh(): Promise<void> {
    await load()
  }

  async function handleSizeChange(size: number): Promise<void> {
    pageSize.value = size
    pageNo.value = 1
    await load()
  }

  async function handlePageChange(page: number): Promise<void> {
    pageNo.value = page
    await load()
  }

  if (immediate) {
    onMounted(() => {
      void load()
    })
  }

  return {
    list,
    total,
    loading,
    query,
    pageNo,
    pageSize,
    load,
    search,
    reset,
    refresh,
    handleSizeChange,
    handlePageChange,
  }
}
