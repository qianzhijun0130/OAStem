import axios, { type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResponse } from '@/api/types'
import { useUserStore } from '@/stores/user'

/** 业务成功码，需与后端 ErrorCode.SUCCESS 保持一致 */
const SUCCESS_CODE = 200
/** 认证失效，需重新登录 */
const UNAUTHORIZED_CODE = 401
const FORBIDDEN_CODE = 403

const service = axios.create({
  baseURL: import.meta.env.VITE_APP_BASE_API || '/api',
  timeout: 10000,
})

service.interceptors.request.use((config) => {
  const userStore = useUserStore()
  if (userStore.token) {
    config.headers.Authorization = `Bearer ${userStore.token}`
  }
  return config
})

service.interceptors.response.use(
  (response) => {
    // 文件下载需要读取 Content-Disposition 才能拿到后端命名的文件名，
    // 因此 blob 请求返回完整响应，其余仍解包为业务数据。
    if (response.config.responseType === 'blob') {
      return response
    }
    const body = response.data as ApiResponse
    // 非标准结构（如文件流）直接放行
    if (body == null || typeof body.code === 'undefined') {
      return response.data
    }
    if (body.code === SUCCESS_CODE) {
      return body.data
    }
    ElMessage.error(body.message || '请求失败')
    return Promise.reject(new Error(body.message || '请求失败'))
  },
  (error) => {
    // 401 由 SecurityConfig 直接写入响应体，code 与 HTTP 状态码都是 401
    const status = error.response?.status
    const bizCode = error.response?.data?.code
    if (status === UNAUTHORIZED_CODE || bizCode === UNAUTHORIZED_CODE) {
      const userStore = useUserStore()
      userStore.forceLogout()
      ElMessage.error('登录已失效，请重新登录')
      return Promise.reject(error)
    }
    if (status === FORBIDDEN_CODE || bizCode === FORBIDDEN_CODE) {
      ElMessage.error('无权限执行该操作')
      return Promise.reject(error)
    }
    ElMessage.error(error.message || '网络异常，请稍后重试')
    return Promise.reject(error)
  },
)

/**
 * 对外暴露的请求方法。
 *
 * 拦截器已把 ApiResponse 解包为业务数据，因此这里直接返回 T，
 * 调用方无需再写 response.data.data 这种嵌套取值。
 */
export function request<T = unknown>(config: AxiosRequestConfig): Promise<T> {
  // 响应拦截器已把 ApiResponse 解包成业务数据，但 axios 的静态类型仍按 AxiosResponse 推导，
  // 因此这里用断言对齐运行时行为
  return service.request(config) as unknown as Promise<T>
}

export default service
