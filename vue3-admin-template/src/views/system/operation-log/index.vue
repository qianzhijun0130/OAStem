<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { cleanOperationLogApi, exportOperationLogApi, getOperationLogPageApi } from '@/api/log'
import { getUserPageApi } from '@/api/user'
import { useListQuery } from '@/composables/useListQuery'
import { logAsyncError } from '@/utils/asyncGuard'
import ListPage from '@/components/list/ListPage.vue'
import type { OperationLogPageQuery, OperationLogVO, UserVO } from '@/api/types'

const RETAIN_DAY_OPTIONS = [30, 90, 180, 365]

const exporting = ref(false)

/** el-date-picker 的区间值，取数前拆成 startTime / endTime */
const timeRange = ref<[string, string] | null>(null)

/** 页面私有查询条件：分页参数由 useListQuery 统一管理 */
interface OperationLogQuery extends Omit<OperationLogPageQuery, 'pageNo' | 'pageSize'> {}

const { list, total, loading, query, pageNo, pageSize, search, reset,
        handleSizeChange, handlePageChange } =
  useListQuery<OperationLogVO, OperationLogQuery>({
    initialQuery: () => ({
      userId: undefined,
      username: undefined,
      module: undefined,
      operationStatus: undefined,
      startTime: undefined,
      endTime: undefined,
    }),
    fetcher: (params) => getOperationLogPageApi(params),
    // 日期区间是页面私有语义，取数前映射为后端的 startTime/endTime
    beforeFetch: (q) => {
      q.startTime = timeRange.value?.[0]
      q.endTime = timeRange.value?.[1]
    },
    onError: (error) => logAsyncError('加载操作日志失败', error),
  })

const detailVisible = ref(false)
const currentLog = ref<OperationLogVO | null>(null)

/**
 * 「用户」精确筛选的可选项。
 *
 * <p>只有持有 {@code system:user:query} 的人才能拉到用户列表；
 * 拿不到时静默降级为「仅账号模糊查询」，页面不报错、不空白。</p>
 */
const userOptions = ref<UserVO[]>([])
const userOptionsLoaded = ref(false)

/** 是否展示「用户」下拉：仅超管 / 人事等有用户查询权限的角色 */
const canPickUser = computed(() => userOptions.value.length > 0)

const cleanVisible = ref(false)
/** 全量清理：true = 清除全部历史操作日志（后端仍保留本次操作的审计日志） */
const cleanAll = ref(false)
const cleanRetainDays = ref(90)
const cleaning = ref(false)

/** 重置：条件由 composable 归位，日期区间是页面私有状态需一并清空 */
function handleReset() {
  timeRange.value = null
  void reset()
}

function openDetail(row: OperationLogVO) {
  currentLog.value = row
  detailVisible.value = true
}

async function handleExport() {
  exporting.value = true
  try {
    await exportOperationLogApi({
      ...query,
      startTime: timeRange.value?.[0],
      endTime: timeRange.value?.[1],
    })
    ElMessage.success('导出已开始，请留意浏览器下载')
  } finally {
    exporting.value = false
  }
}

function openCleanDialog() {
  cleanRetainDays.value = 90
  cleanAll.value = false
  cleanVisible.value = true
}

function cleanBefore(): string {
  return formatDateTime(new Date(Date.now() - cleanRetainDays.value * 24 * 60 * 60 * 1000))
}

function formatDateTime(date: Date): string {
  const pad = (value: number) => String(value).padStart(2, '0')
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ` +
    `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
  )
}

