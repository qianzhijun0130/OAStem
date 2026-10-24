<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  cancelSessionApi,
  checkinEnrollApi,
  deleteCourseApi,
  enrollBatchApi,
  getCoursePageApi,
  getSessionPageApi,
  getStatisticsApi,
  listEnrollsApi,
  saveCourseApi,
  saveSessionApi,
  scoreEnrollApi,
  TRAIN_ASSESS_WAY_OPTIONS,
  TRAIN_CHECKIN_WAY_OPTIONS,
  TRAIN_COURSE_STATUS_OPTIONS,
  TRAIN_REQUIRED_OPTIONS,
  TRAIN_SESSION_STATUS_OPTIONS,
  TRAIN_TEACHER_TYPE_OPTIONS,
  TRAIN_TYPE_OPTIONS,
  type TrainCourseSaveRequest,
  type TrainCourseVO,
  type TrainEnrollBatchRequest,
  type TrainEnrollVO,
  type TrainScoreRequest,
  type TrainSessionSaveRequest,
  type TrainSessionVO,
  type TrainStatisticVO,
} from '@/api/training'
import { getUserCandidatesApi } from '@/api/user'
import { useListQuery } from '@/composables/useListQuery'
import { logAsyncError } from '@/utils/asyncGuard'
import type { UserVO } from '@/api/types'

const userCandidates = ref<UserVO[]>([])
const courseOptions = ref<TrainCourseVO[]>([])
const sessionOptions = ref<TrainSessionVO[]>([])

const activeTab = ref('course')
const loadedTabs = ref<Set<string>>(new Set())

/* ============================ 课程 ============================ */
const courseQ = useListQuery<TrainCourseVO, { keyword: string; type?: number; required?: number }>({
  initialQuery: () => ({ keyword: '', type: undefined, required: undefined }),
  fetcher: (p) => getCoursePageApi(p),
  immediate: false,
})

const courseDialog = ref(false)
const courseSubmitting = ref(false)
const courseFormRef = ref<FormInstance>()
const courseEditingId = ref<number | null>(null)
const courseForm = reactive<TrainCourseSaveRequest>({
  courseCode: '',
  courseName: '',
  trainType: 1,
  teacherType: 1,
  teacherId: undefined,
  teacherName: '',
  teacherOrg: '',
  hours: 0,
  deptIds: '',
  series: '',
  required: 1,
  passScore: 60,
  cost: 0,
  status: 1,
  remark: '',
})
const courseRules: FormRules = {
  courseCode: [{ required: true, message: '请输入课程编码', trigger: 'blur' }],
  courseName: [{ required: true, message: '请输入课程名称', trigger: 'blur' }],
  hours: [{ required: true, message: '请输入学时', trigger: 'blur' }],
}

function resetCourseForm() {
  Object.assign(courseForm, {
    id: null,
    courseCode: '',
    courseName: '',
    trainType: 1,
    teacherType: 1,
    teacherId: undefined,
    teacherName: '',
    teacherOrg: '',
    hours: 0,
    deptIds: '',
    series: '',
    required: 1,
    passScore: 60,
    cost: 0,
    status: 1,
    remark: '',
  })
}

function openCourseCreate() {
  courseEditingId.value = null
  resetCourseForm()
  courseDialog.value = true
}

function openCourseEdit(row: TrainCourseVO) {
  courseEditingId.value = row.id
  Object.assign(courseForm, {
    id: row.id,
    courseCode: row.courseCode,
    courseName: row.courseName,
    trainType: row.trainType || 1,
    teacherType: row.teacherType || 1,
    teacherId: row.teacherId,
    teacherName: row.teacherName || '',
    teacherOrg: row.teacherOrg || '',
    hours: row.hours ?? 0,
    deptIds: row.deptIds || '',
    series: row.series || '',
    required: row.required ?? 1,
    passScore: row.passScore ?? 60,
    cost: row.cost ?? 0,
    status: row.status || 1,
    remark: row.remark || '',
  })
  courseDialog.value = true
}

