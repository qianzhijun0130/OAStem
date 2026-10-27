import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deleteSocialPolicyApi,
  listSocialPoliciesApi,
  saveSocialPolicyApi,
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
const policyVisible = ref(false)
const policyForm = reactive<Partial<SocialPolicyVO>>({
  city: '默认',
  policyYear: new Date().getFullYear(),
  baseMin: 0,
  baseMax: 0,
  fundBaseMin: 0,
  fundBaseMax: 0,
  pensionPersonal: 0.08,
  pensionCompany: 0.16,
  medicalPersonal: 0.02,
  medicalCompany: 0.098,
  unemploymentPersonal: 0.005,
  unemploymentCompany: 0.005,
  injuryCompany: 0.002,
  maternityCompany: 0.008,
  fundPersonal: 0.12,
  fundCompany: 0.12,
  remark: '',
})

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

function openPolicyForm(row?: SocialPolicyVO) {
  policyVisible.value = true
  Object.assign(policyForm, row || {
    id: undefined,
    city: '默认',
    policyYear: new Date().getFullYear(),
    baseMin: 0,
    baseMax: 0,
    fundBaseMin: 0,
    fundBaseMax: 0,
    pensionPersonal: 0.08,
    pensionCompany: 0.16,
    medicalPersonal: 0.02,
    medicalCompany: 0.098,
    unemploymentPersonal: 0.005,
    unemploymentCompany: 0.005,
    injuryCompany: 0.002,
    maternityCompany: 0.008,
    fundPersonal: 0.12,
    fundCompany: 0.12,
    remark: '',
  })
}

async function submitPolicy() {
  try {
    await saveSocialPolicyApi({
      id: policyForm.id,
      city: policyForm.city || '默认',
      policyYear: Number(policyForm.policyYear),
      baseMin: Number(policyForm.baseMin || 0),
      baseMax: Number(policyForm.baseMax || 0),
      fundBaseMin: Number(policyForm.fundBaseMin || 0),
      fundBaseMax: Number(policyForm.fundBaseMax || 0),
      pensionPersonal: Number(policyForm.pensionPersonal || 0),
      pensionCompany: Number(policyForm.pensionCompany || 0),
      medicalPersonal: Number(policyForm.medicalPersonal || 0),
      medicalCompany: Number(policyForm.medicalCompany || 0),
      unemploymentPersonal: Number(policyForm.unemploymentPersonal || 0),
      unemploymentCompany: Number(policyForm.unemploymentCompany || 0),
      injuryCompany: Number(policyForm.injuryCompany || 0),
      maternityCompany: Number(policyForm.maternityCompany || 0),
      fundPersonal: Number(policyForm.fundPersonal || 0),
      fundCompany: Number(policyForm.fundCompany || 0),
      remark: policyForm.remark,
    })
    ElMessage.success('已保存')
    policyVisible.value = false
    await loadPolicies()
  } catch (error) {
    logAsyncError('saveSocialPolicy', error)
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
    openPolicyForm,
    submitPolicy,
    removePolicy,
    policies,
    policyLoading,
    policyVisible,
    policyForm,
  }
}
