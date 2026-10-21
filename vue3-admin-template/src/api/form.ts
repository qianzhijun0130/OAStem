import { request } from '@/utils/request'
import type {
  FormVO,
  FormSaveRequest,
  FormPageQuery,
  PageResult,
  NodeFormVO,
  NodeFormBindRequest,
  NodeFieldPermissionVO,
  NodeFieldPermissionSaveRequest,
} from './types'

/* ---------------- 表单定义管理（system:form:*） ---------------- */

/** 表单分页列表 */
export function getFormPageApi(params: FormPageQuery) {
  return request<PageResult<FormVO>>({
    url: '/system/form/page',
    method: 'get',
    params,
  })
}

/** 启用中的表单选项（供流程模板绑定表单的下拉） */
export function getFormOptionsApi() {
  return request<FormVO[]>({ url: '/system/form/options', method: 'get' })
}

/** 表单详情（含完整规则 JSON） */
export function getFormDetailApi(id: number) {
  return request<FormVO>({ url: `/system/form/${id}`, method: 'get' })
}

/** 新增或更新表单：无 id 为新增 */
export function saveFormApi(data: FormSaveRequest) {
  return request<number>({ url: '/system/form', method: 'post', data })
}

/** 启用 / 停用表单 */
export function updateFormStatusApi(id: number, status: number) {
  return request<void>({ url: `/system/form/${id}/status`, method: 'put', params: { status } })
}

/** 删除表单（逻辑删除；被节点绑定时后端返回 2504） */
export function deleteFormApi(id: number) {
  return request<void>({ url: `/system/form/${id}`, method: 'delete' })
}

/* ---------------- 流程节点↔表单绑定（system:flow:form-bind） ---------------- */

/**
 * 取某流程「发起环节」的主表单规则。
 *
 * 鉴权为 `system:approval:apply`（面向全体员工），供「发起申请」页渲染动态表单；
 * 未绑定时返回 null，前端降级为不渲染表单区。
 */
export function getPrimaryFormApi(flowCode: string) {
  return request<NodeFormVO | null>({
    url: '/workflow/flow-definition/primary-form',
    method: 'get',
    params: { flowCode },
  })
}

/** 列出某流程模板的全部节点表单绑定 */
export function getNodeFormsApi(flowDefId: number) {
  return request<NodeFormVO[]>({
    url: `/workflow/flow-definition/${flowDefId}/node-forms`,
    method: 'get',
  })
}

/** 绑定 / 重绑某节点的表单（全量覆盖语义） */
export function bindNodeFormApi(data: NodeFormBindRequest) {
  return request<void>({ url: '/workflow/flow-definition/node-forms', method: 'post', data })
}

/** 解除节点表单绑定 */
export function unbindNodeFormApi(bindId: number) {
  return request<void>({ url: `/workflow/flow-definition/node-forms/${bindId}`, method: 'delete' })
}

/* ---------------- 节点表单字段权限（system:flow:form-bind） ---------------- */

/**
 * 读取某节点在某表单上的全部字段权限（未配置的以只读补齐）。
 *
 * <p>路径用 /workflow/node-field 而非挂在 /workflow/flow-definition 下：
 * 后者存在 @GetMapping("/{id}") 路由，静态段会被它抢先匹配并报「参数格式不正确: id」。</p>
 */
export function getNodeFieldPermissionsApi(flowDefId: number, nodeKey: string, formId: number) {
  return request<NodeFieldPermissionVO>({
    url: '/workflow/node-field/list',
    method: 'get',
    params: { flowDefId, nodeKey, formId },
  })
}

/** 保存某节点的字段权限（整节点全量覆盖） */
export function saveNodeFieldPermissionsApi(data: NodeFieldPermissionSaveRequest) {
  return request<void>({
    url: '/workflow/node-field/save',
    method: 'post',
    data,
  })
}
