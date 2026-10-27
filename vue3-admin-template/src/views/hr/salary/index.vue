<script setup lang="ts">
import { onMounted, ref } from 'vue'
import type { FormInstance } from 'element-plus'
import { getDeptTreeApi } from '@/api/dept'
import { logAsyncError } from '@/utils/asyncGuard'
import { useSalaryRecord } from './composables/useSalaryRecord'
import { useSalaryProfile } from './composables/useSalaryProfile'
import { useSocialPolicy } from './composables/useSocialPolicy'

/**
 * 薪酬管理：三个标签页（月度核算 / 薪资档案 / 社保政策）。
 *
 * <p>FE-OPT-06 拆分：三块业务的状态与请求各自落在 `composables/useSalary*.ts`，
 * 本文件只做组装（同名解构，模板零改动）与本页公共的部门树、金额显示。
 * 下一步再把三个 tab 的模板与四个弹窗拆成 `tabs/` 与 `dialogs/` 子组件。</p>
 */

/** 右侧当前标签页 */
const activeTab = ref('record')

/** 档案表单实例：由本页持有并注入 useSalaryProfile，模板 ref 才能绑到同一个对象 */
const profileFormRef = ref<FormInstance>()

const {
  loadRecords,
  searchRecords,
  runCalc,
  transit,
  openDetail,
  submitAdjust,
  recordQuery,
  records,
  recordTotal,
  recordLoading,
  calcLoading,
  detailVisible,
  detail,
  adjustForm,
  canAdjust,
} = useSalaryRecord()

const {
  loadProfiles,
  loadUserOptions,
  openProfileForm,
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
} = useSalaryProfile({ formRef: profileFormRef })

const {
  loadPolicies,
  openPolicyForm,
  submitPolicy,
  removePolicy,
  policies,
  policyLoading,
  policyVisible,
  policyForm,
} = useSocialPolicy()

/* ==================== 部门树 ==================== */

const deptOptions = ref<{ value: number; label: string }[]>([])

async function loadDeptOptions() {
  try {
    const tree = await getDeptTreeApi()
    const flat: { value: number; label: string }[] = []
    const walk = (nodes: any[], depth = 0) => {
      for (const node of nodes || []) {
        flat.push({ value: node.id, label: `${'　'.repeat(depth)}${node.deptName}` })
        walk(node.children || [], depth + 1)
      }
    }
    walk(tree || [])
    deptOptions.value = flat
  } catch (error) {
    logAsyncError('loadDeptOptions', error)
  }
}

const money = (value?: number) => (value == null ? '-' : `¥${Number(value).toFixed(2)}`)

/** 折算系数：1 或空表示当月未折算，显示为「—」避免整列都是 1.0000 干扰阅读 */
const prorationText = (value?: number) => (value == null || Number(value) >= 1 ? '—' : Number(value).toFixed(4))

const statusMeta = (status: number) => {
  if (status === 4) {
    return { label: '已发放', type: 'success' as const }
  }
  if (status === 3) {
    return { label: '已封账', type: 'warning' as const }
  }
  if (status === 2) {
    return { label: '已复核', type: 'primary' as const }
  }
  return { label: '草稿', type: 'info' as const }
}

onMounted(async () => {
  await Promise.all([loadDeptOptions(), loadUserOptions()])
  await Promise.all([loadRecords(), loadProfiles(), loadPolicies()])
})
</script>


