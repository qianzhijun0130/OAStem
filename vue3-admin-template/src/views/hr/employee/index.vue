<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import {
  exportEmployeeApi,
  getEmployeeDetailApi,
  getEmployeePageApi,
  listPositionOptionsApi,
  updateEmployeeProfileApi,
} from '@/api/hr'
import CompletenessPanel from './components/CompletenessPanel.vue'
import { getDeptTreeApi } from '@/api/dept'
import { useListQuery } from '@/composables/useListQuery'
import { logAsyncError } from '@/utils/asyncGuard'
import type {
  DeptVO,
  EmployeeDetailVO,
  EmployeePageQuery,
  EmployeeProfileRequest,
  EmployeeVO,
  PositionVO,
} from '@/api/types'

const EMPLOYMENT_STATUS = [
  { value: 1, label: '待入职', type: 'info' as const },
  { value: 2, label: '试用', type: 'warning' as const },
  { value: 3, label: '正式', type: 'success' as const },
  { value: 4, label: '待离职', type: 'warning' as const },
  { value: 5, label: '已离职', type: 'info' as const },
  { value: 6, label: '退休', type: 'info' as const },
]

const deptTree = ref<DeptVO[]>([])
const positionOptions = ref<PositionVO[]>([])

const { list, total, loading, query, pageNo, pageSize, load, search, reset, refresh, handleSizeChange, handlePageChange } =
  useListQuery<EmployeeVO, EmployeePageQuery>({
    initialQuery: () => ({
      keyword: '',
      deptId: undefined,
      positionId: undefined,
      employmentStatus: undefined,
    }),
    fetcher: (params) => getEmployeePageApi(params),
  })

function statusMeta(status: number) {
  return EMPLOYMENT_STATUS.find((s) => s.value === status) || { label: '未知', type: 'info' as const }
}

/* ---------------- 档案抽屉 ---------------- */

const drawerVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<EmployeeDetailVO | null>(null)
const activeTab = ref('basic')

const editing = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<EmployeeProfileRequest>({})

const rules: FormRules = {
  mobile: [{ pattern: /^$|^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }],
  idCard: [{ pattern: /^$|^\d{17}[\dXx]$/, message: '身份证号格式不正确', trigger: 'blur' }],
  emergencyPhone: [{ pattern: /^$|^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }],
}

const statusOptions = computed(() =>
  // 编辑状态下只允许「当前状态」与合法目标，避免前后端规则不一致时用户白填一遍
  EMPLOYMENT_STATUS.map((s) => ({ ...s, disabled: !canTransition(detail.value?.employmentStatus, s.value) })),
)

/** 与后端 EmploymentStatusEnum 的跳转表保持一致；已离职/退休为终态 */
function canTransition(from: number | undefined, to: number): boolean {
  if (!from || from === to) {
    return true
  }
  const allow: Record<number, number[]> = {
    1: [2, 5],
    2: [3, 5],
    3: [4, 5, 6],
    4: [3, 5],
    5: [],
    6: [],
  }
  return (allow[from] || []).includes(to)
}

async function openDetail(row: EmployeeVO) {
  drawerVisible.value = true
  editing.value = false
  activeTab.value = 'basic'
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await getEmployeeDetailApi(row.id)
    if (!positionOptions.value.length) {
      positionOptions.value = (await listPositionOptionsApi()) || []
    }
  } catch (error) {
    logAsyncError('loadEmployeeDetail', error)
  } finally {
    detailLoading.value = false
  }
}

function startEdit() {
  if (!detail.value) {
    return
  }
  Object.assign(form, {
    positionId: detail.value.positionId,
    directLeaderId: detail.value.directLeaderId,
    employmentStatus: detail.value.employmentStatus,
    changeRemark: '',
    mobile: detail.value.mobile,
    email: detail.value.email,
    workStartDate: detail.value.workStartDate,
    probationEndDate: detail.value.probationEndDate,
    birthDate: detail.value.birthDate,
    maritalStatus: detail.value.maritalStatus,
    nativePlace: detail.value.nativePlace,
    address: detail.value.address,
    emergencyName: detail.value.emergencyName,
    emergencyPhone: detail.value.emergencyPhone,
  })
  editing.value = true
}

