<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getDeptTreeApi } from '@/api/dept'
import { logAsyncError } from '@/utils/asyncGuard'
import type { SalaryProfileVO, SalaryRecordVO, SocialPolicyVO } from '@/api/types'
import { money, prorationText, statusMeta } from './display'
import DetailDialog from './dialogs/DetailDialog.vue'
import ProfileFormDialog from './dialogs/ProfileFormDialog.vue'
import HistoryDialog from './dialogs/HistoryDialog.vue'
import PolicyFormDialog from './dialogs/PolicyFormDialog.vue'
import { useSalaryRecord } from './composables/useSalaryRecord'
import { useSalaryProfile } from './composables/useSalaryProfile'
import { useSocialPolicy } from './composables/useSocialPolicy'

/**
 * 薪酬管理（FE-OPT-06 拆分后）：三个标签页 + 四个弹窗组件。
 *
 * <p>列表状态在 `composables/useSalary*.ts`，弹窗各自持有表单/明细状态并只暴露 `open()`，
 * 保存成功后 emit `saved` 让本页重载对应列表。本文件只做组装与打开动作的转发。</p>
 */

/** 右侧当前标签页 */
const activeTab = ref('record')

const {
  loadRecords,
  searchRecords,
  runCalc,
  transit,
  recordQuery,
  records,
  recordTotal,
  recordLoading,
  calcLoading,
} = useSalaryRecord()

const { loadProfiles, profileQuery, profiles, profileTotal, profileLoading } = useSalaryProfile()

const { loadPolicies, removePolicy, policies, policyLoading } = useSocialPolicy()

/** 弹窗实例：状态都在组件内，页面只负责调用 open() */
const detailRef = ref<InstanceType<typeof DetailDialog> | null>(null)
const profileFormRef = ref<InstanceType<typeof ProfileFormDialog> | null>(null)
const historyRef = ref<InstanceType<typeof HistoryDialog> | null>(null)
const policyFormRef = ref<InstanceType<typeof PolicyFormDialog> | null>(null)

function openDetail(row: SalaryRecordVO) {
  void detailRef.value?.open(row)
}
/** 不传 row 为新增；传 row 为调薪 */
function openProfileForm(row?: SalaryProfileVO) {
  void profileFormRef.value?.open(row)
}
function openHistory(row: SalaryProfileVO) {
  void historyRef.value?.open(row)
}
function openPolicyForm(row?: SocialPolicyVO) {
  void policyFormRef.value?.open(row)
}

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

onMounted(async () => {
  await loadDeptOptions()
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

    <!-- 弹窗：各自持有状态，open(row) 打开，saved 后本页重载对应列表 -->
    <DetailDialog ref="detailRef" @saved="loadRecords" />
    <ProfileFormDialog ref="profileFormRef" @saved="loadProfiles" />
    <HistoryDialog ref="historyRef" />
    <PolicyFormDialog ref="policyFormRef" @saved="loadPolicies" />








  </div>
</template>

<style>
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
