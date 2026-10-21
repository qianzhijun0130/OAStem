<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getDashboardOverviewApi } from '@/api/dashboard'
import { getDoneApi, getMineApi } from '@/api/approval'
import { logAsyncError } from '@/utils/asyncGuard'
import ApprovalDetailDrawer from '@/views/workflow/components/ApprovalDetailDrawer.vue'
import type { ApprovalInstanceVO, DashboardOverviewVO } from '@/api/types'

/**
 * 分析中心 · 待办
 *
 * <p>按设计图还原「经营总览 → 模块维度指标 → 异常轨迹」三段式布局。
 * 指标全部来自现有接口（工作台聚合 + 我的已办/我发起的分页 total），不新增后端表；
 * 设计图里属于 ERP 域（销售额/运单/库存）的指标本项目没有对应数据，
 * 统一换成本系统真实可得的等价指标，避免展示假数字。</p>
 */

const loading = ref(false)
const overview = ref<DashboardOverviewVO | null>(null)

/** 计数聚合：每个字段都是某个分页接口的 total，取 pageSize=1 只为拿总数 */
const counts = ref({
  done: 0,
  approving: 0,
  passed: 0,
  rejected: 0,
})

const abnormalList = ref<ApprovalInstanceVO[]>([])

const detailVisible = ref(false)
const detailInstanceId = ref<number>()

const kpiCards = computed(() => [
  { label: '待办任务', value: overview.value?.todoCount ?? 0, unit: '条', tone: 'primary' },
  { label: '我发起的', value: overview.value?.myApplyCount ?? 0, unit: '条', tone: 'primary' },
  { label: '进行中流程', value: counts.value.approving, unit: '条', tone: 'primary' },
  { label: '已通过', value: counts.value.passed, unit: '条', tone: 'success' },
])

const moduleCards = computed(() => [
  { label: '待办任务', value: overview.value?.todoCount ?? 0, unit: '条', hint: '待我处理' },
  { label: '我的已办', value: counts.value.done, unit: '条', hint: '累计处理' },
  { label: '进行中审批', value: counts.value.approving, unit: '条', hint: '我发起的' },
  { label: '已通过', value: counts.value.passed, unit: '条', hint: '我发起的' },
  { label: '已驳回', value: counts.value.rejected, unit: '条', hint: '我发起的' },
  { label: '我发起的', value: overview.value?.myApplyCount ?? 0, unit: '条', hint: '全部状态' },
  { label: '用户总数', value: overview.value?.userCount ?? 0, unit: '人', hint: '系统在用' },
  { label: '部门总数', value: overview.value?.deptCount ?? 0, unit: '个', hint: '组织架构' },
])

/** 取「我的」某状态下发起的单据总数（pageSize=1 只为读 total） */
async function fetchMineTotal(status: number): Promise<number> {
  // 用「我的发起」而非管理员实例列表：无需 system:approval:instance，普通角色也能看到本页
  const page = await getMineApi({ status, pageNo: 1, pageSize: 1 })
  return Number(page.total ?? 0)
}

async function loadAll() {
  loading.value = true
  try {
    const [ov, done, approving, passed, rejectedPage] = await Promise.all([
      getDashboardOverviewApi(),
      getDoneApi({ pageNo: 1, pageSize: 1 }),
      fetchMineTotal(2),
      fetchMineTotal(3),
      fetchMineTotal(4),
    ])
    overview.value = ov
    counts.value = {
      done: Number(done.total ?? 0),
      approving,
      passed,
      rejected: rejectedPage,
    }
  } catch (error) {
    logAsyncError('加载分析中心指标失败', error)
  } finally {
    loading.value = false
  }
}

/** 异常轨迹：被驳回的单据（真实数据，无额外权限要求） */
async function loadAbnormal() {
  try {
    const page = await getMineApi({ status: 4, pageNo: 1, pageSize: 5 })
    abnormalList.value = page.records ?? []
  } catch (error) {
    logAsyncError('加载异常轨迹失败', error)
  }
}

/** 待办数也用于首屏提示；与 overview 同源，避免两处口径不一致 */
const todoCount = computed(() => overview.value?.todoCount ?? 0)

function openDetail(row: ApprovalInstanceVO) {
  detailInstanceId.value = row.id
  detailVisible.value = true
}

onMounted(async () => {
  await Promise.all([loadAll(), loadAbnormal()])
})
</script>

