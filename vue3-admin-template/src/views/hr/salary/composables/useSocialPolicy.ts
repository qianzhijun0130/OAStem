import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deleteSocialPolicyApi,
  listSocialPoliciesApi,
} from '@/api/hr'
import { logAsyncError } from '@/utils/asyncGuard'
import type {
  SocialPolicyVO,
} from '@/api/types'

/**
 * 「社保公积金政策」（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>政策是核算输入（基数/比例决定社保公积金扣款），与核算同属薪酬域但互不引用状态。</p>
 */
export function useSocialPolicy() {

const policies = ref<SocialPolicyVO[]>([])
const policyLoading = ref(false)
async function loadPolicies() {
  policyLoading.value = true
  try {
    policies.value = await listSocialPoliciesApi()
  } catch (error) {
    logAsyncError('loadSocialPolicies', error)
  } finally {
    policyLoading.value = false
  }
}

async function removePolicy(row: SocialPolicyVO) {
  try {
    await ElMessageBox.confirm(`确定删除「${row.city} / ${row.policyYear}」的社保政策吗？`, '删除政策', {
      type: 'warning',
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deleteSocialPolicyApi(row.id)
    ElMessage.success('已删除')
    await loadPolicies()
  } catch (error) {
    logAsyncError('deleteSocialPolicy', error)
  }
}

  return {
    loadPolicies,
    removePolicy,
    policies,
    policyLoading,
  }
}
