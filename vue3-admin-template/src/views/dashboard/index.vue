<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { getDashboardOverviewApi } from '@/api/dashboard'
import { getDoneApi, getMineApi, getTodoApi } from '@/api/approval'
import { logAsyncError } from '@/utils/asyncGuard'
import ApprovalDetailDrawer from '@/views/workflow/components/ApprovalDetailDrawer.vue'
import AppIcon from '@/components/icons/AppIcon.vue'
import type {
  ApprovalInstanceVO,
  ApprovalRecordVO,
  ApprovalTodoVO,
  DashboardOverviewVO,
  LatestNoticeVO,
} from '@/api/types'

/**
 * 工作台
 *
 * <p>布局按设计图还原：左列「问候条 → 应用中心 → 待办表（三态 Tab）」，右列「通知公告 → 日程待办」。
 * 数据全部走现有接口，不新增后端依赖：
 * <ul>
 *   <li>公告与计数：`/dashboard/overview`（一次请求出首屏）</li>
 *   <li>待办/已办/我发起的：审批模块三个列表接口，各取前 8 条</li>
 *   <li>日程日历：用「我的待办」的真实创建日期分组——目前没有独立日程表，
 *       后续接入日程模块时只需替换 {@link loadCalendarSource} 的数据来源</li>
 * </ul>
 * 设计图右侧的天气模块暂不实现（无数据源），故不占位。</p>
 */

const NOTICE_LEVEL: Record<number, { label: string; type: 'info' | 'warning' | 'danger' }> = {
  1: { label: '普通', type: 'info' },
  2: { label: '重要', type: 'warning' },
  3: { label: '紧急', type: 'danger' },
}

const TAB_TODO = 'todo'
const TAB_DONE = 'done'
const TAB_MINE = 'mine'

/** 表格行：三种来源统一成一个渲染模型，避免模板里写三套列 */
interface Row {
  key: string
  instanceId: number
  taskId?: string
  flowName: string
  code: string
  statusName: string
  statusType: 'info' | 'warning' | 'success' | 'danger' | 'primary'
  summary: string
  applicant: string
  company: string
  dept: string
  node: string
  time: string
}

const userStore = useUserStore()
const router = useRouter()

const loading = ref(false)
const overview = ref<DashboardOverviewVO | null>(null)

const activeTab = ref<string>(TAB_TODO)
const rows = ref<Row[]>([])
const tableLoading = ref(false)

const drawerVisible = ref(false)
const drawerInstanceId = ref<number>()

/** 日历：当前选中日期（默认今天） */
const calendarDate = ref(new Date())
/** 待办按创建日期分组，作为日历的「真实数据」来源 */
const todoByDay = ref<Record<string, ApprovalTodoVO[]>>({})

const shortcuts = [
  { label: '发起申请', icon: 'PenLine', to: '/workflow/apply' },
  { label: '我的待办', icon: 'Clock', to: '/workflow/todo' },
  { label: '我的已办', icon: 'CircleCheck', to: '/workflow/done' },
  { label: '个人中心', icon: 'User', to: '/profile' },
]

const displayName = computed(
  () => userStore.userInfo?.realName || userStore.userInfo?.username || '用户',
)

/** 问候语按当前小时切换，与设计图的「早上好」同义 */
const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '凌晨好'
  if (hour < 12) return '早上好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const WEEK = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']
const dateText = computed(() => {
  const now = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${WEEK[now.getDay()]}`
})

const notices = computed<LatestNoticeVO[]>(() => overview.value?.latestNotices ?? [])

/** 选中日期的待办（真实数据） */
const selectedDayKey = computed(() => {
  const d = calendarDate.value
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
})

const selectedDayTodos = computed(() => todoByDay.value[selectedDayKey.value] ?? [])

function statusTypeOf(status: number): Row['statusType'] {
  if (status === 3) return 'success'
  if (status === 4) return 'danger'
  if (status === 6) return 'primary'
  if (status === 5 || status === 7) return 'info'
  return 'warning'
}

function toTodoRow(item: ApprovalTodoVO): Row {
  return {
    key: `todo-${item.taskId}`,
    instanceId: item.instanceId,
    taskId: item.taskId,
    flowName: item.flowName ?? '-',
    code: `#${item.instanceId}`,
    statusName: '审批中',
    statusType: 'warning',
    summary: item.title,
    applicant: item.applicantName ?? '-',
    company: item.companyName ?? '-',
    dept: item.deptName ?? '-',
    node: item.currentNode ?? '-',
    time: item.createTime ?? '-',
  }
}