<template>
  <div class="salary">
    <el-card shadow="never">
      <el-tabs v-model="activeTab">
        <!-- ============ 月度核算 ============ -->
        <el-tab-pane label="月度核算" name="record">
          <el-form :inline="true" @submit.prevent>
            <el-form-item label="月份">
              <el-date-picker v-model="recordQuery.month" type="month" value-format="YYYY-MM" style="width: 140px" />
            </el-form-item>
            <el-form-item label="部门">
              <el-select v-model="recordQuery.deptId" clearable placeholder="全部" style="width: 170px">
                <el-option v-for="d in deptOptions" :key="d.value" :label="d.label" :value="d.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="姓名/工号">
              <el-input v-model="recordQuery.keyword" clearable style="width: 150px" @keyup.enter="searchRecords" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="searchRecords">查询</el-button>
              <el-button v-permission="'hr:salary:calc'" type="primary" plain :loading="calcLoading" @click="runCalc">
                核算
              </el-button>
              <el-button v-permission="'hr:salary:review'" @click="transit('review')">复核</el-button>
              <el-button v-permission="'hr:salary:lock'" @click="transit('lock')">封账</el-button>
              <el-button v-permission="'hr:salary:pay'" @click="transit('pay')">发放</el-button>
            </el-form-item>
          </el-form>

          <el-alert
            class="salary__tip"
            type="info"
            :closable="false"
            show-icon
            title="核算前置：该月考勤月结已封账。顺序为 草稿 → 复核 → 封账 → 发放，复核后本人即可在「我的工资条」查看。"
          />

          <el-table v-loading="recordLoading" :data="records" border class="salary__record-table">
            <el-table-column prop="month" label="月份" width="95" />
            <el-table-column prop="workNumber" label="工号" width="105" />
            <el-table-column label="姓名" width="150">
              <template #default="{ row }">
                <span>{{ row.realName }}</span>
                <el-tooltip v-if="row.priorEmployerIncome" :content="row.priorEmployerTip" placement="top">
                  <el-tag size="small" type="warning" effect="plain" class="salary__prior">跨单位</el-tag>
                </el-tooltip>
              </template>
            </el-table-column>
            <el-table-column prop="deptName" label="部门" min-width="120" show-overflow-tooltip />
            <el-table-column label="折算系数" width="110" align="center">
              <template #default="{ row }">
                <el-tooltip
                  v-if="row.prorationFactor != null && row.prorationFactor < 1"
                  content="入职或离职当月按实际在职应出勤天数折算"
                  placement="top"
                >
                  <span class="salary__proration">{{ prorationText(row.prorationFactor) }}</span>
                </el-tooltip>
                <span v-else class="salary__muted">{{ prorationText(row.prorationFactor) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="折算后标准工资" width="140" align="right">
              <template #default="{ row }">{{ money(row.proratedStandard) }}</template>
            </el-table-column>
            <el-table-column label="应发" width="120" align="right">
              <template #default="{ row }">{{ money(row.grossPay) }}</template>
            </el-table-column>
            <el-table-column label="考勤扣款" width="115" align="right">
              <template #default="{ row }">{{ money(row.attendanceDeduct) }}</template>
            </el-table-column>
            <el-table-column label="社保+公积金" width="130" align="right">
              <template #default="{ row }">{{ money((row.socialPersonal || 0) + (row.fundPersonal || 0)) }}</template>
            </el-table-column>
            <el-table-column label="个税" width="110" align="right">
              <template #default="{ row }">{{ money(row.tax) }}</template>
            </el-table-column>
            <el-table-column label="实发" width="120" align="right">
              <template #default="{ row }">
                <b class="salary__net">{{ money(row.netPay) }}</b>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="95">
              <template #default="{ row }">
                <el-tag :type="statusMeta(row.status).type" effect="plain">{{ row.statusLabel }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openDetail(row)">明细</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            class="salary__pager"
            :current-page="recordQuery.pageNo"
            :page-size="recordQuery.pageSize"
            :total="recordTotal"
            layout="total, prev, pager, next"
            background
            @current-change="(p: number) => { recordQuery.pageNo = p; loadRecords() }"
          />
        </el-tab-pane>

        <!-- ============ 薪资档案 ============ -->
        <el-tab-pane label="薪资档案" name="profile">
          <el-form :inline="true" @submit.prevent>
            <el-form-item label="姓名/工号">
              <el-input v-model="profileQuery.keyword" clearable style="width: 160px" @keyup.enter="loadProfiles" />
            </el-form-item>
            <el-form-item label="部门">
              <el-select v-model="profileQuery.deptId" clearable placeholder="全部" style="width: 170px">
                <el-option v-for="d in deptOptions" :key="d.value" :label="d.label" :value="d.value" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="profileQuery.pageNo = 1; loadProfiles()">查询</el-button>
              <el-button v-permission="'hr:salary:profile'" type="primary" plain @click="openProfileForm()">
                新增档案
              </el-button>
            </el-form-item>
          </el-form>

          <el-table v-loading="profileLoading" :data="profiles" border class="salary__profile-table">
            <el-table-column prop="workNumber" label="工号" width="110" />
            <el-table-column prop="realName" label="姓名" width="95" />
            <el-table-column prop="deptName" label="部门" min-width="120" show-overflow-tooltip />
            <el-table-column prop="effectiveDate" label="生效日期" width="120" />
            <el-table-column label="基本工资" width="110" align="right">
              <template #default="{ row }">{{ money(row.baseSalary) }}</template>
            </el-table-column>
            <el-table-column label="岗位工资" width="110" align="right">
              <template #default="{ row }">{{ money(row.postSalary) }}</template>
            </el-table-column>
            <el-table-column label="绩效基数" width="110" align="right">
              <template #default="{ row }">{{ money(row.perfSalary) }}</template>
            </el-table-column>
            <el-table-column label="津贴" width="100" align="right">
              <template #default="{ row }">{{ money(row.allowance) }}</template>
            </el-table-column>
            <el-table-column label="月标准" width="115" align="right">
              <template #default="{ row }"><b>{{ money(row.standardSalary) }}</b></template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button v-permission="'hr:salary:profile'" link type="primary" @click="openProfileForm(row)">调薪</el-button>
                <el-button link type="primary" @click="openHistory(row)">历史</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            class="salary__pager"
            :current-page="profileQuery.pageNo"
            :page-size="profileQuery.pageSize"
            :total="profileTotal"
            layout="total, prev, pager, next"
            background
            @current-change="(p: number) => { profileQuery.pageNo = p; loadProfiles() }"
          />
        </el-tab-pane>

        <!-- ============ 社保政策 ============ -->
        <el-tab-pane label="社保政策" name="policy">
          <div class="salary__toolbar">
            <el-button v-permission="'hr:salary:profile'" type="primary" plain @click="openPolicyForm()">
              新增政策
            </el-button>
            <span class="salary__hint">基数为 0 表示不设限；比例用小数（0.08 = 8%）</span>
          </div>
          <el-table v-loading="policyLoading" :data="policies" border class="salary__policy-table">
            <el-table-column prop="city" label="城市" width="110" />
            <el-table-column prop="policyYear" label="年度" width="90" />
            <el-table-column label="社保基数区间" width="190">
              <template #default="{ row }">{{ row.baseMin }} ~ {{ row.baseMax || '不封顶' }}</template>
            </el-table-column>
            <el-table-column label="公积金基数区间" width="200">
              <template #default="{ row }">{{ row.fundBaseMin }} ~ {{ row.fundBaseMax || '不封顶' }}</template>
            </el-table-column>
            <el-table-column label="个人比例" width="110">
              <template #default="{ row }">{{ (Number(row.personalRateTotal) * 100).toFixed(2) }}%</template>
            </el-table-column>
            <el-table-column label="单位比例" min-width="110">
              <template #default="{ row }">
                {{ (((Number(row.pensionCompany) + Number(row.medicalCompany) + Number(row.unemploymentCompany)
                  + Number(row.injuryCompany) + Number(row.maternityCompany) + Number(row.fundCompany)) * 100)).toFixed(2) }}%
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
            <el-table-column label="操作" width="140" fixed="right">
              <template #default="{ row }">
                <el-button v-permission="'hr:salary:profile'" link type="primary" @click="openPolicyForm(row)">编辑</el-button>
                <el-button v-permission="'hr:salary:profile'" link type="danger" @click="removePolicy(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- ============ 核算明细 ============ -->
    <el-dialog v-model="detailVisible" title="薪资明细" width="760px">
      <template v-if="detail">
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="姓名">{{ detail.realName }}</el-descriptions-item>
          <el-descriptions-item label="工号">{{ detail.workNumber }}</el-descriptions-item>
          <el-descriptions-item label="月份">{{ detail.month }}</el-descriptions-item>
          <el-descriptions-item label="应发">{{ money(detail.grossPay) }}</el-descriptions-item>
          <el-descriptions-item label="实发">
            <b class="salary__net">{{ money(detail.netPay) }}</b>
          </el-descriptions-item>
          <el-descriptions-item label="社保基数">{{ money(detail.socialBase) }}</el-descriptions-item>
          <el-descriptions-item label="累计应纳税所得额">{{ money(detail.cumulativeTaxable) }}</el-descriptions-item>
          <el-descriptions-item label="单位成本">
            {{ money((detail.socialCompany || 0) + (detail.fundCompany || 0)) }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">{{ detail.statusLabel }}</el-descriptions-item>
        </el-descriptions>

        <el-table :data="detail.items" border size="small" class="salary__items">
          <el-table-column prop="itemName" label="项目" min-width="140" />
          <el-table-column label="类型" width="90">
            <template #default="{ row }">
              <el-tag :type="row.itemType === 1 ? 'success' : 'danger'" effect="plain" size="small">
                {{ row.itemType === 1 ? '收入' : '扣款' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="金额" width="130" align="right">
            <template #default="{ row }">{{ money(row.amount) }}</template>
          </el-table-column>
        </el-table>

        <el-divider content-position="left">手工调整</el-divider>
        <el-alert
          v-if="!canAdjust"
          type="warning"
          :closable="false"
          show-icon
          title="已封账/已发放，不能再调整"
        />
        <el-form v-else :inline="true" label-width="90px">
          <el-form-item label="其他收入">
            <el-input-number v-model="adjustForm.otherIncome" :min="0" :precision="2" :step="100" style="width: 150px" />
          </el-form-item>
          <el-form-item label="其他扣款">
            <el-input-number v-model="adjustForm.otherDeduct" :min="0" :precision="2" :step="100" style="width: 150px" />
          </el-form-item>
          <el-form-item label="调整原因">
            <el-input v-model="adjustForm.remark" placeholder="必填，会写入调整留痕" style="width: 260px" />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button v-if="canAdjust" type="primary" @click="submitAdjust">保存调整</el-button>
      </template>
    </el-dialog>

    <!-- ============ 档案表单 ============ -->
    <el-dialog v-model="profileFormVisible" title="薪资档案 / 调薪" width="640px">
      <el-alert
        class="salary__tip"
        type="info"
        :closable="false"
        show-icon
        title="调薪不是覆盖原记录，而是新增一段更晚生效的档案——历史月份必须能算回原值。"
      />
      <el-form ref="profileFormRef" :model="profileForm" :rules="profileRules" label-width="110px">
        <el-form-item label="员工" prop="userId">
          <el-select v-model="profileForm.userId" filterable placeholder="选择员工" style="width: 100%">
            <el-option v-for="u in userOptions" :key="u.value" :label="u.label" :value="u.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="生效日期" prop="effectiveDate">
          <el-date-picker v-model="profileForm.effectiveDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="基本工资">
              <el-input-number v-model="profileForm.baseSalary" :min="0" :precision="2" :step="500" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="岗位工资">
              <el-input-number v-model="profileForm.postSalary" :min="0" :precision="2" :step="500" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="绩效基数">
              <el-input-number v-model="profileForm.perfSalary" :min="0" :precision="2" :step="500" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="固定津贴">
              <el-input-number v-model="profileForm.allowance" :min="0" :precision="2" :step="100" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="社保基数">
              <el-input-number v-model="profileForm.socialBase" :min="0" :precision="2" :step="100" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="公积金基数">
              <el-input-number v-model="profileForm.fundBase" :min="0" :precision="2" :step="100" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="参保城市">
              <el-input v-model="profileForm.city" placeholder="默认" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="专项附加扣除">
              <el-input-number v-model="profileForm.specialDeduct" :min="0" :precision="2" :step="500" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="调薪原因">
          <el-input v-model="profileForm.changeReason" placeholder="如：年度调薪 / 晋升" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="profileFormVisible = false">取消</el-button>
        <el-button type="primary" @click="submitProfile">保存</el-button>
      </template>
    </el-dialog>

    <!-- ============ 档案历史 ============ -->
    <el-dialog v-model="historyVisible" title="薪资档案历史" width="760px">
      <el-table :data="history" border size="small">
        <el-table-column prop="effectiveDate" label="生效日期" width="120" />
        <el-table-column label="基本" width="100" align="right">
          <template #default="{ row }">{{ money(row.baseSalary) }}</template>
        </el-table-column>
        <el-table-column label="岗位" width="100" align="right">
          <template #default="{ row }">{{ money(row.postSalary) }}</template>
        </el-table-column>
        <el-table-column label="绩效" width="100" align="right">
          <template #default="{ row }">{{ money(row.perfSalary) }}</template>
        </el-table-column>
        <el-table-column label="津贴" width="100" align="right">
          <template #default="{ row }">{{ money(row.allowance) }}</template>
        </el-table-column>
        <el-table-column label="月标准" width="115" align="right">
          <template #default="{ row }"><b>{{ money(row.standardSalary) }}</b></template>
        </el-table-column>
        <el-table-column prop="changeReason" label="原因" min-width="150" show-overflow-tooltip />
      </el-table>
    </el-dialog>

    <!-- ============ 社保政策表单 ============ -->
    <el-dialog v-model="policyVisible" title="社保公积金政策" width="720px">
      <el-form :model="policyForm" label-width="110px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="城市">
              <el-input v-model="policyForm.city" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="年度">
              <el-input-number v-model="policyForm.policyYear" :min="2000" :max="2100" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="社保基数下限">
              <el-input-number v-model="policyForm.baseMin" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="社保基数上限">
              <el-input-number v-model="policyForm.baseMax" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="公积金下限">
              <el-input-number v-model="policyForm.fundBaseMin" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="公积金上限">
              <el-input-number v-model="policyForm.fundBaseMax" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-divider content-position="left">缴费比例（0.08 = 8%）</el-divider>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="养老个人">
              <el-input-number v-model="policyForm.pensionPersonal" :min="0" :max="1" :step="0.005" :precision="4" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="养老单位">
              <el-input-number v-model="policyForm.pensionCompany" :min="0" :max="1" :step="0.005" :precision="4" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="医疗个人">
              <el-input-number v-model="policyForm.medicalPersonal" :min="0" :max="1" :step="0.005" :precision="4" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="医疗单位">
              <el-input-number v-model="policyForm.medicalCompany" :min="0" :max="1" :step="0.005" :precision="4" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="失业个人">
              <el-input-number v-model="policyForm.unemploymentPersonal" :min="0" :max="1" :step="0.005" :precision="4" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="失业单位">
              <el-input-number v-model="policyForm.unemploymentCompany" :min="0" :max="1" :step="0.005" :precision="4" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="工伤单位">
              <el-input-number v-model="policyForm.injuryCompany" :min="0" :max="1" :step="0.001" :precision="4" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="生育单位">
              <el-input-number v-model="policyForm.maternityCompany" :min="0" :max="1" :step="0.001" :precision="4" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="公积金个人">
              <el-input-number v-model="policyForm.fundPersonal" :min="0" :max="1" :step="0.01" :precision="4" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="公积金单位">
              <el-input-number v-model="policyForm.fundCompany" :min="0" :max="1" :step="0.01" :precision="4" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="policyForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="policyVisible = false">取消</el-button>
        <el-button type="primary" @click="submitPolicy">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.salary__tip {
  margin-bottom: 12px;
}

.salary__toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.salary__hint {
  font-size: 12px;
  color: #909399;
}

.salary__net {
  color: #f56c6c;
}

/* 折算系数：未折算时弱化显示，折算时用强调色让 HR 一眼看到「这个人当月不是整月」 */
.salary__proration {
  color: #e6a23c;
  font-weight: 600;
  cursor: help;
}

.salary__muted {
  color: var(--oa-text-tertiary, #697180);
}

/* 换单位标记：本年度在前单位有收入，个税需年度汇算合并 */
.salary__prior {
  margin-left: 6px;
  cursor: help;
}

.salary__pager {
  margin-top: 12px;
  justify-content: flex-end;
}

.salary__items {
  margin-top: 12px;
}
</style>
