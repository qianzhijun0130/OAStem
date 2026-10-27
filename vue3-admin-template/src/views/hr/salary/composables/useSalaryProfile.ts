import { reactive, ref } from 'vue'
import {
  getSalaryProfilePageApi,
} from '@/api/hr'
import { logAsyncError } from '@/utils/asyncGuard'
import type {
  SalaryProfileVO,
} from '@/api/types'

/**
 * 「薪资档案」与「档案历史」（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>调薪生效日决定历史条目，提交后两者都要重载，因此同属一个 composable。</p>
 */
export function useSalaryProfile() {

const profileQuery = reactive({
  keyword: '',
  deptId: undefined as number | undefined,
  pageNo: 1,
  pageSize: 10,
})
const profiles = ref<SalaryProfileVO[]>([])
const profileTotal = ref(0)
const profileLoading = ref(false)

async function loadProfiles() {
  profileLoading.value = true
  try {
    const page = await getSalaryProfilePageApi({ ...profileQuery })
    profiles.value = page.records || []
    profileTotal.value = Number(page.total || 0)
  } catch (error) {
    logAsyncError('loadSalaryProfiles', error)
  } finally {
    profileLoading.value = false
  }
}

  return {
    loadProfiles,
    profileQuery,
    profiles,
    profileTotal,
    profileLoading,
  }
}
