import { ref } from 'vue'
import { getMyLeaveQuotaApi } from '@/api/hr'
import { logAsyncError } from '@/utils/asyncGuard'
import type { LeaveQuotaVO } from '@/api/types'

/**
 * 「我的额度」tab 的数据与年度筛选（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>年份用字符串保存：`el-date-picker` 的 `value-format="YYYY"` 期待 string，
 * 若存数字选择器会显示空白，用户以为没选年份。调接口时再转数字。</p>
 */
export function useLeaveQuota() {
  const quotas = ref<LeaveQuotaVO[]>([])
  const loading = ref(false)
  const year = ref(String(new Date().getFullYear()))

  async function load() {
    loading.value = true
    try {
      quotas.value = (await getMyLeaveQuotaApi(Number(year.value))) || []
    } catch (error) {
      logAsyncError('loadMyQuota', error)
    } finally {
      loading.value = false
    }
  }

  return { quotas, loading, year, load }
}
