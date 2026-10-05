<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { getDashboardOverviewApi } from '@/api/dashboard'
import { useEcharts } from '@/composables/useEcharts'
import type { DashboardOverviewVO } from '@/api/types'

/**
 * 公告级别样式。
 *
 * <p>取值与后端 oa_notice.notice_level、数据字典 notice_level 一一对应。
 * 这里做前端映射而不是再拉一次字典接口：工作台的定位是一次请求出图，
 * 为一个标签多打一次往返不划算；字典化改造放到公告模块（第二轮）时统一处理。</p>
 */
const NOTICE_LEVEL: Record<number, { label: string; type: 'info' | 'warning' | 'danger' }> = {
  1: { label: '普通', type: 'info' },
  2: { label: '重要', type: 'warning' },
  3: { label: '紧急', type: 'danger' },
}

const userStore = useUserStore()
const router = useRouter()

const loading = ref(false)
const overview = ref<DashboardOverviewVO | null>(null)

const trendChartEl = ref<HTMLElement>()
const pieChartEl = ref<HTMLElement>()
const trendChart = useEcharts(trendChartEl)
const pieChart = useEcharts(pieChartEl)

const stats = computed(() => [
  { key: 'user', label: '员工总数', value: overview.value?.userCount ?? 0, icon: 'User', color: '#409eff' },
  { key: 'dept', label: '部门总数', value: overview.value?.deptCount ?? 0, icon: 'OfficeBuilding', color: '#67c23a' },
  { key: 'todo', label: '我的待办', value: overview.value?.todoCount ?? 0, icon: 'Clock', color: '#e6a23c', to: '/workflow/todo' },
  { key: 'mine', label: '我的发起', value: overview.value?.myApplyCount ?? 0, icon: 'Document', color: '#909399', to: '/workflow/mine' },
])

const shortcuts = [
  { label: '发起申请', icon: 'EditPen', to: '/workflow/apply' },
  { label: '我的待办', icon: 'Clock', to: '/workflow/todo' },
  { label: '我的已办', icon: 'Select', to: '/workflow/done' },
  { label: '个人中心', icon: 'User', to: '/profile' },
]

const hasTrend = computed(() => (overview.value?.monthlyTrend ?? []).some((item) => item.total > 0))
const hasDistribution = computed(() => (overview.value?.typeDistribution ?? []).length > 0)

/** 月份轴显示为「4月」，比「2026-04」更省横向空间 */
const trendLabels = computed(() =>
  (overview.value?.monthlyTrend ?? []).map((item) => {
    const month = item.label.split('-')[1]
    return month ? `${Number(month)}月` : item.label
  }),
)

async function loadOverview() {
  loading.value = true
  try {
    overview.value = await getDashboardOverviewApi()
    // 等图表容器被 v-if 渲染出来再 init，否则拿到 0 宽画布、图表不显示
    await nextTick()
    renderCharts()
  } catch {
    // 错误提示已由请求拦截器统一给出，这里只需保证不卡在 loading
  } finally {
    loading.value = false
  }
}

function renderCharts() {
  const trend = overview.value?.monthlyTrend ?? []
  trendChart.render({
    tooltip: { trigger: 'axis' },
    grid: { left: 44, right: 20, top: 24, bottom: 28 },
    xAxis: { type: 'category', boundaryGap: false, data: trendLabels.value },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      {
        name: '审批数量',
        type: 'line',
        smooth: true,
        symbolSize: 6,
        data: trend.map((item) => item.total),
        itemStyle: { color: '#409eff' },
        lineStyle: { color: '#409eff', width: 2 },
        areaStyle: { color: 'rgba(64,158,255,0.12)' },
      },
    ],
  })

  const distribution = overview.value?.typeDistribution ?? []
  pieChart.render({
    tooltip: { trigger: 'item', formatter: '{b}：{c} 单（{d}%）' },
    legend: { bottom: 0, type: 'scroll', icon: 'circle' },
    color: ['#409eff', '#67c23a', '#e6a23c', '#f56c6c', '#909399', '#9b59b6'],
    series: [
      {
        name: '审批类型',
        type: 'pie',
        radius: ['42%', '62%'],
        center: ['50%', '44%'],
        avoidLabelOverlap: true,
        label: { formatter: '{b}\n{c} 单' },
        data: distribution.map((item) => ({ name: item.label, value: item.total })),
      },
    ],
  })
}

