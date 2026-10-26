import { reactive, ref } from 'vue'
import { getFlowPageApi } from '@/api/flow'
import { logAsyncError } from '@/utils/asyncGuard'
import type { FlowDefinitionPageQuery, FlowDefinitionVO } from '@/api/types'

/**
 * 流程模板列表的分页状态与加载逻辑（FE-OPT-05 拆分自 index.vue）。
 *
 * <p>只搬「分页 + 列表数据 + 查询条件」，不搬业务动作（新增/设计/发布/删除仍在页面）。
 * 模板里的 `list` / `loading` / `total` / `query` 与三个事件处理同名解构后无需改动。</p>
 */
export function useDefinitionList() {
  const loading = ref(false)
  const list = ref<FlowDefinitionVO[]>([])
  const total = ref(0)
  const query = reactive<FlowDefinitionPageQuery>({ pageNo: 1, pageSize: 10 })

  async function loadPage() {
    loading.value = true
    try {
      const res = await getFlowPageApi(query)
      list.value = res.records
      total.value = res.total
    } catch (error) {
      logAsyncError('加载流程模板列表失败', error)
    } finally {
      loading.value = false
    }
  }

  function handleSizeChange(size: number) {
    query.pageSize = size
    void loadPage()
  }

  function handlePageChange(page: number) {
    query.pageNo = page
    void loadPage()
  }

  function resetQuery() {
    query.flowName = undefined
    query.status = undefined
    query.category = undefined
    query.pageNo = 1
    void loadPage()
  }

  return { loading, list, total, query, loadPage, handleSizeChange, handlePageChange, resetQuery }
}