<template>
  <div class="analysis" v-loading="loading">
    <!-- 经营总览 -->
    <section class="analysis__section">
      <header class="analysis__head">
        <div>
          <h3 class="analysis__title">经营总览</h3>
          <p class="analysis__desc">按系统真实数据汇总，点击卡片可进入对应列表</p>
        </div>
        <el-tag v-if="todoCount > 0" type="warning" effect="plain" size="small">
          有 {{ todoCount }} 个待办任务
        </el-tag>
      </header>

      <div class="analysis__kpi">
        <div v-for="card in kpiCards" :key="card.label" class="kpi" :class="`kpi--${card.tone}`">
          <div class="kpi__label">
            {{ card.label }}
            <span class="kpi__unit">{{ card.unit }}</span>
          </div>
          <div class="kpi__value">{{ card.value }}</div>
        </div>
      </div>
    </section>

    <!-- 模块维度指标 -->
    <section class="analysis__section">
      <header class="analysis__head">
        <h3 class="analysis__title">模块维度指标</h3>
      </header>
      <div class="analysis__modules">
        <div v-for="item in moduleCards" :key="item.label" class="module">
          <div class="module__label">{{ item.label }}</div>
          <div class="module__value">
            {{ item.value }}
            <span class="module__unit">{{ item.unit }}</span>
          </div>
          <div class="module__hint">{{ item.hint }}</div>
        </div>
      </div>
    </section>

    <!-- 异常轨迹 -->
    <section class="analysis__section">
      <header class="analysis__head">
        <h3 class="analysis__title">异常轨迹</h3>
        <el-button link type="primary" @click="$router.push('/workflow/mine')">查看更多</el-button>
      </header>
      <ul v-if="abnormalList.length" class="abnormal">
        <li v-for="row in abnormalList" :key="row.id" class="abnormal__item">
          <span class="abnormal__dot" />
          <span class="abnormal__title">{{ row.title }}</span>
          <span class="abnormal__meta">{{ row.flowName }} · {{ row.createTime }}</span>
          <el-tag size="small" type="danger" effect="plain">{{ row.statusName || '已驳回' }}</el-tag>
          <el-button link type="primary" size="small" @click="openDetail(row)">查看</el-button>
        </li>
      </ul>
      <el-empty v-else description="暂无异常单据" :image-size="70" />
    </section>

    <ApprovalDetailDrawer v-model:visible="detailVisible" :instance-id="detailInstanceId" />
  </div>
</template>

<style scoped>
.analysis {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.analysis__section {
  padding: 16px 20px;
  background: #fff;
  border-radius: 6px;
}

.analysis__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.analysis__title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.analysis__desc {
  margin: 4px 0 0;
  font-size: 12px;
  color: #909399;
}

/* 4 列 → 窄屏自动降为 2 列 / 1 列 */
.analysis__kpi {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 12px;
}

.kpi {
  padding: 14px 16px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  background: #fff;
}

.kpi--primary {
  border-left: 3px solid var(--el-color-primary);
}

.kpi--success {
  border-left: 3px solid var(--el-color-success);
}

.kpi__label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  color: #606266;
}

.kpi__unit {
  font-size: 12px;
  color: #c0c4cc;
}

.kpi__value {
  margin-top: 10px;
  font-size: 26px;
  font-weight: 600;
  line-height: 1.2;
  color: #303133;
}

/* 设计图为「每行 4 个、共 2 行」，故固定 4 列后按断点降级 */
.analysis__modules {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

@media (max-width: 1200px) {
  .analysis__modules {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .analysis__modules {
    grid-template-columns: minmax(0, 1fr);
  }
}

.module {
  padding: 14px 16px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  background: #fcfcfd;
}

.module__label {
  font-size: 13px;
  color: #606266;
}

.module__value {
  margin-top: 8px;
  font-size: 22px;
  font-weight: 600;
  color: #303133;
}

.module__unit {
  font-size: 12px;
  font-weight: 400;
  color: #909399;
}

.module__hint {
  margin-top: 6px;
  font-size: 12px;
  color: #a8abb2;
}

.abnormal {
  margin: 0;
  padding: 0;
  list-style: none;
}

.abnormal__item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px dashed #ebeef5;
  font-size: 13px;
}

.abnormal__item:last-child {
  border-bottom: none;
}

.abnormal__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--el-color-danger);
  flex: 0 0 auto;
}

.abnormal__title {
  font-weight: 500;
  color: #303133;
}

.abnormal__meta {
  flex: 1;
  font-size: 12px;
  color: #909399;
}

@media (max-width: 640px) {
  .analysis__section {
    padding: 12px;
  }
}
</style>
