import { request } from '@/utils/request'
import type { FileUploadVO } from './types'

/**
 * 上传文件。
 *
 * 手动设置 multipart 头只是为了表达意图，实际 boundary 仍由浏览器生成——
 * 因此这里不指定 Content-Type 也能正常工作，交给 axios 自动处理更稳妥。
 */
export function uploadFileApi(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request<FileUploadVO>({
    url: '/file/upload',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/** 下载附件（鉴权接口，响应为二进制流） */
export function downloadFileApi(id: number) {
  return request<Blob>({ url: `/file/download/${id}`, method: 'get', responseType: 'blob' })
}
