<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  approveDemandApi,
  changeCandidateStageApi,
  closeDemandApi,
  convertOfferApi,
  deletePostApi,
  findDuplicateApi,
  getBoardApi,
  getCandidateDetailApi,
  getCandidatePageApi,
  getDemandPageApi,
  getInterviewDetailApi,
  getInterviewPageApi,
  getOfferPageApi,
  getPostPageApi,
  RECRUIT_CHANNEL_OPTIONS,
  RECRUIT_CONCLUSION_OPTIONS,
  RECRUIT_DEMAND_STATUS_OPTIONS,
  RECRUIT_GENDER_OPTIONS,
  RECRUIT_INTERVIEW_STATUS_OPTIONS,
  RECRUIT_INTERVIEW_WAY_OPTIONS,
  RECRUIT_OFFER_STATUS_OPTIONS,
  RECRUIT_POST_STATUS_OPTIONS,
  RECRUIT_REASON_TYPE_OPTIONS,
  RECRUIT_REJECT_TYPE_OPTIONS,
  RECRUIT_SEND_WAY_OPTIONS,
  RECRUIT_SOURCE_OPTIONS,
  RECRUIT_STAGE_OPTIONS,
  RECRUIT_URGENCY_OPTIONS,
  RECRUIT_YESNO_OPTIONS,
  saveCandidateApi,
  saveDemandApi,
  saveInterviewApi,
  saveOfferApi,
  savePostApi,
  sendOfferApi,
  summarizeInterviewApi,
  acceptOfferApi,
  refuseOfferApi,
  submitFeedbackApi,
  type RecruitBoardVO,
  type RecruitCandidateSaveRequest,
  type RecruitCandidateVO,
  type RecruitConvertRequest,
  type RecruitDemandSaveRequest,
  type RecruitDemandVO,
  type RecruitFeedbackRequest,
  type RecruitInterviewVO,
  type RecruitOfferSaveRequest,
  type RecruitOfferVO,
  type RecruitPostSaveRequest,
  type RecruitPostVO,
} from '@/api/recruit'
import { getDeptTreeApi } from '@/api/dept'
import { listPositionOptionsApi } from '@/api/hr'
import { getUserCandidatesApi } from '@/api/user'
import { useListQuery } from '@/composables/useListQuery'
import { logAsyncError } from '@/utils/asyncGuard'
import type { DeptVO, PositionVO, UserVO } from '@/api/types'

const deptTree = ref<DeptVO[]>([])
const userCandidates = ref<UserVO[]>([])
const positionOptions = ref<PositionVO[]>([])
const postOptions = ref<RecruitPostVO[]>([])
const demandOptions = ref<RecruitDemandVO[]>([])
const candidateOptions = ref<RecruitCandidateVO[]>([])

const activeTab = ref('demand')
const loadedTabs = ref<Set<string>>(new Set())

/* ============================ 需求 ============================ */
const demandQ = useListQuery<RecruitDemandVO, { keyword: string; status?: number; deptId?: number }>({
  initialQuery: () => ({ keyword: '', status: undefined, deptId: undefined }),
  fetcher: (p) => getDemandPageApi(p),
  immediate: false,
})

const demandDialog = ref(false)
const demandSubmitting = ref(false)
const demandFormRef = ref<FormInstance>()
const demandEditingId = ref<number | null>(null)
const demandForm = reactive<RecruitDemandSaveRequest>({
  deptId: undefined,
  positionId: undefined,
  headCount: 1,
  urgency: 1,
  expectDate: '',
  education: '',
  experience: '',
  skillRequire: '',
  salaryMin: undefined,
  salaryMax: undefined,
  reasonType: 1,
  reason: '',
  hrId: undefined,
  remark: '',
})
const demandRules: FormRules = {
  deptId: [{ required: true, message: '请选择需求部门', trigger: 'change' }],
  positionId: [{ required: true, message: '请选择需求岗位', trigger: 'change' }],
  headCount: [{ required: true, message: '请输入招聘人数', trigger: 'blur' }],
}

function resetDemandForm() {
  Object.assign(demandForm, {
    id: null,
    deptId: undefined,
    positionId: undefined,
    headCount: 1,
    urgency: 1,
    expectDate: '',
    education: '',
    experience: '',
    skillRequire: '',
    salaryMin: undefined,
    salaryMax: undefined,
    reasonType: 1,
    reason: '',
    hrId: undefined,
    remark: '',
  })
}

function openDemandCreate() {
  demandEditingId.value = null
  resetDemandForm()
  demandDialog.value = true
}

function openDemandEdit(row: RecruitDemandVO) {
  demandEditingId.value = row.id
  Object.assign(demandForm, {
    id: row.id,
    deptId: row.deptId,
    positionId: row.positionId,
    headCount: row.headCount || 1,
    urgency: row.urgency || 1,
    expectDate: row.expectDate || '',
    education: row.education || '',
    experience: row.experience || '',
    skillRequire: row.skillRequire || '',
    salaryMin: row.salaryMin,
    salaryMax: row.salaryMax,
    reasonType: row.reasonType || 1,
    reason: row.reason || '',
    hrId: row.hrId,
    remark: row.remark || '',
  })
  demandDialog.value = true
}

async function submitDemand() {
  const valid = await demandFormRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  demandSubmitting.value = true
  try {
    await saveDemandApi({ ...demandForm, id: demandEditingId.value ?? undefined })
    ElMessage.success(demandEditingId.value ? '已保存' : '已新增')
    demandDialog.value = false
    await demandQ.refresh()
  } catch (error) {
    logAsyncError('saveDemand', error)
  } finally {
    demandSubmitting.value = false
  }
}