async function handleClean() {
  const all = cleanAll.value
  const before = cleanBefore()
  const tip = all
    ? '将清空全部历史操作日志（含归档表中的历史副本）。追溯链将不可恢复。'
    : `将删除 ${before} 之前的所有操作日志，且无法恢复。`
  try {
    // 危险等级最高：全量清理要求连续两次确认
    await ElMessageBox.confirm(`${tip}\n\n此操作不可撤销，且会在审计日志中留痕。`, '清理操作日志',
      { type: 'warning', confirmButtonText: all ? '我了解风险，继续' : '确定清理' })
    if (all) {
      await ElMessageBox.confirm(
        '最后确认：真的要清空全部操作日志吗？该动作会销毁所有历史追溯凭据。',
        '全量清理二次确认',
        { type: 'error', confirmButtonText: '确认清空', cancelButtonText: '我再想想' },
      )
    }
  } catch {
    return
  }
  cleaning.value = true
  try {
    const deleted = await cleanOperationLogApi(all ? undefined : before, all)
    ElMessage.success(`已清理 ${deleted} 条操作日志`)
    cleanVisible.value = false
    await search()
  } finally {
    cleaning.value = false
  }
}

/** 请求参数是 JSON 串，格式化后展示更易读；格式化失败则原样显示 */
function prettyJson(text?: string): string {
  if (!text) {
    return '-'
  }
  try {
    return JSON.stringify(JSON.parse(text), null, 2)
  } catch {
    return text
  }
}

/** 尝试载入用户下拉；无权限或失败都静默降级 */
async function loadUserOptions() {
  try {
    const page = await getUserPageApi({ pageNo: 1, pageSize: 200 })
    userOptions.value = page.records ?? []
  } catch (error) {
    logAsyncError('加载用户下拉选项失败', error)
  } finally {
    userOptionsLoaded.value = true
  }
}

onMounted(() => {
  // 列表由 useListQuery 自动加载，这里只补用户下拉
  void loadUserOptions()
})
</script>

