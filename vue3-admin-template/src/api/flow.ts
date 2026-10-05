import { request } from '@/utils/request'
import type {
  FlowDefinitionVO,
  FlowOptionVO,
  FlowDefinitionSaveRequest,
  FlowDefinitionPageQuery,
  FlowDefinitionImportRequest,
  PageResult,
} from './types'

/** 流程模板分页列表 */
export function getFlowPageApi(params: FlowDefinitionPageQuery) {
  return request<PageResult<FlowDefinitionVO>>({
    url: '/workflow/flow-definition/page',
    method: 'get',
    params,
  })
}

/**
 * 可发起的流程模板选项（启用中，只读最小字段）。
 *
 * 「发起申请」页必须用这个接口而不是分页接口：分页接口鉴权是 `system:flow:query`
 * （管理职能），普通员工没有该权限会导致页面一打开就 403、模板下拉为空。
 */
export function getFlowOptionsApi() {
  return request<FlowOptionVO[]>({ url: '/workflow/flow-definition/options', method: 'get' })
}

/** 流程模板详情 */
export function getFlowDetailApi(id: number) {
  return request<FlowDefinitionVO>({ url: `/workflow/flow-definition/${id}`, method: 'get' })
}

/** 读取模板的 BPMN XML（设计器回显） */
export function getFlowBpmnApi(id: number) {
  return request<string>({ url: `/workflow/flow-definition/${id}/bpmn`, method: 'get' })
}

/** 新增或更新流程模板：无 id 为新增；传 bpmnXml 时会重新部署 */
export function saveFlowApi(data: FlowDefinitionSaveRequest) {
  return request<number>({ url: '/workflow/flow-definition', method: 'post', data })
}

/** 导入 BPMN XML：后端解析节点审批人约定并部署，返回新建模板 id */
export function importFlowApi(data: FlowDefinitionImportRequest) {
  return request<number>({ url: '/workflow/flow-definition/import', method: 'post', data })
}

/** 启用 / 停用流程模板 */
export function updateFlowStatusApi(id: number, status: number) {
  return request<null>({
    url: `/workflow/flow-definition/${id}/status`,
    method: 'put',
    params: { status },
  })
}

/** 删除流程模板（级联卸载 Flowable 部署） */
export function deleteFlowApi(id: number) {
  return request<null>({ url: `/workflow/flow-definition/${id}`, method: 'delete' })
}