function toDoneRow(item: ApprovalRecordVO): Row {
  return {
    key: `done-${item.instanceId}-${item.createTime ?? ''}`,
    instanceId: item.instanceId,
    flowName: item.flowName ?? '-',
    code: `#${item.instanceId}`,
    statusName: item.actionName ?? '已处理',
    statusType: item.action === 'reject' ? 'danger' : 'success',
    summary: item.title ?? '-',
    applicant: item.handlerName ?? '-',
    // 已办行的公司/部门仍取「单据发起人」的归属，而不是处理人的
    company: item.companyName ?? '-',
    dept: item.deptName ?? '-',
    node: item.nodeName ?? '-',
    time: item.createTime ?? '-',
  }
}

function toMineRow(item: ApprovalInstanceVO): Row {
  return {
    key: `mine-${item.id}`,
    instanceId: item.id,
    flowName: item.flowName ?? '-',
    code: `#${item.id}`,
    statusName: item.statusName ?? '-',
    statusType: statusTypeOf(item.status),
    summary: item.title,
    applicant: item.applicantName ?? '-',
    company: item.companyName ?? '-',
    dept: item.deptName ?? '-',
    node: item.currentNode ?? '-',
    time: item.createTime ?? '-',
  }
}

async function loadTab(tab: string) {
  tableLoading.value = true
  try {
    if (tab === TAB_TODO) {
      const page = await getTodoApi({ pageNo: 1, pageSize: 8 })
      rows.value = (page.records ?? []).map(toTodoRow)
    } else if (tab === TAB_DONE) {
      const page = await getDoneApi({ pageNo: 1, pageSize: 8 })
      rows.value = (page.records ?? []).map(toDoneRow)
    } else {
      const page = await getMineApi({ pageNo: 1, pageSize: 8 })
      rows.value = (page.records ?? []).map(toMineRow)
    }
  } catch (error) {
    logAsyncError('加载工作台列表失败', error)
  } finally {
    tableLoading.value = false
  }
}

async function loadOverview() {
  loading.value = true
  try {
    overview.value = await getDashboardOverviewApi()
  } catch (error) {
    logAsyncError('加载工作台概览失败', error)
  } finally {
    loading.value = false
  }
}

/**
 * 日历数据：按「我的待办」的创建日期分组。
 *
 * <p>没有独立日程表，但日历必须显示真实数据而不是假点：待办就是用户每天最真实的待办事项。
 * 取 100 条足够覆盖一个月的可视范围。</p>
 */
async function loadCalendarSource() {
  try {
    const page = await getTodoApi({ pageNo: 1, pageSize: 100 })
    const grouped: Record<string, ApprovalTodoVO[]> = {}
    for (const item of page.records ?? []) {
      const day = (item.createTime ?? '').slice(0, 10)
      if (!day) continue
      ;(grouped[day] ??= []).push(item)
    }
    todoByDay.value = grouped
  } catch (error) {
    logAsyncError('加载日历数据失败', error)
  }
}

function dayCount(day: string): number {
  return todoByDay.value[day]?.length ?? 0
}

function go(path: string) {
  void router.push(path)
}

/** 各 Tab 的「查看更多」落到对应列表页 */
const morePath = computed(() => {
  if (activeTab.value === TAB_DONE) return '/workflow/done'
  if (activeTab.value === TAB_MINE) return '/workflow/mine'
  return '/workflow/todo'
})

function openRow(row: Row) {
  drawerInstanceId.value = row.instanceId
  drawerVisible.value = true
}

/** 抽屉里办完/撤销后刷新列表与日历，保证首页数据与后端一致 */
async function onDrawerDone() {
  await Promise.all([loadTab(activeTab.value), loadCalendarSource(), loadOverview()])
}

watch(activeTab, (tab) => void loadTab(tab))

onMounted(async () => {
  await Promise.all([loadOverview(), loadTab(TAB_TODO), loadCalendarSource()])
})
</script>