<template>
  <div class="log-page">
    <ListPage
      :data="list"
      :total="total"
      :loading="loading"
      :page-no="pageNo"
      :page-size="pageSize"
      :page-sizes="[10, 20, 50, 100]"
      pagination-layout="total, sizes, prev, pager, next"
      @size-change="handleSizeChange"
      @current-change="handlePageChange"
    >
      <template #filters>
        <el-form :inline="true" class="log-page__filters">
          <el-form-item v-if="canPickUser" label="用户">
            <el-select v-model="query.userId" placeholder="全部用户" clearable filterable class="log-page__select">
              <el-option v-for="u in userOptions" :key="u.id" :label="`${u.realName}（${u.username}）`" :value="u.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="操作账号">
            <el-input
              v-model="query.username"
              placeholder="支持模糊匹配"
              clearable
              class="log-page__input"
              @keyup.enter="search"
            />
          </el-form-item>
          <el-form-item label="模块">
            <el-input
              v-model="query.module"
              placeholder="如 公告管理"
              clearable
              class="log-page__input"
              @keyup.enter="search"
            />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="query.operationStatus" placeholder="全部" clearable class="log-page__select">
              <el-option label="成功" :value="1" />
              <el-option label="失败" :value="0" />
            </el-select>
          </el-form-item>
          <el-form-item label="操作时间">
            <el-date-picker
              v-model="timeRange"
              type="datetimerange"
              value-format="YYYY-MM-DD HH:mm:ss"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
              class="log-page__range"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="search">查询</el-button>
            <el-button @click="handleReset">重置</el-button>
          </el-form-item>
        </el-form>

        <!-- 工具栏保持在筛选区下方独立一行（与改造前视觉一致） -->
        <div class="log-page__toolbar">
          <el-button v-permission="'system:operation-log:export'" :loading="exporting" @click="handleExport">
            导出 Excel
          </el-button>
          <el-button v-permission="'system:operation-log:clean'" type="danger" plain @click="openCleanDialog">
            清理日志
          </el-button>
        </div>
      </template>

      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="username" label="操作账号" min-width="110" />
      <el-table-column prop="module" label="模块" width="120" />
      <el-table-column prop="operation" label="操作描述" min-width="180" show-overflow-tooltip />
      <el-table-column prop="method" label="请求方法" min-width="220" show-overflow-tooltip />
      <el-table-column prop="ipAddress" label="IP 地址" min-width="130" />
      <el-table-column label="耗时" width="90">
        <template #default="{ row }">
          {{ row.costTime === null || row.costTime === undefined ? '-' : `${row.costTime} ms` }}
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.operationStatus === 1 ? 'success' : 'danger'" size="small">
            {{ row.operationStatus === 1 ? '成功' : '失败' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="操作时间" width="170" />
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button icon-only link type="primary" size="small" @click="openDetail(row)">查看</el-button>
        </template>
      </el-table-column>
    </ListPage>

    <el-drawer v-model="detailVisible" title="操作日志详情" size="620px">
      <el-descriptions v-if="currentLog" :column="1" border>
        <el-descriptions-item label="操作账号">{{ currentLog.username || '-' }}</el-descriptions-item>
        <el-descriptions-item label="模块">{{ currentLog.module || '-' }}</el-descriptions-item>
        <el-descriptions-item label="操作描述">{{ currentLog.operation || '-' }}</el-descriptions-item>
        <el-descriptions-item label="请求方法">{{ currentLog.method || '-' }}</el-descriptions-item>
        <el-descriptions-item label="IP 地址">{{ currentLog.ipAddress || '-' }}</el-descriptions-item>
        <el-descriptions-item label="耗时">
          {{ currentLog.costTime ?? '-' }} ms
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="currentLog.operationStatus === 1 ? 'success' : 'danger'" size="small">
            {{ currentLog.operationStatus === 1 ? '成功' : '失败' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="操作时间">{{ currentLog.createTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="请求参数">
          <pre class="log-page__json">{{ prettyJson(currentLog.requestParams) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item v-if="currentLog.errorMsg" label="错误信息">
          <pre class="log-page__json log-page__json--error">{{ currentLog.errorMsg }}</pre>
        </el-descriptions-item>
      </el-descriptions>
    </el-drawer>

    <ResponsiveDialog v-model="cleanVisible" title="清理操作日志" width="460px">
      <el-alert
        type="warning"
        :closable="false"
        title="清理后不可恢复，请确认保留区间与合规要求一致。"
      />
      <el-form label-width="110px" class="log-page__clean-form">
        <el-form-item label="清理范围">
          <el-radio-group v-model="cleanAll">
            <el-radio :value="false">按时间</el-radio>
            <el-radio :value="true">全量（危险）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="!cleanAll" label="保留最近">
          <el-select v-model="cleanRetainDays">
            <el-option v-for="day in RETAIN_DAY_OPTIONS" :key="day" :label="`${day} 天`" :value="day" />
          </el-select>
        </el-form-item>
        <el-form-item label="将删除">
          <span v-if="cleanAll" class="log-page__clean-danger">
            全部历史日志（本次操作的审计日志仍会保留）
          </span>
          <span v-else class="log-page__clean-tip">{{ cleanBefore() }} 之前的日志</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cleanVisible = false">取消</el-button>
        <el-button type="danger" :loading="cleaning" @click="handleClean">确定清理</el-button>
      </template>
    </ResponsiveDialog>
  </div>
</template>

<style scoped>
.log-page__filters {
  display: flex;
  flex-wrap: wrap;
  gap: 0 4px;
}

.log-page__input {
  width: 170px;
}

.log-page__select {
  width: 130px;
}

.log-page__range {
  width: 380px;
}

.log-page__toolbar {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-bottom: 12px;
}

.log-page__json {
  margin: 0;
  max-height: 320px;
  overflow: auto;
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}

.log-page__json--error {
  color: #f56c6c;
}

.log-page__clean-form {
  margin-top: 16px;
}

.log-page__clean-tip {
  color: #f56c6c;
  font-weight: 600;
}
 .log-page__clean-danger {
  color: var(--el-color-danger);
  font-weight: 500;
}
</style>