async function submitCourse() {
  const valid = await courseFormRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  courseSubmitting.value = true
  try {
    await saveCourseApi({ ...courseForm, id: courseEditingId.value ?? undefined })
    ElMessage.success(courseEditingId.value ? '已保存' : '已新增')
    courseDialog.value = false
    await courseQ.refresh()
  } catch (error) {
    logAsyncError('saveCourse', error)
  } finally {
    courseSubmitting.value = false
  }
}

async function removeCourse(row: TrainCourseVO) {
  try {
    await ElMessageBox.confirm(`确定删除课程「${row.courseName}」吗？`, '删除课程', {
      type: 'warning',
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deleteCourseApi(row.id)
    ElMessage.success('已删除')
    await courseQ.refresh()
  } catch (error) {
    logAsyncError('deleteCourse', error)
  }
}

/* ============================ 期次 ============================ */
const sessionQ = useListQuery<TrainSessionVO, { keyword: string; courseId?: number; status?: number }>({
  initialQuery: () => ({ keyword: '', courseId: undefined, status: undefined }),
  fetcher: (p) => getSessionPageApi(p),
  immediate: false,
})

const sessionDialog = ref(false)
const sessionSubmitting = ref(false)
const sessionFormRef = ref<FormInstance>()
const sessionEditingId = ref<number | null>(null)
const sessionForm = reactive<TrainSessionSaveRequest>({
  courseId: undefined,
  sessionName: '',
  startTime: '',
  endTime: '',
  location: '',
  onlineLink: '',
  capacity: 0,
  enrollDeadline: '',
  checkinWay: 1,
  remark: '',
})
const sessionRules: FormRules = {
  courseId: [{ required: true, message: '请选择课程', trigger: 'change' }],
  sessionName: [{ required: true, message: '请输入期次名称', trigger: 'blur' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }],
}

function resetSessionForm() {
  Object.assign(sessionForm, {
    id: null,
    courseId: undefined,
    sessionName: '',
    startTime: '',
    endTime: '',
    location: '',
    onlineLink: '',
    capacity: 0,
    enrollDeadline: '',
    checkinWay: 1,
    remark: '',
  })
}

function openSessionCreate() {
  sessionEditingId.value = null
  resetSessionForm()
  sessionDialog.value = true
}

function openSessionEdit(row: TrainSessionVO) {
  sessionEditingId.value = row.id
  Object.assign(sessionForm, {
    id: row.id,
    courseId: row.courseId,
    sessionName: row.sessionName,
    startTime: row.startTime || '',
    endTime: row.endTime || '',
    location: row.location || '',
    onlineLink: row.onlineLink || '',
    capacity: row.capacity ?? 0,
    enrollDeadline: row.enrollDeadline || '',
    checkinWay: row.checkinWay || 1,
    remark: row.remark || '',
  })
  sessionDialog.value = true
}

async function submitSession() {
  const valid = await sessionFormRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  sessionSubmitting.value = true
  try {
    await saveSessionApi({ ...sessionForm, id: sessionEditingId.value ?? undefined })
    ElMessage.success(sessionEditingId.value ? '已保存' : '已新增')
    sessionDialog.value = false
    await sessionQ.refresh()
  } catch (error) {
    logAsyncError('saveSession', error)
  } finally {
    sessionSubmitting.value = false
  }
}

async function cancelSession(row: TrainSessionVO) {
  try {
    await ElMessageBox.confirm(`确定取消期次「${row.sessionName}」吗？已报名人员将受到影响。`, '取消期次', {
      type: 'warning',
      confirmButtonText: '取消期次',
      cancelButtonText: '返回',
    })
  } catch {
    return
  }
  try {
    await cancelSessionApi(row.id)
    ElMessage.success('已取消期次')
    await sessionQ.refresh()
  } catch (error) {
    logAsyncError('cancelSession', error)
  }
}

/* ============================ 报名 / 成绩（按期次） ============================ */
const enrollSessionId = ref<number | undefined>(undefined)
const enrollLoading = ref(false)
const enrollList = ref<TrainEnrollVO[]>([])

async function loadEnrolls() {
  if (enrollSessionId.value == null) {
    enrollList.value = []
    return
  }
  enrollLoading.value = true
  try {
    enrollList.value = (await listEnrollsApi(enrollSessionId.value)) || []
  } catch (error) {
    logAsyncError('listEnrolls', error)
  } finally {
    enrollLoading.value = false
  }
}

watch(enrollSessionId, () => loadEnrolls())

/* 批量报名 */
const batchDialog = ref(false)
const batchSubmitting = ref(false)
const batchForm = reactive<TrainEnrollBatchRequest>({
  sessionId: undefined,
  userIds: [],
  reason: '',
})

function openBatch() {
  if (enrollSessionId.value == null) {
    ElMessage.warning('请先选择期次')
    return
  }
  batchForm.sessionId = enrollSessionId.value
  batchForm.userIds = []
  batchForm.reason = ''
  batchDialog.value = true
}

async function submitBatch() {
  if (!batchForm.sessionId || !batchForm.userIds || batchForm.userIds.length === 0) {
    ElMessage.warning('请至少选择一名员工')
    return
  }
  batchSubmitting.value = true
  try {
    await enrollBatchApi({ ...batchForm })
    ElMessage.success('已指派报名')
    batchDialog.value = false
    await loadEnrolls()
  } catch (error) {
    logAsyncError('enrollBatch', error)
  } finally {
    batchSubmitting.value = false
  }
}

/* 成绩录入 */
const scoreDialog = ref(false)
const scoreSubmitting = ref(false)
const scoreForm = reactive<TrainScoreRequest>({
  enrollId: undefined,
  score: 60,
  assessWay: 1,
  satisfied: undefined,
  certFileId: undefined,
  remark: '',
})

function openScore(row: TrainEnrollVO) {
  scoreForm.enrollId = row.id
  scoreForm.score = row.score != null ? row.score : 60
  scoreForm.assessWay = row.assessWay || 1
  scoreForm.satisfied = row.satisfied
  scoreForm.certFileId = row.certFileId
  scoreForm.remark = row.remark || ''
  scoreDialog.value = true
}

async function submitScore() {
  if (scoreForm.enrollId == null || scoreForm.score == null) {
    ElMessage.warning('请填写成绩')
    return
  }
  scoreSubmitting.value = true
  try {
    await scoreEnrollApi({ ...scoreForm })
    ElMessage.success('成绩已录入')
    scoreDialog.value = false
    await loadEnrolls()
  } catch (error) {
    logAsyncError('scoreEnroll', error)
  } finally {
    scoreSubmitting.value = false
  }
}

/* 签到（本人，需后端校验） */
async function checkin(row: TrainEnrollVO) {
  try {
    await ElMessageBox.confirm(`确认为「${row.userName}」签到吗？`, '培训签到', {
      type: 'warning',
      confirmButtonText: '签到',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await checkinEnrollApi(row.id)
    ElMessage.success('已签到')
    await loadEnrolls()
  } catch (error) {
    logAsyncError('checkin', error)
  }
}

/* ============================ 统计 ============================ */
const statYear = ref<string>('')
const statLoading = ref(false)
const stat = ref<TrainStatisticVO | null>(null)

async function loadStat() {
  statLoading.value = true
  try {
    stat.value = await getStatisticsApi(statYear.value || undefined)
  } catch (error) {
    logAsyncError('loadStatistics', error)
  } finally {
    statLoading.value = false
  }
}

/* ============================ 懒加载 ============================ */
const TAB_LOADERS: Record<string, () => Promise<void>> = {
  course: courseQ.load,
  session: sessionQ.load,
  enroll: () => Promise.resolve(),
  score: () => Promise.resolve(),
  stat: loadStat,
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
    const [users, courses, sessions] = await Promise.all([
      getUserCandidatesApi(),
      getCoursePageApi({ pageSize: 200 }),
      getSessionPageApi({ pageSize: 300 }),
    ])
    userCandidates.value = users || []
    courseOptions.value = courses?.records ?? []
    sessionOptions.value = sessions?.records ?? []
  } catch (error) {
    logAsyncError('loadTrainOptions', error)
  }
  ensureTabLoaded('course')
})
</script>

<template>
  <div class="training">
    <el-card shadow="never">
      <el-tabs v-model="activeTab">
        <!-- 课程 -->
        <el-tab-pane label="培训课程" name="course">
          <div class="training__head">
            <el-form :inline="true" @submit.prevent>
              <el-form-item label="关键词">
                <el-input
                  v-model="courseQ.query.keyword"
                  placeholder="课程编码 / 名称"
                  clearable
                  style="width: 200px"
                  @keyup.enter="courseQ.search"
                />
              </el-form-item>
              <el-form-item label="类型">
                <el-select v-model="courseQ.query.type" clearable placeholder="全部" style="width: 130px">
                  <el-option v-for="o in TRAIN_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
              </el-form-item>
              <el-form-item label="必修">
                <el-select v-model="courseQ.query.required" clearable placeholder="全部" style="width: 110px">
                  <el-option v-for="o in TRAIN_REQUIRED_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="courseQ.search">查询</el-button>
                <el-button @click="courseQ.reset">重置</el-button>
              </el-form-item>
            </el-form>
            <div class="training__toolbar">
              <el-button v-permission="'hr:training:course'" type="primary" @click="openCourseCreate">
                新增课程
              </el-button>
            </div>
          </div>
          <el-table v-loading="courseQ.loading.value" :data="courseQ.list.value" border>
            <el-table-column prop="courseCode" label="编码" width="130" />
            <el-table-column prop="courseName" label="课程名称" min-width="140" show-overflow-tooltip />
            <el-table-column prop="trainTypeLabel" label="类型" width="110" />
            <el-table-column label="讲师" min-width="120">
              <template #default="{ row }">
                {{ row.teacherType === 2 ? row.teacherName + (row.teacherOrg ? `（${row.teacherOrg}）` : '') : row.teacherName || '-' }}
              </template>
            </el-table-column>
            <el-table-column prop="hours" label="学时" width="80" />
            <el-table-column prop="requiredLabel" label="必修" width="80" />
            <el-table-column prop="sessionCount" label="开课" width="70" />
            <el-table-column prop="completedCount" label="结业" width="70" />
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'" effect="plain">{{ row.statusLabel }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="160" fixed="right" header-align="center" align="center">
              <template #default="{ row }">
                <el-button
                  v-permission="'hr:training:course'"
                  icon-only
                  link
                  type="warning"
                  title="编辑"
                  @click="openCourseEdit(row)"
                >
                  编辑
                </el-button>
                <el-button
                  v-permission="'hr:training:course'"
                  icon-only
                  link
                  type="danger"
                  title="删除"
                  @click="removeCourse(row)"
                >
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            class="training__pagination"
            :current-page="courseQ.pageNo.value"
            :page-size="courseQ.pageSize.value"
            :page-sizes="[10, 20, 50]"
            :total="courseQ.total.value"
            layout="total, sizes, prev, pager, next, jumper"
            background
            @size-change="courseQ.handleSizeChange"
            @current-change="courseQ.handlePageChange"
          />
        </el-tab-pane>

        <!-- 期次 -->
        <el-tab-pane label="培训期次" name="session">
          <div class="training__head">
            <el-form :inline="true" @submit.prevent>
              <el-form-item label="关键词">
                <el-input
                  v-model="sessionQ.query.keyword"
                  placeholder="期次名称"
                  clearable
                  style="width: 200px"
                  @keyup.enter="sessionQ.search"
                />
              </el-form-item>
              <el-form-item label="课程">
                <el-select v-model="sessionQ.query.courseId" clearable filterable placeholder="全部" style="width: 180px">
                  <el-option v-for="c in courseOptions" :key="c.id" :label="c.courseName" :value="c.id" />
                </el-select>
              </el-form-item>
              <el-form-item label="状态">
                <el-select v-model="sessionQ.query.status" clearable placeholder="全部" style="width: 130px">
                  <el-option v-for="o in TRAIN_SESSION_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="sessionQ.search">查询</el-button>
                <el-button @click="sessionQ.reset">重置</el-button>
              </el-form-item>
            </el-form>
            <div class="training__toolbar">
              <el-button v-permission="'hr:training:session'" type="primary" @click="openSessionCreate">
                新增期次
              </el-button>
            </div>
          </div>
          <el-table v-loading="sessionQ.loading.value" :data="sessionQ.list.value" border>
            <el-table-column prop="courseName" label="课程" min-width="130" show-overflow-tooltip />
            <el-table-column prop="sessionName" label="期次" min-width="130" show-overflow-tooltip />
            <el-table-column prop="startTime" label="开始" width="160" />
            <el-table-column prop="endTime" label="结束" width="160" />
            <el-table-column prop="location" label="地点" width="100" show-overflow-tooltip />
            <el-table-column label="名额" width="110">
              <template #default="{ row }">
                {{ row.enrolledCount }}/{{ row.capacity && row.capacity > 0 ? row.capacity : '不限' }}
                <span v-if="row.waitCount" class="training__wait">候补{{ row.waitCount }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : row.status === 4 ? 'info' : 'warning'" effect="plain">
                  {{ row.statusLabel }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="160" fixed="right" header-align="center" align="center">
              <template #default="{ row }">
                <el-button
                  v-permission="'hr:training:session'"
                  icon-only
                  link
                  type="warning"
                  title="编辑"
                  @click="openSessionEdit(row)"
                >
                  编辑
                </el-button>
                <el-button
                  v-permission="'hr:training:session'"
                  icon-only
                  link
                  type="danger"
                  title="取消期次"
                  :disabled="row.status === 4"
                  @click="cancelSession(row)"
                >
                  取消
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            class="training__pagination"
            :current-page="sessionQ.pageNo.value"
            :page-size="sessionQ.pageSize.value"
            :page-sizes="[10, 20, 50]"
            :total="sessionQ.total.value"
            layout="total, sizes, prev, pager, next, jumper"
            background
            @size-change="sessionQ.handleSizeChange"
            @current-change="sessionQ.handlePageChange"
          />
        </el-tab-pane>

        <!-- 报名 -->
        <el-tab-pane label="报名管理" name="enroll">
          <div class="training__head">
            <el-form :inline="true" @submit.prevent>
              <el-form-item label="期次">
                <el-select v-model="enrollSessionId" clearable filterable placeholder="请选择期次" style="width: 280px">
                  <el-option v-for="s in sessionOptions" :key="s.id" :label="`${s.courseName} · ${s.sessionName}`" :value="s.id" />
                </el-select>
              </el-form-item>
            </el-form>
            <div class="training__toolbar">
              <el-button v-permission="'hr:training:enroll'" type="primary" @click="openBatch">批量报名</el-button>
            </div>
          </div>
          <el-table v-loading="enrollLoading" :data="enrollList" border>
            <el-table-column prop="userName" label="姓名" width="100" />
            <el-table-column prop="deptName" label="部门" min-width="120" show-overflow-tooltip />
            <el-table-column prop="enrollWayLabel" label="报名方式" width="100" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag effect="plain">{{ row.statusLabel }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="checkinTime" label="签到时间" width="160" />
            <el-table-column label="成绩" width="80">
              <template #default="{ row }">{{ row.score != null ? row.score : '-' }}</template>
            </el-table-column>
            <el-table-column prop="assignReason" label="指派原因" min-width="140" show-overflow-tooltip />
            <el-table-column label="操作" width="180" fixed="right" header-align="center" align="center">
              <template #default="{ row }">
                <el-button
                  v-permission="'hr:training:score'"
                  icon-only
                  link
                  type="primary"
                  title="成绩录入"
                  @click="openScore(row)"
                >
                  成绩
                </el-button>
                <el-button icon-only link type="success" title="签到" @click="checkin(row)">签到</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 成绩 -->
        <el-tab-pane label="成绩管理" name="score">
          <div class="training__head">
            <el-form :inline="true" @submit.prevent>
              <el-form-item label="期次">
                <el-select v-model="enrollSessionId" clearable filterable placeholder="请选择期次" style="width: 280px">
                  <el-option v-for="s in sessionOptions" :key="s.id" :label="`${s.courseName} · ${s.sessionName}`" :value="s.id" />
                </el-select>
              </el-form-item>
            </el-form>
          </div>
          <el-table v-loading="enrollLoading" :data="enrollList" border>
            <el-table-column prop="userName" label="姓名" width="100" />
            <el-table-column prop="deptName" label="部门" min-width="120" show-overflow-tooltip />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag effect="plain">{{ row.statusLabel }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="成绩" width="80">
              <template #default="{ row }">{{ row.score != null ? row.score : '-' }}</template>
            </el-table-column>
            <el-table-column label="及格" width="80">
              <template #default="{ row }">
                <el-tag :type="row.passed === 1 ? 'success' : 'info'" effect="plain">
                  {{ row.passed === 1 ? '及格' : row.passed === 0 ? '不及格' : '-' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="满意度" width="90">
              <template #default="{ row }">{{ row.satisfied != null ? row.satisfied + ' 星' : '-' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right" header-align="center" align="center">
              <template #default="{ row }">
                <el-button
                  v-permission="'hr:training:score'"
                  icon-only
                  link
                  type="primary"
                  title="成绩录入"
                  @click="openScore(row)"
                >
                  录入
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 统计 -->
        <el-tab-pane label="培训统计" name="stat">
          <div class="training__head">
            <el-form :inline="true" @submit.prevent>
              <el-form-item label="年度">
                <el-input v-model="statYear" placeholder="留空=今年" clearable style="width: 140px" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="loadStat">查询</el-button>
              </el-form-item>
            </el-form>
          </div>
          <div v-loading="statLoading" class="training__stat">
            <el-row :gutter="12">
              <el-col :span="6"><el-statistic title="人均学时" :value="stat?.avgHours ?? 0" /></el-col>
              <el-col :span="6"><el-statistic title="总学时" :value="stat?.totalHours ?? 0" /></el-col>
              <el-col :span="6"><el-statistic title="必修完成率(%)" :value="stat?.requiredFinishRate ?? 0" /></el-col>
              <el-col :span="6"><el-statistic title="课程满意度" :value="stat?.satisfaction ?? 0" /></el-col>
            </el-row>
            <el-row :gutter="12" style="margin-top: 8px">
              <el-col :span="6"><el-statistic title="在职人数" :value="stat?.staffCount ?? 0" /></el-col>
              <el-col :span="6"><el-statistic title="培训总投入" :value="stat?.totalCost ?? 0" /></el-col>
              <el-col :span="6"><el-statistic title="应完成必修" :value="stat?.requiredShouldCount ?? 0" /></el-col>
              <el-col :span="6"><el-statistic title="已完成必修" :value="stat?.requiredDoneCount ?? 0" /></el-col>
            </el-row>

            <el-divider content-position="left">按课程统计</el-divider>
            <el-table :data="stat?.courseStats ?? []" border size="small">
              <el-table-column prop="courseName" label="课程" min-width="140" show-overflow-tooltip />
              <el-table-column label="必修" width="80">
                <template #default="{ row }">{{ row.required === 1 ? '必修' : '选修' }}</template>
              </el-table-column>
              <el-table-column prop="enrollCount" label="报名" width="80" />
              <el-table-column prop="completedCount" label="结业" width="80" />
              <el-table-column label="出勤率" width="90">
                <template #default="{ row }">{{ row.attendRate != null ? row.attendRate + '%' : '-' }}</template>
              </el-table-column>
              <el-table-column label="平均学时" width="90">
                <template #default="{ row }">{{ row.avgHours != null ? row.avgHours : '-' }}</template>
              </el-table-column>
              <el-table-column label="满意度" width="90">
                <template #default="{ row }">{{ row.satisfaction != null ? row.satisfaction : '-' }}</template>
              </el-table-column>
            </el-table>

            <el-divider content-position="left">按部门统计</el-divider>
            <el-table :data="stat?.deptStats ?? []" border size="small">
              <el-table-column prop="deptName" label="部门" min-width="140" show-overflow-tooltip />
              <el-table-column prop="enrollCount" label="参训" width="90" />
              <el-table-column prop="completedCount" label="结业" width="90" />
              <el-table-column label="总学时" width="100">
                <template #default="{ row }">{{ row.totalHours != null ? row.totalHours : '-' }}</template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 课程弹窗 -->
    <el-dialog v-model="courseDialog" :title="courseEditingId ? '编辑课程' : '新增课程'" width="600px" destroy-on-close>
      <el-form ref="courseFormRef" :model="courseForm" :rules="courseRules" label-width="100px">
        <el-form-item label="课程编码" prop="courseCode">
          <el-input v-model="courseForm.courseCode" maxlength="50" />
        </el-form-item>
        <el-form-item label="课程名称" prop="courseName">
          <el-input v-model="courseForm.courseName" maxlength="100" />
        </el-form-item>
        <el-form-item label="课程类型">
          <el-select v-model="courseForm.trainType" style="width: 100%">
            <el-option v-for="o in TRAIN_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="讲师类型">
          <el-select v-model="courseForm.teacherType" style="width: 100%">
            <el-option v-for="o in TRAIN_TEACHER_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="courseForm.teacherType === 1" label="内部讲师">
          <el-select v-model="courseForm.teacherId" clearable filterable placeholder="请选择" style="width: 100%">
            <el-option v-for="u in userCandidates" :key="u.id" :label="u.realName" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="courseForm.teacherType === 2" label="讲师姓名">
          <el-input v-model="courseForm.teacherName" maxlength="50" />
        </el-form-item>
        <el-form-item v-if="courseForm.teacherType === 2" label="讲师机构">
          <el-input v-model="courseForm.teacherOrg" maxlength="100" />
        </el-form-item>
        <el-form-item label="学时" prop="hours">
          <el-input-number v-model="courseForm.hours" :min="0" :max="9999" :precision="1" controls-position="right" />
        </el-form-item>
        <el-form-item label="是否必修">
          <el-select v-model="courseForm.required" style="width: 100%">
            <el-option v-for="o in TRAIN_REQUIRED_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="及格线">
          <el-input-number v-model="courseForm.passScore" :min="0" :max="100" :precision="1" controls-position="right" />
        </el-form-item>
        <el-form-item label="培训成本">
          <el-input-number v-model="courseForm.cost" :min="0" :max="99999999" controls-position="right" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="courseForm.status" style="width: 100%">
            <el-option v-for="o in TRAIN_COURSE_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="courseForm.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="courseDialog = false">取消</el-button>
        <el-button type="primary" :loading="courseSubmitting" @click="submitCourse">确定</el-button>
      </template>
    </el-dialog>

    <!-- 期次弹窗 -->
    <el-dialog v-model="sessionDialog" :title="sessionEditingId ? '编辑期次' : '新增期次'" width="600px" destroy-on-close>
      <el-form ref="sessionFormRef" :model="sessionForm" :rules="sessionRules" label-width="100px">
        <el-form-item label="课程" prop="courseId">
          <el-select v-model="sessionForm.courseId" filterable placeholder="请选择" style="width: 100%">
            <el-option v-for="c in courseOptions" :key="c.id" :label="c.courseName" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="期次名称" prop="sessionName">
          <el-input v-model="sessionForm.sessionName" maxlength="100" placeholder="如 2026春季班" />
        </el-form-item>
        <el-form-item label="开始时间" prop="startTime">
          <el-date-picker v-model="sessionForm.startTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="选择时间" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束时间" prop="endTime">
          <el-date-picker v-model="sessionForm.endTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="选择时间" style="width: 100%" />
        </el-form-item>
        <el-form-item label="地点">
          <el-input v-model="sessionForm.location" maxlength="200" />
        </el-form-item>
        <el-form-item label="线上链接">
          <el-input v-model="sessionForm.onlineLink" maxlength="300" />
        </el-form-item>
        <el-form-item label="名额">
          <el-input-number v-model="sessionForm.capacity" :min="0" :max="99999" controls-position="right" />
          <span class="training__hint">0 表示不限</span>
        </el-form-item>
        <el-form-item label="报名截止">
          <el-date-picker v-model="sessionForm.enrollDeadline" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="选择时间" style="width: 100%" />
        </el-form-item>
        <el-form-item label="签到方式">
          <el-select v-model="sessionForm.checkinWay" style="width: 100%">
            <el-option v-for="o in TRAIN_CHECKIN_WAY_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="sessionForm.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="sessionDialog = false">取消</el-button>
        <el-button type="primary" :loading="sessionSubmitting" @click="submitSession">确定</el-button>
      </template>
    </el-dialog>

    <!-- 批量报名弹窗 -->
    <el-dialog v-model="batchDialog" title="HR 批量指派报名" width="520px" destroy-on-close>
      <el-form :model="batchForm" label-width="90px">
        <el-form-item label="员工" required>
          <el-select v-model="batchForm.userIds" multiple filterable placeholder="请选择员工" style="width: 100%">
            <el-option v-for="u in userCandidates" :key="u.id" :label="u.realName" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="指派原因">
          <el-input v-model="batchForm.reason" type="textarea" :rows="3" maxlength="500" show-word-limit />
          <span class="training__hint">超额指派须说明原因</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchDialog = false">取消</el-button>
        <el-button type="primary" :loading="batchSubmitting" @click="submitBatch">确定指派</el-button>
      </template>
    </el-dialog>

    <!-- 成绩录入弹窗 -->
    <el-dialog v-model="scoreDialog" title="成绩录入" width="460px" destroy-on-close>
      <el-form :model="scoreForm" label-width="90px">
        <el-form-item label="成绩" required>
          <el-input-number v-model="scoreForm.score" :min="0" :max="100" :precision="1" controls-position="right" />
        </el-form-item>
        <el-form-item label="考核方式">
          <el-select v-model="scoreForm.assessWay" style="width: 100%">
            <el-option v-for="o in TRAIN_ASSESS_WAY_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="满意度">
          <el-rate v-model="scoreForm.satisfied" :max="5" allow-half />
        </el-form-item>
        <el-form-item label="评语">
          <el-input v-model="scoreForm.remark" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="scoreDialog = false">取消</el-button>
        <el-button type="primary" :loading="scoreSubmitting" @click="submitScore">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.training__head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}
.training__toolbar {
  flex: 0 0 auto;
}
.training__pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
.training__hint {
  margin-left: 8px;
  color: #8a94a5;
  font-size: 12px;
}
.training__wait {
  margin-left: 6px;
  color: var(--el-color-warning);
  font-size: 12px;
}
.training__stat {
  padding: 4px 0;
}
</style>