<template>
  <div class="db" v-loading="loading">
    <div class="db__main">
      <!-- 问候条 -->
      <section class="banner">
        <div class="banner__text">
          <h2 class="banner__title">{{ greeting }}，{{ displayName }} 欢迎回来，开始您的工作吧！</h2>
          <p class="banner__date">{{ dateText }}</p>
        </div>
      </section>

      <!-- 应用中心 -->
      <section class="panel">
        <header class="panel__head">
          <h3 class="panel__title">应用中心</h3>
        </header>
        <div class="apps">
          <button
            v-for="item in shortcuts"
            :key="item.label"
            type="button"
            class="app"
            @click="go(item.to)"
          >
            <span class="app__icon"><AppIcon :name="item.icon" :size="22" /></span>
            <span class="app__label">{{ item.label }}</span>
          </button>
        </div>
      </section>

      <!-- 待办三态 -->
      <section class="panel">
        <header class="panel__head panel__head--tabs">
          <el-tabs v-model="activeTab" class="db__tabs">
            <el-tab-pane label="我的待办" name="todo" />
            <el-tab-pane label="我的已办" name="done" />
            <el-tab-pane label="我发起的" name="mine" />
          </el-tabs>
          <el-button link type="primary" @click="go(morePath)">查看更多</el-button>
        </header>

        <el-table
          v-loading="tableLoading"
          :data="rows"
          size="small"
          class="db__table"
          empty-text="暂无数据"
        >
          <!--
            列宽按设计图压缩到 10 列一屏可见。
            ⚠️ 改成 6:4 后主区变窄：视口 ≥1700 时 10 列完整不滚动；1440 下主区约 709px，
            而 10 列最小需 ~868px，会出现横向滚动（el-table 自带，不撑破卡片）。
          -->
          <el-table-column prop="flowName" label="单据类型" min-width="100" show-overflow-tooltip />
          <el-table-column prop="code" label="单据编号" width="76" />
          <el-table-column label="审批状态" width="80">
            <template #default="{ row }">
              <el-tag size="small" effect="plain" :type="row.statusType">{{ row.statusName }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="summary" label="摘要" min-width="120" show-overflow-tooltip />
          <el-table-column
            prop="applicant"
            :label="activeTab === 'done' ? '处理人' : '发起人'"
            width="72"
            show-overflow-tooltip
          />
          <el-table-column prop="company" label="所属公司" width="84" show-overflow-tooltip />
          <el-table-column prop="dept" label="所属部门" width="84" show-overflow-tooltip />
          <el-table-column prop="node" label="任务节点" width="76" show-overflow-tooltip />
          <el-table-column prop="time" label="接收时间" width="120" />
          <el-table-column label="操作" width="56" fixed="right">
            <template #default="{ row }">
              <el-button icon-only link type="primary" size="small" @click="openRow(row)">
                {{ activeTab === 'todo' ? '办理' : '详情' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>
    </div>

    <aside class="db__aside">
      <!-- 通知公告 -->
      <section class="panel">
        <header class="panel__head">
          <h3 class="panel__title">通知公告</h3>
          <el-button link type="primary" @click="go('/notice/my')">查看更多</el-button>
        </header>
        <ul v-if="notices.length" class="notice">
          <li v-for="item in notices" :key="item.id" class="notice__item" @click="go('/notice/my')">
            <el-tag v-if="item.topFlag === 1" size="small" type="danger" effect="plain">置顶</el-tag>
            <el-tag
              v-else
              size="small"
              effect="plain"
              :type="NOTICE_LEVEL[item.noticeLevel]?.type ?? 'info'"
            >
              {{ NOTICE_LEVEL[item.noticeLevel]?.label ?? '普通' }}
            </el-tag>
            <span class="notice__title">{{ item.noticeTitle }}</span>
            <span class="notice__time">{{ (item.publishTime ?? '').slice(0, 10) }}</span>
          </li>
        </ul>
        <el-empty v-else description="暂无公告" :image-size="60" />
      </section>

      <!-- 日程待办 -->
      <section class="panel">
        <header class="panel__head">
          <h3 class="panel__title">日程待办</h3>
        </header>
        <el-calendar v-model="calendarDate" class="db__calendar">
          <template #date-cell="{ data }">
            <div class="cal-cell">
              <span class="cal-cell__day">{{ Number(data.day.slice(-2)) }}</span>
              <span v-if="dayCount(data.day)" class="cal-cell__badge">{{ dayCount(data.day) }}</span>
            </div>
          </template>
        </el-calendar>
        <div class="day">
          <div class="day__title">{{ selectedDayKey }} 的待办</div>
          <ul v-if="selectedDayTodos.length" class="day__list">
            <li v-for="item in selectedDayTodos" :key="item.taskId" class="day__item">
              <span class="day__dot" />
              <span class="day__text">{{ item.title }}</span>
              <span class="day__node">{{ item.currentNode }}</span>
            </li>
          </ul>
          <div v-else class="day__empty">当天暂无待办</div>
        </div>
      </section>
    </aside>

    <ApprovalDetailDrawer
      v-model:visible="drawerVisible"
      :instance-id="drawerInstanceId"
      @done="onDrawerDone"
    />
  </div>
</template>

<style scoped>
.db {
  display: grid;
  /* 主区:侧栏 = 6:4（原为 1fr + 固定 330px）：右栏的公告与日历在 330px 下明显偏窄 */
  grid-template-columns: minmax(0, 6fr) minmax(0, 4fr);
  gap: 16px;
  align-items: start;
}

.db__main,
.db__aside {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

/* ---------- 问候条 ---------- */
.banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 96px;
  padding: 18px 24px;
  border-radius: 6px;
  background: linear-gradient(120deg, #3d7eff 0%, #2f6bed 55%, #2a5fd8 100%);
  color: #fff;
}

.banner__title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  line-height: 1.5;
}

.banner__date {
  margin: 8px 0 0;
  /* 12px → 14px：日期+星期是问候语下的第二眼信息，原字号偏小、在高分屏上糊 */
  font-size: 14px;
  opacity: 0.85;
}

/* ---------- 卡片 ---------- */
.panel {
  padding: 14px 16px 16px;
  background: #fff;
  border-radius: 6px;
}

.panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.panel__head--tabs {
  align-items: flex-end;
  margin-bottom: 4px;
}

.panel__title {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.db__tabs {
  flex: 1;
  min-width: 0;
}

.db__tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
}

/* ---------- 应用中心 ---------- */
.apps {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  padding: 12px;
  background: #f4f8ff;
  border-radius: 4px;
}

.app {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 10px 4px;
  border: none;
  background: transparent;
  border-radius: 4px;
  cursor: pointer;
  transition: background-color 0.2s;
}

.app:hover {
  background: #e8f0ff;
}

.app__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: #fff;
  color: var(--el-color-primary);
  box-shadow: 0 1px 4px rgb(64 158 255 / 18%);
}

.app__label {
  font-size: 13px;
  color: #303133;
}

/* ---------- 表格 ---------- */
.db__table {
  width: 100%;
}

/* ---------- 公告 ---------- */
.notice {
  margin: 0;
  padding: 0;
  list-style: none;
}

.notice__item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 0;
  border-bottom: 1px dashed #ebeef5;
  cursor: pointer;
  font-size: 13px;
}

.notice__item:last-child {
  border-bottom: none;
}

.notice__title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #303133;
}

