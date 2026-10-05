import type { AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import service from '@/utils/request'

/** Content-Disposition 里的 filename* 形如 UTF-8''%E6%93%8D%E4%BD%9C... */
const FILENAME_STAR_PATTERN = /filename\*=UTF-8''([^;]+)/i
const FILENAME_PLAIN_PATTERN = /filename="?([^";]+)"?/i

/**
 * 按查询条件下载后端导出的文件。
 *
 * <p>后端失败时会返回 JSON（Result 包装）而不是文件流，此时响应体仍是 Blob。
 * 必须先把 Blob 读成文本判断是不是 JSON，否则用户会拿到一个「内容是一段报错 JSON
 * 的 xlsx」，且看不到任何提示。</p>
 */
export async function downloadFile(
  url: string,
  params: Record<string, unknown>,
  fallbackFileName: string,
): Promise<void> {
  const response = (await service.request({
    url,
    method: 'get',
    params,
    responseType: 'blob',
  })) as unknown as AxiosResponse<Blob>

  await saveResponse(response, fallbackFileName)
}

/** 按完整 URL 下载（无查询条件），用于公告附件等场景 */
export async function downloadBlobUrl(url: string, fallbackFileName: string): Promise<void> {
  const response = (await service.request({
    url,
    method: 'get',
    responseType: 'blob',
  })) as unknown as AxiosResponse<Blob>
  await saveResponse(response, fallbackFileName)
}

/** 抽出响应处理：附件下载与日志导出共用同一套「JSON 报错识别 + 触发保存」逻辑 */
async function saveResponse(response: AxiosResponse<Blob>, fallbackFileName: string): Promise<void> {
  const blob = response.data
  const contentType = blob.type || ''

  if (contentType.includes('application/json')) {
    const body = await parseJsonBlob(blob)
    const message = body?.message || '下载失败，请稍后重试'
    ElMessage.error(message)
    throw new Error(message)
  }

  const fileName = resolveFileName(response, fallbackFileName)
  const objectUrl = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = objectUrl
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(objectUrl)
}

/** 优先用后端给的文件名（含中文时分段都试一遍），取不到再用调用方兜底 */
function resolveFileName(response: AxiosResponse<Blob>, fallback: string): string {
  const disposition = response.headers?.['content-disposition']
  if (!disposition || typeof disposition !== 'string') {
    return fallback
  }
  const starMatch = disposition.match(FILENAME_STAR_PATTERN)
  if (starMatch?.[1]) {
    try {
      return decodeURIComponent(starMatch[1])
    } catch {
      return fallback
    }
  }
  const plainMatch = disposition.match(FILENAME_PLAIN_PATTERN)
  return plainMatch?.[1] || fallback
}

async function parseJsonBlob(blob: Blob): Promise<{ message?: string } | null> {
  try {
    return JSON.parse(await blob.text())
  } catch {
    return null
  }
}