function goTo(path?: string) {
  if (path) {
    router.push(path)
  }
}

onMounted(loadOverview)
</script>

<template>
  <div v-loading="loading" class="dashboard">
    <el-card shadow="never" class="dashboard__welcome">
      <h2>欢迎使用 OA 企业办公管理系统</h2>
      <p>
        当前登录：{{ userStore.displayName }}
        <template v-if="userStore.userInfo?.deptName">（{{ userStore.userInfo.deptName }}）</template>
      </p>
      <p class="dashboard__roles">
        角色：<el-tag v-for="role in userStore.roles" :key="role" size="small">{{ role }}</el-tag>
      </p>
    </el-card>

    <el-row :gutter="16">
      <el-col v-for="item in stats" :key="item.key" :xs="12" :sm="12" :md="6">
        <el-card
          shadow="hover"
          class="stat"
          :class="{ 'stat--clickable': item.to }"
          @click="goTo(item.to)"
        >
          <div class="stat__body">
            <el-icon :size="30" :color="item.color">
              <component :is="item.icon" />
            </el-icon>
            <div>
              <div class="stat__label">{{ item.label }}</div>
              <div class="stat__value">{{ item.value }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never">
      <template #header>
        <span class="dashboard__title">快捷入口</span>
      </template>
      <div class="shortcuts">
        <el-button v-for="item in shortcuts" :key="item.to" :icon="item.icon" plain @click="goTo(item.to)">
          {{ item.label }}
        </el-button>
      </div>
    </el-card>

    <el-row :gutter="16">
      <el-col :xs="24" :md="16">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <span class="dashboard__title">近 6 个月审批数量趋势</span>
          </template>
          <div v-if="hasTrend" ref="trendChartEl" class="chart" />
          <el-empty v-else description="暂无审批数据" :image-size="80" />
        </el-card>
      </el-col>
      <el-col :xs="24" :md="8">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <span class="dashboard__title">审批类型占比</span>
          </template>
          <div v-if="hasDistribution" ref="pieChartEl" class="chart" />
          <el-empty v-else description="暂无审批数据" :image-size="80" />
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never">
      <template #header>
        <span class="dashboard__title">最新公告</span>
      </template>
      <ul v-if="(overview?.latestNotices ?? []).length > 0" class="notice-list">
        <li v-for="notice in overview?.latestNotices" :key="notice.id" class="notice-list__item">
          <el-tag v-if="notice.topFlag === 1" size="small" type="danger" effect="plain">置顶</el-tag>
          <el-tag size="small" :type="NOTICE_LEVEL[notice.noticeLevel]?.type ?? 'info'" effect="plain">
            {{ NOTICE_LEVEL[notice.noticeLevel]?.label ?? '普通' }}
          </el-tag>
          <span class="notice-list__title">{{ notice.noticeTitle }}</span>
          <span class="notice-list__meta">
            {{ notice.publisherName || '系统' }} · {{ notice.publishTime || '-' }}
          </span>
        </li>
      </ul>
      <el-empty v-else description="暂无公告" :image-size="80" />
    </el-card>
  </div>
</template>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.dashboard__welcome h2 {
  margin: 0 0 8px;
  font-size: 20px;
  font-weight: 600;
}

.dashboard__welcome p {
  margin: 4px 0;
  color: #606266;
}

.dashboard__roles {
  display: flex;
  align-items: center;
  gap: 8px;
}

.dashboard__title {
  font-weight: 600;
}

.stat {
  margin-bottom: 16px;
}

.stat--clickable {
  cursor: pointer;
}

.stat__body {
  display: flex;
  align-items: center;
  gap: 12px;
}

.stat__label {
  font-size: 13px;
  color: #909399;
}

.stat__value {
  margin-top: 4px;
  font-size: 24px;
  font-weight: 600;
  color: #303133;
}

.shortcuts {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.chart-card {
  height: 100%;
  margin-bottom: 16px;
}

.chart {
  height: 280px;
}

.notice-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.notice-list__item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 0;
  border-bottom: 1px solid #ebeef5;
}

.notice-list__item:last-child {
  border-bottom: none;
}

.notice-list__title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  color: #303133;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notice-list__meta {
  font-size: 13px;
  color: #909399;
}
</style>
