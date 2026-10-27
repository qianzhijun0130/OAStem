import { reactive, ref, type Ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import {
  getSalaryProfileHistoryApi,
  getSalaryProfilePageApi,
  saveSalaryProfileApi,
} from '@/api/hr'
import { getUserPageApi } from '@/api/user'
import { logAsyncError } from '@/utils/asyncGuard'
import type {
  SalaryProfileVO,
} from '@/api/types'

import { currentMonth } from '../period'

/**
 * 「薪资档案」与「档案历史」（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>调薪生效日决定历史条目，提交后两者都要重载，因此同属一个 composable。</p>
 */
export interface SalaryProfileDeps {
  /**
   * 档案表单实例。必须由页面持有再注入：模板里的 `<el-form ref="profileFormRef">`
   * 只能绑定页面作用域的变量；若 composable 内自建 ref，模板 ref 与它会是两份，
   * 提交时校验静默不执行。
   */
  formRef: Ref<FormInstance | undefined>
}

export function useSalaryProfile(deps: SalaryProfileDeps) {
  const profileFormRef = deps.formRef

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

const profileFormVisible = ref(false)
const profileForm = reactive({
  userId: undefined as number | undefined,
  effectiveDate: currentMonth() + '-01',
  baseSalary: 0,
  postSalary: 0,
  perfSalary: 0,
  allowance: 0,
  socialBase: undefined as number | undefined,
  fundBase: undefined as number | undefined,
  city: '默认',
  specialDeduct: 0,
  changeReason: '',
})
const profileRules: FormRules = {
  userId: [{ required: true, message: '请选择员工', trigger: 'change' }],
  effectiveDate: [{ required: true, message: '请选择生效日期', trigger: 'change' }],
}

const userOptions = ref<{ value: number; label: string }[]>([])

async function loadUserOptions() {
  try {
    // 没有专门的「用户下拉」接口，直接取一页最大 500 条：薪资档案只对在职员工维护，
    // 500 足够覆盖中小规模企业；超出的场景应改为服务端搜索
    const page = await getUserPageApi({ pageNo: 1, pageSize: 500, status: 1 })
    userOptions.value = (page.records || []).map((u) => ({
      value: u.id,
      label: `${u.realName || u.username}（${u.workNumber || ''}）`,
    }))
  } catch (error) {
    logAsyncError('loadUserOptions', error)
  }
}

function openProfileForm(row?: SalaryProfileVO) {
  profileFormVisible.value = true
  if (row) {
    profileForm.userId = row.userId
    // 调薪默认从「下个月 1 日」生效：当月已经算过的薪资不能被追溯改掉
    profileForm.effectiveDate = nextMonthFirstDay()
    profileForm.baseSalary = Number(row.baseSalary || 0)
    profileForm.postSalary = Number(row.postSalary || 0)
    profileForm.perfSalary = Number(row.perfSalary || 0)
    profileForm.allowance = Number(row.allowance || 0)
    profileForm.socialBase = row.socialBase ?? undefined
    profileForm.fundBase = row.fundBase ?? undefined
    profileForm.city = row.city || '默认'
    profileForm.specialDeduct = Number(row.specialDeduct || 0)
    profileForm.changeReason = ''
  } else {
    profileForm.userId = undefined
    profileForm.effectiveDate = currentMonth() + '-01'
    profileForm.baseSalary = 0
    profileForm.postSalary = 0
    profileForm.perfSalary = 0
    profileForm.allowance = 0
    profileForm.socialBase = undefined
    profileForm.fundBase = undefined
    profileForm.city = '默认'
    profileForm.specialDeduct = 0
    profileForm.changeReason = ''
  }
}

function nextMonthFirstDay() {
  const d = new Date()
  d.setMonth(d.getMonth() + 1, 1)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-01`
}

async function submitProfile() {
  const form = profileFormRef.value
  if (!form) {
    return
  }
  const valid = await form.validate().catch(() => false)
  if (!valid) {
    return
  }
  try {
    await saveSalaryProfileApi({
      userId: profileForm.userId as number,
      effectiveDate: profileForm.effectiveDate,
      baseSalary: profileForm.baseSalary,
      postSalary: profileForm.postSalary,
      perfSalary: profileForm.perfSalary,
      allowance: profileForm.allowance,
      socialBase: profileForm.socialBase,
      fundBase: profileForm.fundBase,
      city: profileForm.city,
      specialDeduct: profileForm.specialDeduct,
      changeReason: profileForm.changeReason,
    })
    ElMessage.success('已保存（新增一段档案，历史月份不受影响）')
    profileFormVisible.value = false
    await loadProfiles()
  } catch (error) {
    logAsyncError('saveSalaryProfile', error)
  }
}

/* ---------- 档案历史 ---------- */

const historyVisible = ref(false)
const history = ref<SalaryProfileVO[]>([])

async function openHistory(row: SalaryProfileVO) {
  try {
    history.value = await getSalaryProfileHistoryApi(row.userId)
    historyVisible.value = true
  } catch (error) {
    logAsyncError('loadSalaryProfileHistory', error)
  }
}

  return {
    loadProfiles,
    loadUserOptions,
    openProfileForm,
    nextMonthFirstDay,
    submitProfile,
    openHistory,
    profileQuery,
    profiles,
    profileTotal,
    profileLoading,
    profileFormVisible,
    profileForm,
    profileRules,
    userOptions,
    historyVisible,
    history,
  }
}