async function saveProfile() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid || !detail.value) {
    return
  }
  saving.value = true
  try {
    await updateEmployeeProfileApi(detail.value.id, { ...form })
    ElMessage.success('档案已更新')
    editing.value = false
    const currentId = detail.value.id
    detail.value = await getEmployeeDetailApi(currentId)
    await refresh()
  } catch (error) {
    logAsyncError('updateEmployeeProfile', error)
  } finally {
    saving.value = false
  }
}

/* ---------------- 导出 ---------------- */

const exporting = ref(false)

async function exportRoster() {
  exporting.value = true
  try {
    // 建链、命名、revoke 与「后端返 JSON 报错时提示」都已收敛在 exportEmployeeApi → downloadFile，
    // 页面不再接触 Blob（此前 `blob as Blob` 强转拿到的是 AxiosResponse，点了没反应）
    await exportEmployeeApi({ ...query })
  } catch (error) {
    logAsyncError('exportEmployee', error)
  } finally {
    exporting.value = false
  }
}

onMounted(async () => {
  try {
    deptTree.value = (await getDeptTreeApi()) || []
    positionOptions.value = (await listPositionOptionsApi()) || []
  } catch (error) {
    logAsyncError('loadHrOptions', error)
  }
  await load()
})
</script>

<template>
  <div class="hr-employee">
    <!-- 建档完整度：只读统计，点卡片看待补清单（BIZ-OPT-09） -->
    <el-card shadow="never" class="hr-employee__completeness">
      <CompletenessPanel />
    </el-card>

    <el-card shadow="never">
      <div class="hr-employee__head">
        <el-form :inline="true" class="hr-employee__filters" @submit.prevent>
          <el-form-item label="关键词">
            <el-input
              v-model="query.keyword"
              placeholder="姓名 / 工号 / 手机号"
              clearable
              style="width: 190px"
              @keyup.enter="search"
            />
          </el-form-item>
          <el-form-item label="部门">
            <el-tree-select
              v-model="query.deptId"
              :data="deptTree"
              :props="{ label: 'deptName', value: 'id', children: 'children' }"
              node-key="id"
              check-strictly
              clearable
              placeholder="全部（含下级）"
              style="width: 170px"
            />
          </el-form-item>
          <el-form-item label="岗位">
            <el-select v-model="query.positionId" clearable filterable placeholder="全部" style="width: 160px">
              <el-option v-for="p in positionOptions" :key="p.id" :label="p.positionName" :value="p.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="任职状态">
            <el-select v-model="query.employmentStatus" clearable placeholder="在职" style="width: 120px">
              <el-option v-for="s in EMPLOYMENT_STATUS" :key="s.value" :label="s.label" :value="s.value" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="search">查询</el-button>
            <el-button @click="reset">重置</el-button>
          </el-form-item>
        </el-form>
        <div class="hr-employee__toolbar">
          <el-button v-permission="'hr:employee:export'" :loading="exporting" @click="exportRoster">导出花名册</el-button>
        </div>
      </div>

      <el-alert
        class="hr-employee__tip"
        type="info"
        :closable="false"
        show-icon
        title="默认只查在职（试用 / 正式 / 待离职）；要看离职员工请显式选择任职状态。新增员工请在「系统管理 → 用户管理」中操作。"
      />

      <el-table v-loading="loading" :data="list" border row-key="id">
        <el-table-column prop="workNumber" label="工号" width="110" />
        <el-table-column prop="realName" label="姓名" width="110" />
        <el-table-column label="性别" width="70">
          <template #default="{ row }">{{ row.gender === 2 ? '女' : '男' }}</template>
        </el-table-column>
        <el-table-column prop="deptName" label="部门" min-width="130" show-overflow-tooltip />
        <el-table-column label="岗位" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.positionName || row.position || '-' }}</template>
        </el-table-column>
        <el-table-column label="任职状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusMeta(row.employmentStatus).type" effect="plain">
              {{ row.employmentStatusLabel || statusMeta(row.employmentStatus).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="entryDate" label="入职日期" width="115">
          <template #default="{ row }">{{ row.entryDate || '-' }}</template>
        </el-table-column>
        <el-table-column prop="seniority" label="司龄" width="90">
          <template #default="{ row }">{{ row.seniority || '-' }}</template>
        </el-table-column>
        <el-table-column prop="mobile" label="手机号" width="130" />
        <el-table-column prop="directLeaderName" label="直属上级" width="110">
          <template #default="{ row }">{{ row.directLeaderName || '-' }}</template>
        </el-table-column>
        <el-table-column label="账号" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" effect="plain">
              {{ row.status === 1 ? '启用' : row.status === 0 ? '禁用' : '锁定' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button icon-only link type="primary" title="查看档案" @click="openDetail(row)">档案</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="hr-employee__pagination"
        :current-page="pageNo"
        :page-size="pageSize"
        :page-sizes="[10, 20, 50]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
      />
    </el-card>

    <!-- 档案抽屉 -->
    <el-drawer v-model="drawerVisible" size="720px" :title="detail ? `${detail.realName} 的档案` : '员工档案'">
      <div v-loading="detailLoading" class="hr-emp-detail">
        <template v-if="detail">
          <div class="hr-emp-detail__head">
            <div class="hr-emp-detail__avatar">{{ detail.realName?.charAt(0) || '?' }}</div>
            <div class="hr-emp-detail__meta">
              <div class="hr-emp-detail__name">
                {{ detail.realName }}
                <el-tag :type="statusMeta(detail.employmentStatus).type" effect="plain" size="small">
                  {{ detail.employmentStatusLabel }}
                </el-tag>
              </div>
              <div class="hr-emp-detail__sub">
                {{ detail.deptName || '-' }} · {{ detail.positionName || detail.position || '未设置岗位' }} ·
                工号 {{ detail.workNumber || '-' }} · 司龄 {{ detail.seniority || '-' }}
              </div>
            </div>
            <el-button v-permission="'hr:employee:update'" type="primary" plain @click="editing ? (editing = false) : startEdit()">
              {{ editing ? '取消编辑' : '编辑档案' }}
            </el-button>
          </div>

          <el-tabs v-model="activeTab">
            <el-tab-pane label="基本信息" name="basic">
              <el-form v-if="editing" ref="formRef" :model="form" :rules="rules" label-width="110px" class="hr-emp-form">
                <el-divider content-position="left">任职信息</el-divider>
                <el-form-item label="任职状态">
                  <el-select v-model="form.employmentStatus" style="width: 220px">
                    <el-option
                      v-for="s in statusOptions"
                      :key="s.value"
                      :label="s.label"
                      :value="s.value"
                      :disabled="s.disabled"
                    />
                  </el-select>
                  <span class="hr-emp-form__hint">改为「已离职」会自动禁用账号</span>
                </el-form-item>
                <el-form-item label="变更说明">
                  <el-input v-model="form.changeRemark" maxlength="255" placeholder="变更任职状态时必填" />
                </el-form-item>
                <el-form-item label="岗位">
                  <el-select v-model="form.positionId" clearable filterable style="width: 220px">
                    <el-option v-for="p in positionOptions" :key="p.id" :label="p.positionName" :value="p.id" />
                  </el-select>
                </el-form-item>
                <el-form-item label="直属上级">
                  <el-input-number v-model="form.directLeaderId" :min="1" :controls="false" placeholder="用户ID" />
                  <span class="hr-emp-form__hint">审批人「部门主管」优先取直属上级</span>
                </el-form-item>
                <el-form-item label="参加工作时间">
                  <el-date-picker v-model="form.workStartDate" type="date" value-format="YYYY-MM-DD" />
                </el-form-item>
                <el-form-item label="试用期截止">
                  <el-date-picker v-model="form.probationEndDate" type="date" value-format="YYYY-MM-DD" />
                </el-form-item>

                <el-divider content-position="left">个人信息</el-divider>
                <el-form-item label="手机号" prop="mobile">
                  <el-input v-model="form.mobile" maxlength="11" />
                </el-form-item>
                <el-form-item label="邮箱">
                  <el-input v-model="form.email" maxlength="100" />
                </el-form-item>
                <el-form-item label="身份证号" prop="idCard" v-if="detail.canViewSensitive">
                  <el-input v-model="form.idCard" maxlength="18" placeholder="仅人事可见" />
                </el-form-item>
                <el-form-item label="出生日期">
                  <el-date-picker v-model="form.birthDate" type="date" value-format="YYYY-MM-DD" />
                </el-form-item>
                <el-form-item label="婚姻状况">
                  <el-select v-model="form.maritalStatus" clearable style="width: 160px">
                    <el-option label="未婚" :value="1" />
                    <el-option label="已婚" :value="2" />
                    <el-option label="离异" :value="3" />
                    <el-option label="丧偶" :value="4" />
                  </el-select>
                </el-form-item>
                <el-form-item label="户籍地">
                  <el-input v-model="form.nativePlace" maxlength="100" />
                </el-form-item>
                <el-form-item label="现居住地址">
                  <el-input v-model="form.address" maxlength="255" />
                </el-form-item>
                <el-form-item label="紧急联系人">
                  <el-input v-model="form.emergencyName" maxlength="50" />
                </el-form-item>
                <el-form-item label="紧急联系电话" prop="emergencyPhone">
                  <el-input v-model="form.emergencyPhone" maxlength="11" />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" :loading="saving" @click="saveProfile">保存</el-button>
                </el-form-item>
              </el-form>

              <el-descriptions v-else :column="2" border>
                <el-descriptions-item label="登录账号">{{ detail.username }}</el-descriptions-item>
                <el-descriptions-item label="工号">{{ detail.workNumber || '-' }}</el-descriptions-item>
                <el-descriptions-item label="部门">{{ detail.deptName || '-' }}</el-descriptions-item>
                <el-descriptions-item label="岗位">{{ detail.positionName || detail.position || '-' }}</el-descriptions-item>
                <el-descriptions-item label="任职状态">{{ detail.employmentStatusLabel }}</el-descriptions-item>
                <el-descriptions-item label="账号状态">
                  {{ detail.status === 1 ? '启用' : detail.status === 0 ? '禁用' : '锁定' }}
                </el-descriptions-item>
                <el-descriptions-item label="入职日期">{{ detail.entryDate || '-' }}</el-descriptions-item>
                <el-descriptions-item label="司龄">{{ detail.seniority || '-' }}</el-descriptions-item>
                <el-descriptions-item label="直属上级">{{ detail.directLeaderName || '-' }}</el-descriptions-item>
                <el-descriptions-item label="试用期截止">{{ detail.probationEndDate || '-' }}</el-descriptions-item>
                <el-descriptions-item label="参加工作时间">{{ detail.workStartDate || '-' }}</el-descriptions-item>
                <el-descriptions-item label="手机号">{{ detail.mobile || '-' }}</el-descriptions-item>
                <el-descriptions-item label="邮箱">{{ detail.email || '-' }}</el-descriptions-item>
                <el-descriptions-item label="身份证号">{{ detail.idCard || '-' }}</el-descriptions-item>
                <el-descriptions-item label="出生日期">{{ detail.birthDate || '-' }}</el-descriptions-item>
                <el-descriptions-item label="户籍地">{{ detail.nativePlace || '-' }}</el-descriptions-item>
                <el-descriptions-item label="现居住地址" :span="2">{{ detail.address || '-' }}</el-descriptions-item>
                <el-descriptions-item label="紧急联系人">{{ detail.emergencyName || '-' }}</el-descriptions-item>
                <el-descriptions-item label="紧急联系电话">{{ detail.emergencyPhone || '-' }}</el-descriptions-item>
              </el-descriptions>

              <el-alert
                v-if="!detail.canViewSensitive"
                class="hr-emp-detail__mask"
                type="warning"
                :closable="false"
                show-icon
                title="敏感字段（身份证 / 银行卡）已脱敏，仅人事与超级管理员可查看原文。"
              />
            </el-tab-pane>

            <el-tab-pane :label="`合同（${detail.contracts?.length || 0}）`" name="contract">
              <el-table :data="detail.contracts" border size="small">
                <el-table-column prop="contractNo" label="合同编号" min-width="140" />
                <el-table-column prop="contractTypeLabel" label="类型" width="110" />
                <el-table-column prop="startDate" label="开始" width="110" />
                <el-table-column prop="endDate" label="结束" width="110">
                  <template #default="{ row }">{{ row.endDate || '无固定期限' }}</template>
                </el-table-column>
                <el-table-column prop="signCount" label="第几次" width="80" />
                <el-table-column label="到期" width="110">
                  <template #default="{ row }">
                    <span v-if="row.daysToExpire === undefined || row.daysToExpire === null">-</span>
                    <el-tag v-else :type="row.daysToExpire < 30 ? 'danger' : 'success'" effect="plain" size="small">
                      {{ row.daysToExpire < 0 ? '已过期' : `${row.daysToExpire} 天` }}
                    </el-tag>
                  </template>
                </el-table-column>
              </el-table>
              <el-empty v-if="!detail.contracts?.length" description="暂无合同记录" :image-size="60" />
            </el-tab-pane>

            <el-tab-pane :label="`证照（${detail.certs?.length || 0}）`" name="cert">
              <el-table :data="detail.certs" border size="small">
                <el-table-column prop="certTypeLabel" label="类型" width="110" />
                <el-table-column prop="certName" label="证照名称" min-width="150" />
                <el-table-column prop="certNo" label="编号" min-width="130" />
                <el-table-column prop="issueOrg" label="发证机构" min-width="140" />
                <el-table-column prop="expireDate" label="有效期至" width="120">
                  <template #default="{ row }">{{ row.expireDate || '长期' }}</template>
                </el-table-column>
              </el-table>
              <el-empty v-if="!detail.certs?.length" description="暂无证照记录" :image-size="60" />
            </el-tab-pane>

            <el-tab-pane :label="`异动记录（${detail.changes?.length || 0}）`" name="change">
              <el-timeline v-if="detail.changes?.length">
                <el-timeline-item
                  v-for="c in detail.changes"
                  :key="c.id"
                  :timestamp="c.effectiveDate"
                  placement="top"
                >
                  <div class="hr-emp-detail__change">
                    <b>{{ c.changeTypeLabel }}</b>
                    <span v-if="c.remark" class="hr-emp-detail__change-remark">{{ c.remark }}</span>
                    <div class="hr-emp-detail__change-sub">
                      {{ c.beforeJson }} → {{ c.afterJson }}
                      <span v-if="c.operatorName">（{{ c.operatorName }}）</span>
                    </div>
                  </div>
                </el-timeline-item>
              </el-timeline>
              <el-empty v-else description="暂无异动记录" :image-size="60" />
            </el-tab-pane>
          </el-tabs>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.hr-employee__head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.hr-employee__filters {
  flex: 1 1 auto;
}

.hr-employee__toolbar {
  flex: 0 0 auto;
}

.hr-employee__tip {
  margin: 4px 0 12px;
}

.hr-employee__pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

.hr-emp-detail__head {
  display: flex;
  align-items: center;
  gap: 14px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  margin-bottom: 8px;
}

.hr-emp-detail__avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: 12px;
  color: #fff;
  font-size: 20px;
  font-weight: 600;
  background-image: linear-gradient(135deg, #6aa8ff 0%, #2f6ff5 100%);
}

.hr-emp-detail__meta {
  flex: 1 1 auto;
  min-width: 0;
}

.hr-emp-detail__name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
}

.hr-emp-detail__sub {
  margin-top: 4px;
  color: #8a94a5;
  font-size: 12.5px;
}

.hr-emp-form__hint {
  margin-left: 8px;
  color: #8a94a5;
  font-size: 12px;
}

.hr-emp-detail__mask {
  margin-top: 12px;
}

.hr-emp-detail__change-remark {
  margin-left: 8px;
  color: #8a94a5;
}

.hr-emp-detail__change-sub {
  margin-top: 2px;
  color: #8a94a5;
  font-size: 12px;
}
.hr-employee__completeness {
  margin-bottom: 12px;
}
</style>