.notice__item:hover .notice__title {
  color: var(--el-color-primary);
}

.notice__time {
  flex: 0 0 auto;
  font-size: 12px;
  color: #a8abb2;
}

/* ---------- 日历 ---------- */
.db__calendar {
  --el-calendar-cell-width: auto;
}

.db__calendar :deep(.el-calendar__header) {
  padding: 8px 4px;
}

.db__calendar :deep(.el-calendar__body) {
  padding: 0 0 8px;
}

.db__calendar :deep(.el-calendar-table .el-calendar-day) {
  height: 38px;
  padding: 0;
}

.cal-cell {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  font-size: 12px;
}

.cal-cell__badge {
  position: absolute;
  top: 2px;
  right: 4px;
  min-width: 14px;
  height: 14px;
  padding: 0 3px;
  border-radius: 7px;
  background: var(--el-color-danger);
  color: #fff;
  font-size: 10px;
  line-height: 14px;
  text-align: center;
}

.day {
  margin-top: 8px;
  padding-top: 10px;
  border-top: 1px solid #f0f2f5;
}

.day__title {
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
  color: #606266;
}

.day__list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.day__item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  font-size: 13px;
}

.day__dot {
  flex: 0 0 auto;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--el-color-primary);
}

.day__text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #303133;
}

.day__node {
  font-size: 12px;
  color: #a8abb2;
}

.day__empty {
  padding: 8px 0;
  font-size: 12px;
  color: #a8abb2;
}

/* ---------- 响应式 ---------- */
@media (max-width: 1200px) {
  .db {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 768px) {
  .apps {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .banner {
    padding: 14px 16px;
  }

  .banner__title {
    font-size: 15px;
  }
}
</style>
