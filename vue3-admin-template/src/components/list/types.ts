/**
 * 列表页通用类型。
 *
 * <p>只放「跨页面共用的形状」，不放任何业务字段：具体查询条件由各页面自行定义并通过
 * {@code useListQuery} 的泛型传入，避免这里膨胀成一个谁都不敢改的巨型接口。</p>
 */

/** 分页查询的公共参数（后端约定：pageNo 从 1 开始，pageSize 为每页条数） */
export interface PageParams {
  pageNo: number
  pageSize: number
}

/**
 * 后端统一分页返回。
 *
 * <p>axios 响应拦截器已把 {@code Result.data} 解包，因此页面拿到的是本结构而非
 * {@code { code, data, message }}。字段名与后端 {@code PageResult} 对齐。</p>
 */
export interface PageResult<T> {
  records: T[]
  total: number
}