async function approveDemand(row: RecruitDemandVO) {
  try {
    await ElMessageBox.confirm(`确定审批通过需求「${row.demandCode}」吗？`, '审批需求', {
      type: 'warning',
      confirmButtonText: '通过',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await approveDemandApi(row.id)
    ElMessage.success('已审批通过')
    await demandQ.refresh()
  } catch (error) {
    logAsyncError('approveDemand', error)
  }
}

async function closeDemand(row: RecruitDemandVO) {
  try {
    await ElMessageBox.confirm(`确定关闭需求「${row.demandCode}」吗？`, '关闭需求', {
      type: 'warning',
      confirmButtonText: '关闭',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await closeDemandApi(row.id)
    ElMessage.success('已关闭')
    await demandQ.refresh()
  } catch (error) {
    logAsyncError('closeDemand', error)
  }
}

/* ============================ 职位 ============================ */
const postQ = useListQuery<RecruitPostVO, { keyword: string; status?: number; demandId?: number }>({
  initialQuery: () => ({ keyword: '', status: undefined, demandId: undefined }),
  fetcher: (p) => getPostPageApi(p),
  immediate: false,
})

const postDialog = ref(false)
const postSubmitting = ref(false)
const postFormRef = ref<FormInstance>()
const postEditingId = ref<number | null>(null)
const postForm = reactive<RecruitPostSaveRequest>({
  demandId: undefined,
  postName: '',
  city: '',
  salaryMin: undefined,
  salaryMax: undefined,
  headCount: 1,
  channel: 1,
  status: 1,
  hrId: undefined,
  remark: '',
})
const postRules: FormRules = {
  postName: [{ required: true, message: '请输入职位名称', trigger: 'blur' }],
  headCount: [{ required: true, message: '请输入招聘人数', trigger: 'blur' }],
}

function resetPostForm() {
  Object.assign(postForm, {
    id: null,
    demandId: undefined,
    postName: '',
    city: '',
    salaryMin: undefined,
    salaryMax: undefined,
    headCount: 1,
    channel: 1,
    status: 1,
    hrId: undefined,
    remark: '',
  })
}

function openPostCreate() {
  postEditingId.value = null
  resetPostForm()
  postDialog.value = true
}

function openPostEdit(row: RecruitPostVO) {
  postEditingId.value = row.id
  Object.assign(postForm, {
    id: row.id,
    demandId: row.demandId,
    postName: row.postName,
    city: row.city || '',
    salaryMin: row.salaryMin,
    salaryMax: row.salaryMax,
    headCount: row.headCount || 1,
    channel: row.channel || 1,
    status: row.status || 1,
    hrId: row.hrId,
    remark: row.remark || '',
  })
  postDialog.value = true
}

async function submitPost() {
  const valid = await postFormRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  postSubmitting.value = true
  try {
    await savePostApi({ ...postForm, id: postEditingId.value ?? undefined })
    ElMessage.success(postEditingId.value ? '已保存' : '已新增')
    postDialog.value = false
    await postQ.refresh()
  } catch (error) {
    logAsyncError('savePost', error)
  } finally {
    postSubmitting.value = false
  }
}

async function removePost(row: RecruitPostVO) {
  try {
    await ElMessageBox.confirm(`确定删除职位「${row.postName}」吗？`, '删除职位', {
      type: 'warning',
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deletePostApi(row.id)
    ElMessage.success('已删除')
    await postQ.refresh()
  } catch (error) {
    logAsyncError('deletePost', error)
  }
}

/* ============================ 候选人 ============================ */
const candidateQ = useListQuery<
  RecruitCandidateVO,
  { keyword: string; stage?: number; source?: number; postId?: number }
>({
  initialQuery: () => ({ keyword: '', stage: undefined, source: undefined, postId: undefined }),
  fetcher: (p) => getCandidatePageApi(p),
  immediate: false,
})

const candidateDialog = ref(false)
const candidateSubmitting = ref(false)
const candidateFormRef = ref<FormInstance>()
const candidateEditingId = ref<number | null>(null)
const candidateForm = reactive<RecruitCandidateSaveRequest>({
  name: '',
  mobile: '',
  email: '',
  gender: undefined,
  birthDate: '',
  postId: undefined,
  demandId: undefined,
  source: 5,
  referrerId: undefined,
  expectSalary: undefined,
  currentSalary: undefined,
  education: '',
  experience: '',
  authorized: 1,
  talentPool: 0,
  remark: '',
})
const candidateRules: FormRules = {
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  mobile: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' },
  ],
  authorized: [{ required: true, message: '请确认是否已获授权', trigger: 'change' }],
}

function resetCandidateForm() {
  Object.assign(candidateForm, {
    id: null,
    name: '',
    mobile: '',
    email: '',
    gender: undefined,
    birthDate: '',
    postId: undefined,
    demandId: undefined,
    source: 5,
    referrerId: undefined,
    expectSalary: undefined,
    currentSalary: undefined,
    education: '',
    experience: '',
    authorized: 1,
    talentPool: 0,
    remark: '',
  })
}

function openCandidateCreate() {
  candidateEditingId.value = null
  resetCandidateForm()
  candidateDialog.value = true
}

function openCandidateEdit(row: RecruitCandidateVO) {
  candidateEditingId.value = row.id
  Object.assign(candidateForm, {
    id: row.id,
    name: row.name,
    mobile: row.mobile || '',
    email: row.email || '',
    gender: row.gender,
    birthDate: row.birthDate || '',
    postId: row.postId,
    demandId: row.demandId,
    source: row.source || 5,
    referrerId: row.referrerId,
    expectSalary: row.expectSalary,
    currentSalary: row.currentSalary,
    education: row.education || '',
    experience: row.experience || '',
    authorized: row.authorized ?? 1,
    talentPool: row.talentPool ?? 0,
    remark: row.remark || '',
  })
  candidateDialog.value = true
}

async function submitCandidate() {
  const valid = await candidateFormRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  candidateSubmitting.value = true
  try {
    await saveCandidateApi({ ...candidateForm, id: candidateEditingId.value ?? undefined })
    ElMessage.success(candidateEditingId.value ? '已保存' : '已新增')
    candidateDialog.value = false
    await candidateQ.refresh()
  } catch (error) {
    logAsyncError('saveCandidate', error)
  } finally {
    candidateSubmitting.value = false
  }
}

/* 阶段流转 */
const stageDialog = ref(false)
const stageSubmitting = ref(false)
const stageForm = reactive({
  candidateId: 0 as number,
  candidateName: '',
  stage: undefined as number | undefined,
  rejectType: undefined as number | undefined,
  rejectReason: '',
  rollback: false,
  reason: '',
})
const stageRules: FormRules = {
  stage: [{ required: true, message: '请选择目标阶段', trigger: 'change' }],
}

function openStage(row: RecruitCandidateVO) {
  stageForm.candidateId = row.id
  stageForm.candidateName = row.name
  stageForm.stage = row.stage
  stageForm.rejectType = undefined
  stageForm.rejectReason = ''
  stageForm.rollback = false
  stageForm.reason = ''
  stageDialog.value = true
}

async function submitStage() {
  if (stageForm.stage == null) {
    ElMessage.warning('请选择目标阶段')
    return
  }
  if (stageForm.stage === 8 && !stageForm.rejectReason) {
    ElMessage.warning('请填写淘汰原因')
    return
  }
  if (stageForm.rollback && !stageForm.reason) {
    ElMessage.warning('回退须填写理由')
    return
  }
  stageSubmitting.value = true
  try {
    await changeCandidateStageApi(stageForm.candidateId, {
      stage: stageForm.stage,
      rejectType: stageForm.stage === 8 ? stageForm.rejectType : undefined,
      rejectReason: stageForm.stage === 8 ? stageForm.rejectReason : undefined,
      rollback: stageForm.rollback,
      reason: stageForm.rollback ? stageForm.reason : undefined,
    })
    ElMessage.success('阶段已更新')
    stageDialog.value = false
    await candidateQ.refresh()
  } catch (error) {
    logAsyncError('changeStage', error)
  } finally {
    stageSubmitting.value = false
  }
}

/* 候选人详情抽屉 */
const detailDrawer = ref(false)
const detailLoading = ref(false)
const detail = ref<RecruitCandidateVO | null>(null)
const detailInterviews = ref<RecruitInterviewVO[]>([])

async function openDetail(row: RecruitCandidateVO) {
  detailDrawer.value = true
  detailLoading.value = true
  detail.value = null
  detailInterviews.value = []
  try {
    const [d, list] = await Promise.all([
      getCandidateDetailApi(row.id),
      getInterviewPageApi({ candidateId: row.id, pageSize: 50 }),
    ])
    detail.value = d
    detailInterviews.value = list?.records ?? []
  } catch (error) {
    logAsyncError('candidateDetail', error)
  } finally {
    detailLoading.value = false
  }
}

async function checkDuplicate() {
  if (!candidateForm.mobile) {
    ElMessage.warning('请先填写手机号')
    return
  }
  try {
    const dup = await findDuplicateApi(candidateForm.mobile, candidateForm.name || undefined)
    if (dup && dup.id) {
      ElMessage.info(`库中已存在该候选人：${dup.name}（阶段 ${dup.stageLabel ?? dup.stage}）`)
    } else {
      ElMessage.success('未发现重复候选人')
    }
  } catch (error) {
    logAsyncError('findDuplicate', error)
  }
}

/* ============================ 面试 ============================ */
const interviewQ = useListQuery<RecruitInterviewVO, { candidateId?: number; status?: number }>({
  initialQuery: () => ({ candidateId: undefined, status: undefined }),
  fetcher: (p) => getInterviewPageApi(p),
  immediate: false,
})

const interviewDialog = ref(false)
const interviewSubmitting = ref(false)
const interviewFormRef = ref<FormInstance>()
const interviewForm = reactive({
  id: null as number | null,
  candidateId: undefined as number | undefined,
  postId: undefined as number | undefined,
  roundNo: 1,
  interviewTime: '',
  way: 1,
  location: '',
  interviewerIds: [] as number[],
  remark: '',
})
const interviewRules: FormRules = {
  candidateId: [{ required: true, message: '请选择候选人', trigger: 'change' }],
  interviewTime: [{ required: true, message: '请选择面试时间', trigger: 'change' }],
  interviewerIds: [{ type: 'array', required: true, message: '至少指定一位面试官', trigger: 'change' }],
}

function openInterviewCreate() {
  interviewForm.id = null
  interviewForm.candidateId = undefined
  interviewForm.postId = undefined
  interviewForm.roundNo = 1
  interviewForm.interviewTime = ''
  interviewForm.way = 1
  interviewForm.location = ''
  interviewForm.interviewerIds = []
  interviewForm.remark = ''
  interviewDialog.value = true
}

async function submitInterview() {
  const valid = await interviewFormRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  interviewSubmitting.value = true
  try {
    await saveInterviewApi({ ...interviewForm })
    ElMessage.success('已安排面试')
    interviewDialog.value = false
    await interviewQ.refresh()
  } catch (error) {
    logAsyncError('saveInterview', error)
  } finally {
    interviewSubmitting.value = false
  }
}

/* 面试评价 */
const feedbackDialog = ref(false)
const feedbackSubmitting = ref(false)
const feedbackForm = reactive<RecruitFeedbackRequest>({
  interviewId: undefined,
  professional: 3,
  communication: 3,
  logicScore: 3,
  cultureMatch: 3,
  conclusion: 1,
  comment: '',
})

function openFeedback(row: RecruitInterviewVO) {
  feedbackForm.interviewId = row.id
  feedbackForm.professional = 3
  feedbackForm.communication = 3
  feedbackForm.logicScore = 3
  feedbackForm.cultureMatch = 3
  feedbackForm.conclusion = 1
  feedbackForm.comment = ''
  feedbackDialog.value = true
}

async function submitFeedback() {
  if (feedbackForm.interviewId == null) {
    return
  }
  feedbackSubmitting.value = true
  try {
    await submitFeedbackApi({ ...feedbackForm })
    ElMessage.success('评价已提交')
    feedbackDialog.value = false
    await interviewQ.refresh()
  } catch (error) {
    logAsyncError('submitFeedback', error)
  } finally {
    feedbackSubmitting.value = false
  }
}

/* 面试汇总 */
const summarizeDialog = ref(false)
const summarizeSubmitting = ref(false)
const summarizeTarget = ref<RecruitInterviewVO | null>(null)
const summarizeForm = reactive({
  conclusion: 1 as number,
  summary: '',
  continueReason: '',
})

async function openSummarize(row: RecruitInterviewVO) {
  if (!row.allSubmitted) {
    ElMessage.warning('还有面试官未提交评价，无法汇总')
    return
  }
  try {
    summarizeTarget.value = await getInterviewDetailApi(row.id)
  } catch (error) {
    logAsyncError('interviewDetail', error)
    return
  }
  summarizeForm.conclusion = 1
  summarizeForm.summary = ''
  summarizeForm.continueReason = ''
  summarizeDialog.value = true
}

const summarizeNeedReason = computed(
  () =>
    summarizeTarget.value?.feedbacks?.some((f) => f.conclusion === 3) &&
    summarizeForm.conclusion === 1,
)

async function submitSummarize() {
  if (!summarizeTarget.value) {
    return
  }
  if (summarizeNeedReason.value && !summarizeForm.continueReason) {
    ElMessage.warning('存在「不通过」评价时，继续推进须填写理由')
    return
  }
  summarizeSubmitting.value = true
  try {
    await summarizeInterviewApi(
      summarizeTarget.value.id,
      summarizeForm.conclusion,
      summarizeForm.summary || undefined,
      summarizeForm.continueReason || undefined,
    )
    ElMessage.success('已汇总')
    summarizeDialog.value = false
    await interviewQ.refresh()
  } catch (error) {
    logAsyncError('summarize', error)
  } finally {
    summarizeSubmitting.value = false
  }
}

/* ============================ Offer ============================ */
const offerQ = useListQuery<RecruitOfferVO, { status?: number; candidateId?: number }>({
  initialQuery: () => ({ status: undefined, candidateId: undefined }),
  fetcher: (p) => getOfferPageApi(p),
  immediate: false,
})

const offerDialog = ref(false)
const offerSubmitting = ref(false)
const offerFormRef = ref<FormInstance>()
const offerForm = reactive<RecruitOfferSaveRequest>({
  candidateId: undefined,
  postId: undefined,
  positionId: undefined,
  positionName: '',
  salary: undefined,
  probationMonths: 3,
  reportDate: '',
  expireDate: '',
  sendWay: 1,
  remark: '',
})
const offerRules: FormRules = {
  candidateId: [{ required: true, message: '请选择候选人', trigger: 'change' }],
  salary: [{ required: true, message: '请输入薪资', trigger: 'blur' }],
}

function openOfferCreate() {
  offerForm.candidateId = undefined
  offerForm.postId = undefined
  offerForm.positionId = undefined
  offerForm.positionName = ''
  offerForm.salary = undefined
  offerForm.probationMonths = 3
  offerForm.reportDate = ''
  offerForm.expireDate = ''
  offerForm.sendWay = 1
  offerForm.remark = ''
  offerDialog.value = true
}

async function submitOffer() {
  const valid = await offerFormRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  offerSubmitting.value = true
  try {
    await saveOfferApi({ ...offerForm })
    ElMessage.success('已发起 Offer')
    offerDialog.value = false
    await offerQ.refresh()
  } catch (error) {
    logAsyncError('saveOffer', error)
  } finally {
    offerSubmitting.value = false
  }
}

/** 候选人下拉：仅终试（stage=4）可发起 Offer */
const offerCandidateOptions = computed(() => candidateOptions.value.filter((c) => c.stage === 4))

async function sendOffer(row: RecruitOfferVO) {
  try {
    await ElMessageBox.confirm(`确定向「${row.candidateName}」发出 Offer 吗？`, '发出 Offer', {
      type: 'warning',
      confirmButtonText: '发出',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await sendOfferApi(row.id, row.sendWay ?? 1)
    ElMessage.success('已发出')
    await offerQ.refresh()
  } catch (error) {
    logAsyncError('sendOffer', error)
  }
}

async function acceptOffer(row: RecruitOfferVO) {
  try {
    await acceptOfferApi(row.id)
    ElMessage.success('已接受')
    await offerQ.refresh()
  } catch (error) {
    logAsyncError('acceptOffer', error)
  }
}

async function refuseOffer(row: RecruitOfferVO) {
  const { value } = await ElMessageBox.prompt('请填写拒绝理由', '拒绝 Offer', {
    type: 'warning',
    confirmButtonText: '拒绝',
    cancelButtonText: '取消',
    inputType: 'textarea',
  }).catch(() => ({ value: null }))
  if (value === null) {
    return
  }
  try {
    await refuseOfferApi(row.id, value || undefined)
    ElMessage.success('已拒绝')
    await offerQ.refresh()
  } catch (error) {
    logAsyncError('refuseOffer', error)
  }
}

/* Offer 转正 */
const convertDialog = ref(false)
const convertSubmitting = ref(false)
const convertForm = reactive<RecruitConvertRequest>({
  offerId: undefined,
  reuseMode: 1,
  targetUserId: undefined,
  workNumber: '',
  remark: '',
})

function openConvert(row: RecruitOfferVO) {
  convertForm.offerId = row.id
  convertForm.reuseMode = 1
  convertForm.targetUserId = undefined
  convertForm.workNumber = ''
  convertForm.remark = ''
  convertDialog.value = true
}

async function submitConvert() {
  if (convertForm.offerId == null) {
    return
  }
  convertSubmitting.value = true
  try {
    await convertOfferApi({ ...convertForm })
    ElMessage.success('已转为员工')
    convertDialog.value = false
    await offerQ.refresh()
  } catch (error) {
    logAsyncError('convertOffer', error)
  } finally {
    convertSubmitting.value = false
  }
}

/* ============================ 看板 ============================ */
const boardLoading = ref(false)
const board = ref<RecruitBoardVO | null>(null)

async function loadBoard() {
  boardLoading.value = true
  try {
    board.value = await getBoardApi()
  } catch (error) {
    logAsyncError('loadBoard', error)
  } finally {
    boardLoading.value = false
  }
}

/* ============================ 公共：懒加载 ============================ */
const TAB_LOADERS: Record<string, () => Promise<void>> = {
  demand: demandQ.load,
  post: postQ.load,
  candidate: candidateQ.load,
  interview: interviewQ.load,
  offer: offerQ.load,
  board: loadBoard,
}

function ensureTabLoaded(key: string) {
  if (loadedTabs.value.has(key)) {
    return
  }
  loadedTabs.value.add(key)
  void TAB_LOADERS[key]?.()
}

watch(activeTab, (key) => ensureTabLoaded(key))

onMounted(async () => {
  try {
    const [depts, users, positions, posts, demands, candidates] = await Promise.all([
      getDeptTreeApi(),
      getUserCandidatesApi(),
      listPositionOptionsApi(),
      getPostPageApi({ pageSize: 200 }),
      getDemandPageApi({ pageSize: 200 }),
      getCandidatePageApi({ pageSize: 200 }),
    ])
    deptTree.value = depts || []
    userCandidates.value = users || []
    positionOptions.value = positions || []
    postOptions.value = posts?.records ?? []
    demandOptions.value = demands?.records ?? []
    candidateOptions.value = candidates?.records ?? []
  } catch (error) {
    logAsyncError('loadRecruitOptions', error)
  }
  ensureTabLoaded('demand')
})
</script>

<template>
  <div class="recruit">
    <el-card shadow="never">
      <el-tabs v-model="activeTab">
        <!-- 需求 -->
        <el-tab-pane label="招聘需求" name="demand">
          <div class="recruit__head">
            <el-form :inline="true" @submit.prevent>
              <el-form-item label="关键词">
                <el-input
                  v-model="demandQ.query.keyword"
                  placeholder="需求编码 / 原因"
                  clearable
                  style="width: 200px"
                  @keyup.enter="demandQ.search"
                />
              </el-form-item>
              <el-form-item label="部门">
                <el-tree-select
                  v-model="demandQ.query.deptId"
                  :data="deptTree"
                  :props="{ label: 'deptName', value: 'id', children: 'children' }"
                  node-key="id"
                  check-strictly
                  clearable
                  placeholder="全部部门"
                  style="width: 160px"
                />
              </el-form-item>
              <el-form-item label="状态">
                <el-select v-model="demandQ.query.status" clearable placeholder="全部" style="width: 130px">
                  <el-option v-for="o in RECRUIT_DEMAND_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="demandQ.search">查询</el-button>
                <el-button @click="demandQ.reset">重置</el-button>
              </el-form-item>
            </el-form>
            <div class="recruit__toolbar">
              <el-button v-permission="'hr:recruit:demand'" type="primary" @click="openDemandCreate">新增需求</el-button>
            </div>
          </div>
          <el-table v-loading="demandQ.loading.value" :data="demandQ.list.value" border>
            <el-table-column prop="demandCode" label="需求编码" width="150" />
            <el-table-column prop="deptName" label="部门" min-width="120" show-overflow-tooltip />
            <el-table-column prop="positionName" label="岗位" min-width="120" show-overflow-tooltip />
            <el-table-column label="人数" width="80" prop="headCount" />
            <el-table-column label="紧急度" width="90">
              <template #default="{ row }">
                <el-tag :type="row.urgency === 2 ? 'danger' : 'info'" effect="plain">{{ row.urgencyLabel }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="expectDate" label="期望到岗" width="120" />
            <el-table-column prop="hrName" label="负责HR" width="100" />
            <el-table-column prop="hiredCount" label="已招" width="70" />
            <el-table-column label="缺口" width="70">
              <template #default="{ row }">
                <span :class="{ 'recruit__over': row.overQuota }">{{ row.quotaGap ?? '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 2 ? 'success' : 'info'" effect="plain">{{ row.statusLabel }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="210" fixed="right" header-align="center" align="center">
              <template #default="{ row }">
                <el-button
                  v-permission="'hr:recruit:demand'"
                  icon-only
                  link
                  type="success"
                  title="审批通过"
                  :disabled="row.status !== 1"
                  @click="approveDemand(row)"
                >
                  审批
                </el-button>
                <el-button
                  v-permission="'hr:recruit:demand'"
                  icon-only
                  link
                  type="warning"
                  title="编辑"
                  @click="openDemandEdit(row)"
                >
                  编辑
                </el-button>
                <el-button
                  v-permission="'hr:recruit:demand'"
                  icon-only
                  link
                  type="danger"
                  title="关闭"
                  :disabled="row.status === 5"
                  @click="closeDemand(row)"
                >
                  关闭
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            class="recruit__pagination"
            :current-page="demandQ.pageNo.value"
            :page-size="demandQ.pageSize.value"
            :page-sizes="[10, 20, 50]"
            :total="demandQ.total.value"
            layout="total, sizes, prev, pager, next, jumper"
            background
            @size-change="demandQ.handleSizeChange"
            @current-change="demandQ.handlePageChange"
          />
        </el-tab-pane>

        <!-- 职位 -->
        <el-tab-pane label="招聘职位" name="post">
          <div class="recruit__head">
            <el-form :inline="true" @submit.prevent>
              <el-form-item label="关键词">
                <el-input
                  v-model="postQ.query.keyword"
                  placeholder="职位名称"
                  clearable
                  style="width: 200px"
                  @keyup.enter="postQ.search"
                />
              </el-form-item>
              <el-form-item label="状态">
                <el-select v-model="postQ.query.status" clearable placeholder="全部" style="width: 130px">
                  <el-option v-for="o in RECRUIT_POST_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="postQ.search">查询</el-button>
                <el-button @click="postQ.reset">重置</el-button>
              </el-form-item>
            </el-form>
            <div class="recruit__toolbar">
              <el-button v-permission="'hr:recruit:post'" type="primary" @click="openPostCreate">新增职位</el-button>
            </div>
          </div>
          <el-table v-loading="postQ.loading.value" :data="postQ.list.value" border>
            <el-table-column prop="demandCode" label="关联需求" width="150" show-overflow-tooltip />
            <el-table-column prop="postName" label="职位名称" min-width="140" show-overflow-tooltip />
            <el-table-column prop="city" label="工作地点" width="100" />
            <el-table-column label="薪资范围" width="160">
              <template #default="{ row }">
                {{ row.salaryMin != null ? row.salaryMin : '?' }} ~ {{ row.salaryMax != null ? row.salaryMax : '?' }}
              </template>
            </el-table-column>
            <el-table-column prop="headCount" label="人数" width="70" />
            <el-table-column prop="channelLabel" label="渠道" width="100" />
            <el-table-column prop="hrName" label="负责HR" width="100" />
            <el-table-column prop="candidateCount" label="投递" width="70" />
            <el-table-column prop="hiredCount" label="入职" width="70" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 2 ? 'success' : 'info'" effect="plain">{{ row.statusLabel }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="160" fixed="right" header-align="center" align="center">
              <template #default="{ row }">
                <el-button
                  v-permission="'hr:recruit:post'"
                  icon-only
                  link
                  type="warning"
                  title="编辑"
                  @click="openPostEdit(row)"
                >
                  编辑
                </el-button>
                <el-button
                  v-permission="'hr:recruit:post'"
                  icon-only
                  link
                  type="danger"
                  title="删除"
                  @click="removePost(row)"
                >
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            class="recruit__pagination"
            :current-page="postQ.pageNo.value"
            :page-size="postQ.pageSize.value"
            :page-sizes="[10, 20, 50]"
            :total="postQ.total.value"
            layout="total, sizes, prev, pager, next, jumper"
            background
            @size-change="postQ.handleSizeChange"
            @current-change="postQ.handlePageChange"
          />
        </el-tab-pane>

        <!-- 候选人 -->
        <el-tab-pane label="候选人" name="candidate">
          <div class="recruit__head">
            <el-form :inline="true" @submit.prevent>
              <el-form-item label="关键词">
                <el-input
                  v-model="candidateQ.query.keyword"
                  placeholder="姓名 / 手机号"
                  clearable
                  style="width: 200px"
                  @keyup.enter="candidateQ.search"
                />
              </el-form-item>
              <el-form-item label="阶段">
                <el-select v-model="candidateQ.query.stage" clearable placeholder="全部" style="width: 120px">
                  <el-option v-for="o in RECRUIT_STAGE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
              </el-form-item>
              <el-form-item label="来源">
                <el-select v-model="candidateQ.query.source" clearable placeholder="全部" style="width: 120px">
                  <el-option v-for="o in RECRUIT_SOURCE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="candidateQ.search">查询</el-button>
                <el-button @click="candidateQ.reset">重置</el-button>
              </el-form-item>
            </el-form>
            <div class="recruit__toolbar">
              <el-button v-permission="'hr:recruit:candidate'" type="primary" @click="openCandidateCreate">
                新增候选人
              </el-button>
            </div>
          </div>
          <el-table v-loading="candidateQ.loading.value" :data="candidateQ.list.value" border>
            <el-table-column prop="name" label="姓名" width="100" />
            <el-table-column prop="mobileMasked" label="手机号" width="130" />
            <el-table-column prop="genderLabel" label="性别" width="70" />
            <el-table-column prop="postName" label="应聘职位" min-width="120" show-overflow-tooltip />
            <el-table-column prop="sourceLabel" label="来源" width="100" />
            <el-table-column label="阶段" width="100">
              <template #default="{ row }">
                <el-tag effect="plain">{{ row.stageLabel }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="interviewCount" label="面试次数" width="90" />
            <el-table-column prop="referrerName" label="内推人" width="100" />
            <el-table-column label="操作" width="170" fixed="right" header-align="center" align="center">
              <template #default="{ row }">
                <el-button icon-only link type="primary" title="详情" @click="openDetail(row)">详情</el-button>
                <el-button
                  v-permission="'hr:recruit:candidate'"
                  icon-only
                  link
                  type="warning"
                  title="阶段流转"
                  @click="openStage(row)"
                >
                  阶段
                </el-button>
                <el-button
                  v-permission="'hr:recruit:candidate'"
                  icon-only
                  link
                  type="success"
                  title="编辑"
                  @click="openCandidateEdit(row)"
                >
                  编辑
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            class="recruit__pagination"
            :current-page="candidateQ.pageNo.value"
            :page-size="candidateQ.pageSize.value"
            :page-sizes="[10, 20, 50]"
            :total="candidateQ.total.value"
            layout="total, sizes, prev, pager, next, jumper"
            background
            @size-change="candidateQ.handleSizeChange"
            @current-change="candidateQ.handlePageChange"
          />
        </el-tab-pane>

        <!-- 面试 -->
        <el-tab-pane label="面试" name="interview">
          <div class="recruit__head">
            <el-form :inline="true" @submit.prevent>
              <el-form-item label="候选人">
                <el-select
                  v-model="interviewQ.query.candidateId"
                  clearable
                  filterable
                  placeholder="全部"
                  style="width: 180px"
                >
                  <el-option
                    v-for="c in candidateOptions"
                    :key="c.id"
                    :label="`${c.name}（${c.mobileMasked}）`"
                    :value="c.id"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="状态">
                <el-select v-model="interviewQ.query.status" clearable placeholder="全部" style="width: 120px">
                  <el-option v-for="o in RECRUIT_INTERVIEW_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="interviewQ.search">查询</el-button>
                <el-button @click="interviewQ.reset">重置</el-button>
              </el-form-item>
            </el-form>
            <div class="recruit__toolbar">
              <el-button v-permission="'hr:recruit:interview'" type="primary" @click="openInterviewCreate">
                安排面试
              </el-button>
            </div>
          </div>
          <el-table v-loading="interviewQ.loading.value" :data="interviewQ.list.value" border>
            <el-table-column prop="candidateName" label="候选人" width="100" />
            <el-table-column prop="postName" label="职位" min-width="120" show-overflow-tooltip />
            <el-table-column prop="roundNo" label="轮次" width="70" />
            <el-table-column prop="interviewTime" label="面试时间" width="160" />
            <el-table-column prop="wayLabel" label="方式" width="80" />
            <el-table-column label="面试官" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ (row.interviewerNames || []).join('、') || '-' }}</template>
            </el-table-column>
            <el-table-column label="评价" width="110">
              <template #default="{ row }">{{ row.feedbackCount }}/{{ row.interviewerCount }}</template>
            </el-table-column>
            <el-table-column prop="conclusionLabel" label="结论" width="90" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 2 ? 'success' : 'info'" effect="plain">{{ row.statusLabel }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right" header-align="center" align="center">
              <template #default="{ row }">
                <el-button icon-only link type="primary" title="提交评价" @click="openFeedback(row)">评价</el-button>
                <el-button
                  v-permission="'hr:recruit:interview'"
                  icon-only
                  link
                  type="success"
                  title="汇总结论"
                  :disabled="!row.allSubmitted"
                  @click="openSummarize(row)"
                >
                  汇总
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            class="recruit__pagination"
            :current-page="interviewQ.pageNo.value"
            :page-size="interviewQ.pageSize.value"
            :page-sizes="[10, 20, 50]"
            :total="interviewQ.total.value"
            layout="total, sizes, prev, pager, next, jumper"
            background
            @size-change="interviewQ.handleSizeChange"
            @current-change="interviewQ.handlePageChange"
          />
        </el-tab-pane>

        <!-- Offer -->
        <el-tab-pane label="Offer" name="offer">
          <div class="recruit__head">
            <el-form :inline="true" @submit.prevent>
              <el-form-item label="状态">
                <el-select v-model="offerQ.query.status" clearable placeholder="全部" style="width: 130px">
                  <el-option v-for="o in RECRUIT_OFFER_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="offerQ.search">查询</el-button>
                <el-button @click="offerQ.reset">重置</el-button>
              </el-form-item>
            </el-form>
            <div class="recruit__toolbar">
              <el-button v-permission="'hr:recruit:offer'" type="primary" @click="openOfferCreate">发起 Offer</el-button>
            </div>
          </div>
          <el-table v-loading="offerQ.loading.value" :data="offerQ.list.value" border>
            <el-table-column prop="offerNo" label="Offer 编号" width="160" />
            <el-table-column prop="candidateName" label="候选人" width="100" />
            <el-table-column prop="positionName" label="岗位" min-width="120" show-overflow-tooltip />
            <el-table-column label="薪资" width="100">
              <template #default="{ row }">{{ row.salary != null ? row.salary : '-' }}</template>
            </el-table-column>
            <el-table-column prop="reportDate" label="报到日期" width="120" />
            <el-table-column prop="sendWayLabel" label="发送方式" width="100" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 3 ? 'success' : 'info'" effect="plain">{{ row.statusLabel }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="280" fixed="right" header-align="center" align="center">
              <template #default="{ row }">
                <el-button
                  v-permission="'hr:recruit:offer'"
                  icon-only
                  link
                  type="warning"
                  title="发出"
                  :disabled="row.status !== 1"
                  @click="sendOffer(row)"
                >
                  发出
                </el-button>
                <el-button icon-only link type="success" title="接受" @click="acceptOffer(row)">接受</el-button>
                <el-button icon-only link type="danger" title="拒绝" @click="refuseOffer(row)">拒绝</el-button>
                <el-button
                  v-permission="'hr:recruit:offer'"
                  icon-only
                  link
                  type="primary"
                  title="转正为员工"
                  @click="openConvert(row)"
                >
                  转正
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            class="recruit__pagination"
            :current-page="offerQ.pageNo.value"
            :page-size="offerQ.pageSize.value"
            :page-sizes="[10, 20, 50]"
            :total="offerQ.total.value"
            layout="total, sizes, prev, pager, next, jumper"
            background
            @size-change="offerQ.handleSizeChange"
            @current-change="offerQ.handlePageChange"
          />
        </el-tab-pane>

        <!-- 看板 -->
        <el-tab-pane label="招聘看板" name="board">
          <div class="recruit__board">
            <el-row :gutter="12">
              <el-col :span="6">
                <el-statistic title="在招职位数" :value="board?.postingCount ?? 0" />
              </el-col>
              <el-col :span="6">
                <el-statistic title="需求总人数" :value="board?.demandHeadCount ?? 0" />
              </el-col>
              <el-col :span="6">
                <el-statistic title="已招到岗" :value="board?.hiredCount ?? 0" />
              </el-col>
              <el-col :span="6">
                <el-statistic title="平均周期(天)" :value="board?.avgCycleDays ?? 0" />
              </el-col>
            </el-row>

            <el-divider content-position="left">阶段漏斗</el-divider>
            <el-table :data="board?.stageFunnel ?? []" border size="small">
              <el-table-column prop="label" label="阶段" width="120" />
              <el-table-column prop="count" label="人数" width="100" />
              <el-table-column label="转化率" width="100">
                <template #default="{ row }">{{ row.convertRate != null ? row.convertRate + '%' : '-' }}</template>
              </el-table-column>
            </el-table>

            <el-divider content-position="left">渠道转化</el-divider>
            <el-table :data="board?.channelStats ?? []" border size="small">
              <el-table-column prop="label" label="渠道" width="120" />
              <el-table-column prop="resumeCount" label="简历数" width="100" />
              <el-table-column prop="interviewCount" label="面试数" width="100" />
              <el-table-column prop="hireCount" label="入职数" width="100" />
              <el-table-column label="面试转化率" width="120">
                <template #default="{ row }">{{ row.interviewRate != null ? row.interviewRate + '%' : '-' }}</template>
              </el-table-column>
              <el-table-column label="入职转化率" width="120">
                <template #default="{ row }">{{ row.hireRate != null ? row.hireRate + '%' : '-' }}</template>
              </el-table-column>
            </el-table>

            <el-divider content-position="left">淘汰原因分布</el-divider>
            <el-table :data="board?.rejectStats ?? []" border size="small">
              <el-table-column prop="label" label="原因" width="160" />
              <el-table-column prop="count" label="人数" width="100" />
            </el-table>

            <el-divider content-position="left">部门维度</el-divider>
            <el-table :data="board?.deptStats ?? []" border size="small">
              <el-table-column prop="deptName" label="部门" min-width="140" />
              <el-table-column prop="demandCount" label="需求数" width="100" />
              <el-table-column prop="headCount" label="需求人数" width="100" />
              <el-table-column prop="hiredCount" label="到岗人数" width="100" />
            </el-table>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 需求弹窗 -->
    <el-dialog v-model="demandDialog" :title="demandEditingId ? '编辑需求' : '新增需求'" width="620px" destroy-on-close>
      <el-form ref="demandFormRef" :model="demandForm" :rules="demandRules" label-width="100px">
        <el-form-item label="需求部门" prop="deptId">
          <el-tree-select
            v-model="demandForm.deptId"
            :data="deptTree"
            :props="{ label: 'deptName', value: 'id', children: 'children' }"
            node-key="id"
            check-strictly
            placeholder="请选择部门"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="需求岗位" prop="positionId">
          <el-select v-model="demandForm.positionId" placeholder="请选择岗位" filterable style="width: 100%">
            <el-option v-for="p in positionOptions" :key="p.id" :label="`${p.positionName}（${p.positionCode}）`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="招聘人数" prop="headCount">
          <el-input-number v-model="demandForm.headCount" :min="1" :max="999" controls-position="right" />
        </el-form-item>
        <el-form-item label="紧急度">
          <el-select v-model="demandForm.urgency" style="width: 100%">
            <el-option v-for="o in RECRUIT_URGENCY_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="期望到岗">
          <el-date-picker v-model="demandForm.expectDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width: 100%" />
        </el-form-item>
        <el-form-item label="学历要求">
          <el-input v-model="demandForm.education" placeholder="如 本科及以上" />
        </el-form-item>
        <el-form-item label="经验要求">
          <el-input v-model="demandForm.experience" placeholder="如 3年以上" />
        </el-form-item>
        <el-form-item label="技能要求">
          <el-input v-model="demandForm.skillRequire" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="薪资范围">
          <el-input-number v-model="demandForm.salaryMin" :min="0" :max="9999999" controls-position="right" /> ~
          <el-input-number v-model="demandForm.salaryMax" :min="0" :max="9999999" controls-position="right" />
        </el-form-item>
        <el-form-item label="招聘原因">
          <el-select v-model="demandForm.reasonType" style="width: 100%">
            <el-option v-for="o in RECRUIT_REASON_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="原因说明">
          <el-input v-model="demandForm.reason" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="负责HR">
          <el-select v-model="demandForm.hrId" clearable filterable placeholder="请选择" style="width: 100%">
            <el-option v-for="u in userCandidates" :key="u.id" :label="u.realName" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="demandForm.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="demandDialog = false">取消</el-button>
        <el-button type="primary" :loading="demandSubmitting" @click="submitDemand">确定</el-button>
      </template>
    </el-dialog>

    <!-- 职位弹窗 -->
    <el-dialog v-model="postDialog" :title="postEditingId ? '编辑职位' : '新增职位'" width="560px" destroy-on-close>
      <el-form ref="postFormRef" :model="postForm" :rules="postRules" label-width="100px">
        <el-form-item label="关联需求">
          <el-select v-model="postForm.demandId" clearable filterable placeholder="可不关联" style="width: 100%">
            <el-option v-for="d in demandOptions" :key="d.id" :label="`${d.demandCode} ${d.positionName}`" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="职位名称" prop="postName">
          <el-input v-model="postForm.postName" maxlength="100" />
        </el-form-item>
        <el-form-item label="工作地点">
          <el-input v-model="postForm.city" maxlength="50" />
        </el-form-item>
        <el-form-item label="薪资范围">
          <el-input-number v-model="postForm.salaryMin" :min="0" :max="9999999" controls-position="right" /> ~
          <el-input-number v-model="postForm.salaryMax" :min="0" :max="9999999" controls-position="right" />
        </el-form-item>
        <el-form-item label="招聘人数" prop="headCount">
          <el-input-number v-model="postForm.headCount" :min="1" :max="999" controls-position="right" />
        </el-form-item>
        <el-form-item label="渠道">
          <el-select v-model="postForm.channel" style="width: 100%">
            <el-option v-for="o in RECRUIT_CHANNEL_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="postForm.status" style="width: 100%">
            <el-option v-for="o in RECRUIT_POST_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="负责HR">
          <el-select v-model="postForm.hrId" clearable filterable placeholder="请选择" style="width: 100%">
            <el-option v-for="u in userCandidates" :key="u.id" :label="u.realName" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="postForm.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="postDialog = false">取消</el-button>
        <el-button type="primary" :loading="postSubmitting" @click="submitPost">确定</el-button>
      </template>
    </el-dialog>

    <!-- 候选人弹窗 -->
    <el-dialog v-model="candidateDialog" :title="candidateEditingId ? '编辑候选人' : '新增候选人'" width="620px" destroy-on-close>
      <el-form ref="candidateFormRef" :model="candidateForm" :rules="candidateRules" label-width="100px">
        <el-form-item label="姓名" prop="name">
          <el-input v-model="candidateForm.name" maxlength="50" />
        </el-form-item>
        <el-form-item label="手机号" prop="mobile">
          <el-input v-model="candidateForm.mobile" maxlength="11" />
        </el-form-item>
        <el-form-item label="查重">
          <el-button @click="checkDuplicate">查重</el-button>
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="candidateForm.email" maxlength="100" />
        </el-form-item>
        <el-form-item label="性别">
          <el-select v-model="candidateForm.gender" clearable placeholder="请选择" style="width: 100%">
            <el-option v-for="o in RECRUIT_GENDER_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="出生日期">
          <el-date-picker v-model="candidateForm.birthDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width: 100%" />
        </el-form-item>
        <el-form-item label="应聘职位">
          <el-select v-model="candidateForm.postId" clearable filterable placeholder="请选择" style="width: 100%">
            <el-option v-for="p in postOptions" :key="p.id" :label="p.postName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联需求">
          <el-select v-model="candidateForm.demandId" clearable filterable placeholder="可不关联" style="width: 100%">
            <el-option v-for="d in demandOptions" :key="d.id" :label="`${d.demandCode} ${d.positionName}`" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源">
          <el-select v-model="candidateForm.source" style="width: 100%">
            <el-option v-for="o in RECRUIT_SOURCE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="内推人">
          <el-select v-model="candidateForm.referrerId" clearable filterable placeholder="请选择" style="width: 100%">
            <el-option v-for="u in userCandidates" :key="u.id" :label="u.realName" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="期望薪资">
          <el-input-number v-model="candidateForm.expectSalary" :min="0" :max="9999999" controls-position="right" />
        </el-form-item>
        <el-form-item label="当前薪资">
          <el-input-number v-model="candidateForm.currentSalary" :min="0" :max="9999999" controls-position="right" />
        </el-form-item>
        <el-form-item label="学历">
          <el-input v-model="candidateForm.education" maxlength="50" />
        </el-form-item>
        <el-form-item label="经验">
          <el-input v-model="candidateForm.experience" maxlength="50" />
        </el-form-item>
        <el-form-item label="已授权" prop="authorized">
          <el-radio-group v-model="candidateForm.authorized">
            <el-radio v-for="o in RECRUIT_YESNO_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="进人才库">
          <el-radio-group v-model="candidateForm.talentPool">
            <el-radio v-for="o in RECRUIT_YESNO_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="candidateForm.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="candidateDialog = false">取消</el-button>
        <el-button type="primary" :loading="candidateSubmitting" @click="submitCandidate">确定</el-button>
      </template>
    </el-dialog>

    <!-- 阶段流转弹窗 -->
    <el-dialog v-model="stageDialog" title="阶段流转" width="480px" destroy-on-close>
      <el-form :model="stageForm" :rules="stageRules" label-width="90px">
        <el-form-item label="候选人">
          <span>{{ stageForm.candidateName }}</span>
        </el-form-item>
        <el-form-item label="目标阶段" prop="stage">
          <el-select v-model="stageForm.stage" style="width: 100%">
            <el-option v-for="o in RECRUIT_STAGE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <template v-if="stageForm.stage === 8">
          <el-form-item label="淘汰类型">
            <el-select v-model="stageForm.rejectType" clearable placeholder="请选择" style="width: 100%">
              <el-option v-for="o in RECRUIT_REJECT_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="淘汰原因">
            <el-input v-model="stageForm.rejectReason" type="textarea" :rows="2" />
          </el-form-item>
        </template>
        <el-form-item label="回退">
          <el-switch v-model="stageForm.rollback" />
          <span class="recruit__hint">回退需 HR 权限并填写理由</span>
        </el-form-item>
        <el-form-item v-if="stageForm.rollback" label="回退理由">
          <el-input v-model="stageForm.reason" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="stageDialog = false">取消</el-button>
        <el-button type="primary" :loading="stageSubmitting" @click="submitStage">确定</el-button>
      </template>
    </el-dialog>

    <!-- 候选人详情抽屉 -->
    <el-drawer v-model="detailDrawer" title="候选人详情" size="520px">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="姓名">{{ detail.name }}</el-descriptions-item>
            <el-descriptions-item label="手机号(脱敏)">{{ detail.mobileMasked || detail.mobile }}</el-descriptions-item>
            <el-descriptions-item label="性别">{{ detail.genderLabel || '-' }}</el-descriptions-item>
            <el-descriptions-item label="应聘职位">{{ detail.postName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="来源">{{ detail.sourceLabel || '-' }}</el-descriptions-item>
            <el-descriptions-item label="当前阶段">{{ detail.stageLabel }}</el-descriptions-item>
            <el-descriptions-item label="面试次数">{{ detail.interviewCount }}</el-descriptions-item>
            <el-descriptions-item label="学历">{{ detail.education || '-' }}</el-descriptions-item>
            <el-descriptions-item label="经验">{{ detail.experience || '-' }}</el-descriptions-item>
            <el-descriptions-item label="备注">{{ detail.remark || '-' }}</el-descriptions-item>
          </el-descriptions>
          <el-divider content-position="left">面试记录</el-divider>
          <el-table :data="detailInterviews" border size="small">
            <el-table-column prop="roundNo" label="轮次" width="70" />
            <el-table-column prop="interviewTime" label="时间" width="150" />
            <el-table-column prop="wayLabel" label="方式" width="70" />
            <el-table-column prop="conclusionLabel" label="结论" width="80" />
          </el-table>
        </template>
      </div>
    </el-drawer>

    <!-- 面试弹窗 -->
    <el-dialog v-model="interviewDialog" title="安排面试" width="580px" destroy-on-close>
      <el-form ref="interviewFormRef" :model="interviewForm" :rules="interviewRules" label-width="100px">
        <el-form-item label="候选人" prop="candidateId">
          <el-select v-model="interviewForm.candidateId" filterable placeholder="请选择" style="width: 100%">
            <el-option v-for="c in candidateOptions" :key="c.id" :label="`${c.name}（${c.mobileMasked}）`" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="职位">
          <el-select v-model="interviewForm.postId" clearable filterable placeholder="请选择" style="width: 100%">
            <el-option v-for="p in postOptions" :key="p.id" :label="p.postName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="轮次">
          <el-input-number v-model="interviewForm.roundNo" :min="1" :max="20" controls-position="right" />
        </el-form-item>
        <el-form-item label="面试时间" prop="interviewTime">
          <el-date-picker v-model="interviewForm.interviewTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="选择时间" style="width: 100%" />
        </el-form-item>
        <el-form-item label="方式">
          <el-select v-model="interviewForm.way" style="width: 100%">
            <el-option v-for="o in RECRUIT_INTERVIEW_WAY_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="地点/链接">
          <el-input v-model="interviewForm.location" maxlength="200" />
        </el-form-item>
        <el-form-item label="面试官" prop="interviewerIds">
          <el-select v-model="interviewForm.interviewerIds" multiple filterable placeholder="请选择" style="width: 100%">
            <el-option v-for="u in userCandidates" :key="u.id" :label="u.realName" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="interviewForm.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="interviewDialog = false">取消</el-button>
        <el-button type="primary" :loading="interviewSubmitting" @click="submitInterview">确定</el-button>
      </template>
    </el-dialog>

    <!-- 面试评价弹窗 -->
    <el-dialog v-model="feedbackDialog" title="提交面试评价" width="480px" destroy-on-close>
      <el-form :model="feedbackForm" label-width="100px">
        <el-form-item label="专业能力">
          <el-rate v-model="feedbackForm.professional" :max="5" />
        </el-form-item>
        <el-form-item label="沟通表达">
          <el-rate v-model="feedbackForm.communication" :max="5" />
        </el-form-item>
        <el-form-item label="逻辑思维">
          <el-rate v-model="feedbackForm.logicScore" :max="5" />
        </el-form-item>
        <el-form-item label="文化匹配">
          <el-rate v-model="feedbackForm.cultureMatch" :max="5" />
        </el-form-item>
        <el-form-item label="结论">
          <el-select v-model="feedbackForm.conclusion" style="width: 100%">
            <el-option v-for="o in RECRUIT_CONCLUSION_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="评语">
          <el-input v-model="feedbackForm.comment" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="feedbackDialog = false">取消</el-button>
        <el-button type="primary" :loading="feedbackSubmitting" @click="submitFeedback">提交</el-button>
      </template>
    </el-dialog>

    <!-- 面试汇总弹窗 -->
    <el-dialog v-model="summarizeDialog" title="汇总面试结论" width="480px" destroy-on-close>
      <el-form :model="summarizeForm" label-width="100px">
        <el-form-item label="结论">
          <el-select v-model="summarizeForm.conclusion" style="width: 100%">
            <el-option v-for="o in RECRUIT_CONCLUSION_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="总结">
          <el-input v-model="summarizeForm.summary" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item v-if="summarizeNeedReason" label="继续理由">
          <el-input v-model="summarizeForm.continueReason" type="textarea" :rows="2" placeholder="存在不通过评价，须说明继续推进理由" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="summarizeDialog = false">取消</el-button>
        <el-button type="primary" :loading="summarizeSubmitting" @click="submitSummarize">确定</el-button>
      </template>
    </el-dialog>

    <!-- Offer 弹窗 -->
    <el-dialog v-model="offerDialog" title="发起 Offer" width="560px" destroy-on-close>
      <el-form ref="offerFormRef" :model="offerForm" :rules="offerRules" label-width="100px">
        <el-form-item label="候选人" prop="candidateId">
          <el-select v-model="offerForm.candidateId" filterable placeholder="仅终试候选人" style="width: 100%">
            <el-option v-for="c in offerCandidateOptions" :key="c.id" :label="`${c.name}（${c.mobileMasked}）`" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="职位">
          <el-select v-model="offerForm.postId" clearable filterable placeholder="请选择" style="width: 100%">
            <el-option v-for="p in postOptions" :key="p.id" :label="p.postName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="岗位名称">
          <el-input v-model="offerForm.positionName" maxlength="100" />
        </el-form-item>
        <el-form-item label="薪资" prop="salary">
          <el-input-number v-model="offerForm.salary" :min="0" :max="9999999" controls-position="right" />
        </el-form-item>
        <el-form-item label="试用期(月)">
          <el-input-number v-model="offerForm.probationMonths" :min="0" :max="12" controls-position="right" />
        </el-form-item>
        <el-form-item label="报到日期">
          <el-date-picker v-model="offerForm.reportDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width: 100%" />
        </el-form-item>
        <el-form-item label="有效期至">
          <el-date-picker v-model="offerForm.expireDate" type="date" value-format="YYYY-MM-DD" placeholder="不填按5个工作日" style="width: 100%" />
        </el-form-item>
        <el-form-item label="发送方式">
          <el-select v-model="offerForm.sendWay" style="width: 100%">
            <el-option v-for="o in RECRUIT_SEND_WAY_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="offerForm.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="offerDialog = false">取消</el-button>
        <el-button type="primary" :loading="offerSubmitting" @click="submitOffer">确定</el-button>
      </template>
    </el-dialog>

    <!-- Offer 转正弹窗 -->
    <el-dialog v-model="convertDialog" title="Offer 转正为员工" width="480px" destroy-on-close>
      <el-form :model="convertForm" label-width="100px">
        <el-form-item label="处理方式">
          <el-radio-group v-model="convertForm.reuseMode">
            <el-radio :value="1">复用已有档案</el-radio>
            <el-radio :value="2">新建档案</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="convertForm.reuseMode === 1" label="目标员工">
          <el-select v-model="convertForm.targetUserId" clearable filterable placeholder="请选择" style="width: 100%">
            <el-option v-for="u in userCandidates" :key="u.id" :label="u.realName" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="convertForm.reuseMode === 2" label="工号">
          <el-input v-model="convertForm.workNumber" maxlength="50" placeholder="不填按规则生成" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="convertForm.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="convertDialog = false">取消</el-button>
        <el-button type="primary" :loading="convertSubmitting" @click="submitConvert">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.recruit__head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}
.recruit__toolbar {
  flex: 0 0 auto;
}
.recruit__pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
.recruit__over {
  color: var(--el-color-danger);
  font-weight: 600;
}
.recruit__hint {
  margin-left: 8px;
  color: #8a94a5;
  font-size: 12px;
}
.recruit__board {
  padding: 4px 0;
}
</style>
