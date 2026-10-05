import { request } from '@/utils/request'
import type {
  DictDataPageQuery,
  DictDataSaveRequest,
  DictDataVO,
  DictTypePageQuery,
  DictTypeSaveRequest,
  DictTypeVO,
  PageResult,
} from './types'

/* ========== 字典类型 ========== */

export function getDictTypePageApi(params: DictTypePageQuery) {
  return request<PageResult<DictTypeVO>>({ url: '/system/dict/type/page', method: 'get', params })
}

/**
 * 启用中的字典类型列表（只读基础数据，不做按钮级鉴权）。
 *
 * 用于「新增字典项」时的类型选择和顶部筛选下拉。
 */
export function getEnabledDictTypesApi() {
  return request<DictTypeVO[]>({ url: '/system/dict/type/enabled', method: 'get' })
}

export function saveDictTypeApi(data: DictTypeSaveRequest) {
  return request<number>({ url: '/system/dict/type', method: 'post', data })
}

export function updateDictTypeStatusApi(id: number, status: number) {
  return request<null>({ url: `/system/dict/type/${id}/status`, method: 'put', params: { status } })
}

export function deleteDictTypeApi(id: number) {
  return request<null>({ url: `/system/dict/type/${id}`, method: 'delete' })
}

/* ========== 字典数据项 ========== */

export function getDictDataPageApi(params: DictDataPageQuery) {
  return request<PageResult<DictDataVO>>({ url: '/system/dict/data/page', method: 'get', params })
}

/**
 * 按类型编码取启用中的字典项。
 *
 * 业务表单（如公告类型下拉）可直接用它渲染选项，新增枚举值只需在字典管理里加一条数据。
 */
export function getDictDataByTypeApi(dictType: string) {
  return request<DictDataVO[]>({ url: `/system/dict/data/type/${dictType}`, method: 'get' })
}

export function saveDictDataApi(data: DictDataSaveRequest) {
  return request<number>({ url: '/system/dict/data', method: 'post', data })
}

export function updateDictDataStatusApi(id: number, status: number) {
  return request<null>({ url: `/system/dict/data/${id}/status`, method: 'put', params: { status } })
}

export function deleteDictDataApi(id: number) {
  return request<null>({ url: `/system/dict/data/${id}`, method: 'delete' })
}
